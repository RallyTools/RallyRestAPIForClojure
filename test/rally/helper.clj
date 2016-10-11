(ns rally.helper
  (:require [crypto.random :as random]
            [rally.api :as api]
            [rally.api.request :as request]))

(def ^:dynamic *rest-api*)

(def generate-string (partial random/base32 15))

(defn api-fixture [f]
  (let [default-data (fn [type data] (merge {:name (generate-string)} data))
        rest-api     (request/set-default-data-fn (api/create-rest-api) default-data)]
    (try
      (binding [*rest-api* rest-api]
        (f))
      (finally
        (api/shutdown-rest-api rest-api)))))
