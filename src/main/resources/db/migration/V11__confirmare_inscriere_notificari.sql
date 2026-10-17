-- =====================================================================
-- V11: înscrierea la turneu cu confirmare și notificările utilizatorilor
--   Înscrierea făcută de jucător așteaptă confirmarea unui organizator (PENDING);
--   cea făcută de organizator e confirmată direct (CONFIRMED). Înscrierile existente sunt confirmate.
-- =====================================================================

ALTER TABLE tournament_participant
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    ADD CONSTRAINT ck_participant_status CHECK (status IN ('PENDING', 'CONFIRMED'));

COMMENT ON COLUMN tournament_participant.status IS 'PENDING = cerere de înscriere neconfirmată; CONFIRMED = participant';


CREATE TABLE notification (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    type            VARCHAR(40)  NOT NULL,
    tournament_id   BIGINT,
    tournament_name VARCHAR(150) NOT NULL,
    player_name     VARCHAR(210),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    read_at         TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_notification_user       FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_tournament FOREIGN KEY (tournament_id) REFERENCES tournament (id) ON DELETE SET NULL,
    CONSTRAINT ck_notification_type CHECK (type IN ('REGISTRATION_REQUESTED', 'REGISTRATION_CONFIRMED',
                                                    'REGISTRATION_REJECTED', 'REGISTRATION_EXPIRED'))
);

CREATE INDEX ix_notification_user ON notification (user_id, created_at DESC);

COMMENT ON TABLE notification IS 'Notificările din aplicație (clopoțelul din bara de sus)';
COMMENT ON COLUMN notification.tournament_name IS 'Numele turneului la momentul notificării (rămâne și dacă turneul e șters)';
COMMENT ON COLUMN notification.player_name IS 'La cererile de înscriere (pentru organizatori): jucătorul care a cerut';
