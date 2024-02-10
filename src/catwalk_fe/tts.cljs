(ns catwalk-fe.tts
  (:require [clojure.string :as str]
            [clojure.pprint :as pprint]

            [reagent.core :as r]
            [reagent.dom :as rd]

            [catwalk-fe.api :as api]
            [catwalk-fe.model :as model]))

(defn text-form []
  (let [picked-voice (r/atom "leo")
        text (r/atom "Some text")]
    (fn []
      [:div
       [:textarea {:value @text :on-change #(reset! text (.-value (.-target %))) :class "form-control"}]
       [:select {:class "form-select" :value @picked-voice :on-change #(reset! picked-voice (.-value (.-target %)))}
        (map (fn [voice]
               ^{:key (str "voice-" voice "-" (gensym))}
               [:option {:value voice} voice]) @model/VOICES)]
       [:button
        {:class "btn btn-primary form-input"
         :on-click #(api/tts-job
                     @text @picked-voice
                     (fn [data]
                       (.log js/console "RESPONSE FROM NEW JOB" (clj->js data))))}
        "Record"]])))


(defn interface []
  [:span
   [text-form]
   [:ul
    (map
     (fn [job]
       ^{:key (str "tts-job-" (:id job))}
       [:li
        (if-let [wav (get-in job [:output 0] nil)]
          [:audio {:controls true} [:source {:src wav :type "audio/wav"}]])
        [:figure
         [:blockquote {:class "blockquote"}
          (-> job :input :text)]
         [:figcaption {:class "blockquote-footer"}
          (-> job :input :voice)]]])
     (->> @model/JOB-MAP sort (map second) reverse (filter #(and (not (:parent_job %)) (= "tts" (:job_type %))))))]])
