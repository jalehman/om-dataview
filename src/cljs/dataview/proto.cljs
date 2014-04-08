(ns dataview.proto
  (:require [dataview.editable :refer [editable]]
            [om.core :as om :include-macros true]
            [cljs.core.async :refer [put!]]))


(defprotocol IRenderData
  (render-data [this] "Renders the element as an Om element."))

(extend-protocol IRenderData
;;   om/MapCursor
;;   (render-data [this app owner]
;;                (if (empty? this)
;;                  [:table.jh-type-object [:span.jh-empty-map]]
;;                  [:table.jh-type-object
;;                   (for [[k {:keys [__type __path __value __create]}] this]
;;                     [:tr
;;                      [:th.jh-key.jh-object-key
;;                       (render-data k app owner)
;;                       (when (and (sequential? __value) __create)
;;                         [:button {:type "button"
;;                                   :on-click (fn [_]
;;                                               (put! (om/get-state owner :create)
;;                                                     {:path @__path
;;                                                      :data __create}))}
;;                          "+"])]
;;                      (if __type
;;                        (om/build editable app
;;                                  {:opts {:edit-key __path :type __type}
;;                                   :state (om/get-state owner)})
;;                        [:td.jh-value.jh-object-value (render-data __value app owner)])])]))

;;   om/IndexedCursor
;;   (render-data [this app owner]
;;                (if (empty? this)
;;                  [:table.jh-type-object [:span.jh-empty-collection]]
;;                  [:table.jh-type-object
;;                   (for [[i v] (map-indexed vector this)]
;;                     [:tr
;;                      [:th.jh-key.jh-array-key i
;;                       ;; This is a good spot for the delete button
;;                       ;; [:button {:type "button"} "-"]
;;                       ]
;;                      (if-let [v' (get v :__value)]
;;                        (if-let [__type (get v :__type)]
;;                          (om/build editable app
;;                                    {:opts  {:edit-key (get v :__path) :type __type}
;;                                     :state (om/get-state owner)})
;;                          [:td.jh-value.jh-array-value (render-data v' app owner)])
;;                        [:td.jh-value.jh-array-value (render-data v app owner)])])]))

  cljs.core/Keyword
  (render-data [this]
               [:span.jh-type-string (name this)])

  boolean
  (render-data [this]
               [:span.jh-type-bool (str this)])

  number
  (render-data [this]
               [:span.jh-type-number this])

  string
  (render-data [this]
               [:span.jh-type-string
                (if (empty? (clojure.string/trim this))
                  [:span.jh-empty-string "<empty>"]
                  [:span {:dangerouslySetInnerHTML {:__html this}}])])
  nil
  (render-data [_]
               [:span.jh-empty nil]))
