(ns rally-api.api-test
  (:require [clojure.test :refer :all]
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

(use-fixtures :each api-fixture)

(deftest userstory-can-be-created
  (let [userstory-name "Update project to work with newest dependencies."
        userstory      (api/create-object *rest-api* :userstory {:name userstory-name})]
    (is (= (:metadata/ref-object-name userstory) userstory-name))))
