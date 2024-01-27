(ns catwalk-fe.blogcast
  (:require [clojure.string :as str]
            [clojure.pprint :as pprint]

            [reagent.core :as r]
            [reagent.dom :as rd]

            [catwalk-fe.api :as api]
            [catwalk-fe.model :as model]))

(def BLOGCAST-KEYMAP {})
(def CURRENT-CAST (r/atom nil))

;; (defn -blogcast-child-map [child-jobs]
;;   (reduce
;;    (fn [memo [k v]]
;;      (if (contains? memo k)
;;        (update memo k conj v)
;;        (assoc memo k [v])))
;;    {} (map
;;        (fn [child] [(-> child :input :text) child])
;;        child-jobs)))

;; (defn -blogcast-job-state [job]
;;   (let [script (-> job :output :script)
;;         child-map (-blogcast-child-map (:children job))]
;;     {:script script
;;      :child-map child-map
;;      :files (reduce
;;              (fn [memo ln]
;;                (if (string? ln)
;;                  (assoc memo ln (get-in child-map [ln 0 :output 0] nil))
;;                  memo))
;;              {} script)}))

;; (defn -audio-list [ln jobs state]
;;   (let [lst-id (str (gensym))]
;;     (doall
;;      (map
;;       (fn [wav]
;;         ^{:key (str "wav-" lst-id "-" wav)}
;;         [:li {:class "list-group-item"}
;;          [:input
;;           {:class "form-check-input me-1" :type "radio" :value wav :name lst-id
;;            :on-change #(let [new-file (.-value (.-target %))]
;;                          (swap! state assoc-in [:files ln] new-file))
;;            :checked (= (get-in @state [:files ln]) wav)}]
;;          [:audio {:controls true} [:source {:src wav :type "audio/wav"}]]])
;;       (mapcat :output jobs)))))

;; (defn status-badge [job]
;;   ^{:key (gensym)}
;;   [:span
;;    {:class (str "mx-2 badge "
;;                 (case (:status job)
;;                   "CANCELLED" "bg-warning text-dark"
;;                   "ERRORED" "bg-danger"
;;                   "COMPLETE" "bg-success"
;;                   "bg-primary"))}
;;    (:status job)])

;; (defn line-text [job str-ln]
;;   (let [ln-state (r/atom str-ln)
;;         picked-voice (r/atom "leo")]
;;     (fn []
;;       [:div {:class "input-group"}
;;        [:button {:class "btn btn-primary form-input"
;;                  :on-click #(let [new-line @ln-state]
;;                               (api/update-job
;;                                (:id job)
;;                                (:status job)
;;                                (assoc
;;                                 (:output job) :script
;;                                 (replace {str-ln @ln-state}
;;                                          (:script (:output job))))
;;                                (fn [data]
;;                                  (.log js/console "UPDATED PARENT JOB" (clj->js data))
;;                                  (api/blogcast-line-job
;;                                   (:id job) new-line (or (-> job :input :voice) "leo")
;;                                   (fn [data] (.log js/console "RESPONSE FROM NEW JOB" (clj->js data)))))))}
;;         "Record"]
;;        [:textarea {:class "form-control" :defaultValue str-ln
;;                    :on-change #(reset! ln-state (.-value (.-target %)))}]
;;        [:select {:class "form-select" :value @picked-voice :on-change #(reset! picked-voice (.-value (.-target %)))}
;;       (map (fn [voice]
;;              ^{:key (str "voice-" voice "-" (gensym))}
;;              [:option {:value voice} voice]) @model/VOICES)]])))

(defn remove-by-ix [vec ix]
  (into (subvec vec 0 ix) (subvec vec (inc ix))))

(defn edit-line-interface [script-atom checks-atom parent-id default-voice ix]
  (let [picked-voice (r/atom default-voice)
        line (get @script-atom ix)
        line-text (r/atom line)
        api-update (fn [callback]
                     (api/update-job
                      parent-id callback
                      :output (assoc
                               (get-in @model/JOB-MAP [parent-id :output])
                               :script @script-atom
                               :checks @checks-atom)))]
    (fn []
      (if (string? line)
        [:tr (if (get @checks-atom ix) {:class "table-success"})
         [:td [:button
               {:class "btn btn-primary form-input"
                :on-click #(let [new-ln @line-text]
                             (swap! script-atom (fn [v] (remove-by-ix v ix)))
                             (api-update (fn [data] (.log js/console "UPDATED PARENT JOB" (clj->js data)))))}
               "🗑"]]
         [:td [:button
               {:class "btn btn-primary form-input"
                :on-click #(let [new-ln @line-text]
                             (swap! checks-atom (fn [vec] (update vec ix not)))
                             (api-update (fn [data] (.log js/console "UPDATED PARENT JOB" (clj->js data)))))}
               (if (get @checks-atom ix) "x" "✓")]]
         [:td [:button
               {:class "btn btn-primary form-input"
                :on-click #(let [new-ln @line-text
                                 voice @picked-voice]
                             (swap! script-atom (fn [v] (assoc v ix new-ln)))
                             (api-update
                              (fn [data]
                                (api/blogcast-line-job
                                 parent-id new-ln voice
                                 (fn [data] (.log js/console "RESPONSE FROM NEW JOB" (clj->js data)))))))}
               "ReRecord"]]
         [:td
          [:textarea
           {:class "form-control":value @line-text :style {:height "100%" :width "100%"}
            :on-change #(reset! line-text (.-value (.-target %)))}]]
         [:td [:select {:class "form-select" :value @picked-voice :on-change #(reset! picked-voice (.-value (.-target %)))}
               (map (fn [voice]
                      ^{:key (str "voice-" voice "-" (gensym))}
                      [:option {:value voice} voice]) @model/VOICES)]]
         [:td (if-let [wav (-> (model/children-with-text parent-id @line-text) reverse first (get-in [:output 0]))]
                [:audio {:controls true} [:source {:src wav :type "audio/wav"}]])]]
        [:tr
         [:td [:button
               {:class "btn btn-primary form-input"
                :on-click #(let [new-ln @line-text]
                             (swap! script-atom (fn [v] (remove-by-ix v ix)))
                             (api-update
                              (fn [data]
                                (.log js/console "UPDATED PARENT JOB" (clj->js data)))))}
               "🗑"]]
         [:td {:colSpan 3} [:pre (str line)]]]))))

(defn edit-interface [job]
  (let [script (r/atom (:script (:output job)))
        checks (r/atom (or (:checks (:output job)) (into [] (repeat (count (:script (:output job))) false))))
        stitched (r/atom nil)]
    (fn []
      [:div
       [:div {:class "input-group my-2 fixed-bottom"}
        [:button {:class "btn btn-primary form-input" :on-click #(reset! CURRENT-CAST nil)} "Close"]
        [:span {:class "input-group-text"}
         (str (:input job))]
        (when @stitched
          [:span {:class "input-group-text"} [:audio {:controls true} [:source {:src @stitched :type "audio/wav"}]]])
        (when (= "COMPLETE" (:status job))
          [:button {:class "btn btn-primary form-input"
                    :on-click #(let [stitch-list (map (fn [ln] (or (-> (model/children-with-text (:id job) ln) reverse first (get-in [:output 0])) ln)) @script)]
                                 (.log js/console "AUDIO STITCHING" (clj->js stitch-list))
                                 (api/audio-stitch
                                  stitch-list
                                  (fn [data]
                                    (.log js/console "RETURNED" (clj->js data))
                                    (reset! stitched (:file data)))))}
           "Download"])]
       [:div
        [:table {:class "table table-hover"}
         [:thead
          [:tr
           [:th {:scope "col"}]
           [:th {:scope "col"}]
           [:th {:scope "col"} "text"]
           [:th {:scope "col"} "voice"]
           [:th {:scope "col"} "audio"]]]
         [:tbody
          (when @script
            (map-indexed
             (fn [ix line]
               ^{:key (str "blogcast-child-" (:id job) "-" (gensym))}
               [edit-line-interface script checks (:id job) (:voice (:input job)) ix line])
             @script))]]]])))

(defn list-interface [job]
  (let [input-id (str "input-" (gensym))
        output-id (str "output-"(gensym))
        show-script (r/atom false)]
    (fn []
      (.log js/console "JOB-INTERFACE RENDERING")
      ^{:key (str "job-" (:id job))}
      [:tr
       [:td [:button {:class "btn btn-primary form-input" :on-click #(reset! CURRENT-CAST job)}
             "Edit"]]
       [:td (:id job)] [:td (:status job)]
       [:td (str (:input job))]
       [:td {:on-click #(swap! show-script not)}
        [:ul {:class "list-group"}
         (map
          (fn [ln]
            ^{:key (str "list-interface-line-" (:id job) "-" (gensym))}
            [:li {:class "list-group-item"} ln])
          (let [script-lines (->> job :output :script (filter string?))]
            (if @show-script
              script-lines
              (conj (into [] (take 5 script-lines)) "..."))))]
        ;; [:pre (with-out-str (pprint/pprint (:output job)))]
        ]])))

(defn toolbar []
  (let [cast-url (r/atom "")
        cast-voice (r/atom "leo")]
    (fn []
      [:form {:class "d-flex mx-5"}
       [:input {:class "form-control me-2" :type "search" :placeholder "URL" :aria-label "URL" :on-change #(reset! cast-url (.-value (.-target %)))}]
       [:select {:class "form-select me-2" :value @cast-voice :on-change #(reset! cast-voice (.-value (.-target %)))}
        (map (fn [voice]
               ^{:key (str "voice-" voice "-" (gensym))}
               [:option {:value voice} voice]) @model/VOICES)]
       [:button
        {:class "btn btn-outline-success" :type "input"
         :on-click #(do (.preventDefault %)
                        (.log js/console "CASTING...")
                        (api/blogcast-job
                         @cast-url @cast-voice
                         (fn [data] (.log js/console "GOT BACK DATA - " data))))}
        "Cast"]])))

(defn interface []
  (if @CURRENT-CAST
    [edit-interface @CURRENT-CAST]
    [:table {:class "table table-hover"}
     [:thead
      [:tr
       [:th {:scope "col"} ""]
       [:th {:scope "col"} "id"]
       [:th {:scope "col"} "status"]
       [:th {:scope "col"} "input"]
       [:th {:scope "col"} "output"]]]
     [:tbody
      (doall
       (map
        (fn [job]
          ^{:key (str "blogcast-job-interface-" (:id job))}
          [list-interface job])
        (->> @model/JOB-MAP sort (map second) reverse (filter #(= "blogcast" (:job_type %))))))]]))
