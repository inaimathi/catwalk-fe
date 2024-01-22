(ns catwalk-fe.blogcast
  (:require [clojure.string :as str]

            [reagent.core :as r]
            [reagent.dom :as rd]

            [catwalk-fe.api :as api]
            [catwalk-fe.model :as model]))

(def BLOGCAST-KEYMAP {})

(defn -blogcast-child-map [child-jobs]
  (reduce
   (fn [memo [k v]]
     (if (contains? memo k)
       (update memo k conj v)
       (assoc memo k [v])))
   {} (map
       (fn [child] [(-> child :input :text) child])
       child-jobs)))

(defn -blogcast-job-state [job]
  (let [script (-> job :output :script)
        child-map (-blogcast-child-map (:children job))]
    {:script script
     :child-map child-map
     :files (reduce
             (fn [memo ln]
               (if (string? ln)
                 (assoc memo ln (get-in child-map [ln 0 :output 0] nil))
                 memo))
             {} script)}))

(defn -audio-list [ln jobs state]
  (let [lst-id (str (gensym))]
    (doall
     (map
      (fn [wav]
        ^{:key (str "wav-" lst-id "-" wav)}
        [:li {:class "list-group-item"}
         [:input
          {:class "form-check-input me-1" :type "radio" :value wav :name lst-id
           :on-change #(let [new-file (.-value (.-target %))]
                         (swap! state assoc-in [:files ln] new-file))
           :checked (= (get-in @state [:files ln]) wav)}]
         [:audio {:controls true} [:source {:src wav :type "audio/wav"}]]])
      (mapcat :output jobs)))))

(defn status-badge [job]
  ^{:key (gensym)}
  [:span
   {:class (str "mx-2 badge "
                (case (:status job)
                  "CANCELLED" "bg-warning text-dark"
                  "ERRORED" "bg-danger"
                  "COMPLETE" "bg-success"
                  "bg-primary"))}
   (:status job)])

(defn line-text [job str-ln]
  (let [ln-state (r/atom str-ln)
        picked-voice (r/atom "leo")]
    (fn []
      [:div {:class "input-group"}
       [:button {:class "btn btn-primary form-input"
                 :on-click #(let [new-line @ln-state]
                              (api/update-job
                               (:id job)
                               (:status job)
                               (assoc
                                (:output job) :script
                                (replace {str-ln @ln-state}
                                         (:script (:output job))))
                               (fn [data]
                                 (.log js/console "UPDATED PARENT JOB" (clj->js data))
                                 (api/blogcast-line-job
                                  (:id job) new-line (or (-> job :input :voice) "leo")
                                  (fn [data] (.log js/console "RESPONSE FROM NEW JOB" (clj->js data)))))))}
        "Record"]
       [:textarea {:class "form-control" :defaultValue str-ln
                   :on-change #(reset! ln-state (.-value (.-target %)))}]
       [:select {:class "form-select" :value @picked-voice :on-change #(reset! picked-voice (.-value (.-target %)))}
      (map (fn [voice]
             ^{:key (str "voice-" voice "-" (gensym))}
             [:option {:value voice} voice]) @model/VOICES)]])))

(defn job-interface [job]
  (let [job-state (r/atom (-blogcast-job-state job))
        input-id (str "input-" (gensym))
        output-id (str "output-"(gensym))]
    (fn []
      (.log js/console "JOB-INTERFACE RENDERING")
      ^{:key (str "job-" (:id job))}
      [:ul {:class "list-group"}
       [:li {:class (str "list-group-item active bg-dark")}
        [:span {:class "mr-1"} "CASTING "]
        [:a {:href (-> job :input :url) :class "mr-1"} (-> job :input :url)]
        [:span {:class "mx-2 badge bg-primary"} (-> job :input :voice)]
        (when (= (:status job) "ERRORED")
          [:button
           {:class "btn btn-primary form-input m-1"
            :on-click #(api/retry-job (:id job) (fn [data] (.log js/console (clj->js data))))}
           "Retry"])
        (when (= (:status job) "COMPLETE")
          [:button
           {:class "btn btn-primary form-input m-1"
            :on-click #(let [st @job-state
                             fs (:files st)
                             stitch-list (map (fn [ln] (get fs ln ln)) (:script st))]
                         (api/audio-stitch
                          (map
                           (fn [ln]
                             (get fs ln ln))
                           (:script st))
                          (fn [data] (swap! job-state assoc :file (:file data)))))}
           "Generate"])
        (when (:file @job-state)
          [:audio {:controls true} [:source {:src (:file @job-state) :type "audio/wav"}]])
        (status-badge job)]
       (when (= (:status job) "ERRORED")
         [:li {:class (str "list-group-item")}
          [:pre (:output job)]])
       (when (:output job)
         [:li {:class (str "list-group-item")}
          [:ul {:class "list-group"}
           (doall
            (map
             (fn [ln]
               (let [child-jobs (get-in @job-state [:child-map ln])]
                 (list
                  ^{:key (gensym)}
                  [:li {:class "list-group-item"}
                   (if (string? ln)
                     [line-text job ln]
                     ;; TODO - silence encoding
                     (str ln))
                   (if child-jobs (map status-badge child-jobs))]
                  (if child-jobs (-audio-list ln child-jobs job-state)))))
             (-> job :output :script)))]])])))

(defn interface []
  (doall
   (map
    (fn [job]
      ^{:key (str "blogcast-job-interface-" (:id job))}
      [job-interface job])
    (->> @model/JOB-MAP sort (map second) reverse (filter #(= "blogcast" (:job_type %)))))))
