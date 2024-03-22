(ns catwalk-fe.core
  (:require [clojure.string :as str]
            [clojure.pprint :as pprint]

            [reagent.core :as r]
            [reagent.dom :as rd]

            [catwalk-fe.api :as api]
            [catwalk-fe.model :as model]
            [catwalk-fe.util :as util]
            [catwalk-fe.blogcast :as bc]
            [catwalk-fe.jobs :as jobs]
            [catwalk-fe.tts :as tts]))

(def VERSION "0.0.6")

(defonce SECTION (r/atom (or (-> (util/current-hash-path) first keyword) :blogcast)))

(defn get-initial-state []
  (api/list-jobs #(reset! model/JOB-MAP (->> % :jobs (map (fn [job] [(:id job) job])) (into {}))))
  (api/server-info #(reset! model/VOICES (:voices %))))

(defn navbar []
  (let [nav-item (fn [section label]
                   [:li {:class "nav-item"}
                    [:a {:href "#" :class (str "nav-link" (if (= @SECTION section) " active bg-primary rounded text-light" ""))
                         :on-click #(reset! SECTION section)}
                     label]])
        show-key (r/atom false)]
    (fn []
      [:nav {:class "navbar navbar-expand-lg navbar-light bg-light p-3 sticky-top"}
       [:a {:class "navbar-brand" :href "#"} "Catwalk"]
       [:ul {:class "navbar-nav me-auto mb-2 mb-lg-0"}
        (nav-item :jobs "Jobs")
        (nav-item :blogcast "Blogcast")
        (nav-item :tts "TTS")
        (nav-item :transcribe "Transcribe")]
       [:div {:class "input-group"}
        (if @show-key
          [:input
           {:class "form-control" :type "text" :value @api/API-KEY
            :on-change #(let [new-key (.-value (.-target %))]
                          (reset! api/API-KEY new-key))}])
        [:button
         {:class (str "form-input btn " (if @api/API-KEY "btn-primary" "btn-warning"))
          :on-click #(swap! show-key not)}
         "API Key"]]
       (when (= @SECTION :blogcast)
         [bc/toolbar])
       [:span {:class "navbar-text mx-5"} VERSION]])))

(defn app []
  [:div {:class "container p-5"}
   [navbar]
   (when @api/API-KEY
     (case @SECTION
       :blogcast (bc/interface)
       :jobs (jobs/interface)
       :tts (tts/interface)
       [:div [:h1 (str "TODO - " @SECTION)]]))])

(defn -key-from-event [e]
  (let [k (.-key e)
        C (when (.-ctrlKey e) "C")
        S (when (.-shiftKey e) "S")
        M (when (.-altKey e) "M")]
    (str/join "-" (filter identity [C S M k]))))

(defn ^:export run []
  (.log js/console "HELLO THERE, FROM THE RUN COMMAND!")
  (model/socket!
   (util/on-ws-message
    (fn [m]
      (let [job-update (js->clj (.parse js/JSON (.-data m)) :keywordize-keys true)]
        (.log js/console "GOT WS MESSAGE" (clj->js job-update))
        (.log js/console "PRE" (clj->js @model/JOB-MAP))
        (case (:status job-update)
          "DELETED" (swap! model/JOB-MAP dissoc (:id job-update))
          "STARTED" (swap! model/JOB-MAP assoc (:id job-update) job-update)
          (swap!
           model/JOB-MAP update
           (:id job-update)
           #(merge % {:id (:id job-update)}
                   (select-keys
                    job-update
                    [:job_type :status :input :output]))))
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
   (get-initial-state)
   (.log js/console "Hello from catwalk-fe")
   (.log js/console "CURRENT HASH:" (clj->js (util/current-hash-path)))
   (run)))
