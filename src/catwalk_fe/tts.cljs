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
       [:textarea {:value @text :on-change #(reset! text (.-value (.-target %))) :class "form-control mb-3 mt-3"}]
       [:div {:class "row mb-3"}
        [:div {:class "d-grid gap-2 col-6"}
         [:select {:class "form-select" :value @picked-voice :on-change #(reset! picked-voice (.-value (.-target %)))}
          (map (fn [voice]
                 ^{:key (str "voice-" voice "-" (gensym))}
                 [:option {:value voice} voice]) @model/VOICES)]]
        [:div {:class "d-grid gap-2 col-6"}
         [:button
          {:class "btn btn-primary"
           :on-click #(api/tts-job
                       @text @picked-voice
                       (fn [data]
                         (.log js/console "RESPONSE FROM NEW JOB" (clj->js data))))}
          "Record"]]]])))


(defn interface []
  [:span
   [text-form]
   [:ul {:class "list-group"}
    (map
     (fn [job]
       ^{:key (str "tts-job-" (:id job))}
       [:li {:class "list-group-item"}
        [:div {:class "row" }
         [:div {:class "gap-2 col-6"}
          [:figure {:class "col-6"}
           [:blockquote {:class "blockquote"}
            (-> job :input :text)]
           [:figcaption {:class "blockquote-footer"}
            (-> job :input :voice)]]]
         (if-let [wav (get-in job [:output 0] nil)]
           [:div {:class "gap-2 col-6"}
            [:audio {:controls true :class "col-12"} [:source {:src wav :type "audio/wav"}]]]
           [:div {:class "spinner-border text-primary" :role "status"}
            [:span {:class "visually-hidden"} "Working..."]])]])
     (->> @model/JOB-MAP sort (map second) reverse (filter #(and (not (:parent_job %)) (= "tts" (:job_type %))))))]])
