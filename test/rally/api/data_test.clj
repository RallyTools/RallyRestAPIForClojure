(ns rally.api.data-test
  (:require [rally.api.data :as data]
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
  (is (= {:ObjectID 123} (data/->rally-map {:object-id 123})))
  (is (= {:Name "Jane", :Parent "http://localhost/slm/webservice/v2.0/defect/1234"}
         (data/->rally-map {:name "Jane", :parent {:metadata/ref "http://localhost/slm/webservice/v2.0/defect/1234" :name "Junk"}})))
  (is (= [{:_ref "http://localhost/slm/webservice/v2.0/defect/1234"}] (data/->rally-map [{:metadata/ref "http://localhost/slm/webservice/v2.0/defect/1234"}]))))

(deftest convert-clojure-type-to-rally-type
  (is (= data/user-story-rally-type (data/clojure-type->rally-type :user-story)))
  (is (= data/user-story-rally-type (data/clojure-type->rally-type :userstory)))
  (is (= data/user-story-rally-type (data/clojure-type->rally-type :UserStory)))  
  (is (= "security" (data/clojure-type->rally-type :security)))
  (is (= "Defect" (data/clojure-type->rally-type :defect)))
  (is (= "PortfolioItem/Feature" (data/clojure-type->rally-type :portfolio-item/feature))))

(deftest convert-rally-ref-to-clojure-type
  (is (= :user-story (data/rally-ref->clojure-type "https://localhost/slm/webservice/v2.0/hierarchicalrequirement/1234")))
  (is (= :defect (data/rally-ref->clojure-type "https://localhost/slm/webservice/v2.0/Defect")))
  (is (= :defect (data/rally-ref->clojure-type "https://localhost/slm/webservice/v2.0/Defect/create")))
  (is (= :revision) (data/rally-ref->clojure-type "https://localhost/slm/webservice/v2.0/UserStory/1325/Revision/1231"))
  (is (= :revision (data/rally-ref->clojure-type "https://localhost/slm/webservice/v2.0/UserStory/aa5b2cb6-2912-9e09-692a-d7865c828727/Revision/aa5b2cb6-2912-9e09-692a-d7865c828727")))
  (is (= :portfolio-item/feature (data/rally-ref->clojure-type "https://localhost/slm/webservice/v2.0/PortfolioItem/Feature"))))

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
  (is (= "Name" (data/create-order :name)))
  (is (= "Name" (data/create-order [:name])))
  (is (= "Name desc" (data/create-order [:name :desc])))
  (is (= "Name asc" (data/create-order [:name :asc])))
  (is (= "Name,Description" (data/create-order [:name :description])))
  (is (= "Name asc" (data/create-order [[:name :asc]])))
  (is (= "Description,Name asc" (data/create-order [:description [:name :asc]])))
  (is (= "Name desc,ObjectID" (data/create-order [[:name :desc] :object-id])))
  (is (= "Name desc,ObjectID asc" (data/create-order [[:name :desc] [:object-id :asc]]))))

(deftest create-query-should-translate-names
  (let [query [:= :formatted-id "S80221"]]
    (is (= "(FormattedID = \"S80221\")" (data/create-query query)))))

(deftest create-query-should-translate-objects
  (let [query [:= :parent {:metadata/ref "https://localhost/slm/webservice/v2.0/hierarchicalrequirement/1234"}]]
    (is (= "(Parent = https://localhost/slm/webservice/v2.0/hierarchicalrequirement/1234)" (data/create-query query)))))

(deftest create-query-should-translate-uris
  (let [query [:= :parent (URI. "https://localhost/slm/webservice/v2.0/hierarchicalrequirement/1234")]]
    (is (= "(Parent = https://localhost/slm/webservice/v2.0/hierarchicalrequirement/1234)" (data/create-query query)))))

(deftest create-query-should-translate-uri-string
  (let [query [:= :parent "https://localhost/slm/webservice/v2.0/hierarchicalrequirement/1234"]]
    (is (= "(Parent = https://localhost/slm/webservice/v2.0/hierarchicalrequirement/1234)" (data/create-query query)))))

(deftest create-query-should-handle-nested-properties
  (is (= "(Parent.Name = \"Foo\")" (data/create-query [:= :parent.name "Foo"])))
  (is (= "(Parent.TotalResults = 0)" (data/create-query [:= :parent.total-results 0]))))

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
  (let [ref-str   "http://localhost:7001/slm/webservice/v2.0/defect"
        tasks-ref (str ref-str "/tasks")]
    (is (= ref-str (data/->ref ref-str)))
    (is (= ref-str (data/->ref {:metadata/ref ref-str})))
    (is (= tasks-ref (data/->ref [{:metadata/ref ref-str} :tasks])))
    (is (= tasks-ref (data/->ref [ref-str :tasks])))
    (is (= tasks-ref (data/->ref [(URI. ref-str) :tasks])))
    (is (= tasks-ref (data/->ref [(URI. ref-str) "tasks"])))))

(deftest to-uri-string-should-handle-all-the-cases
  (let [rally {:host "http://localhost:7001", :version :v2.0}]
    (is (= "http://localhost:7001/slm/webservice/v2.0/Defect" (data/->uri-string rally :defect)))
    (is (= "http://localhost:7001/slm/webservice/v2.0/defect/123/tasks" (data/->uri-string rally ["http://localhost:7001/slm/webservice/v2.0/defect/123" :tasks])))
    (is (= "http://localhost:7001/slm/schema/v2.0/workspace/123" (data/->uri-string rally [:slm :schema :v2.0 :workspace "123"])))
    (is (= "http://localhost:7001/slm/webservice/v2.0/defect/123" (data/->uri-string rally {:metadata/ref "http://localhost:7001/slm/webservice/v2.0/defect/123"})))
    (is (= "http://localhost:7001/slm/webservice/v2.0/defect/123" (data/->uri-string rally "http://localhost:7001/slm/webservice/v2.0/defect/123")))
    (is (= "http://localhost:7001/slm/webservice/v2.0/defect/123" (data/->uri-string rally (URI. "http://localhost:7001/slm/webservice/v2.0/defect/123"))))))

(deftest to-uri-string-should-handle-old-versions
  (let [rally {:host "http://localhost:7001", :version :1.43}]
    (is (= "http://localhost:7001/slm/webservice/1.43/Defect.js" (data/->uri-string rally :defect)))))

(deftest convert-to-str
  (is (= "" (data/->str nil)))
  (is (= "100" (data/->str 100)))
  (is (= "name" (data/->str :name)))
  (is (= "name" (data/->str "name")))
  (is (= "http://localhost:7001/slm/webservice/v2.0/defect" (data/->str (URI. "http://localhost:7001/slm/webservice/v2.0/defect")))))

(deftest t-uri-like?
  (is (true? (data/uri-like? "http://localhost:7001/slm/webservice/v2.0/defect")))
  (is (false? (data/metadata-name? :name)))
  (is (true? (data/uri-like? {:metadata/ref "http://localhost:7001/slm/webservice/v2.0/defect"})))
  (is (true? (data/uri-like? [{:metadata/ref "http://localhost:7001/slm/webservice/v2.0/defect"} :tasks])))
  (is (true? (data/uri-like? (URI. "http://localhost:7001/slm/webservice/v2.0/defect")))))
