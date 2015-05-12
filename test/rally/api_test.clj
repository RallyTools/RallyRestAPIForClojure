(ns rally.api-test 
  (:require [clojure.test :refer :all]
            [clojure.set :as set]
            [crypto.random :as random]
            [environ.core :as env]
            [rally.api :as api]
            [rally.api.data :as data]
            [rally.api.request :as request]))

(def ^:dynamic *rest-api*)
(def generate-string (partial random/base32 15))

(defn api-fixture [f]
  (let [default-data (fn [type data] (merge {:name (generate-string)} data))
        rest-api     (request/set-default-data-fn (api/create-rest-api) default-data)]
    (try
      (binding [*rest-api* rest-api]
        (f))
      (finally (api/shutdown-rest-api rest-api)))))

(use-fixtures :each api-fixture)

(deftest ^:integration objects-can-be-created
  (let [userstory-name (generate-string)
        userstory      (api/create! *rest-api* :userstory {:name userstory-name})]
    (is (= (:metadata/ref-object-name userstory) userstory-name))))

(deftest ^:integration objects-can-be-created-with-current-user
  (binding [api/*current-user* *rest-api*]
    (let [userstory-name (generate-string)
        userstory      (api/create! :userstory {:name userstory-name})]
      (is (= (:metadata/ref-object-name userstory) userstory-name)))))

(deftest ^:integration an-error-occurs-when-trying-to-create-object-without-binding-current-user
  (is (thrown? AssertionError (api/create! :userstory {:name (generate-string)}))))

(deftest ^:integration objects-can-be-queried-by-formatted-id
  (let [userstory-name (generate-string)
        userstory      (api/create! *rest-api* :userstory {:name userstory-name})
        read-userstory (api/find-by-formatted-id *rest-api* :userstory (:formatted-id userstory))]
    (is (= (:metadata/ref-object-name read-userstory) userstory-name))))

(deftest ^:integration find-by-formatted-id-should-handle-the-oddity-in-querying-for-artifacts
  (let [userstory (api/create! *rest-api* :userstory)
        defect    (api/create! *rest-api* :defect)
        artifact  (api/find-by-formatted-id *rest-api* :artifact (:formatted-id defect))]
    (is (= (:metadata/ref-object-name artifact) (:metadata/ref-object-name defect)))))

(deftest ^:integration older-versions-of-webservices-can-be-used
  (let [userstory-name (generate-string)
        userstory      (api/create! *rest-api* :userstory {:name userstory-name})
        read-userstory (api/find-by-formatted-id (request/set-version *rest-api* :1.43) :userstory (:formatted-id userstory))]
    (is (= (:metadata/ref-object-name read-userstory) userstory-name))))

(deftest ^:integration objects-can-be-queried-by-id
  (let [userstory-name (generate-string)
        userstory      (api/create! *rest-api* :userstory {:name userstory-name})
        read-userstory (api/find-by-id *rest-api* :userstory (:object-id userstory))]
    (is (= (:metadata/ref-object-name read-userstory) userstory-name))))

(deftest ^:integration objects-can-be-queried-by-id-with-current-user
  (binding [api/*current-user* *rest-api*]
    (let [userstory-name (generate-string)
          userstory      (api/create! :userstory {:name userstory-name})
          read-userstory (api/find-by-id :userstory (:object-id userstory))]
      (is (= (:metadata/ref-object-name read-userstory) userstory-name)))))

(deftest ^:integration objects-can-be-queried-by-uuid
  (let [userstory-name (generate-string)
        userstory      (api/create! *rest-api* :userstory {:name userstory-name})
        read-userstory (api/find-by-id *rest-api* :userstory (:metadata/ref-object-uuid userstory))]
    (is (= (:metadata/ref-object-name read-userstory) userstory-name))))

(deftest ^:integration objects-can-be-updated
  (let [userstory-name (generate-string)
        userstory      (api/create! *rest-api* :userstory)
        _              (api/update! *rest-api* userstory {:name userstory-name})
        read-userstory (api/find-by-formatted-id *rest-api* :userstory (:formatted-id userstory))]
    (is (= (:metadata/ref-object-name read-userstory) userstory-name))))

(deftest ^:integration objects-can-be-updated-with-current-user
  (binding [api/*current-user* *rest-api*]
    (let [userstory-name (generate-string)
          userstory      (api/create! :userstory)
          _              (api/update! userstory {:name userstory-name})
          read-userstory (api/find-by-formatted-id *rest-api* :userstory (:formatted-id userstory))]
      (is (= (:metadata/ref-object-name read-userstory) userstory-name)))))

(deftest ^:integration an-error-occurs-when-trying-to-update-object-without-binding-current-user
  (is (thrown? AssertionError (api/update! :userstory {:name (generate-string)}))))

(deftest ^:integration objects-can-be-updated-using-just-ref
  (let [userstory-name (generate-string)
        userstory      (api/create! *rest-api* :userstory)
        _              (api/update! *rest-api* (:metadata/ref userstory) {:name userstory-name})
        read-userstory (api/find-by-formatted-id *rest-api* :userstory (:formatted-id userstory))]
    (is (= (:metadata/ref-object-name read-userstory) userstory-name))))

(deftest ^:integration objects-can-be-found-by-ref
  (let [userstory-name (generate-string)
        userstory      (api/create! *rest-api* :userstory {:name userstory-name})
        read-userstory (api/find *rest-api* (:metadata/ref userstory))]
    (is (= (:metadata/ref-object-name read-userstory) userstory-name))))

(deftest ^:integration objects-can-be-found-by-ref-with-current-user
  (binding [api/*current-user* *rest-api*]
    (let [userstory-name (generate-string)
          userstory      (api/create! :userstory {:name userstory-name})
          read-userstory (api/find (:metadata/ref userstory))]
      (is (= (:metadata/ref-object-name read-userstory) userstory-name)))))

(deftest ^:integration objects-can-be-deleted
  (let [userstory      (api/create! *rest-api* :userstory)
        _              (api/delete! *rest-api* userstory)
        read-userstory (api/find *rest-api* (:metadata/ref userstory))]
    (is (nil? read-userstory))))

(deftest ^:integration objects-can-be-deleted-with-current-user
  (binding [api/*current-user* *rest-api*]
    (let [userstory      (api/create! :userstory)
          _              (api/delete! userstory)
          read-userstory (api/find (:metadata/ref userstory))]
      (is (nil? read-userstory)))))

(deftest ^:integration query-seq-should-cross-pages
  (let [prefix              (generate-string)
        created-userstories (doall (repeatedly 21 #(api/create! *rest-api* :userstory {:name (str prefix (generate-string))})))
        userstory-seq       (api/query *rest-api* :userstory {:start 1 :pagesize 10 :query [:contains :name prefix]})

        created-refs        (map :metadata/ref created-userstories)
        queried-refs        (map :metadata/ref userstory-seq)]

    (is (set/subset? (set (vec created-refs)) (set queried-refs)))))

(deftest ^:integration query-seq-should-cross-pages-with-default-start-and-pagesizes
  (let [prefix              (generate-string)
        created-userstories (doall (repeatedly 20 #(api/create! *rest-api* :userstory {:name (str prefix (generate-string))})))
        userstory-seq       (api/query *rest-api* :userstory {:query [:contains :name prefix]})

        created-refs        (map :metadata/ref created-userstories)
        queried-refs        (map :metadata/ref userstory-seq)]

    (is (set/subset? (set (vec created-refs)) (set queried-refs)))))

(deftest ^:integration query-should-handle-order-by-correctly
  (let [created-userstories (doall (repeatedly 20 #(api/create! *rest-api* :userstory)))
        userstory-seq       (api/query *rest-api* :userstory {:order :name})

        userstory-names     (map :metadata/ref-object-name userstory-seq)
        sorted-names        (sort String/CASE_INSENSITIVE_ORDER userstory-names)]

    (is (= userstory-names sorted-names))))

(deftest ^:integration relationships-can-be-queried
  (let [created-defects [(api/create! *rest-api* :defect) (api/create! *rest-api* :defect)]
        userstory       (api/create! *rest-api* :userstory)
        _               (api/update-collection! *rest-api* (:defects userstory) :add created-defects)
        defects         (api/query *rest-api* (:defects userstory))]
    (is (= (sort (map :metadata/ref-object-name created-defects)) (sort (map :metadata/ref-object-name defects))))))

(deftest ^:integration query-should-be-able-to-use-current-user
  (binding [api/*current-user* *rest-api*]
    (let [created-defects [(api/create! :defect) (api/create! :defect)]
          userstory       (api/create! :userstory)
          _               (api/update-collection! (:defects userstory) :add created-defects)
          defects         (api/query (:defects userstory))]
      (is (= (sort (map :metadata/ref-object-name created-defects)) (sort (map :metadata/ref-object-name defects)))))))

(deftest ^:integration relationships-can-be-queried-with-uuid
  (let [created-defects [(api/create! *rest-api* :defect) (api/create! *rest-api* :defect)]
        userstory       (api/create! *rest-api* :userstory)
        _               (api/update-collection! *rest-api* (:defects userstory) :add created-defects)
        defects-ref     (data/->uri-string (:rally *rest-api*) :userstory (:metadata/ref-object-uuid userstory) :defects)
        defects         (api/query *rest-api* defects-ref)]
    (is (= (sort (map :metadata/ref-object-name created-defects)) (sort (map :metadata/ref-object-name defects))))))

(deftest ^:integration nested-relationships-can-be-queried
  (let [parent-name (generate-string)
        parent      (api/create! *rest-api* :userstory {:name parent-name})
        child       (api/create! *rest-api* :userstory {:parent parent})
        found-child (first (api/query *rest-api* :userstory [:= :parent.name parent-name]))]
    (is (= (:metadata/ref child) (:metadata/ref found-child)))))

(deftest ^:integration relationships-can-be-removed
  (let [created-defects [(api/create! *rest-api* :defect) (api/create! *rest-api* :defect)]
        userstory       (api/create! *rest-api* :userstory)
        _               (api/update-collection! *rest-api* (:defects userstory) :add created-defects)
        _               (api/update-collection! *rest-api* (:defects userstory) :remove [(first created-defects)])
        defects         (api/query *rest-api* (:defects userstory))]
    (is (= [(:metadata/ref-object-name (second created-defects))] (map :metadata/ref-object-name defects)))))

(deftest ^:integration relationship-can-be-created-using-object
  (let [parent (api/create! *rest-api* :userstory)
        child  (api/create! *rest-api* :userstory {:parent parent})]
    (is (= (:metadata/ref parent) (:metadata/ref (:parent child))))))

(deftest ^:integration relationship-can-be-created-using-ref
  (let [parent     (api/create! *rest-api* :userstory)
        parent-ref (data/->ref parent)
        child      (api/create! *rest-api* :userstory {:parent parent-ref})]
    (is (= parent-ref (data/->ref (:parent child))))))

(deftest ^:integration relationship-can-be-created-using-uuid-ref
  (let [parent     (api/create! *rest-api* :userstory)
        parent-ref (data/->uri-string (:rally *rest-api*) :userstory (:metadata/ref-object-uuid parent))
        child      (api/create! *rest-api* :userstory {:parent parent-ref})]
    (is (= (:metadata/ref parent) (:metadata/ref (:parent child))))))

(deftest ^:integration relationship-can-be-updated-using-object
  (let [parent        (api/create! *rest-api* :userstory)
        child         (api/create! *rest-api* :userstory)
        updated-child (api/update! *rest-api* child {:parent parent})]
    (is (= (:metadata/ref parent) (:metadata/ref (:parent updated-child))))))

(deftest ^:integration relationship-can-be-updated-using-ref
  (let [parent        (api/create! *rest-api* :userstory)
        child         (api/create! *rest-api* :userstory)
        updated-child (api/update! *rest-api* (:metadata/ref child) {:parent parent})]
    (is (= (:metadata/ref parent) (:metadata/ref (:parent updated-child))))))

(deftest ^:integration relationship-can-be-updated-using-uuid-ref
  (let [parent        (api/create! *rest-api* :userstory)
        child         (api/create! *rest-api* :userstory)
        child-ref     (data/->uri-string (:rally *rest-api*) :userstory (:metadata/ref-object-uuid child))
        updated-child (api/update! *rest-api* child-ref {:parent parent})]
    (is (= (:metadata/ref parent) (:metadata/ref (:parent updated-child))))))

(deftest ^:integration vector-is-converted-to-query-spec
  (let [prefix              (generate-string)
        created-userstories (doall (repeatedly 21 #(api/create! *rest-api* :userstory {:name (str prefix (generate-string))})))
        userstory-seq       (api/query *rest-api* :userstory [:contains :name prefix])

        created-refs        (map :metadata/ref created-userstories)
        queried-refs        (map :metadata/ref userstory-seq)]

    (is (set/subset? (set (vec created-refs)) (set queried-refs)))))

(deftest ^:integration relationships-can-be-built-from-ref
  (let [parent (api/create! *rest-api* :userstory)
        child  (api/create! *rest-api* :userstory {:parent parent})]
    (= [child] (api/query *rest-api* [parent :children]))))

(deftest ^:integration objects-can-be-used-queries
  (let [parent (api/create! *rest-api* :userstory)
        child  (api/create! *rest-api* :userstory {:parent parent})]
    (= [child] (api/query *rest-api* :userstory [:= :parent parent]))))

(deftest ^:integration can-create-portfolio-items
  (let [feature-name (generate-string)
        feature      (api/create! *rest-api* :portfolio-item/feature {:name feature-name})
        read-feature (api/find *rest-api* (:metadata/ref feature))]
    (is (= (:metadata/ref-object-name read-feature) feature-name))))
