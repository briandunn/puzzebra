(ns puzzebra.game-test
  (:require-macros [cljs.test :refer [deftest testing is]])
  (:require [cljs.test :as t]
            [puzzebra.game :as game]))

; 0 |
; | 1
(def left-of {:clue/type :left-of, :clue/args [[0 0] [1 1]]})
(def state {:puzzle/puzzle {:puzzle/solution {[0 0] 0 [2 2] 1}} :board {[0 0] 0}} )

(deftest validates-row []
  (is (game/valid? state left-of [0 0]))
  (is (not (game/valid? state left-of [1 0]))))

(deftest only-allows-matching-placements []
  (is (game/valid? state left-of [0 0]))
  (is (not (game/valid? (assoc state :board {[0 0] 1}) left-of [0 0]))))

(deftest keep-hand-and-feet-inside-the-ride []
  (is (game/valid? state left-of [0 0]))
  (is (not (game/valid? state left-of [0 -1])))
  (is (not (game/valid? state left-of [0 3]))))

(deftest rows-are-unique []
  (is (game/valid? state left-of [0 0]))
  (is (not (game/valid? state left-of [0 1]))))
