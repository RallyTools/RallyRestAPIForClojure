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

(defn- not-found? [error]
  (= error "Cannot find object to read"))

(defn- check-for-rally-errors [response]
  (let [errors (:errors response)]
    (cond
     (empty? errors)          response
     (some not-found? errors) nil
     :else                    (throw+ response "rally-errors: %s" errors))))

(defn- ->uri-string [rally-host uri]
  (let [uri-seq (if (keyword? uri) [:webservice :v2.0 (data/clojure-type->rally-type uri)] uri)]
    (->> (cons rally-host uri-seq)
         (map name)
         (string/join "/"))))

(defn- do-request [middleware method uri options]
  (let [headers (merge (:headers options) rally-integration-headers)
        request (merge options {:method  method
                                :url     uri
                                :headers headers
                                :as      :json})]
    (client/with-middleware middleware
      (-> (client/request request)
          :body
          data/->clojure-map
          vals
          first
          check-for-rally-errors))))

(defn- do-modification [{:keys [http-options middleware security-token]} uri rally-type data]
  (let [all-options (assoc http-options
                           :query-params {:key security-token}
                           :body         (json/generate-string (data/->rally-map {rally-type data})))]
    (-> (do-request middleware :post uri all-options)
        :object)))

(defn- do-get
  ([rally-rest-api uri]
   (do-get rally-rest-api uri {}))
  ([{:keys [http-options rally-host middleware]} uri options]
     (let [uri         (->uri-string rally-host uri)
           all-options (merge http-options options)]
     (do-request middleware :get uri all-options))))

(defn create-object [rest-api rally-type data]
  (let [uri         (str (->uri-string (:rally-host rest-api) rally-type) "/" "create") ]
    (do-modification rest-api uri rally-type data)))

(defn update-object [rest-api object data]
  (let [uri         (str (:metadata/ref object))
        rally-type  (:metadata/type object)]
    (do-modification rest-api uri rally-type data)))

(defn delete-object [{:keys [middleware security-token http-options]} object]
  (do-request middleware :delete (str (:metadata/ref object)) (assoc http-options :query-params {:key security-token})))

(defn get-object [{:keys [middleware http-options]} ref]
  (do-request middleware :get (str ref) http-options))

(defn refresh-object [rest-api object]
  (get-object rest-api (:metadata/ref object)))

(defn query [rally-rest-api uri query-spec]
  (let [query-params (-> query-spec
                         (update-in [:query] data/create-query)
                         (update-in [:fetch] data/create-fetch))]
    (-> (do-get rally-rest-api uri {:query-params query-params})
        :results)))

(defn find-first [rally-rest-api uri query-spec]
  (-> (query rally-rest-api uri query-spec)
      first))

(defn find-by-formatted-id [rally-rest-api rally-type formatted-id]
  (let [query-spec {:query [:= :formatted-id formatted-id]
                    :fetch [:description :name :formatted-id :owner :object-id]}]
    (find-first rally-rest-api rally-type query-spec)))

(defn current-workspace [rally-rest-api]
  (let [query-spec {:fetch [:object-id]}]
    (-> (find-first rally-rest-api :workspace query-spec)
        :object-id)))

(defn create-rest-api [username password rally-host]
  (let [connection-manager (conn-mgr/make-reusable-conn-manager {})
        rest-api           {:http-options {:connection-manager connection-manager
                                           :cookie-store       (cookies/cookie-store)}
                            :rally-host   rally-host
                            :middleware   client/default-middleware}
        crt-rest-api       (assoc-in rest-api [:http-options :basic-auth] [username password])
        csrt-response      (do-get crt-rest-api [:webservice :v2.0 :security :authorize])
        security-token     (:security-token csrt-response)]
    (assoc rest-api :security-token security-token)))

(defn stop-rally-rest-api [rally-rest-api]
  (conn-mgr/shutdown-manager (get-in rally-rest-api [:http-options :connection-manager]))
  (assoc-in rally-rest-api [:http-options :connection-manager] nil))
