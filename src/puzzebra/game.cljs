(ns puzzebra.game
  (:require
    [clojure.spec.alpha :as s]
    [clojure.pprint :refer [pprint]]
    [cognitect.transit :as t]))

(defn position-placement [placed-col [[clue-row clue-col] item]]
  [[clue-row (+ clue-col placed-col)] item])

(defmulti clue->placements #(:clue/type %2))

(defmethod clue->placements :next-to [_ {args :clue/args}] [])

(defmethod clue->placements :same-house [{placements :placements} clue]
  (->>
    clue
    :clue/args
    (map (fn [[row item]] [[row (get placements clue 0)] item]))))

(defmethod clue->placements :left-of [{placements :placements} clue]
  (->>
    clue
    :clue/args
    (map (fn [col [row item]] [[row (+ (get placements clue 0) col)] item]) (range))))

(defn ->board [{placements :placements {clues :puzzle/clues} :puzzle/puzzle :as state}]
  (let [board (->> clues
                   (filter #(= (:clue/type %) :in-house))
                   (map (fn [{[[y item] x] :clue/args}] [[y x] item])))]
    (->>
      placements
      keys
      (map (partial clue->placements state))
      (concat board)
      (reduce conj)
      (apply hash-map))))

(defn unique? [col] (apply = (map count [(set col) col])))

(defn flip [state clue]
  (update-in state [:flips clue] not))

(defn place [state clue col]
  (update state :placements assoc clue col))

(defn displace [state clue]
  (dissoc state :placements dissoc clue))

(defn valid? [state clue [placed-row placed-col]]
  (let [{{solution :puzzle/solution} :puzzle/puzzle} state
        board (->board state)
        placements (clue->placements (update state :placements assoc clue placed-col) clue)
        right-board-col (apply max (map second (keys solution)))
        clue-top-row (apply min (map (fn [[[row _] _]] row) placements))
        positioned-clue-cols (map (fn [[[_ col] _]] col) placements)]
    (and
      (= placed-row clue-top-row)                           ; correct row
      (>= (apply min positioned-clue-cols) 0)               ; right of left board edge
      (<= (apply max positioned-clue-cols) right-board-col) ; left of right board edge
      (every?                                               ; occupied spaces are either empty or matching
              (fn [[pos item]]
                (let [board-item (get board pos)]
                  (or (nil? board-item) (= board-item item))))
              placements)
      (->>
        board                                               ; items in every row are unique
        (merge (apply hash-map (reduce conj placements)))
        (group-by ffirst)
        vals
        (map (partial map last))
        (every? unique?)))))
