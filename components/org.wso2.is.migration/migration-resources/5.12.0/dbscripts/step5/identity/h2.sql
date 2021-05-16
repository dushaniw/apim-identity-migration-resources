CREATE TABLE IDN_FED_USER_TOTP_SECRET_KEY (
            USER_ID VARCHAR (255) NOT NULL,
            SECRET_KEY VARCHAR(1024) NOT NULL,
            FOREIGN KEY (USER_ID) REFERENCES IDN_AUTH_USER(USER_ID) ON DELETE CASCADE,
            PRIMARY KEY (USER_ID)
);
