;; very simple example on how to use Animated with reagent
;; https://facebook.github.io/react-native/docs/animations.html
(ns puzzebra.animated
  (:require
    ["react-native" :as ReactNative]
    [reagent.core :as r :refer [atom]]))

(def animated (.-Animated ReactNative))
(def animated-value (.-Value animated))
(def animated-value-xy (.-ValueXY animated))
(def animated-view (r/adapt-react-class (.-View animated)))

(defn animated-event [mapping & [config]]
  (.event animated (clj->js mapping) (clj->js (or config {}))))

(defn get-fields [fields js] (mapv (partial aget js) fields))

(defn on-layout [callback]
  #(->>
     (.. % -nativeEvent -layout)
     (get-fields ["x" "y" "width" "height"])
     (zipmap [:x :y :width :height])
     callback))

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

(defn draggable [{:keys [on-move on-release on-start-should-set on-grant]
                  :or {on-start-should-set (constantly true)
                       on-grant identity
                       on-move identity
                       on-release identity}}]
  (let [layout-pt (atom nil)       ; parent relative
        drag-start-pt (atom [0 0]) ; component relative
        on-layout #(->> % (.-nativeEvent) (.-layout) (get-fields ["x" "y"]) (reset! layout-pt))
        pan (new animated-value-xy)
        pan-handlers (create-pan-responder
                       {:on-start-should-set on-start-should-set
                        :on-grant (fn []
                                    (let [[x y] @drag-start-pt]
                                      (doto pan
                                        (.setOffset #js {:x x :y y})
                                        (.setValue #js {:x 0 :y 0})))
                                    (on-grant))
                        :on-move (animated-event
                                   [nil {:dx pan.x :dy pan.y}]
                                   {:listener
                                    (fn [_ state]
                                      (on-move (mapv + (get-fields ["dx" "dy"] state) @layout-pt)))})
                        :on-release (fn [_ state]
                                      (let [lpt @layout-pt]
                                        (on-release
                                          (get-fields ["dx" "dy"] state)
                                          lpt
                                          (fn [dest-pt]
                                            (let [[x y] (mapv - dest-pt lpt)]
                                              (reset! drag-start-pt [x y])
                                              (.flattenOffset pan)
                                              (-> animated
                                                  (.spring pan (clj->js {:toValue {:x x :y y}}))
                                                  (.start)))))))})]
    (fn []
      (let [this (r/current-component)
            {:keys [style] :or {style {}}} (r/props this)
            props (merge
                    pan-handlers
                    {:style (assoc style :transform (.getTranslateTransform pan))
                     :on-layout on-layout})]
        (into [animated-view props] (r/children this))))))
