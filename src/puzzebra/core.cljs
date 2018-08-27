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

; (rect1.x < rect2.x + rect2.width &&
;    rect1.x + rect1.width > rect2.x &&
;    rect1.y < rect2.y + rect2.height &&
;    rect1.y + rect1.height > rect2.y)

(defn p [x] (print x) x)

(defn colission? [{:keys [x y width height] :as z} item]
  (and
    (< (:x item) (+ x width))
    (> (+ (:width item) (:x item)) x)
    (< (:y item) (+ y height))
    (> (+ (:height item) (:y item)) y)))

(defn collisions [positions]
  (set
    (doall
      (flatten
        (for
          [[clue-a box-a] positions
           [clue-b box-b] positions
           :while (and (not= box-a box-b) (colission? box-a box-b))]
          [clue-a clue-b])))))

(defn draggable []
  (let [pos (atom {:current [0 0] :start [0 0]})
        ref (atom nil)
        {:keys [style key]} (r/props (r/current-component))
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
                                                                      (.measure @ref update-position!)
                                                                      (swap! pos (fn [{start :start :as p}]
                                                                                   (merge p {:current [0 0]
                                                                                             :start (mapv + start (get-delta pan-state))}))))
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
              :style (merge style {:background-color (if @collision? "red" "white")})}
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

(def cell-style {:border-bottom-width 1
                 :border-color "#ccc"
                 :border-right-width 1
                 :border-style "solid"
                 :height 20
                 :width 20})

(defn clue-frame-style [cells-wide]
  {:align-items "flex-start"
   :border-color "#ccc"
   :border-left-width 1
   :border-style "solid"
   :border-top-width 1
   :flex-direction "row"
   :flex-wrap "wrap"
   :margin 11
   :width (* 21 cells-wide )})

(defn cell [i y]
  [view {:style (merge cell-style {:background-color (row-color y)})}
   [text i]])

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
  (let [cells (for [y (row-range args)
                    x (range 2)]
                (let [[row value] (nth args x [])]
                  (partial (if (= y row) (partial cell value) empty-cell) y)))]
    [draggable {:key key :style (merge (clue-frame-style 2) {:background-color "#eee"})}
     (doall (map (fn [component i] ^{:key i}[component]) cells (range)))]))

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
      [view {:style {
                     :align-items "center"
                     :background-color "#fff"
                     :border-style "solid"
                     :border-width 1
                     :border-color "yellow"
                     :flex 1
                     :justify-content "center"
                     }}
       (when-let [clues (get-in @state [:puzzle/puzzle :puzzle/clues])]
         (let [[in-house other-clues] (partition-by #(= (:clue/type %) :in-house) (sort-by :clue/type clues))]
           (into [view] (conj (doall (map (fn [clue] ^{:key clue}[piece clue]) other-clues )) [board in-house size]))))])))

(def app (reactify-component root))
