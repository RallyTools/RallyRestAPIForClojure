(ns rally-api.api
  (:require [cheshire.core :as json]
            [clj-http.client :as client]
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

(def first-val (comp first vals))

(defn- check-for-rally-errors [response]
  (if-let [errors (seq (:errors response))]
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
   (let [uri (->uri-string rally-host uri)
         headers (merge (:headers options) (assoc rally-integration-headers "ZSESSIONID" api-key))
         all-options (merge http-options
                            options
                            {:headers headers
                             :as      :json
                             :debug   true})]
     (client/with-middleware middleware
                             (-> (client/get uri all-options)
                                 :body
                                 data/->clojure-map
                                 first-val
                                 check-for-rally-errors)))))

(defn create-object [{:keys [http-options rally-host security-token]} rally-type data]
  (let [uri         (str (->uri-string rally-host rally-type) "/" "create") 
        headers     rally-integration-headers
        all-options (merge http-options
                           {:headers      headers
                            :as           :json
                            :query-params {:key security-token}
                            :body         (json/generate-string (data/->rally-map {rally-type data}))})]
    (-> (client/post uri all-options)
        :body
        data/->clojure-map
        first-val
        check-for-rally-errors
        :object)))

(defn query [rally-rest-api uri query-spec]
  (let [query-params (-> query-spec
                         (update-in [:query] data/create-query)
                         (update-in [:fetch] data/create-fetch))]
    (-> (do-get rally-rest-api uri {:query-params query-params})
        :results)))

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

(defn create-rest-api [username password rally-host]
  (let [connection-manager (conn-mgr/make-reusable-conn-manager {})
        rest-api           {:http-options {:connection-manager connection-manager
                                           :cookie-store       (cookies/cookie-store)
                                           :basic-auth         [username password]}
                            :rally-host   rally-host
                            :middleware   client/default-middleware}
        csrt-response      (do-get rest-api  [:webservice :v2.0 :security :authorize])
        security-token     (get-in csrt-response [:operation-result :security-token])]
    (assoc rest-api :security-token security-token)))

(defn stop-rally-rest-api [rally-rest-api]
  (conn-mgr/shutdown-manager (get-in rally-rest-api [:http-options :connection-manager]))
  (assoc-in rally-rest-api [:http-options :connection-manager] nil))
