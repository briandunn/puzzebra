(ns puzzebra.rn (:require ["react-native" :as ReactNative]
                          [reagent.core :refer [adapt-react-class]]))

(def text (adapt-react-class (.-Text ReactNative)))
(def view (adapt-react-class (.-View ReactNative)))
(def touchable-opacity (adapt-react-class (.-TouchableOpacity ReactNative)))
(def button (-> ReactNative .-Button adapt-react-class))
(def slider (-> ReactNative .-Slider adapt-react-class))

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
