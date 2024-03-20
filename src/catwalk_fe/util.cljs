(ns catwalk-fe.util
  (:require [clojure.string :as str]))

(defn current-hash-path []
  (->>
   (-> js/window .-location .-hash (str/split #"[#/]"))
   (filter #(not (empty? %)))
   (into [])))

(defn hash-path! [path]
  (set!
   (-> js/window .-location .-hash)
   (str "#" (str/join "/" path))))

(defn on-ws-message [f]
  (let [ws (js/WebSocket. (str "ws://" (-> js/window .-location .-host) "/v1/job/updates"))]
    (.log js/console "Set up websocket" ws)
    (aset ws "onmessage" f)
    ws))
