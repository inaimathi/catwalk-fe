(ns catwalk-fe.model
  (:require [clojure.string :as str]

            [reagent.core :as r]
            [reagent.dom :as rd]

            [catwalk-fe.api :as api]))

(defonce APP-STATE (r/atom {:job-tree []}))
(defonce VOICES (r/atom []))

(defn swap-in! [atom ks val]
  (swap! atom assoc-in ks val))

(defn swupdate-in! [atom ks f]
  (swap! atom update-in ks f))