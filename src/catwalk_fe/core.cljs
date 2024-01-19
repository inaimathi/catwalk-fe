(ns catwalk-fe.front-end.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [clojure.string :as str]
            [clojure.pprint :as pprint]

            [reagent.core :as r]
            [reagent.dom :as rd]

            [catwalk-fe.front-end.blogcast :as bc]))

(defonce st-jobs (r/atom []))

(defn app []
  [:div {:class "container p-5"}
   [:ul {:class "nav nav-pills mb-3"}
    [:li {:class "nav-item"} [:a {:href "#" :class "nav-link active"} "Blogcast"]]
    [:li {:class "nav-item"} [:a {:href "#" :class "nav-link"} "Jobs"]]
    [:li {:class "nav-item"} [:a {:href "#" :class "nav-link"} "TTS"]]]
   (bc/jobs-interface (filter #(= "blogcast" (:job_type %)) @st-jobs))])

(defn ^:export run []
  (.log js/console "HELLO THERE, FROM THE RUN COMMAND!")
  (rd/render [app] (js/document.getElementById "app")))

(.log js/console "HELLO FROM CLJS")

(defn on-load [callback]
  (.addEventListener
   js/window
   "DOMContentLoaded"
   callback))

(defn >api-call [endpoint callback]
  (-> (js/fetch endpoint)
      (.then #(.json %))
      (.then #(js->clj % :keywordize-keys true))
      (.then callback)))

(on-load
 (fn []
   (>api-call "/v1/job" #(reset! st-jobs (:jobs %)))
   (.log js/console "Hello from catwalk-fe")
   (run)))
