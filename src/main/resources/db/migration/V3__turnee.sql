-- =====================================================================
-- V3: turnee, participanți și meciuri
-- =====================================================================

CREATE TABLE tournament (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            VARCHAR(150) NOT NULL,
    tournament_date DATE         NOT NULL,
    format          VARCHAR(20)  NOT NULL DEFAULT 'ROUND_ROBIN',
    best_of         INTEGER      NOT NULL DEFAULT 5,
    status          VARCHAR(20)  NOT NULL DEFAULT 'REGISTRATION',
    commercial      BOOLEAN      NOT NULL DEFAULT FALSE,
    winners_count   INTEGER,
    entry_fee       NUMERIC(10, 2),
    started_at      TIMESTAMP WITH TIME ZONE,
    finished_at     TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_tournament_format     CHECK (format IN ('ROUND_ROBIN')),
    CONSTRAINT ck_tournament_best_of    CHECK (best_of IN (1, 3, 5, 7)),
    CONSTRAINT ck_tournament_status     CHECK (status IN ('REGISTRATION', 'IN_PROGRESS', 'FINISHED')),
    CONSTRAINT ck_tournament_winners    CHECK (winners_count IS NULL OR winners_count IN (1, 2, 3)),
    CONSTRAINT ck_tournament_fee        CHECK (entry_fee IS NULL OR entry_fee >= 0),
    CONSTRAINT ck_tournament_commercial CHECK (NOT commercial OR (winners_count IS NOT NULL AND entry_fee IS NOT NULL))
);

COMMENT ON TABLE  tournament               IS 'Turneele';
COMMENT ON COLUMN tournament.best_of       IS 'Numărul maxim de seturi al unui meci (best of 3/5/7)';
COMMENT ON COLUMN tournament.status        IS 'REGISTRATION (înscriere), IN_PROGRESS (în desfășurare), FINISHED (încheiat)';
COMMENT ON COLUMN tournament.commercial    IS 'Turneu comercial: cu taxă de participare și premii din suma acumulată';
COMMENT ON COLUMN tournament.winners_count IS 'Câștigători premiați: 1 = 100%; 2 = 60/40%; 3 = 50/30/20%';
COMMENT ON COLUMN tournament.entry_fee     IS 'Taxa de participare, în lei';

CREATE INDEX ix_tournament_date ON tournament (tournament_date DESC);


CREATE TABLE tournament_participant (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tournament_id BIGINT  NOT NULL,
    player_id     BIGINT  NOT NULL,
    seed          INTEGER,
    seed_rating   INTEGER,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT fk_participant_tournament FOREIGN KEY (tournament_id) REFERENCES tournament (id) ON DELETE CASCADE,
    CONSTRAINT fk_participant_player     FOREIGN KEY (player_id) REFERENCES player (id),
    CONSTRAINT uq_participant            UNIQUE (tournament_id, player_id)
);

COMMENT ON TABLE  tournament_participant             IS 'Jucătorii înscriși la un turneu';
COMMENT ON COLUMN tournament_participant.seed        IS 'Poziția în grupă (1 = ratingul cel mai mare), stabilită la începerea turneului';
COMMENT ON COLUMN tournament_participant.seed_rating IS 'Ratingul jucătorului în momentul începerii turneului';

CREATE INDEX ix_participant_player ON tournament_participant (player_id);


CREATE TABLE tournament_match (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tournament_id  BIGINT  NOT NULL,
    round_no       INTEGER NOT NULL,
    participant_a  BIGINT  NOT NULL,
    participant_b  BIGINT  NOT NULL,
    sets_a         INTEGER,
    sets_b         INTEGER,
    outcome        VARCHAR(20),
    winner_id      BIGINT,
    recorded_by    VARCHAR(50),
    recorded_at    TIMESTAMP WITH TIME ZONE,
    version        BIGINT  NOT NULL DEFAULT 0,

    CONSTRAINT fk_match_tournament FOREIGN KEY (tournament_id) REFERENCES tournament (id) ON DELETE CASCADE,
    CONSTRAINT fk_match_a          FOREIGN KEY (participant_a) REFERENCES tournament_participant (id) ON DELETE CASCADE,
    CONSTRAINT fk_match_b          FOREIGN KEY (participant_b) REFERENCES tournament_participant (id) ON DELETE CASCADE,
    CONSTRAINT fk_match_winner     FOREIGN KEY (winner_id) REFERENCES tournament_participant (id) ON DELETE CASCADE,
    CONSTRAINT ck_match_players    CHECK (participant_a <> participant_b),
    CONSTRAINT ck_match_outcome    CHECK (outcome IS NULL OR outcome IN ('NORMAL', 'WALKOVER')),
    CONSTRAINT ck_match_sets       CHECK (sets_a IS NULL OR (sets_a >= 0 AND sets_b >= 0)),
    CONSTRAINT ck_match_result     CHECK ((outcome IS NULL AND winner_id IS NULL) OR (outcome IS NOT NULL AND winner_id IS NOT NULL))
);

COMMENT ON TABLE  tournament_match         IS 'Meciurile unui turneu; rezultatul e gol până la înregistrare';
COMMENT ON COLUMN tournament_match.outcome IS 'NORMAL (pe seturi) sau WALKOVER (victorie tehnică, adversarul a refuzat jocul)';

CREATE INDEX ix_match_tournament ON tournament_match (tournament_id, round_no);
