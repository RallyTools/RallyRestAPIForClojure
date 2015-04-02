(ns rally-api.api
  (:require [clj-http.client :as client]
            [clj-http.conn-mgr :as conn-mgr]
            [clj-http.cookies :as cookies]
            [environ.core :as env]
            [rally-api.data :as data]
            [rally-api.request :as request])
  (:use [slingshot.slingshot :only [throw+]]))

(defn- not-found? [error]
  (= error "Cannot find object to read"))

(defn- check-for-rally-errors [response]
  (let [errors (:errors response)]
    (cond
     (empty? errors)          response
     (some not-found? errors) nil
     :else                    (throw+ response "rally-errors: %s" errors))))

(defn- do-request [{:keys [middleware request]}]
  (client/with-middleware middleware
    (-> (client/request request)
        :body
        data/->clojure-map
        vals
        first
        check-for-rally-errors)))

(defn create-object [rest-api type data]
  (-> rest-api
      (request/set-method :put)
      (request/set-uri type "create")
      (request/set-body-as-map type data)
      do-request
      :object))

(defn update-object
  ([rest-api object updated-data]
     (update-object rest-api object (:metadata/type object) updated-data))
  ([rest-api ref-or-object type updated-data]
     (-> rest-api
         (request/set-method :post)
         (request/set-url ref-or-object)
         (request/set-body-as-map type updated-data)
         do-request
         :object)))

(defn delete-object [rest-api ref-or-object]
  (-> rest-api
      (request/set-method :delete)
      (request/set-url ref-or-object)
      do-request))

(defn get-object [rest-api ref-or-object]
  (-> rest-api
      (request/set-url ref-or-object)
      do-request))

(defn query [rest-api uri query-spec]
  (-> rest-api
      (request/set-uri uri)
      (request/merge-query-params query-spec)
      do-request
      :results))

(defn query-seq [rest-api uri {:keys [start pagesize] :as query-spec}]
  (let [start    (or start 1)
        pagesize (or pagesize 200)
        results  (seq (query rest-api uri query-spec))]
    (when results 
      (concat results (lazy-seq (query-seq rest-api uri (assoc query-spec :start (+ start pagesize) :pagesize pagesize)))))))

(defn find-first [rest-api uri query-spec]
  (-> (query rest-api uri query-spec)
      first))

(defn find-by-formatted-id [rest-api rally-type formatted-id]
  (let [query-spec {:query [:= :formatted-id formatted-id]
                    :fetch [:description :name :formatted-id :owner :object-id]}]
    (find-first rest-api rally-type query-spec)))

(defn current-workspace [rest-api]
  (let [current-project (get-object rest-api (request/get-current-project rest-api))]
    (get-object rest-api (:workspace current-project))))

(defn current-project [rest-api]
  (find-first rest-api :project {:fetch true}))

(defn current-user [rest-api]
  (-> rest-api
      (request/set-uri (keyword "user:current"))
      do-request))

(defn- security-token [rest-api {:keys [username password api-key]}]
  (-> rest-api
      (request/set-basic-auth username password)
      (request/set-uri [:security :authorize])
      do-request
      :security-token))

(defn- create-rest-api! [{:keys [api-key] :as credentials} rally-host]
  (let [connection-manager (conn-mgr/make-reusable-conn-manager {})
        rest-api           {:request    {:connection-manager connection-manager
                                         :cookie-store       (cookies/cookie-store)
                                         :headers            {"X-RallyIntegrationOS"       (env/env "os.name")
                                                              "X-RallyIntegrationPlatform" (env/env "java.version")
                                                              "X-RallyIntegrationLibrary"  "RallyRestAPIForClojure"}
                                         :debug              (or (env/env :debug-rally-rest) false)
                                         :method             :get
                                         :as                 :json}
                            :rally-host rally-host
                            :middleware client/default-middleware}
        rest-api           (if api-key
                             (request/add-headers rest-api {:zsessionid api-key})
                             (request/set-security-token (security-token rest-api credentials)))]
    (request/set-current-project rest-api (current-project rest-api))))

(defn create-rest-api
  ([username password rally-host]
   (create-rest-api! {:username username
                      :password password}
                     rally-host))
  ([api-key rally-host]
   (create-rest-api! {:api-key api-key} rally-host))
  ([]
   (let [username   (env/env :username)
         password   (env/env :password)
         api-key    (env/env :api-key)
         rally-host (or (env/env :rally-host) "https://rally1.rallydev.com")]
     (if (and username password)
       (create-rest-api username password rally-host)
       (create-rest-api api-key rally-host)))))

(defn stop-rally-rest-api [rest-api]
  (conn-mgr/shutdown-manager (get-in rest-api [:request :connection-manager]))
  (assoc-in rest-api [:request :connection-manager] nil))
