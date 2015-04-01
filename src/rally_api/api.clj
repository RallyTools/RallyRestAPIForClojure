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

(defn- do-request [{:keys [middleware current-project]} method uri options]
  (let [maybe-update-project (fn [project-ref] (or project-ref (:metadata/ref current-project)))
        headers              (merge (:headers options) rally-integration-headers)
        request              (merge options {:method  method
                                             :url     uri
                                             :headers headers
                                             :as      :json})
        request              (update-in request [:query-params :project] maybe-update-project)]
    (client/with-middleware middleware
      (-> (client/request request)
          :body
          data/->clojure-map
          vals
          first
          check-for-rally-errors))))

(defn- do-modification [{:keys [http-options security-token] :as rally-rest} uri type data]
  (let [rally-type  (data/clojure-type->rally-type type)
        all-options (assoc http-options
                      :query-params {:key security-token}
                      :body         (json/generate-string (data/->rally-map {rally-type data})))]
    (-> (do-request rally-rest :post uri all-options)
        :object)))

(defn- do-get
  ([rally-rest-api uri]
   (do-get rally-rest-api uri {}))
  ([{:keys [http-options rally-host] :as rally-rest} uri options]
     (let [uri         (->uri-string rally-host uri)
           all-options (merge http-options options)]
       (do-request rally-rest :get uri all-options))))

(defn create-object [rest-api type data]
  (let [uri (str (->uri-string (:rally-host rest-api) type) "/" "create") ]
    (do-modification rest-api uri type data)))

(defn update-object [rest-api object data]
  (let [uri  (str (:metadata/ref object))
        type (:metadata/type object)]
    (do-modification rest-api uri type data)))

(defn delete-object [{:keys [security-token http-options] :as rally-rest} object]
  (do-request rally-rest :delete (str (:metadata/ref object)) (assoc http-options :query-params {:key security-token})))

(defn get-object [{:keys [middleware http-options] :as rally-rest} ref]
  (do-request rally-rest :get (str ref) http-options))

(defn refresh-object [rest-api object]
  (get-object rest-api (:metadata/ref object)))

(defn query [rally-rest-api uri query-spec]
  (let [ query-params (-> query-spec
                          (update-in [:query] data/create-query)
                          (update-in [:fetch] data/create-fetch)
                          (update-in [:order] data/create-order))]
    (-> (do-get rally-rest-api uri {:query-params query-params})
        :results)))

(defn query-seq [rally-rest-api uri {:keys [start pagesize] :as query-spec}]
  (let [start    (or start 1)
        pagesize (or pagesize 200)
        results  (seq (query rally-rest-api uri query-spec))]
    (when results 
      (concat results (lazy-seq (query-seq rally-rest-api uri (assoc query-spec :start (+ start pagesize) :pagesize pagesize)))))))

(defn find-first [rally-rest-api uri query-spec]
  (-> (query rally-rest-api uri query-spec)
      first))

(defn find-by-formatted-id [rally-rest-api rally-type formatted-id]
  (let [query-spec {:query [:= :formatted-id formatted-id]
                    :fetch [:description :name :formatted-id :owner :object-id]}]
    (find-first rally-rest-api rally-type query-spec)))

(defn current-workspace [rally-rest-api]
  (refresh-object rally-rest-api (get-in rally-rest-api [:current-project :workspace])))

(defn current-project [rally-rest-api]
  (find-first rally-rest-api :project {:fetch true}))

(defn current-user [{:keys [rally-host] :as rally-rest-api}]
  (get-object rally-rest-api (->uri-string rally-host (keyword "user:current"))))

(defn set-current-project [rally-rest current-project]
  (assoc rally-rest :current-project current-project))

(defn create-rest-api [username password rally-host]
  (let [connection-manager (conn-mgr/make-reusable-conn-manager {})
        rest-api           {:http-options {:connection-manager connection-manager
                                           :cookie-store       (cookies/cookie-store)}
                            :rally-host   rally-host
                            :middleware   client/default-middleware}
        crt-rest-api       (assoc-in rest-api [:http-options :basic-auth] [username password])]
    (-> rest-api
        (assoc :security-token (:security-token (do-get crt-rest-api [:webservice :v2.0 :security :authorize])))
        (set-current-project (current-project rest-api))
        (assoc :current-user (current-user rest-api)))))

(defn stop-rally-rest-api [rally-rest-api]
  (conn-mgr/shutdown-manager (get-in rally-rest-api [:http-options :connection-manager]))
  (assoc-in rally-rest-api [:http-options :connection-manager] nil))
