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

(defn- create-object [type {:keys [object-id uuid] :as object}]
  (let [object-id   (or object-id (rand-int 1000000))
        object-type (clojure-type->rally-type type)
        uuid        (or uuid (str (UUID/randomUUID)))]
    (assoc object
           :_ref           (str "http://localhost:8080/webservice/v2.0/" type "/" object-id)
           :_type          (string/capitalize (name object-type))
           :_objectVersion "20"
           :_refObjectUUID uuid
           :_refObjectName (or (:name object) (:display-name object))
           :object-id      object-id
           :_rallyAPIMajor "2"                                       
           :_rallyAPIMinor "0")))

(defn create-query-results [results]
  {:QueryResult
   {:Errors [],
    :Warnings [],
    :TotalResultCount (count results),
    :StartIndex 1,
    :PageSize 20,
    :_rallyAPIMajor "2",
    :_rallyAPIMinor "0"
    :Results results}})

(defn create-user
  ([] (create-user {}))
  ([{:keys [display-name user-name] :as user}]
   (let [display-name (or display-name (random/base32 10))
         user-name    (or user-name (str (random/base32 10) "@test.com"))]
     (->> (assoc user
                 :display-name display-name
                 :user-name    user-name)
          (create-object :user)
          ->rally-map))))

(defn create-userstory
  ([] (create-userstory {}))
  ([{:keys [formatted-id name] :as userstory}]
   (let [formatted-id (or formatted-id (str "S" (rand-int 100000)))
         name         (or name (random/base32 20))]
     (->> (assoc userstory
                 :formatted-id          formatted-id
                 :name                  name
                 :direct-children-count 0)
          (create-object :userstory)
          ->rally-map))))

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
