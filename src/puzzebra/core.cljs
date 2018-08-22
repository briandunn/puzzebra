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

(defmethod piece :left-of [{args :clue/args}]
  (let [cell-style {:border-bottom-width 1
                    :border-color "#ccc"
                    :border-right-width 1
                    :border-style "solid"
                    :height 20
                    :width 20}
        cell (fn [i x y] [view {:style (merge cell-style {:background-color (row-color y)})} [text i]])
        empty-cell (fn [x y] [view {:style cell-style}])
        cells (for [y (let [[first-row last-row] (sort (map first args))]
                        (range first-row (+ 1 last-row)))
                    x (range 2)]
                (let [[row value] (nth args x [])]
                  (partial (if (= y row) (partial cell value) empty-cell) x y)))]
    [view {:style {:align-items "flex-start"
                   :border-color "#ccc"
                   :border-left-width 1
                   :border-style "solid"
                   :border-top-width 1
                   :flex-direction "row"
                   :flex-wrap "wrap"
                   :margin 10
                   :width 42}}
     (doall (map (fn [component i] ^{:key i}[component]) cells (range)))]))

(defmethod piece :same-house [{args :clue/args}]
  (let [cell-style {:border-bottom-width 1
                    :border-color "#ccc"
                    :border-right-width 1
                    :border-style "solid"
                    :height 20
                    :width 20}
        cell (fn [i y] [view {:style (merge cell-style {:background-color (row-color y)})} [text i]])
        empty-cell (fn [y] [view {:style cell-style}])
        cells (for [y (let [[first-row last-row] (sort (map first args))]
                        (range first-row (+ 1 last-row)))]
                (let [c (some (fn [[row val]] (and (= row y) val)) args)]
                  (partial (if c (partial cell c) empty-cell) y)))]
    [view {:style {:align-items "flex-start"
                   :border-color "#ccc"
                   :border-left-width 1
                   :border-style "solid"
                   :border-top-width 1
                   :flex-direction "row"
                   :flex-wrap "wrap"
                   :margin 10
                   :width 21}}
     (doall (map (fn [component i] ^{:key i}[component]) cells (range)))]))

(defmethod piece :next-to [{paths :clue/args}] [text (.stringify js/JSON (clj->js paths))])
(defmethod piece :in-house [{paths :clue/args}] [text (.stringify js/JSON (clj->js paths))])

(defn root []
  (let [config [(list
                  {:puzzle/puzzle [:puzzle/clues :puzzle/solution]}
                  (merge (get difficulties "normal") {:size 4}))]]
    (.then (fetch-puzzle config) (partial reset! state)))
  (fn []
    [view {:style {:align-items "center"
                   :background-color "#fff"
                   :flex 1
                   :justify-content "center"
                   :padding 20}}
     (when-let [clues (get-in @state [:puzzle/puzzle :puzzle/clues])]
       (doall (map (fn [clue] ^{:key clue}[piece clue]) clues)))
     ]))

(def app (reactify-component root))
