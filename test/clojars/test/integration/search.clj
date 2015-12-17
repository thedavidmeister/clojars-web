(ns clojars.test.integration.search
  (:require [cheshire.core :as json]
            [clj-http.lite.client :as client]
            [clojars.search :as search]
            [clojars.test.test-helper :as help]
            [clojure.set :as set]
            [clojure.test :refer :all]))

(use-fixtures :each
  help/default-fixture
  help/run-test-app)

(defn do-search [fmt query & [opts]]
  (-> (format "http://localhost:%s/search?q=%s&format=%s"
        help/test-port
        query
        (name fmt))
    (client/get opts)))

(deftest search-test
  (let [base-data {:description "foo" :version "1.0"}
        search-data (map (partial merge base-data)
                      [{:jar_name "test" :group_name "test"}
                       {:jar_name "test" :group_name "testing"}])]
    (doseq [data search-data]
      (search/index! (:search help/system)
        (set/rename-keys data {:jar_name :artifact-id
                               :group_name :group-id})))

    (testing "json request returns json"
      (let [resp (do-search :json "test")]
        (is (= 200 (:status resp)))
        (is (= "application/json" (help/get-content-type resp)))))

    (testing "default request returns html"
      (let [resp (do-search "" "test")]
        (is (= 200 (:status resp)))
        (is (= "text/html" (help/get-content-type resp)))))

    (testing "unknown format request returns html" ;; debatable behavior
      (let [resp (do-search :ham "test")]
        (is (= 200 (:status resp)))
        (is (= "text/html" (help/get-content-type resp)))))

    (testing "valid request returns data"
      (let [resp (do-search :json "test")
            result (json/parse-string (:body resp) true)]
        (is (= 200 (:status resp)))
        (is (= 2 (:count result)))
        (is (= search-data (:results result)))))

    (testing "invalid query syntax returns error"
      (let [resp (do-search :json "test+AND" {:throw-exceptions false})
            result (json/parse-string (:body resp) true)]
        (is (= 400 (:status resp)))
        (is (nil? (:count result)))
        (is (nil? (:results result)))
        (is (= "Invalid search syntax for query `test AND`" (:error result)))))))
