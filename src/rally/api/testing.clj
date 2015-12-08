(ns rally.api.testing
  (:require [environ.core :as env]
            [clojure.java.io :as io]
            [cheshire.core :as cheshire]
            [slingshot.slingshot :use [throw+]]
            [clojure.string :as str]
            [clojure.walk :as walk]))

(def ^:private -mode (atom nil))
(def ^:private -directory (atom nil))

(defn override-mode! [value]
  (reset! -mode value))

(defn override-directory! [value]
  (reset! -directory value))

(defn directory []
  (or @-directory
      (env/env "RECORD_DIRECTORY")
      "test-data"))

(defn mode []
  (or @-mode
      (env/env "RECORD_MODE")))

(def ^:dynamic *recordings* nil)

(defn- configure-playback [filename]
  (if (.exists (io/as-file filename))
    (cheshire/parse-string (slurp filename) true)
    (throw+ {:message (str "no file found at : " filename)})))

(defn- persist-recordings [filename recordings]
  (io/make-parents filename)
  (->> (cheshire/generate-string recordings {:pretty true})
       (spit filename)))

(defn- gather-data [api response]
  (swap! *recordings* conj {:request (select-keys (:request api) [:url :query-params :method :body :headers])
                            :response response}))

(defn- find-playback-data-for-response
  [{:keys [url query-params method headers]}]
  (when *recordings*
    (let [keywordized-headers (walk/keywordize-keys headers)
          matching-requests (->> @*recordings*
                                 (filter (fn [d] (= url (-> d :request :url))))
                                 (filter (fn [d] (= method (-> d :request :method keyword))))
                                 (filter (fn [d] (= keywordized-headers (-> d :request :headers))))
                                 (filter (fn [d]
                                           (let [qp (-> d :request :query-params)]
                                             (if (or (:fetch query-params) (:fetch qp))
                                               (= (set (str/split (:fetch query-params) #",")) (set (str/split (:fetch qp) #",")))
                                               true)))))]
      (cond
        (empty? matching-requests)
        (throw+ {:message      "could not find matching request for playback"
                 :url          url
                 :method       method
                 :headers      headers})

        (> (count matching-requests) 1)
        (do
          (clojure.pprint/pprint {:message      "more than 1 matching request found for playback, returning first"
                                  :url          url
                                  :method       method
                                  :headers      headers})
          (first matching-requests))
        

        :default
        (first matching-requests)))))

(defn record-playback-fixture [name f]
  (let [filename   (str (directory) "/" name ".json")
        recordings (atom (if (= (mode) :playback)
                           (configure-playback filename)
                           []))]
    (binding [*recordings* recordings]
      (f))
    (when (= (mode) :record)
      (persist-recordings filename @recordings))))

(defn do-playback [api]
  (:response (find-playback-data-for-response (:request api))))

(defn do-record [api response]
  (when (= (mode) :record)
    (gather-data api response))
  response)

