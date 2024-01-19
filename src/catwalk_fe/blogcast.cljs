(ns catwalk-fe.front-end.blogcast
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [clojure.string :as str]

            [reagent.core :as r]
            [reagent.dom :as rd]))

(defn -blogcast-child-map [child-jobs]
  (reduce
   (fn [memo [k v]]
     (if (contains? memo k)
       (update memo k conj v)
       (assoc memo k [v])))
   {} (map
       (fn [child] [(-> child :input :text) child])
       child-jobs)))

(defn jobs-interface [jobs]
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
                   [:textarea {:class "form-control" :defaultValue ln}]]
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
