(ns akvo.lumen.endpoint.files
  (:require [compojure.core :refer [ANY]]
            [integrant.core :as ig]
            [org.akvo.resumed :refer [make-handler]]))

(defn endpoint
  [{:keys [file-upload-path max-upload-size]}]
  (let [file-upload-handler (make-handler {:save-dir        file-upload-path
                                           :max-upload-size max-upload-size})]
    (ANY "/api/files*" req
         (file-upload-handler req))))

(defmethod ig/init-key :akvo.lumen.endpoint.files/files  [_ opts]
  (endpoint (:upload-config opts)))

(defmethod ig/init-key :akvo.lumen.upload  [_ opts]
  opts)
