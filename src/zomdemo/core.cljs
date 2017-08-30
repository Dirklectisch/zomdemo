(ns zomdemo.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [goog.dom :as gdom]
            [om.dom :as dom]
            [om.next :as om :refer [defui]]
            [cljs.core.async :as async :refer [<! >! put! chan]]
            [clojure.string :as string]
            zomdemo.repl)
  (:import [goog Uri]
           [goog.net Jsonp]))

(enable-console-print!)

(def base-url "https://www.entoen.nu/api/search?text=")

(defn jsonp
  ([uri]
   (jsonp (chan) uri))
  ([c uri]
   (let [gjsonp (Jsonp. (Uri. uri))]
     (.send gjsonp nil #(put! c %))
     c)))

;; parser

(defmulti read-fn om/dispatch)

(defmethod read-fn :search/results
  [{:keys [state ast] :as env} k {:keys [text]}]
  (merge
    {:value (get @state k [])}
    (when-not (or (string/blank? text)
                  (< (count text) 3))
      {:search ast})))

(def parser (om/parser {:read read-fn}))

;; components

;; reconciler

(def default-state {:search/results []})

(defn on-js-reload [])
;; optionally touch your app-state to force rerendering depending on
;; your application
;; (swap! app-state update-in [:__figwheel_counter] inc)

