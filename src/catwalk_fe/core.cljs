(ns catwalk-fe.core
  (:require [clojure.string :as str]
            [clojure.pprint :as pprint]

            [reagent.core :as r]
            [reagent.dom :as rd]

            [catwalk-fe.api :as api]
            [catwalk-fe.model :as model]
            [catwalk-fe.blogcast :as bc]
            [catwalk-fe.jobs :as jobs]))

(def VERSION "0.0.5")

(defonce SECTION (r/atom :blogcast))

(defn navbar []
  (let [nav-item (fn [section label]
                   [:li {:class "nav-item"}
                    [:a {:href "#" :class (str "nav-link" (if (= @SECTION section) " active bg-primary rounded text-light" ""))
                         :on-click #(reset! SECTION section)}
                     label]])]
    (fn []
      [:nav {:class "navbar navbar-expand-lg navbar-light bg-light p-3 fixed-top"}
       [:a {:class "navbar-brand" :href "#"} "Catwalk"]
       [:ul {:class "navbar-nav me-auto mb-2 mb-lg-0"}
        (nav-item :blogcast "Blogcast")
        (nav-item :jobs "Jobs")
        (nav-item :tts "TTS")]
       (when (= @SECTION :blogcast)
         [bc/toolbar])
       [:span {:class "navbar-text mx-5"} VERSION]])))

(defn app []
  [:div {:class "container p-5"}
   [navbar]
   (case @SECTION
     :blogcast (bc/interface)
     :jobs (jobs/interface)
     [:div [:h1 (str "TODO - " @SECTION)]])])

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
    (aset
     ws "onmessage"
     (fn [m]
       (let [job-update (js->clj (.parse js/JSON (.-data m)) :keywordize-keys true)]
         (.log js/console "GOT WS MESSAGE" (clj->js job-update))
         (.log js/console "PRE" (clj->js @model/JOB-MAP))
         (swap!
          model/JOB-MAP update
          (:job_id job-update)
          #(merge % {:id (:job_id job-update)} (select-keys job-update [:job_type :status :input :output])))
         (.log js/console "POST" (clj->js @model/JOB-MAP))))))
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
   (api/list-jobs #(reset! model/JOB-MAP (->> % :jobs (map (fn [job] [(:id job) job])) (into {}))))
   (api/available-voices #(reset! model/VOICES (:voices %)))
   (.log js/console "Hello from catwalk-fe")
   (run)))
