(ns zomdemo.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [goog.dom :as gdom]
            [om.dom :as dom]
            [om.next :as om :refer [defui]]
            [cljs.core.async :as async :refer [<! >! put! chan]]
            [clojure.string :as string]
            zomdemo.repl)
  (:import [goog Uri]
           [goog.net Jsonp]))

(enable-console-print!)

;; parser

(defmulti read-fn om/dispatch)

(defmethod read-fn :search/results
  [{:keys [state ast query] :as env} k {:keys [text]}]
  (merge
    {:value (om/db->tree query (get @state k []) @state)}
    (when-not (or (string/blank? text)
                  (< (count text) 3))
      {:search ast})))

(def parser (om/parser {:read read-fn}))

;; components

(defui Result
  static om/Ident
  (ident [this {:keys [id]}]
    [:resource/by-id id])
  static om/IQuery
  (query [_]
    [:id :title])
  Object
  (render [this]
    (let [{:keys [id title]} (om/props this)]
      (dom/li #js {:key id} (-> title :trans :nl)))))

(def result (om/factory Result))

(defn result-list [results]
  (dom/ul #js {:key "result-list"}
    (map result results)))

(defn search-field [this text]
  (dom/input
    #js {:key "search-field"
         :value text
         :onChange (fn [e]
                     (om/set-query! this
                       {:params {:text (.. e -target -value)}}))}))

(defui SearchWidget
  static om/IQueryParams
  (params [_]
    {:text ""})
  static om/IQuery
  (query [_]
    (let [params '{:text ?text}]
      `[({:search/results ~(om/get-query Result)} ~params)]))
  Object
  (render [this]
    (let [{:keys [search/results]} (om/props this)]
      (dom/div nil
        (dom/h2 nil "Search")
        (cond->
          [(search-field this (:text (om/get-params this)))]
          (not (empty? results)) (conj (result-list results)))))))

;; remote sync

(def base-url "https://www.entoen.nu/api/search?format=simple&text=")

(defn jsonp
  ([uri]
   (jsonp (chan) uri))
  ([c uri]
   (let [gjsonp (Jsonp. (Uri. uri))]
     (.send gjsonp nil #(put! c %))
     c)))

(defn search-loop [c]
  (go-loop [[text cb] (<! c)]
    (let [results (<! (jsonp (str base-url text)))]
      (cb {:search/results (js->clj results :keywordize-keys true)}))
    (recur (<! c))))

(defn send-to-chan [c]
  (fn [{:keys [search]} cb]
    (when search
      (let [{[search] :children} (om/query->ast search)
            text (get-in search [:params :text])]
        (put! c [text cb])))))

(def send-chan (chan))

(search-loop send-chan)

;; reconciler

(def default-state {:search/results []})

(def reconciler
  (om/reconciler
    {:state   default-state
     :parser  parser
     :send    (send-to-chan send-chan)
     :remotes [:search]}))

(om/add-root! reconciler SearchWidget (gdom/getElement "app"))

(defn on-js-reload [])
;; optionally touch your app-state to force rerendering depending on
;; your application
;; (swap! app-state update-in [:__figwheel_counter] inc)
