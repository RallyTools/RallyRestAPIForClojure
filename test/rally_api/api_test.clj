(ns rally-api.api-test
  (:require [clojure.test :refer :all]
            [crypto.random :as random]
            [environ.core :as env]
            [rally-api.api :as api]))

(def ^:dynamic *rest-api*)

(defn api-fixture [f]
  (let [username   (env/env :username)
        password   (env/env :password)
        rally-host (env/env :rally-host)
        rest-api   (api/create-rest-api username password rally-host)]
    (try
      (binding [*rest-api* rest-api]
        (f))
      (finally (api/stop-rally-rest-api rest-api)))))

(def generate-string (partial random/base64 15))

(use-fixtures :each api-fixture)

(deftest userstory-can-be-created
  (let [userstory-name (generate-string)
        userstory      (api/create-object *rest-api* :userstory {:name userstory-name})]
    (is (= (:metadata/ref-object-name userstory) userstory-name))))

(deftest userstory-can-be-queried-by-formatted-id
  (let [userstory-name (generate-string)
        userstory      (api/create-object *rest-api* :userstory {:name userstory-name})
        read-userstory (api/find-by-formatted-id *rest-api* :userstory (:formatted-id userstory))]
    (is (= (:metadata/ref-object-name read-userstory) userstory-name))))

(deftest userstory-can-be-updated
  (let [userstory-name (generate-string)
        userstory      (api/create-object *rest-api* :userstory {:name (generate-string)})
        _              (api/update-object *rest-api* userstory {:name userstory-name})
        read-userstory (api/find-by-formatted-id *rest-api* :userstory (:formatted-id userstory))]
    (is (= (:metadata/ref-object-name read-userstory) userstory-name))))

(deftest can-get-userstory-by-ref
  (let [userstory-name (generate-string)
        userstory      (api/create-object *rest-api* :userstory {:name userstory-name})
        read-userstory (api/get-object *rest-api* (:metadata/ref userstory))]
    (is (= (:metadata/ref-object-name read-userstory) userstory-name))))

(deftest can-delete-userstory
  (let [ userstory     (api/create-object *rest-api* :userstory {:name (generate-string)})
        _              (api/delete-object *rest-api* userstory)
        read-userstory (api/get-object *rest-api* (:metadata/ref userstory))]
    (is (nil? read-userstory))))
