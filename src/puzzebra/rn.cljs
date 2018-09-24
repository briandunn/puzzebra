(ns puzzebra.rn (:require ["react-native" :as ReactNative]
                          [reagent.core :refer [adapt-react-class]]))

(defn- adapt [prop] (-> ReactNative (aget prop) adapt-react-class))

(def activity-indicator (adapt "ActivityIndicator"))
(def button (adapt "Button"))
(def scroll-view (adapt "ScrollView"))
(def slider (adapt "Slider"))
(def text (adapt "Text"))
(def touchable-opacity (adapt "TouchableOpacity"))
(def view (adapt "View"))

(defn get-fields [fields js] (mapv (partial aget js) fields))

(defn create-pan-responder [config]
  (let [key-names {:on-start-should-set :onStartShouldSetPanResponder
                   :on-release :onPanResponderRelease
                   :on-grant :onPanResponderGrant
                   :on-move :onPanResponderMove}]
    (js->clj
      (.-panHandlers
        (.create
          (.-PanResponder ReactNative)
          (clj->js (reduce (fn [acc [k v]] (assoc acc (k key-names) v)) {} config)))))))

(defn on-layout [callback]
  #(->>
     (.. % -nativeEvent -layout)
     (get-fields ["x" "y" "width" "height"])
     (zipmap [:x :y :width :height])
     callback))
