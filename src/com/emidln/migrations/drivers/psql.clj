(ns com.emidln.migrations.drivers.psql
  (:require [com.emidln.migrations.db :refer [up down status create create-db backfill]]
            [clojure.string :as str]
            [clojure.java.shell :refer [sh]])
  (:import java.io.File
           java.io.FilenameFilter
           java.net.URI))

(defn psql
  [db-uri & args]
  (let [r (apply sh "psql" db-uri args)]
    (if (= (:exit r) 0)
      [true (:out r)]
      (do
        (println (:err r))
        [false (:errr r)]))))

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
        [?success ?out] (psql db-uri "-tc" sql)]
    (if ?success
      (let [out (or ?out "")
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
                         :completed (second m)}]))))
      (do (println (format "Problem reading migrations table (%s) at %s. Does it exist?" migrations-table db-uri))
          (System/exit 2)))))

(defn print-formatted-status
  [migration-status]
  (if (seq migration-status)
    (doseq [[id {:keys [status] :as m}] (sort migration-status)]
      (println
       (case status
         :completed (format "completed migration %s (%d) at %d" (:name m) id (:completed m))
         :on-disk (format "on-disk migration %s (%d) missing in db" (:name m) id)
         :in-db (format "in-db migration %s (%d) completed at %d missing on disk" (:name m) id (:completed m)))))
    (println "No migrations on disk, no migrations in db.")))

(defn ts
  []
  (quot (System/currentTimeMillis) 1000))


(defn backfile-from-status
  [db-uri migrations-table migration-status]
  ;; display a status
  (print-formatted-status migration-status)
  ;; perform the backfill
  (let [migrations (->> (map second migration-status)
                        (filter (comp #{:on-disk} :status)))]
    (println (format "Backfilling %d migrations" (count migrations)))
    (doseq [m migrations]
      (record-migration-up db-uri migrations-table (:id m) (:name m) (ts)))))

(defmethod status :postgres
  [db-uri migrations-dir migrations-table]
  (print-formatted-status (calc-status db-uri migrations-dir migrations-table)))

(defn migration-direction
  [m]
  (second (re-matches #".*\.(up|down)\.sql" (-> m :file .getName))))

(defn apply-migration
  [db-uri migrations-table {:keys [id name file] :as migration}]
  (println (format "Running migration %s (%d) %s ..."
                   name
                   id
                   (.toUpperCase (migration-direction migration))))
  (let [[?success message] (psql db-uri "-f" (.getPath file))]
    (if ?success
      (do
        (println message)
        (record-migration-up db-uri migrations-table id name (ts))
        (println (format "Done.")))
      (do
        (println (format "Failed. Aborting!"))
        (throw (ex-info "Applying Migration Failed!" {:migrations migration
                                                      :db-uri db-uri
                                                      :migrations-table migrations-table}))))))

(defmethod up :postgres
  [db-uri migrations-dir migrations-table]
  (let [status (calc-status db-uri migrations-dir migrations-table)]
    (doseq [m (->> status (filter #(= :on-disk (-> % second :status))) sort (map second))]
      (apply-migration db-uri migrations-table m))))

(defmethod down :postgres
  [db-uri migrations-dir migrations-table]
  (println "not supported :("))

(defmethod create-db :postgres
  [db-uri migrations-table]
  (let [original-uri (URI. db-uri)
        postgres-uri (URI. (.getScheme original-uri)
                           (.getUserInfo original-uri)
                           (.getHost original-uri)
                           (.getPort original-uri)
                           "/postgres"
                           (.getQuery original-uri)
                           (.getFragment original-uri))
        db-sql (format "CREATE DATABASE %s;" (subs (.getPath original-uri) 1))
        table-sql (format
                   "CREATE TABLE %s (id SERIAL PRIMARY KEY, name TEXT NOT NULL, ts BIGINT NOT NULL);"
                   migrations-table)]
    (psql (str postgres-uri) "-c" db-sql)
    (psql (str original-uri) "-c" table-sql)))

(defmethod backfill :postgres
  [db-uri migrations-dir migrations-table]
  (let [status (calc-status db-uri migrations-dir migrations-table)]
    (backfile-from-status db-uri migrations-table status)))
