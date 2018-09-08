(ns puzzebra.core
  (:require
    ["react-native" :as ReactNative]
    [clojure.spec.alpha :as s]
    [clojure.pprint :refer [pprint]]
    [reagent.core :as r :refer [adapt-react-class reactify-component]]
    [puzzebra.game :as game :refer [valid?]]
    [puzzebra.puzzle :as puzzle]
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

(defn on-drop [state clue rect start-pt]
  (let [{board-rect :board-rect} state
        rect-pt (rect->pt rect)]
    (if
      (collision? board-rect rect)
      (let [board-pt (rect->pt board-rect)
            [col row] (cell-distance rect-pt board-pt)]
        (if
          (valid? state clue [row col])
          (-> state
              (game/place clue col)
              (position-clue-at-point clue (snap-pt [col row] board-pt)))
          (position-clue-at-point state clue start-pt)))
      (position-clue-at-point state clue start-pt))))

(def won?
  (reaction (game/won? @state)))

(defn draggable []
  (let [layout-pt (atom nil)
        drag (atom {:cell nil
                    :start-pt [0 0]})
        {:keys [key on-press]} (r/props (r/current-component))
        position-rect (cursor state [:positions key])
        position-pt (reaction (rect->pt @position-rect))
        translation (reaction (mapv - @position-pt (or @layout-pt [0 0])))
        apply-delta #(swap! state position-clue-at-point key (mapv + (:start-pt @drag) %))
        placement (cursor state [:placements key])
        placed? (reaction (not (nil? @placement)))
        layout (on-layout (fn [rect]
                            (swap! state update :positions assoc key rect)
                            (reset! layout-pt (rect->pt rect))))
        pan-handlers (create-pan-responder
                       {:on-start-should-set #(-> state deref :touched-cell nil? not)
                        :on-grant (fn []
                                     (reset! drag {:start-pt @position-pt
                                                   :cell (:touched-cell @state)})
                                     (swap! state #(-> %
                                                       (game/displace key)
                                                       (dissoc :touched-cell))))
                        :on-release (fn [delta]
                                      (if (and on-press (every? zero? delta))
                                        (on-press)
                                        (swap! state on-drop key @position-rect @layout-pt)))
                        :on-move apply-delta})]
    (fn []
      (let [[tx ty] @translation
            {style :style} (r/props (r/current-component))]
        (into
          [view
           (merge
             pan-handlers
             {:transform [{:translateX tx} {:translateY ty}]
              :on-layout layout
              :style (merge style (if @placed? {:shadow-opacity 0} {:shadow-opacity 1 :shadow-offset {:width 0 :height 0}}))})]
          (r/children (r/current-component)))))))

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
