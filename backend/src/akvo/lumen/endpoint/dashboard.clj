(ns akvo.lumen.endpoint.dashboard
  (:require [akvo.lumen.component.tenant-manager :as tenant-manager]
            [akvo.lumen.lib :as lib]
            [akvo.lumen.lib.auth :as l.auth]
            [akvo.lumen.lib.dashboard :as dashboard]
            [akvo.lumen.protocols :as p]
            [akvo.lumen.specs.dashboard :as dashboard.s]
            [clojure.spec.alpha :as s]
            [clojure.walk :as w]
            [clojure.tools.logging :as log]
            [integrant.core :as ig]))

(defn all-dashboards [auth-service tenant-conn]
  (let [dashboards      (dashboard/all tenant-conn)
        auth-dashboards (->> dashboards
                             (l.auth/ids ::dashboard.s/dashboards)
                             (p/auth auth-service)
                             :auth-dashboards)]
    (log/debug :auth-dashboards auth-dashboards (mapv :id dashboards))
    (->> dashboards
         (filter #(contains? auth-dashboards (:id %)))
         (lib/ok))))

(defn routes [{:keys [tenant-manager windshaft-url] :as opts}]
  ["/dashboards"
   ["" {:get {:handler (fn [{tenant :tenant
                             auth-service :auth-service}]
                         (all-dashboards auth-service (p/connection tenant-manager tenant)))}
        :post {:parameters {:body map?}
               :handler (fn [{tenant :tenant
                              jwt-claims :jwt-claims
                              auth-service :auth-service
                              body :body}]
                          (let [payload (w/keywordize-keys body)
                                ids (l.auth/ids ::dashboard.s/dashboard-post-payload payload)]
                            (if (p/allow? auth-service ids)
                              (dashboard/create (p/connection tenant-manager tenant) payload jwt-claims)
                              (lib/not-authorized {:ids ids}))))}}]
   ["/:id"
    {:middleware [(fn [handler]
                    (fn [{{:keys [id]} :path-params
                          auth-service :auth-service
                          :as req}]
                      (if (p/allow? auth-service (l.auth/ids ::dashboard.s/id id))
                        (handler req)
                        (lib/not-authorized {:id id}))))]}
    ["" {:get {:parameters {:path-params {:id string?}}
               :handler (fn [{tenant :tenant
                              {:keys [id]} :path-params}]
                          (let [;; VIP Inject dashboard filter from payload
                                ;; datasetId "5e2a05a0-69ba-43b8-aa6e-7f6e200158c4"
                                ;; filter {"value" "lisa",
                                ;;         "column" "c2",
                                ;;         "strategy" "is",
                                ;;         "operation" "keep",
                                ;;         "columnType" "text"}
                                ;; filters {datasetId [filter]}
                                ;; Juan example
                                ;; {"filter": {"columns":[{"value":"Cambodia",
                                ;;                         "column":"c4",
                                ;;                         "strategy":"is",
                                ;;                         "operation":"keep",
                                ;;                         "columnType":"text"},
                                ;;                        {"value":null,
                                ;;                         "column":"c5",
                                ;;                         "strategy":"is",
                                ;;                         "operation":"keep",
                                ;;                         "columnType":"text"}],
                                ;;             "datasetId":"5e2887a9-b7c6-4306-a30a-20b90731e549"}}
                                ;; filters []
                                ;; filters {"columns" [{"value" "lisa",
                                ;;                      "column" "c2",
                                ;;                      "strategy" "is",
                                ;;                      "operation" "keep",
                                ;;                      "columnType" "text"}]
                                ;;          "datasetId" "5e2a05a0-69ba-43b8-aa6e-7f6e200158c4"}
                                filters {}
                                ]
                            (if-let [d (-> tenant-manager
                                           (p/connection tenant)
                                           (dashboard/fetch-aggregated id windshaft-url filters))]
                              (lib/ok d)
                              (lib/not-found {:error "Not found"}))))}
         :put {:parameters {:body map?
                            :path-params {:id string?}}
               :handler (fn [{tenant :tenant
                              auth-service :auth-service
                              body :body
                              {:keys [id]} :path-params}]
                          (let [payload (w/keywordize-keys body)
                                ids (l.auth/ids ::dashboard.s/dashboard-payload payload)]
                            (if (p/allow? auth-service ids)
                              (dashboard/upsert (p/connection tenant-manager tenant) id payload)
                              (lib/not-authorized {:ids ids}))))}
         :delete {:parameters {:path-params {:id string?}}
                  :handler (fn [{tenant :tenant
                                 {:keys [id]} :path-params}]
                             (dashboard/delete (p/connection tenant-manager tenant) id))}}]]])

(defmethod ig/init-key :akvo.lumen.endpoint.dashboard/dashboard  [_ opts]
  (routes opts))

(s/def ::windshaft-url string?)

(defmethod ig/pre-init-spec :akvo.lumen.endpoint.dashboard/dashboard [_]
  (s/keys :req-un [::tenant-manager/tenant-manager
                   ::windshaft-url] ))
