(ns mba-fiap.adapter.http.server
  (:require
    [clojure.data.json :as json]
    [integrant.core :as ig]
    [io.pedestal.http :as http]
    [io.pedestal.http.route :as route]
    [mba-fiap.adapter.preparo-rest :as preparo-rest]
    [io.pedestal.interceptor.helpers :as interceptor]))


(defn context-interceptor
  [context]
  (interceptor/on-request #(assoc % :app-context context)))


(def parse-json-body-interceptor
  (interceptor/on-response #(update % :body json/write-str)))


(def tap-error-interceptor
  (interceptor/after
    (fn [x]
      (tap> [::dev-logging x])
      x)))


(defn routes
  []
  (route/expand-routes
    (into []
          [(preparo-rest/preparo-routes)])))


(defn add-interceptors
  [service-map & interceptors]
  (update service-map
          :io.pedestal.http/interceptors
          #(vec (concat % interceptors))))


(defn server
  [{:keys [env port join? app-context]}]
  (println "Starting server")
  (let [ctx-interceptor (context-interceptor app-context)]
    (cond-> {:env env
             ::http/routes (routes)
             ::http/resource-path "/public"
             ::http/type :jetty
             ::http/join? join?
             ::http/port port
             ::http/host "0.0.0.0"}
      :always http/default-interceptors
      :always (add-interceptors ctx-interceptor parse-json-body-interceptor)
      (or (= :dev env)
          (= :test env)) (-> http/dev-interceptors
                             (add-interceptors tap-error-interceptor))
      :then http/create-server)))


(defmethod ig/init-key ::server [_ cfg]
  (http/start (server cfg)))


(defmethod ig/halt-key! ::server [_ server]
  (http/stop server))

