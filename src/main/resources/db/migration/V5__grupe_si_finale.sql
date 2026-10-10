-- =====================================================================
-- V5: turneul „Grupe + finale”
--   Etapa 1: grupe Round Robin (jucătorii repartizați în șerpuială după rating).
--   Etapa 2: Finala 1 (primii din fiecare grupă) și Finala 2 (ceilalți), tot Round Robin;
--            meciurile directe din etapa 1 se preiau și nu se mai joacă.
-- =====================================================================

ALTER TABLE tournament DROP CONSTRAINT ck_tournament_format;
ALTER TABLE tournament
    ADD CONSTRAINT ck_tournament_format CHECK (format IN ('ROUND_ROBIN', 'GROUPS_FINALS')),
    ADD COLUMN stage                VARCHAR(20),
    ADD COLUMN group_count          INTEGER,
    ADD COLUMN qualifiers_per_group INTEGER,
    ADD CONSTRAINT ck_tournament_stage CHECK (stage IS NULL OR stage IN ('GROUPS', 'FINALS')),
    ADD CONSTRAINT ck_tournament_group_count CHECK (group_count IS NULL OR group_count >= 2),
    ADD CONSTRAINT ck_tournament_qualifiers CHECK (qualifiers_per_group IS NULL OR qualifiers_per_group >= 1);

COMMENT ON COLUMN tournament.stage                IS 'La „Grupe + finale”: GROUPS (etapa 1) sau FINALS (etapa 2)';
COMMENT ON COLUMN tournament.group_count          IS 'Numărul de grupe din etapa 1';
COMMENT ON COLUMN tournament.qualifiers_per_group IS 'Câți jucători din fiecare grupă trec în Finala 1';


CREATE TABLE tournament_group (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tournament_id BIGINT      NOT NULL,
    stage         VARCHAR(20) NOT NULL,
    position      INTEGER     NOT NULL,
    name          VARCHAR(50) NOT NULL,

    CONSTRAINT fk_group_tournament FOREIGN KEY (tournament_id) REFERENCES tournament (id) ON DELETE CASCADE,
    CONSTRAINT ck_group_stage      CHECK (stage IN ('GROUPS', 'FINALS')),
    CONSTRAINT uq_group_position   UNIQUE (tournament_id, stage, position)
);

COMMENT ON TABLE tournament_group IS 'Grupele etapei 1 (Grupa A, B …) și finalele etapei 2 (Finala 1, Finala 2)';


CREATE TABLE tournament_group_member (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id       BIGINT  NOT NULL,
    participant_id BIGINT  NOT NULL,
    seed           INTEGER NOT NULL,

    CONSTRAINT fk_member_group       FOREIGN KEY (group_id) REFERENCES tournament_group (id) ON DELETE CASCADE,
    CONSTRAINT fk_member_participant FOREIGN KEY (participant_id) REFERENCES tournament_participant (id) ON DELETE CASCADE,
    CONSTRAINT uq_group_member       UNIQUE (group_id, participant_id)
);

COMMENT ON COLUMN tournament_group_member.seed IS 'Poziția jucătorului în grupă (1 = primul)';


ALTER TABLE tournament_match
    ADD COLUMN group_id BIGINT,
    ADD CONSTRAINT fk_match_group FOREIGN KEY (group_id) REFERENCES tournament_group (id) ON DELETE CASCADE;

COMMENT ON COLUMN tournament_match.group_id IS 'Grupa în care se joacă meciul (gol la turneele Round Robin simple)';
