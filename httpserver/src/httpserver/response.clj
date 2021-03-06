(ns httpserver.response
  (:require [httpserver.file :as file]
            [httpserver.encoding :as code]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(def status-line (str "%s %d %s\r\n"))

(def reason-phrase {200 "OK"
                    204 "No content"
                    206 "Partial content"
                    401 "Unauthorized"
                    404 "Not found"
                    405 "Method not allowed"
                    409 "Conflict"
                    418 "I'm a teapot"
                    302 "Found"})

(defn format-status-line [status-code]
  (format status-line
          "HTTP/1.1"
          status-code
          (reason-phrase status-code)))

; I think you clould use map/join here too, same as below
(defn format-headers [headers-map]
  (apply str
         (for [header (keys headers-map)]
           (str header ": " (headers-map header) "\r\n"))))

(defn linkify [paths]
  (string/join (map #(str "<a href=\"/"
                          %
                          "\">"
                          %
                          "</a><br />") paths)))

(defn htmlify [title body]
  (str "<html><head><title>"
       title
       "</title></head><body>"
       body
       "</body></html>"))

(defn ls [path]
  (apply list (.list (io/as-file path))))

(defn content
  ;Option to supply indices for partial content
  ([path]
    (if (file/directory? path)
      (let [[all dir] (re-find #".*(/.*?)$" path)]
        (htmlify (str "Index of " dir)
                 (linkify (ls path))))
      (let [file (io/as-file path)]
        (with-open [stream (io/input-stream file)]
          (vec (repeatedly (.length file)
                           #(.read stream)))))))
  ([path start end]
   (cond
     (nil? end) (subvec (content path) start)
     (nil? start) (vec (take-last end (content path)))
     :else (subvec (content path) start (inc end)))))

(defn content-type [path]
  (cond
    (file/directory? path) "text/html"
    ((complement nil?)
      (re-find #"(?i)\.jpe{0,1}g$" path)) "image/jpeg"
    :else "text/plain"))

(defn compose
  "Option to provide headers and message body in params"
  ([status-code]
    (code/str->bytes (format-status-line status-code)))
  ([status-code headers-map]
    (concat (compose status-code)
            (code/str->bytes
              (str (format-headers headers-map)
                   "\r\n"))))
  ([status-code headers-map body]
   (let [msg-body (if (string? body) (code/str->bytes body)
                    body)]
     (if (contains?
           headers-map
           "Content-Length") (concat (compose status-code
                                              headers-map)
                                     msg-body) ; FORMATTING! Yuuuuuck
       (compose status-code
                (merge headers-map
                       {"Content-Length" (count msg-body)})
                msg-body)))))
