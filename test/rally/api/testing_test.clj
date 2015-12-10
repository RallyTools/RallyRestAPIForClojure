(ns rally.api.testing-test
  (:require [rally.api.testing :as testing]
            [rally.api :as api]
            [rally.api.request :as request]
            [clojure.test :refer :all]
            [rally.helper :refer [*rest-api*] :as helper])
  (:import [java.util UUID]))

(use-fixtures :each helper/api-fixture)

(deftest ^:integration can-use-record-playback
  (let [action (fn []
                 (let [userstory-name "userstory-name"
                       defect-name    "defect-name"
                       userstory      (-> *rest-api*
                                          (api/create! :userstory {:name userstory-name}))
                       defect         (-> *rest-api*
                                          (api/create! :defect {:name defect-name}))]
                   (is (= (:metadata/ref-object-name userstory) userstory-name))
                   (is (= (:metadata/ref-object-name defect) defect-name))))]
    (testing/override-mode! :record)
    (testing/override-directory! ".test-data")
    (testing/record-playback-fixture "testing" action)
    (testing/override-mode! :playback)
    (testing/record-playback-fixture "testing" action)
    (testing/override-mode! nil)))

(deftest ^:integration should-throw-exception-if-playback-file-is-missing
  (let [action (fn []
                 (-> *rest-api*
                     (api/create! :userstory {:name "userstory-name"})))]
    (testing/override-mode! :playback)
    (is (thrown? Exception (testing/record-playback-fixture (helper/generate-string) action)))
    (testing/override-mode! nil)))

(deftest ^:integration should-not-find-response-to-playback-if-headers-are-different
  (let [action-generator (fn [header-value]
                           (fn []
                             (-> *rest-api*
                                 (request/add-headers {:x-cool-header header-value})
                                 (api/create! :userstory {:name "userstory-name"}))))]
    (testing/override-mode! :record)
    (testing/override-directory! ".test-data")
    (testing/record-playback-fixture "headers" (action-generator "some header"))
    (testing/override-mode! :playback)
    (is (thrown? Exception (testing/record-playback-fixture "headers" (action-generator "different header"))))
    (testing/override-mode! nil)))

(deftest ^:integration should-not-find-response-to-playback-if-query-params-are-different
  (let [action-generator (fn [fetch-value]
                           (fn []
                             (-> *rest-api*
                                 (request/set-query-param :fetch fetch-value)
                                 (api/create! :userstory {:name "userstory-name"}))))]
    (testing/override-mode! :record)
    (testing/override-directory! ".test-data")
    (testing/record-playback-fixture "queryparams" (action-generator "ZuulID,Blah,SomeID"))
    (testing/override-mode! :playback)
    (is (thrown? Exception (testing/record-playback-fixture "queryparams" (action-generator "Totally,Some,Other,Query,Params"))))
    (testing/override-mode! nil)))

(deftest ^:integration should-find-response-to-playback-if-fetch-query-params-are-different-order
  (let [action-generator (fn [fetch-value]
                           (fn []
                             (-> *rest-api*
                                 (request/set-query-param :fetch fetch-value)
                                 (api/create! :userstory {:name "userstory-name"}))))]
    (testing/override-mode! :record)
    (testing/override-directory! ".test-data")
    (testing/record-playback-fixture "queryparams" (action-generator "ZuulID,Blah,SomeID"))
    (testing/override-mode! :playback)
    (testing/record-playback-fixture "queryparams" (action-generator "Blah,SomeID,ZuulID"))
    (testing/override-mode! nil)))

(deftest ^:integration should-use-header-matcher-override-if-available
  (let [matcher          (fn [request-headers potential-match-headers]
                           (= (-> request-headers :test even?)
                              (-> potential-match-headers :test even?)))
        action-generator (fn [header-value]
                           (fn []
                             (-> *rest-api*
                                 (request/add-headers {:test header-value})
                                 (api/create! :userstory {:name "userstory-name"}))))]
    (testing/override-header-matcher! matcher)
    (testing/override-mode! :record)
    (testing/override-directory! ".test-data")
    (testing/record-playback-fixture "queryparams" (action-generator 6))
    (testing/override-mode! :playback)
    (testing/record-playback-fixture "queryparams" (action-generator 2))
    (testing/override-mode! nil)
    (testing/override-header-matcher! nil)))
