(defproject catwalk-fe "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/clojurescript "1.10.844"]
                 [org.clojure/tools.cli "0.3.5"]

                 [cheshire "5.10.0"]
                 [reagent "1.0.0"]
                 [alandipert/storage-atom "2.0.1"]]

  :hooks [leiningen.cljsbuild]
  :plugins [[lein-cljsbuild "1.1.7"]]
  :cljsbuild {:builds
              [{:source-paths ["src/catwalk_fe"]
                :compiler {:output-to "../catwalk/static/core.js"
                           :optimizations :whitespace
                           :pretty-print true}
                :jar true}]}

  :repl-options {:init-ns catwalk-fe.core}

  :main catwalk-fe.core
  :aot [catwalk-fe.core])
