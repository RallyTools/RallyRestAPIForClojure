(ns rally.api.schema-test
  (:require [rally.api :as api]
            [rally.api.schema :as schema]
            [clojure.test :refer :all]))

(def ^:dynamic *rest-api*)
(defn schema-fixture [f]
  (let [rest-api (api/create-rest-api)]
    (try
      (binding [*rest-api* rest-api]
        (f))
      (finally (api/shutdown-rest-api rest-api)))))

(use-fixtures :each schema-fixture)

(deftest ^:integration required-attributes
  (is (= [:name] (schema/required-attribute-names *rest-api* :defect))))

(deftest ^:integration required-attributes-with-current-user
  (binding [api/*current-user* *rest-api*]
    (is (= [:name] (schema/required-attribute-names :defect)))))

