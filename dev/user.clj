(ns user
  (:require [clojure.pprint :refer [pprint]]
            [clojure.tools.namespace.repl :refer [refresh]]
            [tkad.services.datomic :as datomic]
            [tkad.services.pedestal :as pedestal]
            [puppetlabs.trapperkeeper.app :as tka]
            [puppetlabs.trapperkeeper.core :as tk]
            [services.cljx :as cljx]
            [services.shadow :as shadow]))

(declare go stop)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; App system

;; The services we want to run
(def app-services [datomic/service pedestal/service])

;; Config from disk, augmented for development
(def app-config
  (-> "config.edn" slurp read-string
      (assoc :environment :development)))

;; Placeholder var for the system
(def app-system nil)

;; Closure to start the system up
(def app-go #(go app-services app-config #'app-system))

;; Stop the app system, reload changes, start the app system
(defn app-reset []
  (stop #'app-system)
  (refresh :after 'user/app-go))

;; An alias so that our Emacs keybind works
(def reset app-reset)

;; A helper for quickly grabbing a Datomic database uri at the REPL.
;; Only for REPL use; production code would get this from the DatomicService at run-time.
(defn database [key]
  (-> app-config :datomic key))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Development system

;; The services we want to run
(def dev-services [shadow/service cljx/service])

;; Configure shadow mode(s)
(def dev-config {:shadow #{:debug :production}}) ;; or :production, or both

;; Placeholder var for the system
(def dev-system nil)

;; Closure to start the system up
(def dev-go #(go dev-services dev-config #'dev-system))

;; Stop the dev system, reload changes, start the dev system
(defn dev-reset []
  (stop #'dev-system)
  (refresh :after 'user/dev-go))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; System introspection

(defn check-for-errors! [system]
  (tka/check-for-errors! system))

(defn context [system]
  @(tka/app-context system))

(defn print-context [system]
  (pprint (context system)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Workflow

;; Given a services list, a config map, and a var, initialise and store a system
(defn init [services config system-var]
  (alter-var-root system-var
                  (-> (tk/build-app services config)
                      tka/init
                      constantly))
  (check-for-errors! @system-var))

;; Start system at var
(defn start [system-var]
  (alter-var-root system-var tka/start)
  (check-for-errors! @system-var))

;; Stop system at var
(defn stop [system-var]
  (when @system-var
    (alter-var-root system-var tka/stop)))

(defn go [services config system-var]
  (init services config system-var)
  (start system-var))
