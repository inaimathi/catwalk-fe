(ns catwalk-fe.model
  (:require [clojure.string :as str]

            [reagent.core :as r]
            [reagent.dom :as rd]

            [catwalk-fe.api :as api]))

(defonce JOB-MAP (r/atom {}))

(defonce APP-STATE (r/atom {:job-map {}}))
(defonce VOICES (r/atom []))

(defn current-hash-path []
  (->>
   (-> js/window .-location .-hash (str/split #"[#/]"))
   (filter #(not (empty? %)))
   (into [])))

(defn hash-path! [path]
  (set!
   (-> js/window .-location .-hash)
   (str "#" (str/join "/" path))))

(defn children-of [job-id]
  (->> @JOB-MAP sort
       (map second)
       (filter #(= job-id (:parent_job %)))))

(defn children-with-text [job-id text]
  (->> @JOB-MAP sort
       (map second)
       (filter #(and (= job-id (:parent_job %))
                     (= text (:text (:input %)))))))
