(ns catwalk-fe.api
  (:require [clojure.string :as str]
            [clojure.pprint :as pprint]

            [reagent.core :as r]
            [reagent.dom :as rd]))

(defn -form-encoded [m]
  (->> m
       (map (fn [[k v]]
              (str
               (if (keyword? k) (name k) k)
               "="
               (js/encodeURIComponent
                (if (string? v)
                  v
                  (.stringify js/JSON (clj->js v)))))))
       (str/join "&")))

(defn -api-call [endpoint callback & {:keys [method data] :or {method "GET" data nil}}]
  (-> (js/fetch
       endpoint
       (clj->js
        (if data
          {:method method
           :headers {:Content-Type "application/x-www-form-urlencoded"}
           :body data}
          {:method method})))
      (.then #(.json %))
      (.then #(js->clj % :keywordize-keys true))
      (.then callback)))

;;;;;;;;;; Jobs interface
(defn list-jobs [callback]
  (-api-call "/v1/job" callback))

(defn get-job [job-id callback]
  (let [url (str "/v1/job/" job-id "?include_children=True")]
    (.log js/console "URL: " url)
    (-api-call url callback)))

(defn retry-job [job-id callback]
  (-api-call (str "/v1/job/" job-id) callback :method "PUT"))

(defn cancel-job [job-id callback]
  (-api-call (str "/v1/job/" job-id) callback :method "DELETE"))

(defn delete-job [job-id callback]
  (-api-call (str "/v1/job/" job-id) callback :method "DELETE" :data (-form-encoded {:shred true})))

(defn update-job [job-id callback & {:keys [status output]}]
  (let [data (->> {:status status :output output} (filter second) (into {}))]
    (if (not (empty? data))
      (-api-call
       (str "/v1/job/" job-id) callback
       :method "POST"
       :data (-form-encoded data)))))

(defn create-job [parent type input callback]
  (let [data (if parent {:type type :parent_job parent :input input} {:type type :input input})]
    (-api-call
     "/v1/job" callback :method "POST"
     :data (-form-encoded data))))

;;;;;;;;;; Voices interface
(defn available-voices [callback] (-api-call "/v0/audio/tts" callback))

(defn blogcast-job [url voice callback]
  (create-job nil "blogcast" {:url url :voice voice :k 1} callback))

(defn blogcast-line-job [parent-id text voice callback]
  (create-job parent-id "tts" {:text text :voice voice :k 1} callback))

(defn tts-job [text voice callback]
  (create-job nil "tts" {:text text :voice voice :k 1} callback))

(defn audio-stitch [stitch-list callback]
  (-api-call
   "/v1/audiofile/stitch" callback
   :method "POST"
   :data (-form-encoded {:stitch_list stitch-list})))
