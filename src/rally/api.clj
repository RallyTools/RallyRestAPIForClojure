(ns rally.api
  (:require [clj-http.client :as client]
            [clj-http.conn-mgr :as conn-mgr]
            [clj-http.cookies :as cookies]
            [environ.core :as env]
            [rally.api.data :as data]
            [rally.api.request :as request])
  (:use [slingshot.slingshot :only [throw+]])
  (:refer-clojure :exclude [find]))

(def ^:dynamic *current-user*)

(defn- not-found? [error]
  (= error "Cannot find object to read"))

(defn- valid-rest-api? [rest-api]
  (not (nil? (get-in rest-api [:request :cookie-store]))))

(defn- check-for-rally-errors [api response]
  (let [errors (:errors response)]
    (cond
      (empty? errors)                response
      (:disable-throw-on-error? api) {:object response}
      (some not-found? errors)       nil
      :else                          (throw+ response "rally-errors: %s" errors))))

(defn do-request [{:keys [request] :as api}]
  (let [response (client/request request)]
    (if (string? (:body response))
      (check-for-rally-errors api response)
      (->> response
           :body
           data/->clojure-map
           vals
           first
           (check-for-rally-errors api)))))

(defn create!
  ([type] (create! *current-user* type {}))
  
  ([api-or-type type-or-data]
   (if (keyword? api-or-type)
     (create! *current-user* api-or-type type-or-data)
     (create! api-or-type type-or-data {})))

  ([api-or-type type-or-data data-or-query-params]
   (if (keyword? api-or-type)
     (create! *current-user* api-or-type type-or-data data-or-query-params)
     (create! api-or-type type-or-data data-or-query-params {})))

  ([rest-api type data query-params]
   {:pre [(valid-rest-api? rest-api)]}
   (let [default-data-fn (request/get-default-data-fn rest-api)]
     (-> rest-api
         (request/set-method :put)
         (request/set-uri type "create")
         (request/set-body-as-map type (default-data-fn type data))
         (request/merge-query-params query-params)
         do-request
         :object))))

(defn copy!
  ([ref-or-object]
   (copy! *current-user* ref-or-object))
  
  ([rest-api ref-or-object]
   {:pre [(valid-rest-api? rest-api)]}
   (let [ref  (data/->ref ref-or-object)
         type (or (:metadata/type ref-or-object) (data/rally-ref->clojure-type ref))]
     (-> rest-api
         (request/set-method :post)
         (request/set-uri ref "copy")
         (request/set-body-as-map type {})
         do-request
         :object))))

(defn update!
  ([ref-or-object updated-data]
   (update! *current-user* ref-or-object updated-data))

  ([api-or-ref-or-object ref-or-object-or-updated-data updated-data-or-query-params]
   (if (valid-rest-api? api-or-ref-or-object)
     (update! api-or-ref-or-object ref-or-object-or-updated-data updated-data-or-query-params {})
     (update! *current-user* api-or-ref-or-object ref-or-object-or-updated-data updated-data-or-query-params)))

  ([rest-api ref-or-object updated-data query-params]
   {:pre [(valid-rest-api? rest-api)]}
   (let [ref  (data/->ref ref-or-object)
         type (or (:metadata/type ref-or-object) (data/rally-ref->clojure-type ref))]
     (-> rest-api
         (request/set-method :post)
         (request/set-url ref)
         (request/set-body-as-map type updated-data)
         (request/merge-query-params query-params)
         do-request
         :object))))

(defn update-collection!
  ([collection-ref-or-object action items]
   (update-collection! *current-user* collection-ref-or-object action items))
  
  ([rest-api collection-ref-or-object action items]
   {:pre [(valid-rest-api? rest-api)]}
   (let [ref (str (data/->ref collection-ref-or-object) "/" (name action))
         items (map #(hash-map :metadata/ref (data/->ref %)) items)]
     (-> rest-api
         (request/set-method :post)
         (request/set-url ref)
         (request/set-body-as-map :collection-items items)
         do-request))))

(defn delete!
  ([ref-or-object]
   (delete! *current-user* ref-or-object))
  
  ([rest-api ref-or-object]
   {:pre [(valid-rest-api? rest-api)]}
   (-> rest-api
       (request/set-method :delete)
       (request/set-url ref-or-object)
       do-request)))

(defn- query-for-page [rest-api uri start pagesize query-spec]
  (-> rest-api
      (request/set-uri uri)
      (request/merge-query-params query-spec)
      (request/set-query-param :start start)
      (request/set-query-param :pagesize pagesize)
      do-request))

(defn query
  ([uri]
   (query *current-user* uri {}))
  
  ([api-or-uri uri-or-spec]
   (if (valid-rest-api? api-or-uri)
     (query api-or-uri uri-or-spec {})
     (query *current-user* api-or-uri uri-or-spec)))
  
  ([rest-api uri query-spec]
   {:pre [(valid-rest-api? rest-api)]}
   (let [query-spec         (if (vector? query-spec) {:query query-spec} query-spec)
         start              (or (:start query-spec) 1)
         pagesize           (or (:pagesize query-spec) 200)
         next-start         (+ start pagesize)
         page               (query-for-page rest-api uri start pagesize query-spec)
         total-result-count (:total-result-count page)]
     (concat (:results page)
             (when (<= next-start total-result-count)
               (lazy-seq (query rest-api uri (assoc query-spec :start next-start))))))))

(defn find
  ([ref-or-object]
   (find *current-user* ref-or-object))
  
  ([api-ref-or-object ref-or-object-or-query-spec]
   (if (valid-rest-api? api-ref-or-object)
     (-> api-ref-or-object
         (request/set-url ref-or-object-or-query-spec)
         do-request)
     (find *current-user* api-ref-or-object ref-or-object-or-query-spec)))
  
  ([rest-api uri query-spec]
   {:pre [(valid-rest-api? rest-api)]}
   (-> (query rest-api uri query-spec)
       first)))

(defn find-by-formatted-id
  ([rally-type formatted-id]
   (find-by-formatted-id *current-user* rally-type formatted-id))
  
  ([rest-api rally-type formatted-id]
   {:pre [(valid-rest-api? rest-api)]}
   (let [query-spec {:query [:= :formatted-id formatted-id]
                     :fetch true}]
     ;; We use a query and then search because if you look for formatted-id
     ;; in the artifact endpoint it will return all artifacts with the number
     ;; part of the formatted it. So searching for US11 will also return DE11,T11, and so on.
     (->> (query rest-api rally-type query-spec)
          (some #(when (= formatted-id (:formatted-id %)) %))))))

(defn find-by-id
  ([type id]
   (find-by-id *current-user* type id))
  
  ([rest-api type id]
   {:pre [(valid-rest-api? rest-api)]}
   (-> rest-api
       (request/set-uri type id)
       do-request)))

(defn current-workspace
  ([] (current-workspace *current-user*))
  
  ([rest-api]
   {:pre [(valid-rest-api? rest-api)]}
   (let [current-project (find rest-api (request/get-current-project rest-api))]
     (find rest-api (:workspace current-project)))))

(defn current-project
  ([] (current-project *current-user*))
  
  ([rest-api]
   {:pre [(valid-rest-api? rest-api)]}
   (find rest-api :project {:fetch true})))

(defn current-user
  ([] (current-user *current-user*))
  
  ([rest-api]
   {:pre [(valid-rest-api? rest-api)]}
   (-> rest-api
       (request/set-uri (keyword "user:current"))
       do-request)))

(defn- security-token [rest-api {:keys [username password]}]
  (-> rest-api
      (request/set-basic-auth username password)
      (request/set-uri :security :authorize)
      do-request
      :security-token))

(defn create-basic-rest-api
  "Create a rest-api, but do not make any calls to the server."
  [{:keys [api-key] :as credentials} rally-host conn-props]
  (let [connection-manager (conn-mgr/make-reusable-conn-manager conn-props)
        rest-api           {:request {:connection-manager connection-manager
                                      :cookie-store       (cookies/cookie-store)
                                      :headers            {"X-RallyIntegrationOS"       (env/env "os.name")
                                                           "X-RallyIntegrationPlatform" (env/env "java.version")
                                                           "X-RallyIntegrationLibrary"  "RallyRestAPIForClojure"}
                                      :debug              (or (env/env :debug-rally-rest) false)
                                      :method             :get
                                      :as                 :json}
                            :rally   {:host    rally-host
                                      :version :v2.0}}]
    (if api-key
      (request/add-headers rest-api {:zsessionid api-key})
      rest-api)))

(defn init-rest-api
  "Sets the current project, and the the security token (if applicable)."
  [rest-api {:keys [api-key] :as credentials}]
  (let [rest-api        (if api-key
                          rest-api
                          (request/set-security-token rest-api (security-token rest-api credentials)))
        current-project (find rest-api :project {})]
    (request/set-current-project rest-api current-project)))

(defn create-rest-api
  "Create and initialize a rest-api."
  ([]
   (let [username   (env/env :username)
         password   (env/env :password)
         api-key    (env/env :api-key)]
     (create-rest-api {:username   username
                       :password   password
                       :api-key    api-key})))
  ([credentials]
   (let [rally-host (or (env/env :rally-host) "https://rally1.rallydev.com")]
     (create-rest-api credentials rally-host)))
  ([credentials rally-host]
   (create-rest-api credentials rally-host {}))
  ([{:keys [api-key] :as credentials} rally-host conn-props]
   (let [rest-api (create-basic-rest-api credentials rally-host conn-props)]
     (init-rest-api rest-api credentials))))

(defn shutdown-rest-api [rest-api]
  (conn-mgr/shutdown-manager (get-in rest-api [:request :connection-manager]))
  (assoc-in rest-api [:request :connection-manager] nil))
