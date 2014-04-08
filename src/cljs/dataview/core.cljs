(ns dataview.core
    (:require-macros [cljs.core.async.macros :refer [go alt!]])
    (:require [dataview.proto :refer [render-data]]
              [dataview.editable :refer [editable]]
              [cljs.core.async :refer [put! <! >! chan timeout]]
              [om.core :as om :include-macros true]
              [om.dom :as dom :include-macros true]
              [cljs-http.client :as http]
              [sablono.core :as html :refer-macros [html]]))

(enable-console-print!)

(defn- annotate-structure
  ([m s]
   (annotate-structure m s []))
  ([m s p]
   (if (or (map? m) (sequential? m))
     (reduce (fn [acc [k v]]
               (cond
                (map? v)        (merge acc {k {:__value (annotate-structure v (get s k)
                                                                            (conj p k))}})
                (sequential? v) (merge
                                 acc {k
                                      (merge (get-in s [k 0])
                                             {:__path (conj p k)}
                                             {:__value (vec
                                                        (map-indexed
                                                         #(annotate-structure %2
                                                                              (get-in s [k 0 :__schema])
                                                                              (conj (conj p k) %1))
                                                                     v))})})
                :otherwise      (assoc acc k (merge (get s k)
                                                    {:__value v :__path (conj p k)}))))
             {} m)
     (merge s {:__value m :__path p}))))

(def sample-render-schema
  {:name      {:__type :string}
   :sex       {:__type ["male" "female"]}
   :age       {:__type :float}
   :address   {:street {:__type :string} :number {:__type :integer}}
   :languages [{:__schema {:name  {:__type :string}
                           :like? {:__type :boolean}
                           :tags  [{:__schema {:__type :string}
                                    :__create ""}]}
                :__create {:name "NAME" :like? false :tags []}}]
   :tags      [{:__schema {:__type :string}
                :__create ""}]})

;; (annotate-structure (get @app-state :datum) sample-render-schema)

(def app-state
  (atom {:datum {:name "Josh" :sex :male :age 25.01
                 :address {:street "Wild Currant Way" :number 7255}
                 :languages [{:name "clojure" :tags ["immutable" "functional" "lisp"] :like? true}
                             {:name "javascript" :tags ["functional" "mutable"] :like? false}]
                 :tags ["programmer" "entrepreneur"]
                }}))

(defn- dataview-k?
  [k]
  (= (subs (name k) 0 2) "__"))

(defn add-item!
  [app owner {:keys [path data]} schema]
  (do
    (om/transact! app path #(conj % (om/value data)))
    (om/update! app [:__annotated]
                (annotate-structure (dissoc @app :__annotated) schema))
    (.log js/console (clj->js @app))
;;     (put! (om/get-state owner :events)
;;           {:action :stage-create :path path :value (om/value data)})
    ))

(declare map-view*)

(defn seq-view*
  [app owner]
  (om/component
   (html
    (if (empty? app)
      [:div.jh-type-object [:span.jh-empty-collection]]
      [:table.jh-type-object
       [:tbody
        (for [[i v] (map-indexed vector app)]
         [:tr
          [:th.jh-key.jh-array-key i
           ;; This is a good spot for the delete button
           ;; [:button {:type "button"} "-"]
           ]
          (if-let [v' (get v :__value)]
            (if-let [__type (get v :__type)]
              (om/build editable app
                        {:opts  {:orig-key (get v :__path) :edit-key [i :__value]
                                 :type __type :td-class "jh-array-value"}
                         :state (om/get-state owner)})
              [:td.jh-value.jh-array-value
               (cond
                (map? v')        (om/build map-view* v' {:state (om/get-state owner)})
                (sequential? v') (om/build seq-view* v' {:state (om/get-state owner)})
                :otherwise       (render-data v'))])
            [:td.jh-value.jh-array-value
             (cond
              (map? v)        (om/build map-view* v {:state (om/get-state owner)})
              (sequential? v) (om/build seq-view* v {:state (om/get-state owner)})
              :otherwise      (render-data v))])])]]))))

(defn map-view*
  [app owner]
  (reify
    om/IRenderState
    (render-state [_ state]
      (html
       (if (empty? app)
         [:div.jh-type-object [:span.jh-empty-map]]
         [:table.jh-type-object
          [:tbody
           (for [[k {:keys [__type __path __value __create]}] app]
            [:tr
             [:th.jh-key.jh-object-key
              (render-data k)
              (when (and (sequential? __value) __create)
                [:button {:type "button"
                          :on-click (fn [_]
                                      (put! (om/get-state owner :create)
                                            {:path @__path :data __create}))}
                 "+"])]
             (if __type
               (om/build editable app
                         {:opts {:edit-key [k :__value] :orig-key __path :type __type}
                          :state (om/get-state owner)})
               [:td.jh-value.jh-object-value
                (cond
                 (map? __value) (om/build map-view* __value
                                          {:state (om/get-state owner)})
                 (sequential? __value) (om/build seq-view* __value
                                                 {:state (om/get-state owner)})
                 :otherwise (render-data __value))])])]])))))


(defn data-view
  [app owner {:keys [schema events]}]
  (reify
    om/IInitState
    (init-state [_]
      {:events (or events (chan))
       :create (chan)})
    om/IWillMount
    (will-mount [_]
      ;; To get Om cursor info into our annotated structure
      (let [{:keys [create]} (om/get-state owner)]
        (om/update! app [:__annotated] (annotate-structure app schema))
        (go (while true
              (alt!
               create ([v c] (add-item! app owner v schema)))))))
    om/IRenderState
    (render-state [_ state]
      (html
       [:div.jh-root
        (om/build map-view* (:__annotated app) {:state state})]))))

(defn dataview-app
  [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:events (chan)})
    om/IWillMount
    (will-mount [_]
      (go (while true
            (let [v (<! (om/get-state owner :events))]
              (prn v)))))
    om/IRender
    (render [_]
      (html
       [:div
        [:h1 "dataview"]

        (om/build data-view (:datum app)
                  {:opts {:schema sample-render-schema
                          :events (om/get-state owner :events)}})
        ]))))

(om/root dataview-app app-state {:target (.getElementById js/document "content")})
