-- =====================================================================
-- V10: seturile de departajare (turnee comerciale)
--   Când jucătorii de la egalitate nu pot fi despărțiți prin meciurile directe,
--   joacă între ei câte un set suplimentar, cu scorul la puncte (ex. 11:8).
--   Dacă egalitatea rămâne, se joacă o nouă rundă (round_no 2, 3 …).
--   Seturile nu sunt meciuri: nu contează la rating, la statistici sau la lista meciurilor.
-- =====================================================================

CREATE TABLE tournament_tiebreak (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version        BIGINT       NOT NULL DEFAULT 0,
    tournament_id  BIGINT       NOT NULL,
    group_id       BIGINT,
    round_no       INTEGER      NOT NULL,
    participant_a  BIGINT       NOT NULL,
    participant_b  BIGINT       NOT NULL,
    points_a       INTEGER      NOT NULL,
    points_b       INTEGER      NOT NULL,
    recorded_by    VARCHAR(50),
    recorded_at    TIMESTAMPTZ,

    CONSTRAINT fk_tiebreak_tournament FOREIGN KEY (tournament_id) REFERENCES tournament (id) ON DELETE CASCADE,
    CONSTRAINT fk_tiebreak_group      FOREIGN KEY (group_id) REFERENCES tournament_group (id) ON DELETE CASCADE,
    CONSTRAINT fk_tiebreak_a          FOREIGN KEY (participant_a) REFERENCES tournament_participant (id) ON DELETE CASCADE,
    CONSTRAINT fk_tiebreak_b          FOREIGN KEY (participant_b) REFERENCES tournament_participant (id) ON DELETE CASCADE,
    CONSTRAINT ck_tiebreak_players    CHECK (participant_a <> participant_b),
    CONSTRAINT ck_tiebreak_round      CHECK (round_no >= 1),
    CONSTRAINT ck_tiebreak_points     CHECK (points_a >= 0 AND points_b >= 0 AND points_a <> points_b)
);

CREATE INDEX ix_tiebreak_tournament ON tournament_tiebreak (tournament_id);

COMMENT ON TABLE tournament_tiebreak IS 'Seturile de departajare între jucătorii rămași la egalitate (turnee comerciale)';
COMMENT ON COLUMN tournament_tiebreak.group_id IS 'Grupa sau finala; NULL la turneele Round Robin simple';
COMMENT ON COLUMN tournament_tiebreak.round_no IS 'Runda de departajare: 1, apoi 2 … dacă egalitatea se repetă';
