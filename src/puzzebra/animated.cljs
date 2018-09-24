(ns puzzebra.animated
  (:import [goog.async Throttle])
  (:require
    ["react-native" :as ReactNative]
    [puzzebra.rn :as rn :refer [get-fields create-pan-responder]]
    [puzzebra.rn.animated :refer [view value value-xy event timing spring]]
    [reagent.core :as r :refer [atom]]))

(defn fade-in []
  (let [opacity (value 0)]
    (-> opacity
        (timing {:toValue 1})
        (.start))
    (fn []
      (let [this (r/current-component)]
        (into
          [view (update (r/props this) :style assoc :opacity opacity)]
          (r/children this))))))

(defn draggable [{:keys [on-move on-release on-start-should-set on-grant on-layout]
                  :or {on-start-should-set (constantly true)
                       on-layout identity
                       on-grant identity
                       on-move identity
                       on-release identity}}]
  (let [layout-pt (atom nil)       ; parent relative
        drag-start-pt (atom [0 0]) ; component relative
        on-layout (rn/on-layout (fn [{:keys [x y] :as rect}]
                               (reset! layout-pt [x y])
                               (on-layout rect)))
        pan (value-xy {:x 0 :y 0})
        on-move (new Throttle (fn [state] (on-move (mapv + (get-fields ["dx" "dy"] state) @layout-pt))) 250)
        pan-handlers (create-pan-responder
                       {:on-start-should-set on-start-should-set
                        :on-grant (fn []
                                    (let [[x y] @drag-start-pt]
                                      (doto pan
                                        (.setOffset #js {:x x :y y})
                                        (.setValue #js {:x 0 :y 0})))
                                    (on-grant))
                        :on-move (event
                                   [nil {:dx pan.x :dy pan.y}]
                                   {:listener (fn [_ state]
                                                (.fire on-move state))})
                        :on-release (fn [_ state]
                                      (let [lpt @layout-pt]
                                        (on-release
                                          (get-fields ["dx" "dy"] state)
                                          lpt
                                          (fn [dest-pt]
                                            (let [[x y] (mapv - dest-pt lpt)]
                                              (reset! drag-start-pt [x y])
                                              (.flattenOffset pan)
                                              (-> pan
                                                  (spring {:toValue {:x x :y y}})
                                                  (.start)))))))})]
    (fn []
      (let [this (r/current-component)
            {:keys [style] :or {style {}}} (r/props this)
            props (merge
                    pan-handlers
                    {:style (assoc style :transform (.getTranslateTransform pan))
                     :on-layout on-layout})]
        (into [view props] (r/children this))))))
