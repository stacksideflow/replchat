CREATE TABLE IF NOT EXISTS chats (id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY, text TEXT, author TEXT, stamp TIMESTAMP);
--;;
CREATE TABLE IF NOT EXISTS people (id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY, username TEXT, password TEXT, cookie TEXT, lastseen TIMESTAMP, signupdate DATE);