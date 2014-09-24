(ns tkad.model.model
  (:require [schema.core :as s])
  #+cljs (:require-macros [schema.macros :as sm]))

;; code that is used both by the server and client goes here, 
;; such as schemas, validation, utility and helper functions.