(defproject tk-app-dev "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
                 [org.clojure/tools.logging "0.3.0"]
                 [org.clojure/tools.cli "0.3.1"]
                 [ch.qos.logback/logback-classic "1.1.2"]
                 [org.slf4j/jul-to-slf4j "1.7.7"]
                 [org.slf4j/jcl-over-slf4j "1.7.7"]
                 [org.slf4j/log4j-over-slf4j "1.7.7"]
                 [hiccup "1.0.5"]
                 [clj-time "0.8.0"]
                 [prismatic/plumbing "0.3.3"]
                 [puppetlabs/trapperkeeper "0.5.1" :exclusions [prismatic/schema]]
                 [io.pedestal/pedestal.service "0.3.0"]
                 [io.pedestal/pedestal.jetty "0.3.0"]
                 [com.datomic/datomic-free "0.9.4815.12"
                  :exclusions [org.clojure/tools.cli
                               org.slf4j/slf4j-nop]]
                 [io.rkn/conformity "0.3.2" :exclusions [com.datomic/datomic-free]]]
  :profiles
  {:dev {:source-paths ["dev"]
         :dependencies [[org.clojure/tools.namespace "0.2.6"]
                        [thheller/shadow-build "0.9.3" :exclusions [org.clojure/clojurescript]]
                        [org.clojure/clojurescript "0.0-2341"]
                        [com.keminglabs/cljx "0.4.0" :exclusions [org.clojure/clojure]]
                        [juxt/dirwatch "0.2.0"]
                        [om "0.7.3"]
                        [prismatic/om-tools "0.3.3" :exclusions [org.clojure/clojure]]
                        [sablono "0.2.22" :exclusions [com.facebook/react]]
                        [puppetlabs/trapperkeeper "0.5.1"
                         :classifier "test"
                         :scope "test"]
                        [puppetlabs/kitchensink "0.7.2"
                         :classifier "test"
                         :scope "test"]]
         :plugins [[com.keminglabs/cljx "0.4.0"
                    :exclusions [org.clojure/clojure]]]}
   :cljx {:plugins [[com.keminglabs/cljx "0.4.0"
                     :exclusions [org.clojure/clojure]]]}}
  :jar-exclusions [#"\.cljx|\.swp|\.swo|\.DS_Store"]
  :source-paths ["src" "target/classes"]
  :aliases {"shadow"     ["with-profile" "dev""run" "-m" "services.shadow"]
            "build-prod" ["with-profile" "dev" "do" "clean," "check," "cljx once," "shadow -b both"]
            "tk"         ["trampoline" "run" "--config" "config.edn"]}
  :cljx {:builds [{:source-paths ["src"]
                   :output-path "target/classes"
                   :rules :clj}
                  {:source-paths ["src"]
                   :output-path "target/classes"
                   :rules :cljs}]}
  :shadow {:public-path  "/js/tkad"
           :target-path  "resources/public/js/tkad"
           :core-libs    [cljs.core
                          cljs.core.async
                          cljs.reader
                          om-tools.core
                          sablono.core]
           :externs      []
           :modules      [{:id :tkad :main tkad.app}]}
  :jvm-opts ["-Dfile.encoding=UTF-8" "-Xmx1024M" "-server"
             "-Dlogback.configurationFile=logback.xml"]
  :main puppetlabs.trapperkeeper.main)
