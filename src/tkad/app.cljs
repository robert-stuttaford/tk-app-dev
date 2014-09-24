(ns tkad.app
  (:require [cljs.core.async :refer [chan put! <!]]
            [cljs.reader :as edn]
            [goog.dom]
            [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponentk]]
            [sablono.core :as html :refer-macros [html]])
  (:require-macros [cljs.core.async.macros :as csp :refer [go]]))

(defcomponentk app-view [data owner]
  (render [_]
    (html
     [:div
      "Hello! You're in "
      (if (:debug? (om/get-shared owner :config))
        "debug"
        "production")
      " mode!"])))

(defn log-tx [tx-data root-cursor]
  (let [{:keys [path old-value new-value]} tx-data
        c js/console]
    (doto c (.group (str "TRANSACTION " path)) (.groupCollapsed "OLD"))
    (prn (pr-str old-value))
    (doto c (.groupEnd) (.group "NEW"))
    (prn (pr-str new-value))
    (doto c (.groupEnd) (.groupEnd))))

(defn render
  [root state app debug?]
  (when debug?
    (enable-console-print!))
  (om/root app
           state
           (cond-> {:target root
                    :shared {:config (assoc (:config @state) :debug? debug?)}}
                   debug? (assoc :tx-listen log-tx))))

(defn start
  ([app-id state-id app]
     (start app-id state-id app false))
  ([app-id state-id app debug?]
     (render
       (goog.dom/getElement app-id)
       (->> state-id
            goog.dom/getElement
            .-textContent
            edn/read-string
            atom)
       app
       debug?)))

(defn ^:export init
  [app-id state-id debug?]
  (start app-id state-id app-view debug?))
