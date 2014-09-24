(ns tkad.web.routes
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [datomic.api :as d]
            [hiccup.core :refer [html]]
            [hiccup.element :refer [javascript-tag]]
            [hiccup.page :refer [include-js]]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.route.definition :refer [defroutes]]
            [plumbing.core :refer :all]
            [ring.util.response :refer [response]]))

(def default-state
  {:config {:base-url ""}})

(defn om-app [environment app-state]
  (let [debug? (= environment :development)
        path   (str "/js/tkad" (when debug? "-debug"))]
    (html
     [:div#om-root]
     (include-js (str path "/core.js"))
     (include-js (str path "/tkad.js"))
     [:script {:id "om-state" :type "application/edn"}
      (pr-str (merge default-state app-state))]
     (javascript-tag
      (format "tkad.app.init('om-root','om-state', %s);" debug?)))))

(defn index [context]
  (let [om-app (om-app (-> context :config :environment) {})]
    (-> "./resources/public/index.html"
        slurp
        (string/replace "#app#" om-app)
        response)))

(defroutes routes
  [[["/" {:get index}
     ^:interceptors [http/html-body]]]])

(def url-for (route/url-for-routes routes))
