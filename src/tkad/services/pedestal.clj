(ns tkad.services.pedestal
  (:require [clojure.tools.logging :as log]
            [tkad.web.routes :as routes]
            [tkad.datomic.util :refer :all]
            [io.pedestal.http :as http]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.ring-middlewares :as middlewares]
            [io.pedestal.interceptor :refer [definterceptor definterceptorfn on-request]]
            [plumbing.core :refer :all]
            [puppetlabs.trapperkeeper.core :as tk]
            [ring.middleware.session.cookie :as cookie]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Default interceptors

;; Build up an interceptor list from the routes and the built-in defaults
(def default-interceptors
  (-> {::http/routes routes/routes}
      http/default-interceptors
      ::http/interceptors))

(definterceptor session-interceptor
  (middlewares/session {:store (cookie/cookie-store)}))

;; Add sessions and GET/POST param handling
(def interceptors
  (conj default-interceptors
        session-interceptor
        (body-params/body-params)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Service map

;; Configure Pedestal server and interceptors
(def service-map
  {::http/routes        routes/routes
   ::http/join?         false
   ::http/resource-path "/public"
   ::http/interceptors  interceptors
   ::http/type          :jetty})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Interceptors requiring values from other components

;; Inject config from ConfigService into each request context
(definterceptorfn with-config
  [config]
  (on-request ::add-config #(assoc % :config config)))

;; Inject Datomic connections and latest database values into each request context
(definterceptorfn with-databases
  [conns]
  (on-request ::add-database #(assoc % :databases (prepare-conns-and-dbs conns))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Service

(tk/defservice service
  [[:ConfigService get-config]
   [:DatomicService conn]]
  (start [this context]
    (log/info "Starting Pedestal service")
    (let [config (get-config)
          conns  (->> (get-in config [:datomic])
                      keys
                      (map (juxt identity conn)))
          port   (get-in config [:webserver :port])
          server (-> service-map
                     ;; set Pedestal environment
                     (assoc :env (when (= (get-in config [:environment]) :production) :prod))
                     (assoc ::http/port port)
                     ;; add configured interceptors with config and Datomic vaues
                     (update-in [::http/interceptors] conj
                                (with-config config)
                                (with-databases conns))
                     http/create-server)]
      (log/info (str "> Awaiting connections on port " port "."))
      ;; Actually start the HTTP server
      (http/start server)
      (assoc context :server server)))
  (stop [this context]
    (when-let [server (:server context)]
      (log/info "Pedestal Server stopped.")
      (http/stop server)
      (dissoc context :server))))
