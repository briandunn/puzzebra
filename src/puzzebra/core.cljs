(ns puzzebra.core
  (:require
    ["react-native" :as ReactNative]
    [clojure.spec.alpha :as s]
    [clojure.set :refer [difference]]
    [clojure.pprint :refer [pprint]]
    [reagent.core :as r :refer [adapt-react-class reactify-component]]
    [puzzebra.game :as game]
    [puzzebra.puzzle :as puzzle]
    [puzzebra.animated :as animated]
    [reagent.ratom :refer [reaction cursor atom]]))

(def text (adapt-react-class (.-Text ReactNative)))
(def view (adapt-react-class (.-View ReactNative)))
(def touchable-opacity (adapt-react-class (.-TouchableOpacity ReactNative)))

(def state (atom {}))
(def side-length 30)

(defn p [x] (pprint x) x)

(defn get-fields [fields js] (mapv (partial aget js) fields))

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

(defn position-clue-at-point [state clue pt]
  (update-in
    state
    [:positions clue]
    merge
    (zipmap [:x :y] pt)))

(defn cell-distance [src-pt target-pt]
  (mapv #(.round js/Math ( / % side-length)) (map - src-pt target-pt)))

(defn snap-pt [cell-dist origin-pt]
  (->> cell-dist (map (partial * side-length)) (mapv + origin-pt)))

(defn valid? [state clue position-pt]
  (->> state
       :board-rect
       rect->pt
       (cell-distance position-pt)
       reverse
       (game/valid? state clue)))

(defn on-drop [state clue start-pt]
  (or
    (let [{board-rect :board-rect positions :positions} state
          rect (get positions clue)
          board-pt (rect->pt board-rect)
          [col row] (cell-distance (rect->pt rect) board-pt)]
      (when (game/valid? state clue [row col])
        (-> state
            (game/place clue col)
            (position-clue-at-point clue (snap-pt [col row] board-pt)))))
    (position-clue-at-point state clue start-pt)))

(def won?
  (reaction (game/won? @state)))

(defn draggable []
  (let [{:keys [key on-press]} (r/props (r/current-component))
        position-rect (cursor state [:positions key])
        position-pt (reaction (rect->pt @position-rect))
        placement (cursor state [:placements key])
        placed? (reaction (not (nil? @placement)))
        valid? (reaction (valid? @state key @position-pt))
        drag-handlers {:on-start-should-set #(-> state deref :touched-cell nil? not)
                       :on-release (fn [delta layout-pt spring-to]
                                     (if (and on-press (every? zero? delta))
                                       (on-press)
                                       (do
                                         (swap! state on-drop key layout-pt)
                                         (spring-to @position-pt))))
                       :on-move (partial swap! state position-clue-at-point key)
                       :on-grant (fn []
                                   (swap! state #(-> %
                                                     (game/displace key)
                                                     (dissoc :touched-cell))))}]
    (fn []
      (let [this (r/current-component)
            {style :style} (r/props this)
            children (r/children this)]
        (into
          [animated/draggable
           (merge
             drag-handlers
             {:style (merge
                       style
                       (if @valid? {:border-color "green"} {})
                       {:shadow-offset {:width 0 :height 0}
                        :shadow-opacity (if @placed? 0 1 )})})]
          children)))))

(defn row-color [row-number]
  (nth
    ["#9ccc65" "#ffa726" "#fdd835" "#29b6f6" "#80cbc4" "#ef5350" "#8d6e63"]
    row-number))

(defmulti piece :clue/type)

(def cell-style {:height side-length
                 :justify-content "center"
                 :width side-length})

(defn clue-frame-style [cells-wide]
  {:align-items "flex-start"
   :border-color "#ccc"
   :border-style "solid"
   :border-width 1
   :flex-direction "row"
   :flex-wrap "wrap"
   :margin 10
   :overflow "hidden"
   :width (* (+ 1 side-length) cells-wide)})

(defn cell [i y]
  [view {:on-start-should-set-responder #(do
                                           (swap! state assoc :touched-cell {:row y :item i})
                                           false)
         :style (assoc cell-style :background-color (row-color y))}
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
                  :style (merge
                           (clue-frame-style 2)
                           {:border-radius 10})}
       (doall (map (fn [component i] ^{:key i}[component])
                   (for [y (row-range args)
                         x (range 2)]
                     (let [[row value] (nth (if @flip? (reverse args) args) x [])]
                       (partial (if (= y row) (partial cell value) empty-cell) y)))
                   (range)))])))

(defn board [in-house size]
  [view {:style (clue-frame-style size)
         :on-layout (on-layout (partial swap! state assoc :board-rect))}
   (for [y (range size) x (range size)]
     (let [i (some (fn [[[clue-y i] clue-x]] (and (= x clue-x) (= y clue-y) i)) (map :clue/args in-house))
           k (str x " " y)]
       (if i
         ^{:key k}[cell i y]
         ^{:key k}[view {:style cell-style}])))])

(defn fill-in [{{{width :puzzle/width} :puzzle/grid
                 clues :puzzle/clues
                 solution :puzzle/solution} :puzzle/puzzle
                :as state}]
  (let [in-house (filter #(= (:clue/type %) :in-house) clues)]
    (if @won?
      ; fill in the rest of the board with in-house clues
      (->>
        state
        game/->board
        keys
        set
        (difference (set (for [x (range width) y (range width)] [y x])))
        (map (fn [[row col]] {:clue/args [[row (get solution [row col])] col]}))
        (concat in-house))
      in-house)))

(defn root []
  (let [size 5]
    (puzzle/fetch {:difficulty "normal" :size size} (partial reset! state))
    (fn []
      [view {:style {:align-items "center"
                     :background-color "#fff"
                     :border-style "solid"
                     :border-width 1
                     :border-color "yellow"
                     :flex 1
                     :justify-content "center"}}
       (when-let [clues (get-in @state [:puzzle/puzzle :puzzle/clues])]
         (let [other-clues (filter #(not= (:clue/type %) :in-house) clues)]
           (into [view {:style {:width "100%"
                                :justify-content "center"
                                :align-items "center"
                                :background-color "#eee"
                                :flex-direction "row"
                                :flex-wrap "wrap"}}]
                 (conj
                   (doall (map (fn [clue] ^{:key clue}[piece clue]) (sort-by :clue/type other-clues)))
                   [board (fill-in @state) size]))))])))

(def app (reactify-component root))
