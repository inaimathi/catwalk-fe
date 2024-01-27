(ns catwalk-fe.blogcast
  (:require [clojure.string :as str]
            [clojure.pprint :as pprint]

            [reagent.core :as r]
            [reagent.dom :as rd]

            [catwalk-fe.api :as api]
            [catwalk-fe.model :as model]))

(def BLOGCAST-KEYMAP {})
(def CURRENT-CAST (r/atom nil))
(def DEFERRED-EDITS (r/atom []))

(defn remove-by-ix [vec ix]
  (into (subvec vec 0 ix) (subvec vec (inc ix))))

(defn edit-line-interface [update-state remove-line audio-job-id parent-id ix default-voice default-check line]
  (let [picked-voice (r/atom default-voice)
        line-text (r/atom line)
        audio-job (r/atom audio-job-id)
        check (r/atom default-check)
        update! (fn [] (update-state @check @line-text))]
    (fn []
      (if (string? line)
        [:tr (if @check {:class "table-success"})
         [:td [:button
               {:class "btn btn-primary form-input"
                :on-click remove-line}
               "🗑"]]
         [:td [:button
               {:class "btn btn-primary form-input"
                :on-click #(let [new-ln @line-text]
                             (swap! check not)
                             (update!))}
               (if @check "x" "✓")]]
         [:td [:button
               {:class "btn btn-primary form-input"
                :on-click #(let [new-ln @line-text
                                 voice @picked-voice]
                             (.log js/console "ReRecording" new-ln voice)
                             (update!)
                             (api/blogcast-line-job
                              parent-id new-ln voice
                              (fn [data]
                                (.log js/console "RESPONSE FROM NEW JOB" (clj->js data))
                                (reset! audio-job (:id data)))))}
               "ReRecord"]]
         [:td
          [:textarea
           {:class "form-control":value @line-text :style {:height "100%" :width "100%"}
            :on-change #(reset! line-text (.-value (.-target %)))}]]
         [:td [:select {:class "form-select" :value @picked-voice :on-change #(reset! picked-voice (.-value (.-target %)))}
               (map (fn [voice]
                      ^{:key (str "voice-" voice "-" (gensym))}
                      [:option {:value voice} voice]) @model/VOICES)]]
         [:td (if-let [wav (get-in @model/JOB-MAP [@audio-job :output 0] nil)]
                [:audio {:controls true} [:source {:src wav :type "audio/wav"}]])]]
        [:tr
         [:td [:button
               {:class "btn btn-primary form-input"
                :on-click remove-line}
               "🗑"]]
         [:td {:colSpan 3} [:pre (str line)]]]))))

(def CURRENT-SCRIPT (r/atom nil))
(def CURRENT-CHECKS (r/atom nil))
(def CURRENT-STITCHED (r/atom nil))

(defn -api-update [callback]
  (let [job @CURRENT-CAST
        update (assoc
                (:output job)
                :script (->> @CURRENT-SCRIPT sort (filter second) (map second) (into []))
                :checks @CURRENT-CHECKS
                :stitched @CURRENT-STITCHED)]
    (.log js/console "UPDATING " (clj->js job))
    (.log js/console " ------- " (clj->js update))
    (api/update-job (:id job) callback :output update)))

(defn -init! [job]
  (reset! CURRENT-CAST job)
  (reset! CURRENT-SCRIPT (->> job :output :script (map-indexed (fn [ix el] [ix el])) (into {})))
  (reset! CURRENT-CHECKS (or (:checks (:output job)) (into [] (repeat (count (:script (:output job))) false))))
  (reset! CURRENT-STITCHED (->> job :output :stitched)))

(defn -close! []
  (reset! CURRENT-CAST nil)
  (reset! CURRENT-SCRIPT nil)
  (reset! CURRENT-CHECKS nil)
  (reset! CURRENT-STITCHED nil))

(defn edit-interface [job]
  [:div
   [:div {:class "input-group my-2"}
    [:button {:class "btn btn-primary form-input" :on-click -close!} "Close"]
    [:span {:class "input-group-text"}
     "URL: " [:a {:href (:url (:input job))} (:url (:input job))]
     " Voice: " (:voice (:input job))]
    (when @CURRENT-STITCHED
      [:span {:class "input-group-text"} [:audio {:controls true} [:source {:src @CURRENT-STITCHED :type "audio/wav"}]]])
    (when (= "COMPLETE" (:status job))
      [:button {:class "btn btn-primary form-input"
                :on-click #(let [stitch-list (->> @CURRENT-SCRIPT sort (map second)
                                                  (map (fn [ln] (or (-> (model/children-with-text (:id job) ln) reverse first (get-in [:output 0])) ln))))]
                             (.log js/console "AUDIO STITCHING" (clj->js stitch-list))
                             (api/audio-stitch
                              stitch-list
                              (fn [data]
                                (.log js/console "RETURNED" (clj->js data))
                                (reset! CURRENT-STITCHED (:file data)))))}
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
      (when @CURRENT-SCRIPT
        (->> @CURRENT-SCRIPT sort
             (map
              (fn [[ix line]]
                (let [update-state
                      (fn [check text]
                        (swap! CURRENT-SCRIPT (fn [v] (assoc v ix text)))
                        (swap! CURRENT-CHECKS (fn [v] (assoc v ix check))))
                      remove-line
                      (fn []
                        (swap! CURRENT-SCRIPT (fn [v] (dissoc v ix))))]
                  ^{:key (str "blogcast-child-" (:id job) "-" (gensym))}
                  [edit-line-interface
                   update-state remove-line
                   (-> (model/children-with-text (:id job) line) reverse first :id)
                   (:id job) ix (:voice (:input job)) (get @CURRENT-CHECKS ix) line])))))]]]])

(defn list-interface [job]
  (let [input-id (str "input-" (gensym))
        output-id (str "output-"(gensym))
        show-script (r/atom false)]
    (fn []
      (.log js/console "JOB-INTERFACE RENDERING")
      ^{:key (str "job-" (:id job))}
      [:tr
       [:td [:button {:class "btn btn-primary form-input" :on-click #(-init! job)}
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
       [:div {:class "input-group"}
        [:input {:class "form-control me-2" :type "search" :placeholder "URL" :aria-label "URL" :on-change #(reset! cast-url (.-value (.-target %)))}]
        [:select {:class "form-select me-2" :value @cast-voice :on-change #(reset! cast-voice (.-value (.-target %)))}
         (map (fn [voice]
                ^{:key (str "voice-" voice "-" (gensym))}
                [:option {:value voice} voice]) @model/VOICES)]
        [:button
         {:class "from-input btn btn-outline-success" :type "input"
          :on-click #(do (.preventDefault %)
                         (.log js/console "CASTING...")
                         (api/blogcast-job
                          @cast-url @cast-voice
                          (fn [data] (.log js/console "GOT BACK DATA - " data))))}
         "Cast"]]
       (when @CURRENT-CAST
         [:button
          {:class "btn btn-outline-success mx-2" :type "input"
           :on-click #(do (.preventDefault %)
                          (.log js/console "SAVING")
                          (-api-update (fn [data] (.log js/console "GOT BACK DATA - " (clj->js data))))
                          ;; (api/blogcast-job
                          ;;  @cast-url @cast-voice
                          ;;  (fn [data] (.log js/console "GOT BACK DATA - " data)))
                          )}
          "Save"])])))

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
