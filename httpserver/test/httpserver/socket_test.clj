(ns httpserver.socket-test
  (:import (java.net ServerSocket Socket)
           (java.io StringReader))
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [httpserver.http-messages :refer :all] ;You get the deal, avoid :refer :all
            [httpserver.socket :refer :all]))

(deftest test-open
  (let [server-socket (open 5000)]
    (testing "Create a ServerSocket"
      (is (instance? ServerSocket server-socket)))
    (testing "Socket listening on given port" ; do you know it's really listening?
      (is (= 5000 (.getLocalPort server-socket))))
    (.close server-socket))) ; what happens if there is an error above? Won't the socket stay open

(deftest test-close
  (let [server-socket (open 5000)]
    (testing "Close socket"
      (close server-socket)
      (is (.isClosed server-socket))))
) ; parens should usually go at the end

(deftest test-listen
  (with-open [server-socket (open 5000)
              client-socket (Socket. "localhost" 5000)]
    (testing "Client can connect to server"
      (is (.isConnected client-socket)))))

(def single-line-request (str "GET / HTTP/1.1\r\n"
                              "\r\n"
                              "\r\n"))

(def multi-line-request (str "PUT /form HTTP/1.1\r\n"
                             "Content-Length: 7\r\n"
                             "\r\n"
                             "My=Data"))

(deftest test-body?
  (testing "Request with no Content-Length header"
    (is ((complement body?) single-line-request)))
  (testing "Request with Content-Length header"
    (is (body? multi-line-request))))

(deftest test-read-request-line
  (testing "Get request line from request with no headers"
    (with-open [input (StringReader. single-line-request)
                stream (io/reader input)]
      (is (= "GET / HTTP/1.1\r\n"
             (read-request-line stream)))))
  (testing "Get request line from request with headers"
    (with-open [input (StringReader. multi-line-request)
                stream (io/reader input)]
      (is (= "PUT /form HTTP/1.1\r\n"
             (read-request-line stream))))))

(deftest test-read-headers
  (testing "Request with no headers"
    (with-open [input (StringReader. single-line-request)
                stream (io/reader input)]
      (read-request-line stream)
      (is (= "\r\n"
             (read-headers stream)))))
  (testing "Request with headers"
    (with-open [input (StringReader. multi-line-request)
                stream (io/reader input)]
      (read-request-line stream)
      (is (= (str "Content-Length: 7\r\n")
             (read-headers stream))))))

; ^ read-headers is recursive. This should really have several cases
; line is blank and headers are blank. The problem seems to be you're using single-line-request
; and multi-line-request as your only available test cases.
; - The (and) inside the cond should have both sides tested
; - The (= "" line) tested alone
; - finally a test of the recursion
; - This is probably allo covered, but it's not explicit

(deftest test-read-body
  (testing "Read message body from request"
    (with-open [input (StringReader. multi-line-request)
                stream (io/reader input)]
      (is (= "My=Data"
             (read-body (read-headers stream) stream))))))

(deftest test-receive
  (with-open [server-socket (open 5000)
              client-socket (Socket. "localhost" 5000)
              connection (listen server-socket)
              stream (io/writer client-socket)]
    (testing "Server can get input"
      (.write stream single-line-request)
      (.flush stream)
      (is (= single-line-request
             (receive connection))))
    (testing "Server can get multiline input"
      (.write stream multi-line-request)
      (.flush stream)
      (is (= multi-line-request
             (receive connection))))
))

(deftest test-give
  (with-open [server-socket (open 5000)
              client-socket (Socket. "localhost" 5000)
              connection (listen server-socket)
              stream (io/reader client-socket)]
    (testing "Server can send output"
      (let [status-line "HTTP/1.1 200 OK\r\n"
            output (map (comp byte int) status-line)]
        (give connection output)
        (is (= (str/trim-newline status-line)
               (.readLine stream)))))
))
