(ns rally-api.api
  (:require [clj-http.client :as client]
            [clj-http.cookies :as cookies]
            [clj-http.conn-mgr :as conn-mgr]
            [clojure.core.cache :as cache]
            [clojure.string :as string]
            [rally-api.data :as data]
            [environ.core :as env])
  (:use [slingshot.slingshot :only [throw+]]))

(def ^:private rally-integration-headers
  {"X-RallyIntegrationOS"       (env/env "os.name")
   "X-RallyIntegrationPlatform" (env/env "java.version")
   "X-RallyIntegrationLibrary"  "RallyRestAPIForClojure"})

(defn- check-for-rally-errors [response]
  (if-let [errors (seq (get-in response [:query-result :errors]))]
    (throw+ response "rally-errors: %s" errors)
    response))

(defn- ->uri-string [rally-host uri]
  (let [uri-seq (if (keyword? uri) [:webservice :v2.0 (data/clojure-type->rally-type uri)] uri)]
    (->> (cons rally-host uri-seq)
         (map name)
         (string/join "/"))))

(defn- do-get
  ([rally-rest-api uri]
   (do-get rally-rest-api uri {}))
  ([{:keys [http-options rally-host api-key middleware]} uri options]
   (let [uri         (->uri-string rally-host uri)
         headers     (merge (:headers options) (assoc rally-integration-headers "ZSESSIONID" api-key))
         all-options (merge http-options
                            options
                            {:headers headers
                             :as      :json})]
     (client/with-middleware middleware
       (-> (client/get uri all-options)
           :body
           data/->clojure-map
           check-for-rally-errors)))))

(defn query [rally-rest-api uri query-spec]
  (let [query-params (-> query-spec
                         (update-in [:query] data/create-query)
                         (update-in [:fetch] data/create-fetch))]
    (-> (do-get rally-rest-api uri {:query-params query-params})
        (get-in [:query-result :results]))))

(defn find-first [rally-rest-api uri query-spec]
  (-> (query rally-rest-api uri query-spec)
      first))

(defn find-artifact-by-formatted-id [rally-rest-api formatted-id]
  (let [query-spec {:query [:= :formatted-id formatted-id]
                    :fetch [:description :name :formatted-id :owner :object-id]}]
    (find-first rally-rest-api :artifact query-spec)))

(defn current-workspace [rally-rest-api]
  (let [query-spec {:fetch [:object-id]}]
    (-> (find-first rally-rest-api :workspace query-spec)
        :object-id)))

(defn create-rest-api [http-client-params]
  (let [connection-manager (conn-mgr/make-reusable-conn-manager http-client-params)
        cache              (atom (cache/lru-cache-factory {}))
        cache-ttl          (or (:http-cache-ttl http-client-params) (env/env :http-cache-ttl))]
    {:http-options {:connection-manager connection-manager
                    :cookie-store       (cookies/cookie-store)}
     :api-key      (or (:api-key http-client-params) (env/env :rally-api-key))
     :rally-host   (or (:rally-host http-client-params) (env/env :rally-host))
     :cache        cache
     :cache-ttl    cache-ttl
     :middleware   client/default-middleware}))

(defn stop-rally-rest-api [rally-rest-api]
  (conn-mgr/shutdown-manager (get-in rally-rest-api [:http-options :connection-manager]))
  (assoc-in rally-rest-api [:http-options :connection-manager] nil))