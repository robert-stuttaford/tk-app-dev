(ns services.shadow
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [plumbing.core :refer :all]
            [puppetlabs.trapperkeeper.core :as tk]
            [shadow.cljs.build :as cljs]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; React

(defn compose-externs [externs]
  (conj externs "react/externs/react.js"))

;; from the React jar file
(defn react-js-source [mode]
  (let [load-resource #(-> % io/resource slurp)]
    (case mode
      :debug (load-resource "react/react.js")
      :production (load-resource "react/react.min.js"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Shadow Logging

(defn shadow-logger []
  (reify cljs/BuildLog
    (log-warning [_ log-string]
      (log/info log-string))
    (log-progress [_ log-string]
      (log/info log-string))
    (log-time-start [_ log-string])
    (log-time-end [_ log-string time-in-ms]
      (log/info log-string time-in-ms))))

(defn configure-logging [state]
  (assoc state :logger (shadow-logger)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Build Steps

(defn compiler-options [state {:keys [mode externs public-path target-path]}]
  (-> state
      (into {:public-path public-path
             :public-dir  (io/file target-path)
             :work-dir    (io/file (str target-path "-work"))})
      (into (case mode
              :debug      {:optimizations :none
                           :pretty-print  true}
              :production {:optimizations :advanced
                           :pretty-print  false
                           :externs       (compose-externs externs)}))))

(defn configure-source-paths [state source-paths]
  (reduce (fn [state source-path]
            (cljs/step-find-resources state source-path))
          state
          source-paths))

(defn configure-core-module [state core-libs mode]
  (cljs/step-configure-module
   state :core core-libs #{} {:prepend (react-js-source mode)}))

(defn configure-modules [state {:keys [mode core-libs modules] :as opts}]
  (let [state (configure-core-module state core-libs mode)]
    (reduce (fn [state {:keys [id main]}]
              (cljs/step-configure-module state id [main] #{:core}))
     state
     modules)))

(defnk build-state [source-paths :as opts]
  (-> (cljs/init-state)
      configure-logging
      cljs/enable-source-maps
      (compiler-options opts)
      cljs/step-find-resources-in-jars
      (configure-source-paths source-paths)
      cljs/step-finalize-config
      cljs/step-compile-core
      (configure-modules opts)))

(defmulti compile-step #(:mode %2))

(defmethod compile-step :debug [state _]
  (-> state
      cljs/step-compile-modules
      cljs/flush-unoptimized))

(defmethod compile-step :production [state _]
  (-> state
      cljs/step-compile-modules
      cljs/closure-optimize
      cljs/flush-modules-to-disk))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Build modes

(defn extract-shadow [project-file]
  (->> project-file (drop 3) (apply hash-map) :shadow))

(defn project-config [project-root]
  (-> (str project-root "/project.clj")
      slurp
      read-string
      extract-shadow
      (assoc :root project-root)))

(defn prepare-opts [opts mode]
  (let [root #(-> opts :root (str "/" %))
        suffix (if (= :debug mode) "-debug")
        add-suffix #(str % suffix)]
    (-> opts
        (assoc :mode mode :source-paths [(root "src") (root "target/classes")])
        (update-in [:externs]      (partial map root))
        (update-in [:public-path]  add-suffix)
        (update-in [:target-path]  (comp root add-suffix))
        (dissoc :root))))

(defn prepare-project [project build]
  (-> project project-config (prepare-opts build)))

(defn build-once [opts]
  (-> (build-state opts)
      (compile-step opts))
  (log/info "BUILD COMPLETE")
  :build-completed)

(defn delete-folder [path]
  (log/info "Deleting path " path "...")
  (shell/sh "bash" "-c" (str "rm -rf " path)))

(defn clean-project [project-cfg]
  (delete-folder (:target-path project-cfg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Service

(tk/defservice service
  [[:ConfigService get-in-config]]
  (start [this context]
    (let [modes   (get-in-config [:shadow])
          context (assoc context :active true)]
      (log/info "Initializing Shadow service for build modes" modes)
      (doseq [mode modes]
        (.start
         (Thread.
          (fn []
            (let [opts  (prepare-project "." mode)
                  state (build-state opts)]
              (loop [state state]
                (recur
                 (do
                   (try
                     (when (:active context)
                       (-> state
                           (compile-step opts)
                           cljs/wait-and-reload!))
                     (catch Throwable t
                       (log/error "Failed to compile: " t)
                       (.printStackTrace t)
                       (cljs/wait-and-reload! state))))))
              (log/info "Stopping Shadow builds for" (:mode opts))))))))
    context)
  (stop [this context]
    (dissoc context :builds :active)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Command line

(defn build-auto [opts]
  (let [state (build-state opts)]
    (loop [state state]
      (recur
       (try
         (-> state
             (compile-step opts)
             cljs/wait-and-reload!)
         (catch Throwable t
           (log/error "Failed to compile: " t)
           (.printStackTrace t)
           (cljs/wait-and-reload! state)))))))

(defnk do-build [build mode {clean nil}]
  (let [project-cfg (prepare-project "." build)
        state (build-state project-cfg)]
    (log/info "Build" build mode "...")
    (if clean (clean-project project-cfg))
    (case mode
      :once (build-once project-cfg)
      :auto (build-auto project-cfg))))

(def cli-options
  [["-c" "--clean" "Clean target first."]
   ["-b" "--build debug|production|both" "Build type. Default is debug."
    :default :debug
    :parse-fn keyword
    :validate [#{:debug :production :both} "Must be either debug, production or both."]]
   ["-m" "--mode once|auto" "Build once or auto. Default is once."
    :default :once
    :parse-fn keyword
    :validate [#{:once :auto} "Must be either once or auto"]]])

(defn -main [& args]
  (do-build (:options (parse-opts args cli-options))))

