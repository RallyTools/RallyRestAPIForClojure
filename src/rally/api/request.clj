(ns rally.api.request
  (:require [cheshire.core :as json]
            [cheshire.generate :as encoding]
            [clj-time.coerce :as coerce]
            [clj-time.format :as format]
            [clojure.string :as string]
            [rally.api.data :as data]))

(encoding/add-encoder java.net.URI encoding/encode-str)

(defn encode-date [date jg]
  (.writeString jg (format/unparse data/date-format (coerce/to-date-time date))))

(encoding/add-encoder java.util.Date encode-date)
(encoding/add-encoder org.joda.time.ReadableInstant encode-date)

(defn set-query-param [rest-api name value]
  (assoc-in rest-api [:request :query-params name] value))

(defn get-query-param [rest-api name]
  (get-in rest-api [:request :query-params name]))

(defn- maybe-update-in [m k f]
  (if (contains? m k)
    (assoc m k (f (k m)))
    m))

(defn merge-query-params [rest-api query-params]
  (let [default-params (get-in rest-api [:request :query-params])
        query-params   (-> query-params
                           (maybe-update-in :query data/create-query)
                           (maybe-update-in :order data/create-order)
                           (maybe-update-in :fetch data/create-fetch))
        merged-params  (merge default-params query-params)]
    (assoc-in rest-api [:request :query-params] merged-params)))

(defn set-current-project [rest-api project]
  (assoc-in rest-api [:request :query-params :project] (:metadata/ref project)))

(defn get-current-project [rest-api]
  (get-in rest-api [:request :query-params :project]))

(defn set-security-token [rest-api security-token]
  (assoc-in rest-api [:request :query-params :key] security-token))

(defn set-url [rest-api ref-or-object]
  (assoc-in rest-api [:request :url] (data/->ref ref-or-object)))

(defn get-url [rest-api]
  (get-in rest-api [:request :url]))

(defn set-uri [{:keys [rally] :as rest-api} uri & additional]
  (let [url (apply data/->uri-string rally uri additional)]
    (set-url rest-api url)))

(defn add-headers [rest-api headers]
  (update-in rest-api [:request :headers] #(merge % headers)))

(defn set-method [rest-api method]
  (assoc-in rest-api [:request :method] method))

(defn set-basic-auth [rest-api username password]
  (assoc-in rest-api [:request :basic-auth] [username password]))

(defn set-body-as-map [rest-api clojure-type data]
  (let [rally-type (data/clojure-type->rally-type clojure-type)
        body-map   {rally-type (data/->rally-map data)}]
    (assoc-in rest-api [:request :body] (json/generate-string body-map {:date-format data/date-format}))))

(defn get-body [rest-api]
  (get-in rest-api [:request :body]))

(defn set-default-data-fn [rest-api default-data-fn]
  (assoc rest-api :default-data-fn default-data-fn))

(defn get-default-data-fn [rest-api]
  (:default-data-fn rest-api (fn [type values] values)))

(defn debug [rest-api]
  (assoc-in rest-api [:request :debug] true))

(defn set-version [rest-api version]
  (assoc-in rest-api [:rally :version] version))

(defn set-application-vendor [rest-api vendor]
  (add-headers rest-api {"X-RallyIntegrationVendor" vendor}))

(defn set-application-version [rest-api version]
  (add-headers rest-api {"X-RallyIntegrationVersion" version}))

(defn disable-throw-on-error [rest-api]
  (assoc rest-api :disable-throw-on-error? true))
