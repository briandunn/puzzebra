(ns puzzebra.core
  (:require
    ["react-native" :as ReactNative]
    [cognitect.transit :as t]
    [reagent.core :as r :refer [atom adapt-react-class reactify-component]]
    [reagent.ratom :refer [reaction]]))

(def text (adapt-react-class (.-Text ReactNative)))
(def view (adapt-react-class (.-View ReactNative)))
(def touchable-opacity (adapt-react-class (.-TouchableOpacity ReactNative)))
(def state (atom {}))

(defn create-pan-responder [config] (.create (.-PanResponder ReactNative) (clj->js config)))

(defn get-fields [fields js] (mapv (partial aget js) fields))

(defn round-to-nearest [size value] (* size (.round js/Math (/ value size))))

(defn row-range [args]
  (let [[first-row last-row] (sort (map first args))] (range first-row (+ 1 last-row))))

(defn p [x] (print x) x)

(defn colission? [{:keys [x y width height]} item]
  (and
    (< (:x item) (+ x width))
    (> (+ (:width item) (:x item)) x)
    (< (:y item) (+ y height))
    (> (+ (:height item) (:y item)) y)))

(defn collisions [positions]
  (set
    (flatten
      (for
        [[clue-a box-a] positions
         [clue-b box-b] positions
         :when (and (not= box-a box-b) (colission? box-a box-b))]
        [clue-a clue-b]))))

(defn draggable []
  (let [pos (atom {:current [0 0] :start [0 0]})
        ref (atom nil)
        {:keys [style key on-press]} (r/props (r/current-component))
        update-position! (fn [x y]
                           (let [dimensions (select-keys @pos [:width :height])]
                             (swap! state update :positions assoc key (merge {:x x :y y} dimensions))))
        get-delta (partial get-fields ["dx" "dy"])
        on-layout #(.measure
                     @ref
                     (fn [x y width height]
                       (swap! pos merge {:width width :height height})
                       (update-position! x y)))
        collision? (reaction (contains? (collisions (get @state :positions)) key))
        pan-responder (create-pan-responder {:onStartShouldSetPanResponder (constantly true)
                                             :onPanResponderRelease (fn [e pan-state]
                                                                      (let [delta (get-delta pan-state)]
                                                                        (when (and on-press (every? zero? delta))
                                                                          (on-press e))
                                                                      (.measure @ref update-position!)
                                                                      (swap! pos (fn [{start :start :as p}]
                                                                                   (merge p {:current [0 0]
                                                                                             :start (mapv + start delta)})))))
                                             :onPanResponderMove (fn [_ pan-state]
                                                                   (swap! pos assoc :current (get-delta pan-state)))})]
    (fn []
      (let [{:keys [current start]} @pos
            [tx ty] (mapv + current start)]
        (into
          [view
           (merge
             {:transform [{:translateX tx} {:translateY ty}]
              :on-layout on-layout
              :ref (partial reset! ref)
              :style (merge style {:z-index (if (= 0 (get-in @pos [:current 0])) 1 2)
                                   :border-color (if @collision? "red" "white")})}
             (js->clj (.-panHandlers pan-responder)))]
          (r/children (r/current-component)))))))

(def difficulties
  {"beginner"
   {:extra-clues 2
    :clue-weights
    {:in-house 1.2
     :left-of 1.0
     :same-house 1.3}
    :ensured-clues
    {:in-house 1
     :same-house 2
     :left-of 1}}
   "normal"
   {:extra-clues 1
    :clue-weights
    {:in-house 1.0
     :left-of 1.0
     :next-to 1.0
     :same-house 1.0}
    :ensured-clues
    {:in-house 1
     :same-house 1
     :left-of 1}}
   "expert"
   {:extra-clues 0
    :clue-weights
    {:in-house 0.9
     :left-of 1.1
     :next-to 1.2
     :same-house 1.1}
    :ensured-clues
    {:same-house 1
     :left-of 1}}})

(defn fetch-puzzle [config]
  (let [body (t/write (t/writer :json) config)]
    (->
      (.fetch
        js/window
        "https://zebra.joshuadavey.com/api"
        (clj->js {:method "POST"
                  :headers {"Content-Type" "application/transit+json"}
                  :body body}))
      (.then #(.text %))
      (.then (partial t/read (t/reader :json))))))

(defn row-color [row-number] (nth ["#9ccc65" "#ffa726" "#fdd835" "#29b6f6"] row-number))

(defmulti piece :clue/type)

(def side-length 30)

(def cell-style {:height side-length
                 :width side-length})

(defn clue-frame-style [cells-wide]
  {:align-items "flex-start"
   :flex-direction "row"
   :flex-wrap "wrap"
   :border-color "#ccc"
   :border-style "solid"
   :border-width 1
   :overflow "hidden"
   :margin 10
   :width (* (+ 1 side-length) cells-wide)})

(defn cell [i y]
  [view {:style (merge cell-style {:background-color (row-color y) :justify-content "center"})}
   [text {:style {:text-align "center"}} i]])

(defn empty-cell []
  [view {:style cell-style}])

(defmethod piece :left-of [{args :clue/args :as key}]
  (let [cells (for [y (row-range args)
                    x (range 2)]
                (let [[row value] (nth args x [])]
                  (partial (if (= y row) (partial cell value) empty-cell) y)))]
    [draggable {:key key :style (clue-frame-style 2)}
     (doall (map (fn [component i] ^{:key i}[component]) cells (range)))]))

(defmethod piece :same-house [{args :clue/args :as key}]
  (let [cells (for [y (row-range args) ]
                (let [c (some (fn [[row val]] (and (= row y) val)) args)]
                  (partial (if c (partial cell c) empty-cell) y)))]
    [draggable {:key key :style (clue-frame-style 1) }
     (doall (map (fn [component i] ^{:key i}[component]) cells (range)))]))

(defmethod piece :next-to [{args :clue/args :as key}]
  (let [flip? (atom false)
        on-press #(swap! flip? not)]
    (fn []
      [draggable {:key key
                  :on-press on-press
                  :style (merge (clue-frame-style 2) {:border-radius 10})}
       (doall (map (fn [component i] ^{:key i}[component])
                   (for [y (row-range args)
                         x (range 2)]
                     (let [[row value] (nth (if @flip? (reverse args) args) x [])]
                       (partial (if (= y row) (partial cell value) empty-cell) y)))
                   (range)))])))

(defn board [in-house size]
  [view {:style (clue-frame-style size) }
   (for [y (range size) x (range size)]
     (let [i (some (fn [[[clue-y i] clue-x]] (and (= x clue-x) (= y clue-y) i)) (map :clue/args in-house))
           k (str x " " y)]
       (if i
         ^{:key k}[cell i y]
         ^{:key k}[view {:style cell-style}])))])

(defn root []
  (let [size 4
        config [(list
                  {:puzzle/puzzle [:puzzle/clues :puzzle/solution]}
                  (merge (get difficulties "normal") {:size size}))]]
    (.then (fetch-puzzle config) (partial reset! state))
    (fn []
      [view {:style {:align-items "center"
                     :background-color "#fff"
                     :border-style "solid"
                     :border-width 1
                     :border-color "yellow"
                     :flex 1
                     :justify-content "center"}}
       (when-let [clues (get-in @state [:puzzle/puzzle :puzzle/clues])]
         (let [[in-house other-clues] (partition-by #(= (:clue/type %) :in-house) (sort-by :clue/type clues))]
           (into [view {:style {
                                 :width "100%"
                                 :justify-content "center"
                                 :align-items "center"
                                 :background-color "#eee"
                                 :flex-direction "row"
                                 :flex-wrap "wrap"
                                 }} ] (conj (doall (map (fn [clue] ^{:key clue}[piece clue]) other-clues)) [board in-house size]))))])))

(def app (reactify-component root))
