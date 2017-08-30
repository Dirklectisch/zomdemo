(ns zomdemo.repl
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [zomdemo.core :as core :refer [jsonp base-url parser default-state]]
            [cljs.core.async :refer [<!]]))

(defn try-request [text]
  (go
    (println (<! (jsonp (str base-url text))))))

(defn try-query
  ([state query] (parser {:state state} query))
  ([query] (try-query (atom default-state) query))
  ([] (try-query '[(:search/results {:text "canon"})])))
