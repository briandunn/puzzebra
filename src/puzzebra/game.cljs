(ns puzzebra.game
  (:require
    [clojure.spec.alpha :as s]
    [clojure.pprint :refer [pprint]]
    [cognitect.transit :as t]))

(defn p [x] (pprint x) x)

(defmulti clue->placements #(:clue/type %2))

(defmethod clue->placements :next-to [{placements :placements flips :flips}
                                      {args :clue/args :as clue}]
  (let [placed-col (get placements clue 0)
        flip? (get flips clue)]
    (->>
      (if flip? (reverse args) args)
      (map (fn [col [row item]] [[row (+ placed-col col)] item]) (range)))))

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

(defn kv->map [kv]
  (->> kv (reduce concat) (apply hash-map)))

(defn ->board [{placements :placements {clues :puzzle/clues} :puzzle/puzzle :as state}]
  (let [board (->> clues
                   (filter #(= (:clue/type %) :in-house))
                   (map (fn [{[[y item] x] :clue/args}] [[y x] item])))]
    (->>
      placements
      keys
      (mapcat (partial clue->placements state))
      (concat board)
      kv->map)))

(defn won? [{{clues :puzzle/clues} :puzzle/puzzle, placements :placements}]
  (=
   (count placements)
   (count (filter #(not= (:clue/type %) :in-house) clues))))

(defn unique? [col] (apply = (map count [(set col) col])))

(defn flip [state clue]
  (update-in state [:flips clue] not))

(defn place [state clue col]
  (update state :placements assoc clue col))

(defn displace [state clue]
  (update state :placements dissoc clue))

(defn rows [board]
  (->>
    board
    (group-by ffirst)
    vals
    (map (partial map last))))

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
        (merge (kv->map placements))
        rows
        (every? unique?)))))
