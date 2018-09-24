(ns puzzebra.core
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.spec.alpha :as s]
    [puzzebra.animated :as animated]
    [puzzebra.game :as game]
    [puzzebra.puzzle :as puzzle]
    [puzzebra.rn :refer [text view button activity-indicator slider on-layout]]
    [reagent.core :as r]
    [reagent.ratom :refer [reaction cursor atom]]))

(def state (atom {}))
(def side-length 48)

(defn p [x] (pprint x) x)

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
   :width (* (+ 1 side-length) cells-wide)})

(defn cell [i y]
  [animated/fade-in {:on-start-should-set-responder #(do
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

(defn fill-in [{{clues :puzzle/clues} :puzzle/puzzle
                :as state}]
  (let [in-house (filter #(= (:clue/type %) :in-house) clues)]
    (apply
      concat
      in-house
      (if @won?
        (game/build-one-in-house-per-row state)
        []))))

(defn game [{{clues :puzzle/clues
              {width :puzzle/width} :puzzle/grid
              difficulty :puzzle/difficulty} :puzzle/puzzle, :as s}]
  (let [other-clues (filter #(not= (:clue/type %) :in-house) clues)]
    [view
     (into [view {:style {:justify-content "center"
                          :align-items "center"
                          :background-color "#fafbfc"
                          :flex-direction "row"
                          :flex-wrap "wrap"}
                  :on-layout (on-layout :game-height)
                  }]
           (conj
             (doall (map (fn [clue] ^{:key clue}[piece clue]) (sort-by :clue/type other-clues)))
             [board (fill-in s) width]))]))

(defn new-game []
  (let [config (atom {:difficulty 1 :size 4 :waiting false})
        difficulties (keys puzzle/difficulties)
        change-difficulty (partial swap! config assoc :difficulty)
        change-size (partial swap! config assoc :size)
        press-new-game #(do
                          (swap! config assoc :waiting true)
                          (puzzle/fetch
                            (update @config :difficulty (partial nth difficulties))
                            (partial reset! state)))]
    (fn []
      (let [{:keys [size difficulty waiting]} @config]
        (if waiting
          [activity-indicator]
          [view {:style {:width "50%" :height "50%" :justify-content "space-evenly"}}
           [view
            [view {:style {:flex-direction "row" :justify-content "space-between"}}
             [text "difficulty"]
             [text {:style {:font-weight "bold"}} (nth difficulties difficulty)]]
            [slider {:on-value-change change-difficulty
                     :step 1
                     :maximum-value 2
                     :value difficulty}]]
           [view
            [view {:style {:flex-direction "row" :justify-content "space-between"}}
             [text "size"]
             [text {:style {:font-weight "bold"}} (str size "x" size)]]
            [slider {:on-value-change change-size
                     :step 1
                     :minimum-value 3
                     :maximum-value 7
                     :value size}]]
           [button {:title "Start" :on-press press-new-game}]])))))

(defn root []
  (let [s @state]
    [view {:style {:height "100%"
                   :width "100%"}}
     (into
       [view {:style {:align-items "center"
                      :border-bottom-color "#ccc"
                      :border-bottom-width 1
                      :flex-direction "row"
                      :height 48
                      :justify-content "space-between"
                      :left 0
                      :margin-top 30
                      :padding-left 8
                      :padding-right 8
                      :position "absolute"
                      :right 0
                      :top 0}}
        [text {:style {:font-size 28}} "puzzebra"]]
       (when-let [difficulty (get-in s [:puzzle/puzzle :puzzle/difficulty])]
         [[animated/fade-in [text difficulty]]
          [animated/fade-in [button {:title "New Game" :on-press #(reset! state nil)}]]]))
     [view {:style {:align-items "center"
                    :background-color "#fff"
                    :flex 1
                    :justify-content "center"
                    :margin-top 78
                    :overflow "hidden" }}
      (if (:puzzle/puzzle s)
        [game s]
        [new-game])]]))

(def app (r/reactify-component root))
