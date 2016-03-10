(ns com.emidln.migrations
  (:require [com.emidln.migrations.config :as config]
            [com.emidln.migrations.db :as db]
            com.emidln.migrations.drivers.psql))

(defn parse-args
  [& args]
  {})

(defmulti run
  "Runs a command with args"
  (fn [cmd opts] cmd))

(defmethod run :up
  [_ {:keys [db-uri migrations-dir migrations-table] :as opts}]
  (try
    (db/up db-uri migrations-dir migrations-table)
    (catch Exception e
      (when-let [type (:type (ex-data e))]
        (when (= type :database-missing)
          (db/create-db db-uri)
          (run :up opts))))))

(defmethod run :down
  [_ {:keys [db-uri migrations-dir migrations-table]}]
  (db/down db-uri migrations-dir migrations-table))

(defmethod run :status
  [_ {:keys [db-uri migrations-dir migrations-table]}]
  (db/status db-uri migrations-dir migrations-table))

(defmethod run :create
  [_ {:keys [migrations-dir name]}]
  (db/create migrations-dir name))

(defmethod run :help
  [_ {:keys [help]}]
  (println help))

(defn runner
  [config & args]
  (apply run cmd (merge (config/parse-config config)
                        (parse-args args))))

(defn -main
  [& args]
  (try
    (runner (get-config) args)
    (System/exit 0)
    (catch Throwable t
      (prn t)
      (System/exit 1))))
