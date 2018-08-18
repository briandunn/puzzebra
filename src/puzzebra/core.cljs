(ns puzzebra.core
  (:require
    ["react-native" :as ReactNative]
    [reagent.core :as r :refer [atom adapt-react-class reactify-component]]))

(def text (adapt-react-class (.-Text ReactNative)))
(def view (adapt-react-class (.-View ReactNative)))

(defn root []
  [text "pants"])

(def app (reactify-component root))
