(ns akvo.lumen.transformation.split-column
  (:require [akvo.lumen.transformation.engine :as engine]
            [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]
            [clojure.string :as string]
            [hugsql.core :as hugsql]
            [clojure.walk :refer (keywordize-keys stringify-keys)])
  (:import [java.util.regex Pattern]))

(hugsql/def-db-fns "akvo/lumen/transformation.sql")
(hugsql/def-db-fns "akvo/lumen/transformation/engine.sql")

(defn selected-column [args]
  (-> args :selectedColumn))

(defn col-name [args]
  (-> (selected-column args) :columnName))

(defn pattern [args]
  (-> args :pattern))

(defn prefix [args]
  (or (-> args :prefix) "EX-"))
(defmethod engine/valid? :core/split-column
  [op-spec]
  (let [{:keys [onError op args] :as op-spec} (keywordize-keys op-spec)]
    (and (engine/valid-column-name? (col-name args))
         (pattern args))))

(defn- add-name-to-new-columns
  [columns new-columns]
  (let [next-column-index (engine/next-column-index columns)
        indexes (map engine/derivation-column-name (iterate inc next-column-index))]
    (map #(assoc % :columnName %2 :id %2) new-columns indexes)))


(defn columns-to-extract [prefix number-new-rows selected-column columns]
  (let [base-column (dissoc selected-column :type :columnName)
        new-columns (map #(assoc base-column :title (str prefix % ": "(:title base-column)) :type "text" :splitable nil)
                         (range 1 (inc number-new-rows)))]
    (add-name-to-new-columns columns new-columns)))

(defn- update-row [conn table-name row-id vals-map]
  (let [r (string/join "," (doall (map (fn [[k v]]
                                         (str (name k) "='" v "'::TEXT")) vals-map)))
        sql (str  "update " table-name " SET "  r " where rnum=" row-id)]
    (log/debug :sql sql)
    (jdbc/execute! conn sql)))


(defmethod engine/apply-operation :core/split-column
  [{:keys [tenant-conn]} table-name columns op-spec]
  (log/debug :engine/apply-operation :core/split-column table-name)
  (jdbc/with-db-transaction [tenant-conn tenant-conn]
    (let [{:keys [onError op args] :as op-spec} (keywordize-keys op-spec)
          column-name                           (col-name args)
          pattern                               (pattern args)
          re-pattern                            (re-pattern (Pattern/quote pattern))
          splitable                             (-> args :selectedColumn :splitable)
          analysis                              ((keyword pattern) splitable)
          number-new-rows                       (inc (:max-coincidences-in-one-row analysis))
          new-columns                           (columns-to-extract (prefix args) number-new-rows (selected-column args) columns)
          add-db-columns                        (doseq [c new-columns]
                                                  (add-column tenant-conn {:table-name      table-name
                                                                           :column-type     (:type c)
                                                                           :new-column-name (:id c)}))
          update-db-columns                     (->> (select-rnum-and-column tenant-conn {:table-name table-name :column-name column-name})
                                                     (map
                                                      #(let [value       ((keyword column-name) %)
                                                             values      (string/split value  re-pattern)
                                                             update-vals (map (fn [a b]
                                                                                [(keyword (:id a)) b]) new-columns values)]
                                                         (update-row tenant-conn table-name (:rnum %) update-vals)))
                                                     doall)]      
      {:success?      true
       :execution-log [(format "Splitted column %s with pattern %s" column-name pattern)]
       :columns       (into columns (vec new-columns))})))
