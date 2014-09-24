(ns tkad.services.datomic
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [tkad.datomic.util :refer :all]
            [datomic.api :as d]
            [io.rkn.conformity :as c]
            [plumbing.core :refer :all]
            [puppetlabs.trapperkeeper.core :as tk]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Utilities

(defn load-edn-resource [filename]
  (-> filename
      io/resource
      io/reader
      slurp
      read-string))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Service

(defprotocol DatomicService
  (conn [this] [this uri-key])
  (db   [this] [this uri-key]))

(tk/defservice service
  DatomicService
  [[:ConfigService get-in-config]]
  (init [this context]
    (log/info "Initializing Datomic service")
    (let [uris (get-in-config [:datomic])]
      ;; Ensure the databases exist
      (doseq [uri (vals uris)]
        (d/create-database uri))
      ;; Ensure the schema is loaded for the :main database
      (c/ensure-conforms (as-conn (:main uris))
                         (load-edn-resource "schema/schema.edn")))
    context)
  ;; Provide access to the first connection found (considered primary).
  (conn [this]
    (-> (get-in-config [:datomic]) vals first as-conn))
  ;; Provide access to the connection specified by uri-key.
  (conn [this uri-key]
    (-> (get-in-config [:datomic]) (get uri-key) as-conn))
  ;; Provide access to the current database value for the first connection found.
  (db [this]
    (-> (get-in-config [:datomic]) vals first as-db))
  ;; Provide access to the current database value for the connection specified by uri-key.
  (db [this uri-key]
    (-> (get-in-config [:datomic]) (get uri-key) as-db)))
