(ns rally.api.data
  (:require [camel-snake-kebab.core :as csk]
            [clj-time.coerce :as coerce]
            [clj-time.format :as format]
            [clojure.string :as string]
            [clojure.walk :as walk])
  (:import [java.net URI]
           [java.util UUID]))


(def ^:const user-story-rally-type "HierarchicalRequirement")

(defn metadata-name? [n]
  (or
   (.startsWith (name n) "_")
   (and (keyword? n) (= "metadata" (namespace n)))))

(defn custom-field-name? [n]
  (or
   (.startsWith (name n) "c_")
   (and (keyword? n) (= "custom" (namespace n)))))

(defn ->rally-case [n]
  (cond
    (true? n)              "true"
    (false? n)             "false"
    (metadata-name? n)     (str "_" (csk/->camelCaseString n))
    (custom-field-name? n) (str "c_" (csk/->PascalCaseString n))
    :else                  (-> n
                               csk/->PascalCaseString
                               (.replace "Id" "ID"))))
(defn ->clojure-case [k]
  (cond
   (metadata-name? k)     (keyword "metadata" (csk/->kebab-case-string k))
   (custom-field-name? k) (keyword "custom" (csk/->kebab-case-string (.substring (name k) 2)))
   :else                  (csk/->kebab-case-keyword k)))

(defn ->rally-map [m]
  (if (sequential? m)
    (map ->rally-map m)
    (let [f         (comp keyword ->rally-case)
          transform (fn [[k v]]
                      (let [new-k (f k)]
                        (if (map? v)
                          [new-k (:metadata/ref v)]
                          [new-k v])))]
      (into {} (map transform m)))))

(defn rally-type->clojure-type [type]
  (if (.equalsIgnoreCase type user-story-rally-type)
    :user-story
    (let [[p1 p2] (string/split type #"\/")]
      (if (string/blank? p2)
        (csk/->kebab-case-keyword type)
        (keyword (csk/->kebab-case p1) (csk/->kebab-case p2))))))

(defn clojure-type->rally-type [type]
  (let [namespace (namespace type)
        result    (case type
                    :userstory  user-story-rally-type
                    :user-story user-story-rally-type
                    :UserStory  user-story-rally-type
                    :security   "security"
                    (csk/->PascalCaseString type))]
    (if (string/blank? namespace)
      result (str (-> namespace keyword clojure-type->rally-type) "/" result))))

(defn- oid? [value]
  (let [long-matcher #"[-+]?\d+$"]
    (not (nil? (re-matches long-matcher value)))))

(defn- uuid? [value]
  (let [uuid-matcher #"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"]
    (not (nil? (re-matches uuid-matcher value)))))

(defn rally-ref->clojure-type [rally-ref]
  (let [type-part? (fn [part] (not (or (oid? part)
                                       (uuid? part)
                                       (= "create" part)
                                       (.startsWith part "v2"))))
        [p3 p2 p1]   (-> (string/split rally-ref #"\/")
                         reverse)]
    ; Look at the last 3 parts of the ref
    (-> (cond
         ; /slm/webservice/v2.0/PortfolioItem/Feature
         (and (type-part? p2)
              (type-part? p3))
         (str p2 "/" p3)

         ; /slm/webservice/v2.0/PortfolioItem/Feature/1234
         (and (type-part? p1)
              (type-part? p2))
         (str p1 "/" p2)

         ; /slm/webservice/v2.0/UserStory
         (type-part? p3)
         p3

         ; /slm/webservice/v2.0/UserStory/1234
         :else
         p2)
        rally-type->clojure-type)))

(def date-format (format/formatters :date-time))

(defn- date? [key]
  (.contains (.toLowerCase (name key)) "date"))

(defn- to-date [date-format raw-value]
  (try
    (coerce/to-date (format/parse date-format raw-value))
    (catch Exception _ raw-value)))

(defn ->clojure-map [m]
  (letfn [(not-nil? [v] (not (or (empty? v) (= "null" v))))
          (transform [[k v]]
            (let [new-k (->clojure-case k)
                  new-v (case new-k
                          :metadata/type            (rally-type->clojure-type v)
                          :metadata/ref-object-uuid (when (not-nil? v) (UUID/fromString v))
                          :metadata/object-version  (Integer/parseInt v)
                          :metadata/ref             (when (not-nil? v) (URI/create v))
                          :metadata/rally-api-major (Integer/parseInt v)
                          :metadata/rally-api-minor (Integer/parseInt v)
                          v)
                  new-v (if (and (date? new-k) (not (nil? new-v)))
                          (to-date date-format new-v)
                          new-v)]
              [new-k new-v]))]
    (walk/postwalk (fn [x] (if (map? x) (into {} (map transform x)) x)) m)))

(defn ->str [v]
  (if (keyword? v) (name v) (str v)))

(defn ->ref [ref-or-object]
  (cond
    (sequential? ref-or-object)
    (->> ref-or-object
         (map ->ref)
         (map ->str)
         (string/join "/"))

    :else
    (->str (or (:metadata/ref ref-or-object) ref-or-object))))

(defn uri-like? [thing]
  (-> (->ref thing)
      str
      (.startsWith "http")))

(defn- build-uri-for-type [host version type]
  (let [rally-type  (clojure-type->rally-type type)]
    [host :slm :webservice version rally-type]))

(defn ->uri-string [{:keys [host version]} uri & additional]

  (let [base-uri     (cond
                       (keyword? uri)    (->ref (build-uri-for-type host version uri))
                       (uri-like? uri)   (->ref uri)
                       (sequential? uri) (->ref (cons host uri))
                       :else             (->ref uri))
        additional   (map ->str additional)
        uri-string   (string/join "/" (cons base-uri additional))
        requires-js? (fn  [version] (.startsWith (name version) "1"))]
    (if (requires-js? version)
      (str uri-string ".js")
      uri-string)))

(defn create-fetch [fetch]
  (cond
    (string? fetch) fetch
    (true? fetch)   "true"
    (false? fetch)  "false"
    :else           (->> fetch
                         (map ->rally-case)
                         (string/join ","))))

(defn create-order [orders]
  (let [direction? #{:asc :desc}
        ;; :name => [:name]
        orders     (if (keyword? orders) [orders] orders)
        ;; [:name :desc] => [[:name :desc]]
        orders     (if (direction? (second orders)) [orders] orders)
        transform  (fn [[attribute direction]]
                     (str (->rally-case attribute) " " (name direction)))]
    (->> orders
         (map (fn [order] (if (sequential? order) (transform order) (->rally-case order))))
         (string/join ","))))

(declare create-query)

(defn- translate-value [value]
  (cond
   (string? value) (if (.startsWith value "http") value (str "\"" value "\""))
   :else           (->ref value)))

(defn- create-expression [[operator left right]]
  (let [lhs (->> (string/split (name left) #"\.")
                 (map ->rally-case)
                 (string/join "."))]
    (str "(" lhs " " (name operator) " " (translate-value right) ")")))

(defn- group-expressions [logic-expression expression]
  (let [logic-str (string/upper-case (name logic-expression))]
    (if (sequential? expression)
      (str "(" (group-expressions logic-expression (first expression)) " " logic-str " " (second expression) ")")
      expression)))

(defn- create-expressions [logic-expr expressions]
  (->> expressions
       (map create-query)
       (reduce #(vector %1 %2))
       (group-expressions logic-expr)))

(defn create-query [query]
  (let [logic-expression? #{:or :and}]
    (when-let [[expr & rest] query]
      (if (logic-expression? expr)
        (create-expressions expr rest)
        (create-expression query)))))
