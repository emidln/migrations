(ns com.emidln.migrations.db
  (:import java.net.URI
           java.io.File))

(defn db-uri-dispatch
  [uri]
  (keyword (.getScheme (URI. uri))))

(defmulti up
  "Applies all outstanding migrations"
  (fn [db-uri migrations-dir migrations-table]
    (db-uri-dispatch db-uri)))

(defmulti down
  "Removes the last migration"
  (fn [db-uri migrations-dir migrations-table]
    (db-uri-dispatch db-uri)))

(defmulti status
  "Provide the status of all migrations"
  (fn [db-uri migrations-dir migrations-table]
    (db-uri-dispatch db-uri)))

(defmulti create
  "Creates a new migration named [migration-name]"
  (fn [db-uri migrations-dir & [migration-name]]
    (db-uri-dispatch db-uri)))

(defmethod create :default
  [db-uri migrations-dir & [migration-name]]
  (let [ts (quot (System/currentTimeMillis) 1000)
        filename (format "%d-%s.sql" ts (or migration-name "new_migration"))
        dir (doto (File. migrations-dir) .mkdirs)
        f (File. dir filename)]
    (print "Creating " (.getPath f) "... ")
    (let [result (.createNewFile f)]
      (println "Done")
      result)))

(defmulti create-db
  "Create the database"
  (fn [db-uri migrations-table]
    (db-uri-dispatch db-uri)))

(defmulti backfill
  "Backfills migrations into the migrations table"
  (fn [db-uri migrations-dir migrations-table]
    (db-uri-dispatch db-uri)))
