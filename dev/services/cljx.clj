(ns services.cljx
  (:require [cljx.core :as cljx]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [juxt.dirwatch :refer [watch-dir close-watcher]]
            [plumbing.core :refer :all]
            [puppetlabs.trapperkeeper.core :as tk]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Helpers

(defn extract-cljx [project-file]
  (->> project-file (drop 3) (apply hash-map) :cljx))

(defn builds-config []
  (-> "./project.clj"
      slurp
      read-string
      extract-cljx
      :builds))

(defn build-cljx! [builds event]
  (when-let [file (some-> event :file str)]
    (when (and (re-find #".cljx$" file)
               (not (re-find #"\.#" file)))
      (log/info "File modified: " file)
      (cljx/cljx-compile builds))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Service

(tk/defservice service
  [[:ConfigService get-in-config]]
  (start [this context]
    (log/info "Initializing cljx service")
    (let [builds  (builds-config)
          watcher (watch-dir (partial build-cljx! builds) (io/file "./src"))]
      (cljx/cljx-compile builds)
      (assoc context :watcher watcher)))
  (stop [this context]
    (let [watcher (:watcher context)]
      (close-watcher watcher)
      (dissoc context :watcher))))
