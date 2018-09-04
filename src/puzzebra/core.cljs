(ns puzzebra.core
  (:require
    ["react-native" :as ReactNative]
    [clojure.spec.alpha :as s]
    [clojure.pprint :refer [pprint]]
    [cognitect.transit :as t]
    [reagent.core :as r :refer [adapt-react-class reactify-component]]
    [puzzebra.game :as game :refer [valid?]]
    [reagent.ratom :refer [reaction cursor atom]]))

(def text (adapt-react-class (.-Text ReactNative)))
(def view (adapt-react-class (.-View ReactNative)))
(def touchable-opacity (adapt-react-class (.-TouchableOpacity ReactNative)))

(def state (atom {}))
(def side-length 30)

(defn p [x] (pprint x) x)

(defn get-fields [fields js] (mapv (partial aget js) fields))

(defn create-pan-responder [config]
  (let [call-with-delta #(fn [_ state] (% (get-fields ["dx" "dy"] state)))
        key-names {:on-start-should-set :onStartShouldSetPanResponder
                   :on-release :onPanResponderRelease
                   :on-grant :onPanResponderGrant
                   :on-move :onPanResponderMove}]
    (js->clj
      (.-panHandlers
        (.create
          (.-PanResponder ReactNative)
          (clj->js (reduce (fn [acc [k v]] (assoc acc (k key-names) (call-with-delta v))) {} config)))))))

(defn round-to-nearest [size value] (* size (.round js/Math (/ value size))))

(defn on-layout [callback]
  #(->>
     (.. % -nativeEvent -layout)
     (get-fields ["x" "y" "width" "height"])
     (zipmap [:x :y :width :height])
     callback))

(defn row-range [args]
  (let [[first-row last-row] (sort (map first args))] (range first-row (+ 1 last-row))))

(defn collision? [{:keys [x y width height]} item]
  (and
    (< (:x item) (+ x width))
    (> (+ (:width item) (:x item)) x)
    (< (:y item) (+ y height))
    (> (+ (:height item) (:y item)) y)))

(defn collisions* [positions]
  (set
    (flatten
      (for
        [[clue-a box-a] positions
         [clue-b box-b] positions
         :when (and (not= box-a box-b) (collision? box-a box-b))]
        [clue-a clue-b]))))

(def collisions (reaction (collisions* (get @state :positions))))

(defn rect->pt [rect] (mapv #(get rect % 0) [:x :y]))

(defn snap-pt! [clue rect]
  (let [{board-rect :board-rect} @state
        rect-pt (rect->pt rect)]
    (when (collision? board-rect rect)
      (let [board-pt (rect->pt board-rect)
            [col row] (mapv #(.round js/Math ( / % side-length)) (map - rect-pt board-pt))
            snapped-pt (->> [col row] (map (partial * side-length)) (mapv + board-pt))]
        (when (valid? @state clue [row col])
          (swap! state #(->
                          %
                          (update :placements assoc clue col)
                          p
                          (update-in
                            [:positions clue]
                            merge
                            (zipmap [:x :y] snapped-pt)))))))))

(def won?
  (reaction
    (let [{{clues :puzzle/clues} :puzzle/puzzle, board-rect :board-rect, positions :positions} @state]
      (= (count (filter (partial collision? board-rect) (vals positions)))
         (count (filter #(not= (:clue/type %) :in-house) clues))))))

(defn set-position-pt! [clue pt]
  (swap!
    state
    update-in
    [:positions clue]
    merge
    (zipmap [:x :y] pt)))

(defn draggable []
  (let [layout-pt (atom [0 0])
        drag-start-pt (atom [0 0])
        {:keys [style key on-press]} (r/props (r/current-component))
        position-rect (cursor state [:positions key])
        position-pt (reaction (rect->pt @position-rect))
        translation (reaction (mapv - @position-pt @layout-pt))
        set-position-pt! (partial set-position-pt! key)
        apply-delta #(set-position-pt! (mapv + @drag-start-pt %))
        layout (on-layout (fn [rect]
                            (reset! layout-pt (rect->pt rect))
                            (swap! state update :positions assoc key rect)))
        pan-handlers (create-pan-responder
                       {:on-start-should-set (constantly true)
                        :on-grant #(do
                                     (reset! drag-start-pt @position-pt)
                                     (swap! state update :placements dissoc key))
                        :on-release (fn [delta]
                                      (if (and on-press (every? zero? delta))
                                        (on-press)
                                        (snap-pt! key @position-rect)))
                        :on-move apply-delta})]
    (fn []
      (let [[tx ty] @translation
            collision? (contains? @collisions key)]
        (into
          [view
           (merge
             pan-handlers
             {:transform [{:translateX tx} {:translateY ty}]
              :on-layout layout
              :style (merge style {:border-color (if collision? "red" "white")})})]
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

(defn row-color [row-number]
  (nth
    ["#9ccc65" "#ffa726" "#fdd835" "#29b6f6" "#80cbc4" "#ef5350" "#8d6e63"]
    row-number))

(defmulti piece :clue/type)


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
  (let [flip? (cursor state [:flips key])
        on-press #(swap! state game/flip key)]
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
  (let []
    (fn []
      [view {:style (clue-frame-style size)
             :on-layout (on-layout (partial swap! state assoc :board-rect))}
       (for [y (range size) x (range size)]
         (let [i (some (fn [[[clue-y i] clue-x]] (and (= x clue-x) (= y clue-y) i)) (map :clue/args in-house))
               k (str x " " y)]
           (if i
             ^{:key k}[cell i y]
             ^{:key k}[view {:style cell-style}])))])))

(defn root []
  (let [size 5
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
           (into [view {:style {:width "100%"
                                :justify-content "center"
                                :align-items "center"
                                :background-color "#eee"
                                :flex-direction "row"
                                :flex-wrap "wrap"
                                }} ] (conj (doall (map (fn [clue] ^{:key clue}[piece clue]) other-clues)) [board in-house size]))))
       (when @won? [text "you win!"])])))

(def app (reactify-component root))
