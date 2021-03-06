(ns httpserver.core-test
  (:import (java.net Socket))
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [httpserver.socket :as socket]
            [httpserver.http-messages :refer :all]
            [httpserver.core :refer :all]))

(deftest test-set-vars
  (testing "Use default settings if no flags"
    (is (= (hash-map :port 5000 :dir default-dir) ; instead of calling hash-map you can just use a literal
           (set-vars '()))))
  (testing "Set port when only dir is given"
    (is (= (hash-map :port 8888 :dir default-dir)
           (set-vars '("-p" "8888")))))
  (testing "Set dir when only port is given"
    (is (= (hash-map :port 5000 :dir "~")
           (set-vars '("-d" "~")))))
  (testing "Use given settings if both flags"
    (is (= (hash-map :port 8888 :dir "~")
           (set-vars '("-p" "8888" "-d" "~")))))) ; Generally speaking you want to keep the parens lined up at the end

(deftest test-serve
  (with-open [server (socket/open 5000)
              client-socket (Socket. "localhost" 5000)
              client-out (io/writer client-socket)
              client-in (io/reader client-socket)
              connection (socket/listen server)]
    (testing "Server sends response to request"
      (.write client-out not-found-get-request)
      (.flush client-out)
      (serve connection ".")
      (is (= (string/trim-newline simple-404-response) ; avoid refer all except clojure.test
             (.readLine client-in)))))) ; is 404 always a safe response here?

(deftest test-threading
  (with-open [server (socket/open 5000)
              client-one (Socket. "localhost" 5000)]
    (testing "Can accept second connection if one client already connected"
      (future (threading server "."))
      (.isConnected client-one) ; Shouldn't you be checkint this, if I delete this line the tests pass.
      (with-open [client-two (Socket. "localhost" 5000)]
        (is (.isConnected client-two))))))
