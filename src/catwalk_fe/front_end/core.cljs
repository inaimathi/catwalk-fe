(ns catwalk-fe.front-end.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [clojure.string :as str]
            [clojure.pprint :as pprint]

            [reagent.core :as r]
            [reagent.dom :as rd]))

(defonce st-jobs (r/atom []))

(defn -blogcast-child-map [child-jobs]
  (reduce
   (fn [memo [k v]]
     (if (contains? memo k)
       (update memo k conj v)
       (assoc memo k [v])))
   {} (map
       (fn [child] [(-> child :input :text) child])
       child-jobs)))

(defn blogcast-jobs [jobs]
  (map
   (fn [job]
     (let [input-id (str "input-" (gensym))
           output-id (str "output-"(gensym))
           child-map (-blogcast-child-map (:children job))]
       ^{:key (str "job-" (:id job))}
       [:ul {:class "list-group"}
        [:li {:class (str "list-group-item active bg-dark")}
         [:span {:class "mr-1"} "CASTING "]
         [:a {:href (-> job :input :url) :class "mr-1"} (-> job :input :url)]
         [:span {:class "mx-2 badge bg-primary"} (-> job :input :voice)]
         [:span
          {:class (str "mx-2 badge "
                       (case (:status job)
                         "CANCELLED" "bg-warning text-dark"
                         "ERRORED" "bg-danger"
                         "COMPLETE" "bg-success"
                         "bg-primary"))}
          (:status job)]]
        (when (:output job)
          [:li {:class "list-group-item"}
           [:ul {:class "list-group"}
            (map
             (fn [ln]
               ^{:key (gensym)}
               [:li {:class "list-group-item"}
                (if (string? ln)
                  [:div {:class "input-group"}
                   [:button {:class "btn btn-primary form-input"} "Record"]
                   [:textarea {:class "form-control"} ln]]
                  ;; TODO - silence encoding
                  (str ln))
                (if-let [child-jobs (get child-map ln)]
                  (map
                   (fn [ln-job]
                     ^{:key (gensym)}
                     [:li {:class "list-group-item"}
                      [:span
                       {:class (str "mx-2 badge "
                                    (case (:status ln-job)
                                      "CANCELLED" "bg-warning text-dark"
                                      "ERRORED" "bg-danger"
                                      "COMPLETE" "bg-success"
                                      "bg-primary"))}
                       (or (:status ln-job) "STARTED")]
                      [:span {:class "mx-2 badge bg-primary"} (-> ln-job :input :voice)]
                      (when (:output ln-job)
                        ;; [:button
                        ;;  {:class "btn btn-primary bg-primary"}
                        ;;  "Play"]
                        (let [lst-id (str (gensym))]
                          (map
                           (fn [wav]
                             [:li {:class "list-group-item"}
                              [:input {:class "form-check-input me-1" :type "radio" :value "" :name lst-id}]
                              [:audio {:controls true} [:source {:src wav :type "audio/wav"}]]])
                           (:output ln-job))))])
                   child-jobs))])
             (-> job :output :script))]])]))
   jobs))

(defn app []
  [:div {:class "container p-5"}
   [:ul {:class "nav nav-pills mb-3"}
    [:li {:class "nav-item"} [:a {:href "#" :class "nav-link active"} "Blogcast"]]
    [:li {:class "nav-item"} [:a {:href "#" :class "nav-link"} "Jobs"]]
    [:li {:class "nav-item"} [:a {:href "#" :class "nav-link"} "TTS"]]]
   (blogcast-jobs (filter #(= "blogcast" (:job_type %)) @st-jobs))])

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
