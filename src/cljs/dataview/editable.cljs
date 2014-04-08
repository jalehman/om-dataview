(ns dataview.editable
  (:require [cljs.core.async :refer [put!]]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]))

(defn handle-change [e data edit-key]
  (om/transact! data edit-key (fn [_] (.. e -target -value))))

(defn commit-change [val path owner]
  (om/set-state! owner :editing false)
  (when-not (= (om/get-state owner :original) val)
    (put! (om/get-state owner :events)
          {:new-value val :path path :action :update
           :old-value (om/get-state owner :original)})
    (om/set-state! owner :original val)))

(defn start-edit [_ data edit-key owner]
  (om/set-state! owner :editing true)
  (om/set-state! owner :original (get-in @data edit-key)))

(def jh-types
  {:string  {:input-type "text"   :class "jh-type-string"}
   :integer {:input-type "number" :class "jh-type-number"}
   :float   {:input-type "number" :class "jh-type-number"
             :input-opts {:step "0.001"}}})

(defn editable [data owner {:keys [orig-key edit-key type td-class]
                            :or {type "text", td-class "jh-object-value"}}]
  (reify
    om/IInitState
    (init-state [_]
      {:editing false})
    om/IRenderState
    (render-state [_ {:keys [editing events]}]
      (let [value (get-in data edit-key)
            text  (str value)
            {:keys [input-type input-opts class] :or {class "jh-type-string"}} (get jh-types type)
            on-blur (fn [e]
                      (when editing
                        (commit-change value @orig-key owner)))]
        (html
         [:td.jh-value {:class td-class}
          (if-not (or editing (= :boolean type))
            [:span {:class (str class " " (when (empty? text) "jh-empty"))
                    :style {:cursor "pointer"}
                    :on-click #(start-edit % data edit-key owner)}
             (cond
              (keyword? value)  (name value)
              (empty?   text)   "<empty>"
              :otherwise text)]
            (cond
             (sequential? type)
             [:select {:default-value value :on-change #(handle-change % data edit-key)
                       :on-blur on-blur}
              (for [v type]
                [:option {:value v}
                 (if (keyword? v) (name v) v)])]

             (= :boolean type)
             [:span.jh-type-bool
              {:on-click (fn [_]
                           (put! events {:new-value (not value) :old-value value
                                         :path @orig-key :action :update})
                           (om/transact! data edit-key not))
               :style {:cursor "pointer"}}
              text]

             :otherwise
             [:input
              (merge input-opts
                     {:value text :type input-type :style {:width "100%"}
                      :on-change #(handle-change % data edit-key)
                      :on-key-press #(when (and editing (== (.-keyCode %) 13))
                                       (commit-change value @orig-key owner))
                      :on-blur on-blur})]))])))))
