(ns catwalk-fe.jobs
  (:require [clojure.string :as str]
            [clojure.pprint :as pprint]

            [reagent.core :as r]

            [catwalk-fe.api :as api]
            [catwalk-fe.model :as model]))

(defn single-job [job]
  (let [expand-input (r/atom false)
        expand-output (r/atom false)
        limit 40]
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
        [:pre {:class "text-start" :style {"cursor" "pointer"} :on-click #(swap! expand-input not)}
         (let [inp (with-out-str (pprint/pprint (:input job)))]
           (if (or (>= limit (count inp)) @expand-input)
             inp
             (str (subs inp 0 limit) "...")))]]
       [:td
        [:pre {:class "text-start" :style {"cursor" "pointer"} :on-click #(swap! expand-output not)}
         (let [outp (with-out-str (pprint/pprint (:output job)))]
           (if (or (>= limit (count outp)) @expand-output)
             outp
             (str (subs outp 0 limit) "...")))]]])))

(defn child-jobs [parent]
  (let [expand-children (r/atom false)]
    (fn []
      [:tr
       [:td]
       [:td {:colspan 6}
        (let [children (->> @model/JOB-MAP sort (map second) (filter #(= (:id parent) (:parent_job %))))]
          (if (not @expand-children)
            (let [stat-map (frequencies (map :status children))
                  stat-total (count children)]
              [:div {:class "progress" :on-click #(swap! expand-children not) :style {"cursor" "pointer"}}
               [:div {:class "progress-bar bg-success" :style {"width" (str (* (/ (get stat-map "COMPLETE") stat-total) 100) "%") "cursor" "pointer"}}]
               [:div {:class "progress-bar bg-danger" :style {"width" (str (* (/ (get stat-map "ERRORED") stat-total) 100) "%") "cursor" "pointer"}}]])
            [:table {:class "table table-hover"}
             [:thead {:on-click #(swap! expand-children not)}
              [:tr
               [:th {:scope "col"} ""]
               [:th {:scope "col"} "type"]
               [:th {:scope "col"} "status"]
               [:th {:scope "col"} "input"]
               [:th {:scope "col"} "output"]]]
             [:tbody
              (doall
               (map
                (fn [job]
                  ^{:key (str "job-child-" (:id job) "-" (gensym))}
                  [single-job job])
                children))]]))]])))

(defn interface []
  [:table {:class "table table-hover"}
   [:thead
    [:tr
     [:th {:scope "col"} ""]
     [:th {:scope "col"} "type"]
     [:th {:scope "col"} "status"]
     [:th {:scope "col"} "input"]
     [:th {:scope "col"} "output"]]]
   [:tbody
    (doall
     (map
      (fn [job]
        ^{:key (str "job-interface-" (:id job) "-" (gensym))}
        (list
         [single-job job]
         (when (nil? (:parent_job job))
           [child-jobs job])))
      (->> @model/JOB-MAP sort (map second) (filter #(not (:parent_job %))) reverse)))]])
