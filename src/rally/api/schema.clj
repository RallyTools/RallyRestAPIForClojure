(ns rally.api.schema
  (:require [clojure.walk :as walk]
            [rally.api :as api]
            [rally.data :as data]
            [rally.request :as request]))

(defn- required-attribute? [{:keys [read-only required]}]
  (and (not read-only) required))

(defn- map-by-element-name [s]
  (->> s
    (map #(vector (:element-name %) %))
    (into {})))

(defn- ->cleanup-schema [m]
  (let [transform (fn [[k v]]
                    (let [new-v (case k
                                  :element-name            (data/rally-type->clojure-type v)
                                  :attributes              (map-by-element-name v)
                                  v)]
                      [k new-v]))]
    (walk/postwalk (fn [x] (if (map? x) (into {} (map transform x)) x)) m)))

(defn current-schema [rest-api]
  (let [current-project (api/find rest-api (request/get-current-project rest-api))]
    ;; The schema endpoint ignores page information and gives you the entire result.
    (-> (api/query rest-api [:slm :schema :v2.0 :project (str (:object-id current-project))])
        ->cleanup-schema
        map-by-element-name)))

(defn type-def [rest-api type]
  (let [schema-for-project (current-schema rest-api)
        rally-type         (data/clojure-type->rally-type type)]
    (get schema-for-project type)))

(defn attribute-def [rest-api type attribute]
  (-> (type-def rest-api type)
      (get-in [:attributes attribute])))

(defn filtered-attributes [rest-api type pred]
  (->> (type-def rest-api type)
       :attributes
       vals
       (filter pred)
       (map :element-name)
       sort))

(defn attribute-names [rest-api type]
  (filtered-attributes rest-api type (constantly true)))

(defn required-attribute-names [rest-api type]
  (filtered-attributes rest-api type required-attribute?))
