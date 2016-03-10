(ns com.emidln.migrations.drivers.psql
  (:require [com.emidln.migrations.db :refer [up down status create create-db]]
            [clojure.string :as str]
            [clojure.java.shell :refer [sh]])
  (:import java.io.File
           java.io.FilenameFilter
           java.net.URI))

(defn psql
  [db-uri & args]
  (let [r (apply sh "psql" db-uri args)]
    (if (= (:exit r) 0)
      (:out r)
      (do (println (:err  r))
          nil))))

(defn list-migration-files
  [direction migration-dir]
  "Returns a map of migration up files with the integer id as a key"
  (let [d (doto (File. migration-dir) .mkdirs)
        filename-suffix (format ".%s.sql" direction)
        files (.listFiles d (reify FilenameFilter
                              (accept [this dir name]
                                (boolean
                                 (and (not (.startsWith name "."))
                                      (.endsWith name filename-suffix))))))]
    (into {}
          (for [f files]
            (let [[raw-id migration-name] (drop 1 (re-matches #"(\d+)-([\w_]+)\..*" (.getName f)))
                  id (BigInteger. raw-id)]
              [id {:name migration-name
                   :id id
                   :file f}])))))

(def list-migration-up-files (partial list-migration-files "up"))
(def list-migration-down-files (partial list-migration-files "down"))

(defn record-migration-up
  [db-uri migrations-table id name ts]
  (psql db-uri "-c" (format "INSERT INTO %s (id, name, ts) VALUES (%d, '%s', %d);"
                            migrations-table
                            id name ts)))

(defn record-migration-down
  [db-uri migrations-table id]
  (psql db-uri "-c" (format "DELETE FROM %s WHERE id = %d;" migrations-table id)))

(defn calc-status
  [db-uri migrations-dir migrations-table]
  (let [sql (format "SELECT id, name, ts FROM %s;" migrations-table)
        out (or (psql db-uri "-tc" sql) "")
        existing-migrations (->> out
                                 str/split-lines
                                 (remove #{"" " " nil})
                                 (map #(map str/trim (str/split % #"\|+")))
                                 (map #(hash-map (BigInteger. (first %))
                                                 [(second %) (BigInteger. (nth % 2))]))
                                 (apply merge))
        possible-migrations (list-migration-up-files migrations-dir)
        migrations (merge-with #(assoc %1 :completed (second %2)) possible-migrations existing-migrations)]
    (into {}
          (for [[id m] (sort migrations)]
            (if (map? m)
              (if (:completed m)
                [id (assoc m :status :completed)]
                [id (assoc m :status :on-disk)])
              [id {:status :in-db
                   :name (first m)
                   :id id
                   :completed (second m)}])))))

(defn print-formatted-status
  [migration-status]
  (doseq [[id {:keys [status] :as m}] (sort migration-status)]
    (println
     (case status
       :completed (format "completed migration %s (%d) at %d" (:name m) id (:completed m))
       :on-disk (format "on-disk migration %s (%d) missing in db" (:name m) id)
       :in-db (format "in-db migration %s (%d) completed at %d missing on disk" (:name m) id (:completed m))))))

(defmethod status :postgres
  [db-uri migrations-dir migrations-table]
  (print-formatted-status (calc-status db-uri migrations-dir migrations-table)))

(defn migration-direction
  [m]
  (second (re-matches #".*\.(up|down)\.sql" (-> m :file .getName))))

(defn ts
  []
  (quot (System/currentTimeMillis) 1000))

(defn apply-migration
  [db-uri migrations-table {:keys [id name file] :as migration}]
  (println (format "Running migration %s (%d) %s ..."
                   name
                   id
                   (.toUpperCase (migration-direction migration))))
  (println (psql db-uri "-f" (.getPath file)))
  (record-migration-up db-uri migrations-table id name (ts))
  (println (format "Done.")))

(defmethod up :postgres
  [db-uri migrations-dir migrations-table]
  (let [status (calc-status db-uri migrations-dir migrations-table)]
    (doseq [m (->> status (filter #(= :on-disk (-> % second :status))) sort (map second))]
      (apply-migration db-uri migrations-table m))))

(defmethod down :postgres
  [db-uri migrations-dir migrations-table]
  (println "not supported :("))


(defmethod create-db :postgres
  [db-uri migrations-dir migrations-table]
  (let [original-uri (URI. db-uri)
        postgres-uri (URI. (.getScheme original-uri)
                           (.getUserInfo original-uri)
                           (.getHost original-uri)
                           (.getPort original-uri)
                           "/postgres"
                           (.getQuery original-uri)
                           (.getFragment original-uri))
        sql (format "CREATE DATABASE %s;" (subs (.getPath original-uri) 1))]
    (psql (str postgres-uri) "-c" sql)))
