(ns rally-api.data
  (:require [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as utils]
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
   (metadata-name? n)     (str "_" (csk/->camelCaseString n))
   (custom-field-name? n) (str "c_" (csk/->PascalCaseString n))
   :else                  (-> (csk/->PascalCaseString n)
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
    (csk/->kebab-case-keyword type)))

(defn clojure-type->rally-type [type]
  (case type
    :userstory  user-story-rally-type
    :user-story user-story-rally-type
    :UserStory  user-story-rally-type
    :security   "security"
    (csk/->PascalCaseString type)))

(defn rally-ref->clojure-type [rally-ref]
  (let [type-regex #"/slm/webservice/[^/]+/([^/]+).*"
        [_ type]   (re-find type-regex (str rally-ref))]
    (rally-type->clojure-type type)))

(defn- not-nil? [v]
  (not (or (empty? v) (= "null" v))))

(defn ->clojure-map [m]
  (letfn [(transform [[k v]]
            (let [new-k (->clojure-case k)
                  new-v (case new-k
                          :metadata/type            (rally-type->clojure-type v)
                          :metadata/ref-object-uuid (when (not-nil? v) (UUID/fromString v))
                          :metadata/object-version  (Integer/parseInt v)
                          :metadata/ref             (when (not-nil? v) (URI/create v))
                          :metadata/rally-api-major (Integer/parseInt v)
                          :metadata/rally-api-minor (Integer/parseInt v)
                          v)]
              [new-k new-v]))]
    (walk/postwalk (fn [x] (if (map? x) (into {} (map transform x)) x)) m)))

(defn ->ref [ref-or-object]
  (or (:metadata/ref ref-or-object) ref-or-object))

(defn ->oid [value]
  (-> value
      ->ref
      str
      (string/split #"\/")
      last
      (Long.)))

(defn create-fetch [fetch]
  (if (true? fetch)
    "true"
    (->> (map ->rally-case fetch)
         (string/join ","))))

(defn create-order [orders]
  (let [transform (fn [[attribute direction]]
                    (str (->rally-case attribute) " " (name direction)))]
    (->> orders
         (map (fn [order] (if (sequential? order) (transform order) (->rally-case order))))
         (string/join ","))))

(declare create-query)
(def ^:private logic-expression? #{:or :and})

(defn- create-expression [[operator left right]]
  (let [right (if (string? right) (str "\"" right "\"") right)]
    (str "(" (->rally-case left) " " (name operator) " " right ")")))

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
  (when-let [[expr & rest] query]
    (if (logic-expression? expr)
      (create-expressions expr rest)
      (create-expression query))))
