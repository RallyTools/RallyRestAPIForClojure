(ns rally-api.data
  (:require [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as utils]
            [clojure.string :as string]
            [clojure.walk :as walk]
            [crypto.random :as random])
  (:import [java.util UUID]
           [java.net URI]))

(def metadata-name? #{:_ref :_type :_objectVersion :_refObjectUUID :_refObjectName :_rallyAPIMajor :_rallyAPIMinor})

(defn ->rally-case [n]
  (if (metadata-name? n)
    (name n)
    (-> (csk/->PascalCaseString n)
        (.replace "Id" "ID"))))

(defn ->rally-map [m]
  (let [f (comp keyword ->rally-case)]
    (utils/transform-keys f m)))

(defn rally-type->clojure-type [type]
  (if (= type "HierarchicalRequirement")
    :userstory
    (csk/->kebab-case-keyword type)))

(defn clojure-type->rally-type [type]
  (if (= type :userstory)
    "HierarchicalRequirement"
    (csk/->PascalCaseString type)))

(defn ->clojure-key-name [k]
  (if (metadata-name? k)
    (keyword "metadata" (csk/->kebab-case-string k))
    (csk/->kebab-case-keyword k)))

(defn ->clojure-map [m]
  (letfn [(transform [[k v]]
            (let [new-k (->clojure-key-name k)
                  new-v (case new-k
                          :metadata/type            (rally-type->clojure-type v)
                          :metadata/ref-object-uuid (UUID/fromString v)
                          :metadata/object-version  (Integer/parseInt v)
                          :metadata/ref             (URI/create v)
                          :metadata/rally-api-major (Integer/parseInt v)
                          :metadata/rally-api-minor (Integer/parseInt v)
                          v)]
              [new-k new-v]))]
    (walk/postwalk (fn [x] (if (map? x) (into {} (map transform x)) x)) m)))

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
