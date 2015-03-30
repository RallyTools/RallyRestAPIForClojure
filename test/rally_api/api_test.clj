(ns rally-api.api-test
  (:require [rally-api.api :as api]
            [rally-api.data :as data]
            [clojure.test :refer :all]
            [clojure.string :as string]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [ring.util.codec :as codec])
  (:import [com.github.tomakehurst.wiremock WireMockServer]
           [com.github.tomakehurst.wiremock.client WireMock]))

(defn with-test-server [f]
  (let [server (WireMockServer.)]
    (try
      (.start server)
      (f)
      (finally (.shutdown server)))))

(use-fixtures :each with-test-server)

(def rally-rest-api {:rally-host "http://localhost:8080" :middleware http/default-middleware})

(defn query [uri-parts]
  (->> (string/join "/" uri-parts)
       (str "/")
       WireMock/urlPathEqualTo
       WireMock/get))

(defn params [mock-request params]
  (doseq [[key value] params] (.withQueryParam mock-request (name key) (WireMock/equalTo (codec/form-encode value))))
  mock-request)

(defn with-rally-results [mock-request results]
  (let [query-results (data/create-query-results results)
        response      (doto (WireMock/aResponse)
                        (.withBody (json/generate-string query-results)))]
    (.willReturn mock-request response)
    (WireMock/stubFor mock-request)
    mock-request))

(deftest find-artifact-by-formatted-id-should-return-the-correct-results
  (let [userstory-name "Update project to work with newest dependencies."
        userstory      (data/create-userstory {:name  userstory-name
                                               :owner (data/create-user {:name "Jane"})})]
    (-> (query ["webservice" "v2.0" "artifact"])
        (params {:query (data/create-query [:= :formatted-id "S123"])
                 :fetch (data/create-fetch [:description :name :formatted-id :owner :object-id])})
        (with-rally-results [userstory]))

    (is (= userstory-name (:name (api/find-artifact-by-formatted-id rally-rest-api "S123"))))))