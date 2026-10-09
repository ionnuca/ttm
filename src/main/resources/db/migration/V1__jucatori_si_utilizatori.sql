-- =====================================================================
-- V1: jucători și conturi de utilizator
-- Rulat automat de Flyway la pornirea aplicației.
-- =====================================================================

CREATE TABLE player (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    first_name  VARCHAR(100) NOT NULL,
    last_name   VARCHAR(100) NOT NULL,
    play_style  VARCHAR(20)  NOT NULL DEFAULT 'ATTACK',
    city        VARCHAR(100),
    phone       VARCHAR(30),
    rating      INTEGER      NOT NULL DEFAULT 1000,
    wins        INTEGER      NOT NULL DEFAULT 0,
    losses      INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    version     BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_player_play_style CHECK (play_style IN ('ATTACK', 'DEFENCE')),
    CONSTRAINT ck_player_rating     CHECK (rating >= 0),
    CONSTRAINT ck_player_wins       CHECK (wins >= 0),
    CONSTRAINT ck_player_losses     CHECK (losses >= 0)
);

COMMENT ON TABLE  player            IS 'Jucătorii de tenis de masă';
COMMENT ON COLUMN player.play_style IS 'Stilul de joc: ATTACK (atac) sau DEFENCE (apărare)';
COMMENT ON COLUMN player.rating     IS 'Ratingul curent; lista de jucători e ordonată după el';

-- Lista implicită: ordonată după rating, apoi alfabetic
CREATE INDEX ix_player_ranking ON player (rating DESC, last_name, first_name);


CREATE TABLE app_user (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    player_id     BIGINT,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uq_app_user_username UNIQUE (username),
    CONSTRAINT uq_app_user_player   UNIQUE (player_id),
    CONSTRAINT fk_app_user_player   FOREIGN KEY (player_id) REFERENCES player (id) ON DELETE CASCADE,
    CONSTRAINT ck_app_user_role     CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_app_user_username CHECK (username = lower(username))
);

COMMENT ON TABLE  app_user           IS 'Conturile de autentificare; fiecare utilizator este un jucător';
COMMENT ON COLUMN app_user.username  IS 'Numele de utilizator, salvat mereu cu litere mici';
COMMENT ON COLUMN app_user.role      IS 'USER (utilizator logat) sau ADMIN (administrator)';
COMMENT ON COLUMN app_user.player_id IS 'Profilul de jucător; NULL doar pentru administratorul tehnic inițial';
