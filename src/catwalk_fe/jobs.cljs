(ns catwalk-fe.jobs
  (:require [clojure.string :as str]
            [clojure.pprint :as pprint]

            [reagent.core :as r]

            [catwalk-fe.api :as api]
            [catwalk-fe.model :as model]))

(defn single-job [job]
  (let [expand-input (r/atom false)
        expand-output (r/atom false)]
    (fn []
      ^{:key (str "job-row-" (:id job))}
      [:tr
       [:td
        (cond
          (contains? #{"CANCELLED" "ERRORED"} (:status job))
          [:button {:class "btn btn-primary form-input"
                    :on-click #(api/retry-job
                                (:id job)
                                (fn [data] (.log js/console "RESTARTED" (clj->js data))))}
           "Restart"]

          (not (contains? #{"RUNNING" "CANCELLED" "COMPLETE" "ERRORED"} (:status job)))
          [:button {:class "btn btn-primary form-input"
                    :on-click #(api/cancel-job
                                (:id job)
                                (fn [data] (.log js/console "CANCELLED" (clj->js data))))}
           "Cancel"])]
       [:td (str (:id job))]
       [:td (str (:parent_job job))]
       [:td [:code (:job_type job)]]
       [:td
        [:span {:class (str "input-group-text badge align-middle "
                            (case (:status job)
                              "CANCELLED" "bg-warning text-dark"
                              "ERRORED" "bg-danger"
                              "COMPLETE" "bg-success"
                              "bg-primary"))}
         (:status job)]]
       [:td
        [:pre {:class "text-start" :style (clj->js {"cursor" "pointer"}) :on-click #(swap! expand-input not)}
         (let [limit 40
               inp (with-out-str (pprint/pprint (:input job)))]
           (if (or (>= limit (count inp)) @expand-input)
             inp
             (str (subs inp 0 limit) "...")))]]
       [:td
        [:pre {:class "text-start" :style (clj->js {"cursor" "pointer"}) :on-click #(swap! expand-output not)}
         (let [limit 40
               outp (with-out-str (pprint/pprint (:output job)))]
           (if (or (>= limit (count outp)) @expand-output)
             outp
             (str (subs outp 0 limit) "...")))]]])))

(defn interface []
  [:table {:class "table table-hover"}
   [:thead
    [:tr
     [:th {:scope "col"} ""]
     [:th {:scope "col"} "id"]
     [:th {:scope "col"} "parent"]
     [:th {:scope "col"} "type"]
     [:th {:scope "col"} "status"]
     [:th {:scope "col"} "input"]
     [:th {:scope "col"} "output"]]]
   [:tbody
    (doall
     (map
      (fn [job]
        ^{:key (str "job-interface-" (:id job) "-" (gensym))}
        [single-job job])
      (->> @model/JOB-MAP sort (map second) reverse)))]])
