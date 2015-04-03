(ns rally-api.data-test
  (:require [rally-api.data :as data]
            [clojure.test :refer :all])
  (:import [java.util UUID]
           [java.net URI]))

(deftest metadata-name?-should-work-with-rally-case-and-clojure-case-names
  (is (= true (data/metadata-name? "_ref")))
  (is (= true (data/metadata-name? :_ref)))
  (is (= true (data/metadata-name? :metadata/ref)))
  (is (= false (data/metadata-name? :name)))
  (is (= false (data/metadata-name? "Name"))))

(deftest custom-field-name?-should-work-with-rally-case-and-clojure-case-names
  (is (= true (data/custom-field-name? "c_MyCustomField")))
  (is (= true (data/custom-field-name? :c_MyCustomField)))
  (is (= true (data/custom-field-name? :custom/my-custom-field)))
  (is (= false (data/custom-field-name? :name)))
  (is (= false (data/custom-field-name? "Name"))))

(deftest convert-from-clojure-to-rally-case
  (is (= "ObjectID" (data/->rally-case :object-id)))
  (is (= "Name" (data/->rally-case :name)))
  (is (= "DirectChildrenCount" (data/->rally-case :direct-children-count)))
  (is (= "_ref" (data/->rally-case :metadata/ref)))
  (is (= "FormattedID" (data/->rally-case :formatted-id)))
  (is (= "_objectVersion" (data/->rally-case :metadata/object-version)))
  (is (= "c_MyCustomField" (data/->rally-case :custom/my-custom-field))))

(deftest convert-from-rally-to-clojure-case
  (is (= :object-id (data/->clojure-case "ObjectID")))
  (is (= :metadata/ref (data/->clojure-case :_ref)))
  (is (= :metadata/ref (data/->clojure-case "_ref")))
  (is (= :custom/my-custom-field (data/->clojure-case "c_MyCustomField")))
  (is (= :custom/my-custom-field (data/->clojure-case :c_MyCustomField)))
  (is (= :current-project-name (data/->clojure-case "CurrentProjectName"))))

(deftest convert-to-rally-map
  (is (= {:Name "Adam"} (data/->rally-map {:name "Adam"})))
  (is (= {:ObjectID 123} (data/->rally-map {:object-id 123}))))

(deftest convert-clojure-type-to-rally-type
  (is (= data/user-story-rally-type (data/clojure-type->rally-type :user-story)))
  (is (= data/user-story-rally-type (data/clojure-type->rally-type :userstory)))
  (is (= data/user-story-rally-type (data/clojure-type->rally-type :UserStory)))  
  (is (= "security" (data/clojure-type->rally-type :security)))
  (is (= "Defect" (data/clojure-type->rally-type :defect))))

(deftest convert-rally-ref-to-clojure-type
  (is (= :user-story (data/rally-ref->clojure-type "https://localhost/slm/webservice/v2.0/hierarchicalrequirement/1234")))
  (is (= :defect (data/rally-ref->clojure-type "https://localhost/slm/webservice/v2.0/Defect")))
  (is (= :defect (data/rally-ref->clojure-type "https://localhost/slm/webservice/v2.0/Defect/create"))))

(deftest convert-to-clojure-map
  (let [uuid (UUID/randomUUID)]
    (is (= {:query-result {:metadata/type :user :total-result-count 1}} (data/->clojure-map {:QueryResult {:_type "User" :TotalResultCount 1}})))
    (is (= {:query-result {:metadata/type :user-story}} (data/->clojure-map {:QueryResult {:_type data/user-story-rally-type}})))
    (is (= {:query-result {:metadata/ref-object-uuid uuid}} (data/->clojure-map {:QueryResult {:_refObjectUUID (str uuid)}})))
    (is (= {:metadata/object-version 123} (data/->clojure-map {:_objectVersion "123"})))
    (is (= {:metadata/ref (URI. "https://localhost/slm/webservice/v2.0/hierarchicalrequirement/1234")}
           (data/->clojure-map {:_ref "https://localhost/slm/webservice/v2.0/hierarchicalrequirement/1234"})))
    (is (= {:metadata/rally-api-major 2} (data/->clojure-map {:_rallyAPIMajor "2"})))
    (is (= {:metadata/rally-api-minor 0} (data/->clojure-map {:_rallyAPIMinor "0"})))))

(deftest create-fetch-should-translate-names
  (let [fetch [:name :formatted-id :object-id :description]]
    (is (= "Name,FormattedID,ObjectID,Description" (data/create-fetch fetch)))))

(deftest create-fetch-should-passthrough-true
  (let [fetch true]
    (is (= "true" (data/create-fetch fetch)))))

(deftest create-order-should-translate-correctly
  (is (= "Name" (data/create-order [:name])))
  (is (= "Name,Description" (data/create-order [:name :description])))
  (is (= "Name asc" (data/create-order [[:name :asc]])))
  (is (= "Description,Name asc" (data/create-order [:description [:name :asc]])))
  (is (= "Name desc,ObjectID" (data/create-order [[:name :desc] :object-id]))))

(deftest create-query-should-translate-names
  (let [query [:= :formatted-id "S80221"]]
    (is (= "(FormattedID = \"S80221\")" (data/create-query query)))))

(deftest create-query-should-do-proper-nesting
  (is (= "((FormattedID = \"S123\") AND (Name contains \"foo\"))"
         (data/create-query [:and
                             [:= :formatted-id "S123"]
                             [:contains :name "foo"]])))
  (is (= "(((Name = \"Junk\") AND (Age = 34)) AND (Email contains \"test.com\"))"
         (data/create-query [:and
                             [:= :name "Junk"]
                             [:= :age 34]
                             [:contains :email "test.com"]])))
  (is (= "(((Name = \"Junk\") OR (Age = 34)) OR (Email contains \"test.com\"))"
         (data/create-query [:or
                             [:= :name "Junk"]
                             [:= :age 34]
                             [:contains :email "test.com"]])))
  (is (= "(((Name = \"Junk\") AND (Age = 34)) OR (Email contains \"test.com\"))"
         (data/create-query [:or
                             [:and 
                              [:= :name "Junk"]
                              [:= :age 34]]
                             [:contains :email "test.com"]])))
    (is (= "((Name = \"Junk\") OR ((Age = 34) AND (Email contains \"test.com\")))"
         (data/create-query [:or
                             [:= :name "Junk"]
                             [:and 
                              [:= :age 34]
                              [:contains :email "test.com"]]]))))

(deftest convert-to-ref
  (let [ref-str "http://localhost:7001/slm/webservice/v2.0/defect"]
    (is (= ref-str (data/->ref ref-str)))
    (is (= ref-str (data/->ref {:metadata/ref ref-str})))))
