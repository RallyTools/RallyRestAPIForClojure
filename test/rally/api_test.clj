(ns rally.api-test 
  (:require [clojure.test :refer :all]
            [clojure.set :as set]
            [crypto.random :as random]
            [environ.core :as env]
            [rally.api :as api]))

(def ^:dynamic *rest-api*)

(defn api-fixture [f]
  (let [rest-api (api/create-rest-api)]
    (try
      (binding [*rest-api* rest-api]
        (f))
      (finally (api/shutdown-rest-api rest-api)))))

(def generate-string (partial random/base32 15))

(use-fixtures :each api-fixture)

(deftest ^:integration userstory-can-be-created
  (let [userstory-name (generate-string)
        userstory      (api/create! *rest-api* :userstory {:name userstory-name})]
    (is (= (:metadata/ref-object-name userstory) userstory-name))))

(deftest ^:integration userstory-can-be-queried-by-formatted-id
  (let [userstory-name (generate-string)
        userstory      (api/create! *rest-api* :userstory {:name userstory-name})
        read-userstory (api/find-by-formatted-id *rest-api* :userstory (:formatted-id userstory))]
    (is (= (:metadata/ref-object-name read-userstory) userstory-name))))

(deftest ^:integration find-by-formatted-id-should-handle-the-oddity-in-querying-for-artifacts
  (let [userstory (api/create! *rest-api* :userstory {:name (generate-string)})
        defect    (api/create! *rest-api* :defect {:name (generate-string)})
        artifact  (api/find-by-formatted-id *rest-api* :artifact (:formatted-id defect))]
    (is (= (:metadata/ref-object-name artifact) (:metadata/ref-object-name defect)))))

(deftest ^:integration userstory-can-be-updated
  (let [userstory-name (generate-string)
        userstory      (api/create! *rest-api* :userstory {:name (generate-string)})
        _              (api/update! *rest-api* userstory {:name userstory-name})
        read-userstory (api/find-by-formatted-id *rest-api* :userstory (:formatted-id userstory))]
    (is (= (:metadata/ref-object-name read-userstory) userstory-name))))

(deftest ^:integration userstory-can-be-updated-without-existing-object
  (let [userstory-name (generate-string)
        userstory      (api/create! *rest-api* :userstory {:name (generate-string)})
        _              (api/update! *rest-api* (:metadata/ref userstory) {:name userstory-name})
        read-userstory (api/find-by-formatted-id *rest-api* :userstory (:formatted-id userstory))]
    (is (= (:metadata/ref-object-name read-userstory) userstory-name))))

(deftest ^:integration can-get-userstory-by-ref
  (let [userstory-name (generate-string)
        userstory      (api/create! *rest-api* :userstory {:name userstory-name})
        read-userstory (api/find *rest-api* (:metadata/ref userstory))]
    (is (= (:metadata/ref-object-name read-userstory) userstory-name))))

(deftest ^:integration can-delete-userstory
  (let [userstory      (api/create! *rest-api* :userstory {:name (generate-string)})
        _              (api/delete! *rest-api* userstory)
        read-userstory (api/find *rest-api* (:metadata/ref userstory))]
    (is (nil? read-userstory))))

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
  (let [created-userstories (doall (repeatedly 20 #(api/create! *rest-api* :userstory {:name (generate-string)})))
        userstory-seq       (api/query *rest-api* :userstory {:order [:name]})

        userstory-names     (map :metadata/ref-object-name userstory-seq)
        sorted-names        (sort String/CASE_INSENSITIVE_ORDER userstory-names)]

    (is (= userstory-names sorted-names))))

(deftest ^:integration relationships-can-be-queried
  (let [created-defects [(api/create! *rest-api* :defect {:name (generate-string)}) (api/create! *rest-api* :defect {:name (generate-string)})]
        userstory       (api/create! *rest-api* :userstory {:name (generate-string)})
        _               (api/update-collection! *rest-api* (:defects userstory) :add created-defects)
        defects         (api/query *rest-api* (:defects userstory))]
    (is (= (sort (map :metadata/ref-object-name created-defects)) (sort (map :metadata/ref-object-name defects))))))

(deftest ^:integration relationships-can-be-removed
  (let [created-defects [(api/create! *rest-api* :defect {:name (generate-string)}) (api/create! *rest-api* :defect {:name (generate-string)})]
        userstory       (api/create! *rest-api* :userstory {:name (generate-string)})
        _               (api/update-collection! *rest-api* (:defects userstory) :add created-defects)
        _               (api/update-collection! *rest-api* (:defects userstory) :remove [(first created-defects)])
        defects         (api/query *rest-api* (:defects userstory))]
    (is (= [(:metadata/ref-object-name (second created-defects))] (map :metadata/ref-object-name defects)))))

(deftest ^:integration parent-can-be-set-on-userstory
  (let [parent (api/create! *rest-api* :userstory {:name (generate-string)})
        child  (api/create! *rest-api* :userstory {:name (generate-string) :parent parent})]
    (is (= (:metadata/ref parent) (:metadata/ref (:parent child))))))

(deftest ^:integration parent-can-be-updated-on-userstory
  (let [parent        (api/create! *rest-api* :userstory {:name (generate-string)})
        child         (api/create! *rest-api* :userstory {:name (generate-string)})
        updated-child (api/update! *rest-api* child {:parent parent})]
    (is (= (:metadata/ref parent) (:metadata/ref (:parent updated-child))))))
