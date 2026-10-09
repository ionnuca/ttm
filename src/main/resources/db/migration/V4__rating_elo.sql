-- =====================================================================
-- V4: ratingul Elo
-- Ratingul curent (player.rating) și statisticile (wins, losses) se calculează de aplicație
-- din ratingul inițial și din toate turneele încheiate, în ordine cronologică.
-- =====================================================================

ALTER TABLE player
    ADD COLUMN initial_rating INTEGER NOT NULL DEFAULT 1000,
    ADD CONSTRAINT ck_player_initial_rating CHECK (initial_rating >= 0);

-- Ratingul existent devine punctul de plecare
UPDATE player SET initial_rating = rating;

COMMENT ON COLUMN player.initial_rating IS 'Ratingul de pornire, stabilit de administrator; ratingul curent = inițial + schimbările din turnee';
COMMENT ON COLUMN player.rating         IS 'Ratingul curent, calculat (Elo) din turneele încheiate';
COMMENT ON COLUMN player.wins           IS 'Victorii în turneele încheiate (calculat)';
COMMENT ON COLUMN player.losses         IS 'Înfrângeri în turneele încheiate (calculat)';


-- Schimbarea de rating a fiecărui meci (doar meciurile jucate efectiv, din turnee încheiate)
ALTER TABLE tournament_match
    ADD COLUMN rating_delta_a INTEGER,
    ADD COLUMN rating_delta_b INTEGER;


-- Istoricul: ratingul fiecărui jucător înainte și după fiecare turneu încheiat
CREATE TABLE rating_history (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    player_id      BIGINT  NOT NULL,
    tournament_id  BIGINT  NOT NULL,
    rating_before  INTEGER NOT NULL,
    rating_after   INTEGER NOT NULL,
    k_factor       INTEGER NOT NULL,
    rated_matches  INTEGER NOT NULL,

    CONSTRAINT fk_rating_history_player     FOREIGN KEY (player_id) REFERENCES player (id) ON DELETE CASCADE,
    CONSTRAINT fk_rating_history_tournament FOREIGN KEY (tournament_id) REFERENCES tournament (id) ON DELETE CASCADE,
    CONSTRAINT uq_rating_history            UNIQUE (player_id, tournament_id)
);

COMMENT ON TABLE  rating_history               IS 'Evoluția ratingului pe turnee; se reconstruiește la fiecare recalculare';
COMMENT ON COLUMN rating_history.k_factor      IS 'Factorul K folosit: 40 până la 30 de meciuri cu rating, apoi 20';
COMMENT ON COLUMN rating_history.rated_matches IS 'Meciurile jucate efectiv în turneu (fără victoriile tehnice)';
