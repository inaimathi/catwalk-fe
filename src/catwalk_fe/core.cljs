(ns catwalk-fe.core
  (:require [clojure.string :as str]
            [clojure.pprint :as pprint]

            [reagent.core :as r]
            [reagent.dom :as rd]

            [catwalk-fe.api :as api]
            [catwalk-fe.model :as model]
            [catwalk-fe.blogcast :as bc]))

(def VERSION "0.0.3")

(defonce CAST-URL (r/atom ""))
(defonce CAST-VOICE (r/atom "leo"))


(defn app []
  [:div {:class "container p-5"}
   [:nav {:class "navbar navbar-expand-lg navbar-light bg-light p-3 sticky-top"}
    [:a {:class "navbar-brand" :href "#"} "Catwalk"]
    ;; [:ul {:class "navbar-nav me-auto mb-2 mb-lg-0"}
    ;;  [:li {:class "nav-item"} [:a {:href "#" :class "nav-link active"} "Blogcast"]]
    ;;  [:li {:class "nav-item"} [:a {:href "#" :class "nav-link"} "Jobs"]]
    ;;  [:li {:class "nav-item"} [:a {:href "#" :class "nav-link"} "TTS"]]]
    [:form {:class "d-flex mx-5"}
     [:input {:class "form-control me-2" :type "search" :placeholder "URL" :aria-label "URL" :on-change #(reset! CAST-URL (.-value (.-target %)))}]
     [:select {:class "form-select me-2" :value @CAST-VOICE :on-change #(reset! CAST-VOICE (.-value (.-target %)))}
      (map (fn [voice]
             ^{:key (str "voice-" voice "-" (gensym))}
             [:option {:value voice} voice]) @model/VOICES)]
     [:button
      {:class "btn btn-outline-success" :type "input"
       :on-click #(do (.preventDefault %)
                      (.log js/console "CASTING...")
                      (api/blogcast-job
                       @CAST-URL @CAST-VOICE
                       (fn [data] (.log js/console "GOT BACK DATA - " data))))}
      "Cast"]]
    [:span {:class "navbar-text mx-5"} VERSION]]
   (let [bc-jobs (filter #(= "blogcast" (:job_type %)) (:job-tree @model/APP-STATE))]
     (bc/jobs-interface bc-jobs))])

(defn -key-from-event [e]
  (let [k (.-key e)
        C (when (.-ctrlKey e) "C")
        S (when (.-shiftKey e) "S")
        M (when (.-altKey e) "M")]
    (str/join "-" (filter identity [C S M k]))))

(defn ^:export run []
  (.log js/console "HELLO THERE, FROM THE RUN COMMAND!")
  (let [ws (js/WebSocket. (str "ws://" (-> js/window .-location .-host) "/v1/job/updates"))]
    (.log js/console "Set up websocket" ws)
    (aset ws "onmessage" (fn [m] (.log js/console "GOT WS MESSAGE" m))))
  (.addEventListener
   (js/document.querySelector "body")
   "keydown"
   (fn [e]
     (when (not (contains? #{"Control" "Alt" "Shift"} (.-key e)))
       (.log js/console "KEYDOWN - " e " - `" (-key-from-event e) "`"))))
  (rd/render [app] (js/document.getElementById "app")))

(defn on-load [callback]
  (.addEventListener
   js/window
   "DOMContentLoaded"
   callback))

(on-load
 (fn []
   (api/job-tree #(swap! model/APP-STATE assoc :job-tree (:jobs %)))
   (api/available-voices #(reset! model/VOICES (:voices %)))
   (.log js/console "Hello from catwalk-fe")
   (run)))
