(ns puzzebra.puzzle
  (:require
    [clojure.spec.alpha :as s]
    [cognitect.transit :as t]))

(def fixtures [
{:puzzle/puzzle
 {:puzzle/grid {:puzzle/width 5, :puzzle/height 5},
  :puzzle/solution
  {[0 1] 4,
   [4 3] 4,
   [3 4] 3,
   [1 2] 2,
   [0 0] 2,
   [2 2] 2,
   [3 2] 1,
   [2 4] 4,
   [4 2] 3,
   [1 3] 4,
   [2 3] 1,
   [3 1] 4,
   [0 2] 0,
   [3 0] 0,
   [1 1] 0,
   [3 3] 2,
   [1 4] 1,
   [0 3] 1,
   [2 1] 3,
   [4 1] 0,
   [4 4] 1,
   [1 0] 3,
   [2 0] 0,
   [4 0] 2,
   [0 4] 3},
  :puzzle/clues
  #{{:clue/type :left-of, :clue/args [[4 2] [3 1]]}
    {:clue/type :in-house, :clue/args [[1 0] 3]}
    {:clue/type :in-house, :clue/args [[1 1] 0]}
    {:clue/type :left-of, :clue/args [[3 2] [2 2]]}
    {:clue/type :next-to, :clue/args [[1 4] [0 2]]}
    {:clue/type :next-to, :clue/args [[0 3] [2 0]]}
    {:clue/type :left-of, :clue/args [[2 1] [0 1]]}
    {:clue/type :same-house, :clue/args [[0 3] [4 4]]}
    {:clue/type :left-of, :clue/args [[4 0] [0 4]]}
    {:clue/type :left-of, :clue/args [[4 2] [1 3]]}
    {:clue/type :next-to, :clue/args [[4 1] [3 2]]}
    {:clue/type :left-of, :clue/args [[0 2] [2 3]]}
    {:clue/type :left-of, :clue/args [[0 3] [3 3]]}
    {:clue/type :next-to, :clue/args [[3 4] [2 2]]}
    {:clue/type :in-house, :clue/args [[1 2] 2]}
    {:clue/type :in-house, :clue/args [[1 3] 4]}}}}

{:puzzle/puzzle
 {:puzzle/grid {:puzzle/width 5, :puzzle/height 5},
  :puzzle/solution
  {[0 1] 1,
   [4 3] 0,
   [3 4] 1,
   [1 2] 1,
   [0 0] 4,
   [2 2] 2,
   [3 2] 3,
   [2 4] 4,
   [4 2] 1,
   [1 3] 4,
   [2 3] 1,
   [3 1] 0,
   [0 2] 0,
   [3 0] 2,
   [1 1] 0,
   [3 3] 4,
   [1 4] 2,
   [0 3] 3,
   [2 1] 3,
   [4 1] 3,
   [4 4] 2,
   [1 0] 3,
   [2 0] 0,
   [4 0] 4,
   [0 4] 2},
  :puzzle/clues
  #{{:clue/type :left-of, :clue/args [[0 4] [3 2]]}
    {:clue/type :left-of, :clue/args [[4 1] [3 3]]}
    {:clue/type :same-house, :clue/args [[4 3] [3 1]]}
    {:clue/type :in-house, :clue/args [[2 3] 1]}
    {:clue/type :left-of, :clue/args [[1 4] [0 3]]}
    {:clue/type :in-house, :clue/args [[3 2] 3]}
    {:clue/type :next-to, :clue/args [[3 2] [4 0]]}
    {:clue/type :next-to, :clue/args [[2 3] [4 4]]}
    {:clue/type :same-house, :clue/args [[3 0] [0 4]]}
    {:clue/type :left-of, :clue/args [[0 2] [1 2]]}
    {:clue/type :next-to, :clue/args [[0 1] [1 4]]}
    {:clue/type :next-to, :clue/args [[4 1] [2 2]]}
    {:clue/type :next-to, :clue/args [[2 4] [1 0]]}
    {:clue/type :next-to, :clue/args [[4 2] [2 2]]}
    {:clue/type :left-of, :clue/args [[1 1] [2 3]]}
    {:clue/type :same-house, :clue/args [[4 1] [2 1]]}}}}

{:puzzle/puzzle
 {:puzzle/grid {:puzzle/width 5, :puzzle/height 5},
  :puzzle/solution
  {[0 1] 0,
   [4 3] 3,
   [3 4] 0,
   [1 2] 0,
   [0 0] 2,
   [2 2] 1,
   [3 2] 4,
   [2 4] 0,
   [4 2] 2,
   [1 3] 2,
   [2 3] 4,
   [3 1] 2,
   [0 2] 1,
   [3 0] 1,
   [1 1] 1,
   [3 3] 3,
   [1 4] 3,
   [0 3] 4,
   [2 1] 3,
   [4 1] 0,
   [4 4] 1,
   [1 0] 4,
   [2 0] 2,
   [4 0] 4,
   [0 4] 3},
  :puzzle/clues
  #{{:clue/type :left-of, :clue/args [[4 2] [1 4]]}
    {:clue/type :next-to, :clue/args [[4 1] [1 1]]}
    {:clue/type :left-of, :clue/args [[4 3] [3 2]]}
    {:clue/type :same-house, :clue/args [[2 0] [3 1]]}
    {:clue/type :left-of, :clue/args [[0 1] [3 0]]}
    {:clue/type :left-of, :clue/args [[3 1] [1 4]]}
    {:clue/type :left-of, :clue/args [[1 3] [3 3]]}
    {:clue/type :same-house, :clue/args [[3 0] [0 2]]}
    {:clue/type :in-house, :clue/args [[0 0] 2]}
    {:clue/type :next-to, :clue/args [[3 0] [2 4]]}
    {:clue/type :next-to, :clue/args [[2 1] [1 3]]}
    {:clue/type :left-of, :clue/args [[1 1] [3 1]]}
    {:clue/type :in-house, :clue/args [[2 2] 1]}
    {:clue/type :in-house, :clue/args [[1 2] 0]}
    {:clue/type :next-to, :clue/args [[0 0] [1 1]]}
    {:clue/type :same-house, :clue/args [[0 3] [4 0]]}}}}

{:puzzle/puzzle
 {:puzzle/grid {:puzzle/width 5, :puzzle/height 5},
  :puzzle/solution
  {[0 1] 0,
   [4 3] 3,
   [3 4] 3,
   [1 2] 4,
   [0 0] 3,
   [2 2] 0,
   [3 2] 4,
   [2 4] 4,
   [4 2] 4,
   [1 3] 3,
   [2 3] 3,
   [3 1] 2,
   [0 2] 1,
   [3 0] 1,
   [1 1] 0,
   [3 3] 0,
   [1 4] 2,
   [0 3] 4,
   [2 1] 2,
   [4 1] 2,
   [4 4] 1,
   [1 0] 1,
   [2 0] 1,
   [4 0] 0,
   [0 4] 2},
  :puzzle/clues
  #{{:clue/type :next-to, :clue/args [[1 2] [4 3]]}
    {:clue/type :left-of, :clue/args [[2 2] [0 2]]}
    {:clue/type :left-of, :clue/args [[3 4] [2 4]]}
    {:clue/type :left-of, :clue/args [[0 1] [3 0]]}
    {:clue/type :in-house, :clue/args [[2 3] 3]}
    {:clue/type :in-house, :clue/args [[0 4] 2]}
    {:clue/type :same-house, :clue/args [[0 4] [3 1]]}
    {:clue/type :in-house, :clue/args [[4 2] 4]}
    {:clue/type :same-house, :clue/args [[4 3] [0 0]]}
    {:clue/type :next-to, :clue/args [[1 3] [2 1]]}
    {:clue/type :next-to, :clue/args [[2 0] [4 0]]}
    {:clue/type :next-to, :clue/args [[1 0] [3 3]]}
    {:clue/type :same-house, :clue/args [[2 2] [1 1]]}
    {:clue/type :next-to, :clue/args [[3 4] [4 1]]}
    {:clue/type :in-house, :clue/args [[1 2] 4]}
    {:clue/type :same-house, :clue/args [[4 1] [2 1]]}
    {:clue/type :next-to, :clue/args [[0 1] [1 0]]}}}}

{:puzzle/puzzle
 {:puzzle/grid {:puzzle/width 5, :puzzle/height 5},
  :puzzle/solution
  {[0 1] 2,
   [4 3] 0,
   [3 4] 3,
   [1 2] 2,
   [0 0] 4,
   [2 2] 0,
   [3 2] 1,
   [2 4] 3,
   [4 2] 2,
   [1 3] 0,
   [2 3] 2,
   [3 1] 0,
   [0 2] 3,
   [3 0] 4,
   [1 1] 3,
   [3 3] 2,
   [1 4] 4,
   [0 3] 0,
   [2 1] 1,
   [4 1] 3,
   [4 4] 4,
   [1 0] 1,
   [2 0] 4,
   [4 0] 1,
   [0 4] 1},
  :puzzle/clues
  #{{:clue/type :left-of, :clue/args [[3 2] [0 1]]}
    {:clue/type :left-of, :clue/args [[0 1] [3 4]]}
    {:clue/type :same-house, :clue/args [[4 3] [1 3]]}
    {:clue/type :in-house, :clue/args [[0 4] 1]}
    {:clue/type :in-house, :clue/args [[2 4] 3]}
    {:clue/type :left-of, :clue/args [[2 1] [1 2]]}
    {:clue/type :left-of, :clue/args [[4 1] [2 0]]}
    {:clue/type :left-of, :clue/args [[0 2] [2 0]]}
    {:clue/type :same-house, :clue/args [[0 4] [2 1]]}
    {:clue/type :same-house, :clue/args [[3 0] [1 4]]}
    {:clue/type :same-house, :clue/args [[2 3] [3 3]]}
    {:clue/type :left-of, :clue/args [[2 4] [0 0]]}
    {:clue/type :same-house, :clue/args [[1 0] [2 1]]}
    {:clue/type :next-to, :clue/args [[2 3] [0 2]]}
    {:clue/type :same-house, :clue/args [[4 2] [1 2]]}
    {:clue/type :next-to, :clue/args [[4 4] [0 2]]}}}}

{:puzzle/puzzle
 {:puzzle/grid {:puzzle/width 5, :puzzle/height 5},
  :puzzle/solution
  {[0 1] 0,
   [4 3] 2,
   [3 4] 4,
   [1 2] 4,
   [0 0] 4,
   [2 2] 3,
   [3 2] 0,
   [2 4] 0,
   [4 2] 1,
   [1 3] 1,
   [2 3] 2,
   [3 1] 1,
   [0 2] 1,
   [3 0] 3,
   [1 1] 2,
   [3 3] 2,
   [1 4] 3,
   [0 3] 3,
   [2 1] 4,
   [4 1] 4,
   [4 4] 3,
   [1 0] 0,
   [2 0] 1,
   [4 0] 0,
   [0 4] 2},
  :puzzle/clues
  #{{:clue/type :in-house, :clue/args [[2 4] 0]}
    {:clue/type :next-to, :clue/args [[4 1] [3 0]]}
    {:clue/type :next-to, :clue/args [[3 0] [2 1]]}
    {:clue/type :left-of, :clue/args [[4 4] [1 2]]}
    {:clue/type :left-of, :clue/args [[0 2] [3 3]]}
    {:clue/type :left-of, :clue/args [[4 3] [1 4]]}
    {:clue/type :same-house, :clue/args [[0 0] [3 4]]}
    {:clue/type :left-of, :clue/args [[2 0] [1 1]]}
    {:clue/type :same-house, :clue/args [[2 4] [4 0]]}
    {:clue/type :left-of, :clue/args [[0 1] [3 1]]}
    {:clue/type :same-house, :clue/args [[4 3] [1 1]]}
    {:clue/type :next-to, :clue/args [[2 4] [1 3]]}
    {:clue/type :same-house, :clue/args [[2 3] [3 3]]}
    {:clue/type :in-house, :clue/args [[1 3] 1]}
    {:clue/type :next-to, :clue/args [[1 4] [0 0]]}
    {:clue/type :next-to, :clue/args [[0 4] [3 1]]}}}}

{:puzzle/puzzle
 {:puzzle/grid {:puzzle/width 5, :puzzle/height 5},
  :puzzle/solution
  {[0 1] 1,
   [4 3] 0,
   [3 4] 1,
   [1 2] 2,
   [0 0] 0,
   [2 2] 0,
   [3 2] 0,
   [2 4] 1,
   [4 2] 4,
   [1 3] 1,
   [2 3] 3,
   [3 1] 4,
   [0 2] 3,
   [3 0] 3,
   [1 1] 3,
   [3 3] 2,
   [1 4] 0,
   [0 3] 4,
   [2 1] 4,
   [4 1] 3,
   [4 4] 1,
   [1 0] 4,
   [2 0] 2,
   [4 0] 2,
   [0 4] 2},
  :puzzle/clues
  #{{:clue/type :next-to, :clue/args [[1 4] [2 4]]}
    {:clue/type :in-house, :clue/args [[3 0] 3]}
    {:clue/type :left-of, :clue/args [[3 2] [0 1]]}
    {:clue/type :same-house, :clue/args [[2 4] [4 4]]}
    {:clue/type :next-to, :clue/args [[1 0] [0 2]]}
    {:clue/type :left-of, :clue/args [[2 4] [4 0]]}
    {:clue/type :left-of, :clue/args [[0 4] [4 1]]}
    {:clue/type :in-house, :clue/args [[3 3] 2]}
    {:clue/type :next-to, :clue/args [[2 0] [1 3]]}
    {:clue/type :next-to, :clue/args [[2 3] [0 3]]}
    {:clue/type :left-of, :clue/args [[1 3] [4 0]]}
    {:clue/type :left-of, :clue/args [[3 3] [2 3]]}
    {:clue/type :in-house, :clue/args [[2 2] 0]}
    {:clue/type :left-of, :clue/args [[4 3] [3 4]]}
    {:clue/type :in-house, :clue/args [[1 2] 2]}
    {:clue/type :left-of, :clue/args [[0 4] [3 0]]}}}}])

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

; (defn fetch [_ callback] (callback (rand-nth fixtures)))

(defn fetch [{:keys [difficulty size]} callback]
  (let [config [(list
                  {:puzzle/puzzle [:puzzle/clues :puzzle/solution]}
                  (assoc (get difficulties difficulty) :size size))]
        body (t/write (t/writer :json) config)]
    (->
      (.fetch
        js/window
        "https://zebra.joshuadavey.com/api"
        (clj->js {:method "POST"
                  :headers {"Content-Type" "application/transit+json"}
                  :body body}))
      (.then #(.text %))
      (.then (partial t/read (t/reader :json)))
      (.then callback))))
