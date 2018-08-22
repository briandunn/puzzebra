(ns puzzebra.core
  (:require
    ["react-native" :as ReactNative]
    [cognitect.transit :as t]
    [reagent.core :as r :refer [atom adapt-react-class reactify-component]]))

(def text (adapt-react-class (.-Text ReactNative)))
(def view (adapt-react-class (.-View ReactNative)))

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

(def state (atom {}))

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
                   :margin 10
                   :width (* 21 cells-wide )})

(defn cell [i y]
  [view {:style (merge cell-style {:background-color (row-color y)})}
   [text i]])

(defn empty-cell []
  [view {:style cell-style}])

(defn row-range [args]
  (let [[first-row last-row] (sort (map first args))] (range first-row (+ 1 last-row))))

(defmethod piece :left-of [{args :clue/args}]
  (let [cells (for [y (row-range args)
                    x (range 2)]
                (let [[row value] (nth args x [])]
                  (partial (if (= y row) (partial cell value) empty-cell) y)))]
    [view {:style (clue-frame-style 2)}
     (doall (map (fn [component i] ^{:key i}[component]) cells (range)))]))

(defmethod piece :same-house [{args :clue/args}]
  (let [cells (for [y (row-range args) ]
                (let [c (some (fn [[row val]] (and (= row y) val)) args)]
                  (partial (if c (partial cell c) empty-cell) y)))]
    [view {:style (clue-frame-style 1) }
     (doall (map (fn [component i] ^{:key i}[component]) cells (range)))]))

(defmethod piece :next-to [{args :clue/args}]
  (let [cells (for [y (row-range args)
                    x (range 2)]
                (let [[row value] (nth args x [])]
                  (partial (if (= y row) (partial cell value) empty-cell) y)))]
    [view {:style (clue-frame-style 2)}
     (doall (map (fn [component i] ^{:key i}[component]) cells (range)))]))

(defn board [in-house size]
  [view {:style (clue-frame-style size)}
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
                     :flex 1
                     :justify-content "center"
                     :padding 20}}
       (when-let [clues (get-in @state [:puzzle/puzzle :puzzle/clues])]
         (print clues)
         (let [[in-house other-clues] (partition-by #(= (:clue/type %) :in-house) (sort-by :clue/type clues))]
           [view
             [view
              (doall (map (fn [clue] ^{:key clue}[piece clue]) other-clues ))]
             [board in-house size]]))])))

(def app (reactify-component root))
