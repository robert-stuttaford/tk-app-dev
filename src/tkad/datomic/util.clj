(ns tkad.datomic.util
  (:require [datomic.api :as d]
            [plumbing.core :refer :all]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Connection

(defprotocol DatomicConnection
  (as-conn [_]))

(extend-protocol DatomicConnection
  datomic.Connection
  (as-conn [c] c)
  java.lang.String
  (as-conn [db-uri] (d/connect db-uri)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Database

(defprotocol DatabaseReference
  (as-db [_]))

(extend-protocol DatabaseReference
  datomic.db.Db
  (as-db [db] db)
  datomic.Connection
  (as-db [conn] (d/db conn))
  java.lang.String
  (as-db [db-uri] (as-db (as-conn db-uri))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Entity

(defprotocol EntityReference
  (id [_])
  (entity [_ db]))

(extend-protocol EntityReference
  datomic.query.EntityMap
  (id [e] (:db/id e))
  (entity [e db] e)
  clojure.lang.PersistentArrayMap
  (id [m] (:db/id m))
  (entity [m db] (entity (id m) (as-db db)))
  clojure.lang.PersistentHashMap
  (id [m] (:db/id m))
  (entity [m db] (entity (id m) (as-db db)))
  java.lang.Long
  (id [id] id)
  (entity [id db] (d/entity (as-db db) id))
  datomic.db.Datum
  (id [[e]] e)
  (entity [[e] db] (entity e (as-db db)))
  clojure.lang.Keyword
  (id [kw] kw)
  (entity [kw db] (let [db (as-db db)]
                    (entity (d/entid db kw) (as-db db)))))

(defn to-entity [db v]
  (entity v db))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Helpers

(defn prepare-conns-and-dbs [conns]
  (for-map [[uri-key conn] conns]
    uri-key
    {:conn conn
     :db   (as-db conn)}))

(def tempid (partial d/tempid :db.part/user))

(defn all-ref-value-datoms [db attr value]
  (let [db (as-db db)]
    (seq (d/datoms db :vaet value attr))))

(defn all-ref-values [db attr value]
  (some->> (all-ref-value-datoms db attr value)
           (map #(entity % db))))

(defn all-value-datoms [db attr value]
  (let [db (as-db db)]
    (seq (d/datoms db :avet attr value))))

(defn all-values [db attr value]
  (some->> (all-value-datoms db attr value)
           (map #(entity % db))))

(defn all-datoms [db attr]
  (let [db (as-db db)]
    (seq (d/datoms db :aevt attr))))

(defn all [db attr]
  (some->> (all-datoms db attr)
           (map #(entity % db))))
