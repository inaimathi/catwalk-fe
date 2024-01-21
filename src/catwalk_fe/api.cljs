(ns catwalk-fe.api
  (:require [clojure.string :as str]
            [clojure.pprint :as pprint]

            [reagent.core :as r]
            [reagent.dom :as rd]

            [catwalk-fe.blogcast :as bc]))

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

(defn job-tree [callback] (-api-call "/v1/job" callback))

(defn available-voices [callback] (-api-call "/v0/audio/tts" callback))

(defn blogcast-job [url voice callback]
  (-api-call
   "/v1/job" callback :method "POST"
   :data (-form-encoded {:type "blogcast" :input {:url url :voice voice :k 1}})))

(defn blogcast-line [parent-id text voice callback]
  (-api-call
   "/v1/job" callback :method "POST"
   :data (-form-encoded {:type "tts" :parent parent-id :input {:text text :voice voice :k 1}})))

(defn retry-job [job-id callback]
  (-api-call (str "/v1/job/" job-id) callback :method "PUT"))

(defn update-job [job-id status output callback]
  (-api-call
   (str "/v1/job/" job-id) callback
   :method "POST"
   :data (-form-encoded {:status status :output output})))

(defn audio-stitch [stitch-list callback]
  (-api-call
   "/v1/audiofile/stitch" callback
   :method "POST"
   :data (-form-encoded {:stitch_list stitch-list})))
