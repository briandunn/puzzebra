(ns puzzebra.game
  (:require
    [clojure.spec.alpha :as s]
    [clojure.pprint :refer [pprint]]
    [cognitect.transit :as t]))

(defn position-placement [placed-col [[clue-row clue-col] item]]
  [[clue-row (+ clue-col placed-col)] item])

(defmulti clue->placements :clue/type)

(defmethod clue->placements :next-to [{args :clue/args}] [])

(defmethod clue->placements :same-house [{args :clue/args}]
  (apply hash-map (mapcat (fn [[row item]] [[row 0] item]) args)))

(defmethod clue->placements :left-of [{args :clue/args}]
  (reduce
    (fn [acc [row item col]]
      (assoc acc [row col] item))
    {}
    (map conj args (range))))

(defn ->board [{placements :placements {clues :puzzle/clues} :puzzle/puzzle}]
  (let [in-house (filter #(= (:clue/type %) :in-house) (sort-by :clue/type clues))
        board (reduce (fn [board {[[y item] x] :clue/args}] (assoc board [y x] item)) {} in-house)]
    (reduce
      (fn [board [clue col]]
        (apply
          assoc
          board
          (mapcat (partial position-placement col) (clue->placements clue))))
      board
      placements)))

(defn unique? [col] (apply = (map count [(set col) col])))

(defn flip [state clue] (update-in state [:flips clue] not))

(defn valid? [state clue [placed-row placed-col]]
  (let [{{solution :puzzle/solution} :puzzle/puzzle} state
        board (->board state)
        placements (clue->placements clue)
        right-board-col (apply max (map second (keys solution)))
        clue-top-row (apply min (map (fn [[[row _] _]] row) placements))
        positioned-placements (apply hash-map (mapcat (partial position-placement placed-col) placements))
        positioned-clue-cols (map (fn [[[_ col] _]] col) positioned-placements)]
    (and
      (= placed-row clue-top-row)                           ; correct row
      (>= (apply min positioned-clue-cols) 0)               ; right of left board edge
      (<= (apply max positioned-clue-cols) right-board-col) ; left of right board edge
      (every?                                               ; occupied spaces are either empty or matching
              (fn [[pos item]]
                (let [board-item (get board pos)]
                  (or (nil? board-item) (= board-item item))))
              positioned-placements)
      (->> board                                            ; items in every row are unique
           (merge positioned-placements)
           (group-by ffirst)
           vals
           (map (partial map last))
           (every? unique?)))))
