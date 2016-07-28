(ns com.emidln.migrations
  (:require [com.emidln.migrations.db :as db]
            com.emidln.migrations.drivers.psql
            [com.emidln.migrations.build-info :as bi])
  (:gen-class))

(defn env
  []
  (->> (merge {"MIGRATIONS_DB_URI" "postgres://postgres@db:5432/postgres"
               "MIGRATIONS_DIR" "migrations/"
               "MIGRATIONS_TABLE" "schema_migrations"}
              (select-keys (System/getenv) ["MIGRATIONS_DB_URI" "MIGRATIONS_DIR" "MIGRATIONS_TABLE"]))
       (map #(vector (-> % first .toLowerCase (.replace "_" "-") keyword)
                     (second %)))
       (into {})))

(defmulti run
  "Runs a command with args"
  (fn [cmd opts args] (keyword cmd)))

(defmethod run :up
  [_ {:keys [migrations-db-uri migrations-dir migrations-table] :as opts} _]
  (db/up migrations-db-uri migrations-dir migrations-table))

(defmethod run :down
  [_ {:keys [migrations-db-uri migrations-dir migrations-table]} _]
  (db/down migrations-db-uri migrations-dir migrations-table))

(defmethod run :status
  [_ {:keys [migrations-db-uri migrations-dir migrations-table]} _]
  (db/status migrations-db-uri migrations-dir migrations-table))

(defmethod run :create
  [_ {:keys [migrations-db-uri migrations-dir]} args]
  (db/create migrations-db-uri migrations-dir (first args)))

(defmethod run :create-db
  [_ {:keys [migrations-db-uri migrations-table]} _]
  (db/create-db migrations-db-uri migrations-table))

(defmethod run :backfill
  [_ {:keys [migrations-db-uri migrations-dir migrations-table]} _]
  (db/backfill migrations-db-uri migrations-dir migrations-table))

(def help
  "  Migrations v%s (build time: %s)

  Usage: java -jar migrations.jar command

   Commands:

     status                 - Print status of all migrations in MIGRATIONS_DIR and at MIGRATIONS_DB_URI
     up                     - Run all migrations in need of running in MIGRATIONS_DIR at MIGRATIONS_DB_URI
     down                   - Roll back the last known migration (not implemented)
     create-db              - Idempotently create the database and table specified by MIGRATIONS_DB_URI
     create                 - Create an empty migration in MIGRATIONS_DIR
     backfill               - Backfill the existing migrations in a directory to the database without executing
                            - said migrations.

   Configuration:

     Environment Variables:

       MIGRATIONS_DB_URI      (current: '%s')
                              (default: 'postgres://postgres@db:5432/postgres')
       MIGRATIONS_DIR         (current: '%s')
                              (default: './migrations')
       MIGRATIONS_TABLE       (current: '%s')
                              (default: 'schema_migrations')

   ")

(defmethod run :default
  [_ _ _]
  (let [e (env)
        build-info (bi/read-build-info)]
    (println
     (format help
             (:project-version build-info)
             (:timestamp build-info)
             (:migrations-db-uri e)
             (:migrations-dir e)
             (:migrations-table e)))))

(defn -main
  [& [cmd & args]]
  (try
    (run cmd (env) args)
    (System/exit 0)
    (catch Throwable t
      (prn t)
      (System/exit 1))))
