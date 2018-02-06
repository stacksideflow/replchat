(ns spachat.routes.home
  (:require [clj-time.core :as t]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [compojure.core :refer [defroutes GET POST]]
            [digest :as hashlib]
            [ring.util.http-response :as response]
            [spachat.db.core :as db]
            [spachat.layout :as layout]
            spachat.spec))

(defn rnd-str
  "get str of pseudorandom safe chars"
  ([] (rnd-str 3))
  ([n]
   (let [chars (map char (range 48 127))
         password (take n (repeatedly #(rand-nth chars)))]
     (reduce str password))))

(defn home-page
  "render the page hosting js app" []
  (layout/render "home.html"))

(defn signup [{:keys [params]}]
  (let [{:keys [username password]} params
        full-info (db/get-user {:username username})
        existing-username (:username full-info)
        existing-password (:password full-info)]
    (cond
      (or (empty? username)
          (and (not (empty? existing-username))
               (not= existing-password (hashlib/md5 password))))
      (response/bad-request {:errorText "Incorrect password for existing user"})
    ;;double-check specced pass
      (not (s/valid? :spachat.spec/password password))
      (response/bad-request {:errorText (str "Congrats! You sneaked past our "
                                             "cljs.spec, but not clj.spec though!")})
     ;;user exists, pass is OK
      (and (= username existing-username)
           (= existing-password (hashlib/md5 password)))
      (let [new-cookie (rnd-str 64)]
        (println "successfull login form" existing-username)
        (db/put-cookie {:cookie new-cookie :username username})
        (response/ok {:okCookie new-cookie}))
     ;;a vacant username entered
      (empty? existing-username)
      (let [new-cookie (rnd-str 64)]
        (println "creating new user" username)
        (db/put-cookie-and-user {:username username
                                 :password (hashlib/md5 password)
                                 :cookie new-cookie
                                 :signupdate (t/now)})
        (response/ok {:okCookie new-cookie})))))

(defn- get-msgs-author-data
  "supplementary fn to get-chat to assoc author data"
  [msg]
  (merge msg (db/get-user-with-id {:id (:author msg)})))

(defn get-chat [_]
  (let [msgs-with-authors (map get-msgs-author-data (db/get-chat))]
    (response/ok {:ok true
                  :okChats msgs-with-authors})))

(defn put-chat-with-repl [message]
   (db/put-chat {:text message
                 :author 1
                 :stamp (t/now)})
  (response/ok {:ok true}))

(defn put-chat [{:keys [params]}]
  (let [{:keys [cookie username message]} params
        cookie-check (db/get-cookie {:cookie cookie})]
    (if (= username (get cookie-check :username))
      (do (db/put-chat {:text message
                        :author (get cookie-check :id)
                        :stamp (t/now)})
          (response/ok {:ok true}))
      (response/ok {:ok false}))))

(defn ping
  "receives UDP-styled ping, returns whether user is up-to-date"
  [{:keys [params]}]
  (let [{:keys [cookie lastchat] :or {lastchat 0}} params
        now-stamp (t/now)
        users-online (db/get-online-users)
        last-stored-chat-id (or (:id (db/get-last-chat)) 0)]
    (db/put-ping {:cookie cookie :lastseen now-stamp})
    (if (> last-stored-chat-id lastchat)
      (response/ok {:updateneeded true :onlineUsersNow users-online})
      (response/ok {:updateneeded false :onlineUsersNow users-online}))))

(defroutes home-routes
  (GET "/" [] (home-page))
  (POST "/API/signupGo" request (signup request))
  (POST "/API/putChat" request (put-chat request))
  (POST "/API/ping" request (ping request))
  (POST "/API/getChat" request (get-chat request)))
