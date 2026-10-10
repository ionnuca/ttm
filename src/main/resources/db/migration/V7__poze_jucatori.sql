-- =====================================================================
-- V7: poza de profil a jucătorului
-- O poză pe jucător, deja redimensionată de aplicație (JPEG pătrat, 400 x 400).
-- Stă în baza de date, deci intră automat în backup.
-- =====================================================================

CREATE TABLE player_photo (
    player_id     BIGINT                   NOT NULL,
    content       BYTEA                    NOT NULL,
    content_type  VARCHAR(50)              NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT pk_player_photo        PRIMARY KEY (player_id),
    CONSTRAINT fk_player_photo_player FOREIGN KEY (player_id) REFERENCES player (id) ON DELETE CASCADE
);

COMMENT ON TABLE player_photo IS 'Poza de profil a jucătorului (JPEG 400 x 400)';

-- Lista ultimelor meciuri se ordonează după momentul înregistrării rezultatului
CREATE INDEX ix_tournament_match_recorded_at ON tournament_match (recorded_at DESC) WHERE outcome IS NOT NULL;
