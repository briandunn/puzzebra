(ns puzzebra.rn.animated (:require ["react-native" :as ReactNative]
                                   [reagent.core :refer [adapt-react-class]]))

(def animated (.-Animated ReactNative))

(def view (-> animated .-View adapt-react-class))
(defn spring [value config] (.spring animated value (clj->js config)))
(defn timing [value config] (.timing animated value (clj->js config)))
(defn value [config] (new (.-Value animated) (clj->js config)))
(defn value-xy [config] (new (.-ValueXY animated) (clj->js config)))

(defn event [mapping & [config]]
  (.event animated (clj->js mapping) (clj->js (or config {}))))
