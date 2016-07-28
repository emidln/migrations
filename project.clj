(defproject com.emidln/migrations "0.5.0-SNAPSHOT"
  :description "Migrations for lazy people"
  :url "https://github.com/emidln/lazy-migrations"
  :main com.emidln.migrations
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :lein-release {:deploy-via :shell
                 :shell ["echo" "\"fake deploy\""]})
