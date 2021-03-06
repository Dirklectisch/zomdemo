(ns zomdemo.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [goog.dom :as gdom]
            [om.dom :as dom]
            [om.next :as om :refer [defui]]
            [cljs.core.async :as async :refer [<! >! put! chan]]
            [clojure.string :as string])
  (:import [goog Uri]
           [goog.net Jsonp]))

(enable-console-print!)

;; parser

(defn search-categories [search-results]
  (->> (group-by #(last (:category %)) search-results)
       (mapv (fn [[name items]] {:name name :count (count items)}))))

(defmulti read-fn om/dispatch)

(defmethod read-fn :search/results
  [{:keys [state ast query] :as env} k {:keys [text]}]
  (let [st @state]
    (merge
      {:value (om/db->tree query
                           (get st k [])
                           st)}
      (when-not (or (string/blank? text)
                    (< (count text) 3))
        {:search ast}))))

(defmethod read-fn :search/categories
  [{:keys [state query] :as env} key params]
  (let [st @state
        search-results (om/db->tree [:id :category]
                                    (get st :search/results [])
                                    st)]
    {:value (search-categories search-results)}))

(def parser (om/parser {:read read-fn}))

;; components

(defn set-params! [this params]
  (->>
    {:params (merge (om/get-params this) params)}
    (om/set-query! this)))

(defn translate [title]
  (let [{:keys [trans]} title]
    (condp #(contains? %2 %1) trans
      :nl (:nl trans)
      :en (:en trans)
      (str title))))

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
      (dom/li #js {:key id} (translate title)))))

(def result (om/factory Result))

(defn result-list [results]
  (dom/ul #js {:key "result-list"}
    (map result results)))

(defn search-field [this text]
  (dom/input
    #js {:key "search-field"
         :value text
         :onChange (fn [e]
                     (set-params! this {:text (.. e -target -value)}))}))

(defn category-select [this cat]
  (let [name (:name cat)
        count (:count cat)]
    (dom/div #js {:key name}
      (dom/input #js{:type "checkbox"
                     :name "category"
                     :value name
                     :onChange (fn [e]
                                 (let [params (om/get-params this)
                                       cats (vec (:cats params))
                                       key-name (keyword name)]
                                   (->> (if (.. e -target -checked)
                                          (conj cats key-name)
                                          (filter #(not (#{key-name} %)) cats))
                                        (assoc {} :cats)
                                        (set-params! this))))})
      (dom/label nil (str name " (" count "+) ")))))

(defn category-list [this cats]
  (dom/fieldset #js {:key "category-list"}
    (dom/legend nil "Select Categories")
    (map #(category-select this %) cats)))

(defui SearchWidget
  static om/IQueryParams
  (params [_]
    {:text "" :cats []})
  static om/IQuery
  (query [_]
    (let [params '{:text ?text :cats ?cats}]
      `[({:search/results ~(om/get-query Result)} ~params)
        (:search/categories ~params)]))
  Object
  (render [this]
    (let [{:keys [search/results search/categories]} (om/props this)]
      (dom/div nil
        (dom/h2 nil "Search")
        (category-list this categories)
        (cond->
          [(search-field this (:text (om/get-params this)))]
          (not (empty? results)) (conj (result-list results)))))))

;; remote sync

(def base-url "https://www.entoen.nu/api/search?format=simple&limit=100")

(defn req-url [text cats]
  (str base-url "&text=" text
    (apply str (map #(str "&cat=" %) cats))))

(defn jsonp
  ([uri]
   (jsonp (chan) uri))
  ([c uri]
   (let [gjsonp (Jsonp. (Uri. uri))]
     (.send gjsonp nil #(put! c %))
     c)))

(defn search-loop [c]
  (go-loop [[text cats cb] (<! c)]
    (let [results (<! (jsonp (req-url text cats)))]
      (cb {:search/results (js->clj results :keywordize-keys true)}))
    (recur (<! c))))

(defn send-to-chan [c]
  (fn [{:keys [search]} cb]
    (when search
      (let [{[search] :children} (om/query->ast search)
            text (get-in search [:params :text])
            cats (get-in search [:params :cats])]
        (put! c [text cats cb])))))

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
