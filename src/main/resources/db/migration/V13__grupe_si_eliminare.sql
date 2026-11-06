-- =====================================================================
-- V13: turneul „Grupe + eliminare”
--   Etapa 1: una sau mai multe grupe Round Robin.
--   Etapa 2: tablou eliminatoriu (KNOCKOUT): olimpic (SINGLE) sau cu ramura de jos (DOUBLE).
-- =====================================================================

ALTER TABLE tournament DROP CONSTRAINT ck_tournament_format;
ALTER TABLE tournament DROP CONSTRAINT ck_tournament_stage;
ALTER TABLE tournament DROP CONSTRAINT ck_tournament_group_count;
ALTER TABLE tournament
    ADD CONSTRAINT ck_tournament_format CHECK (format IN ('ROUND_ROBIN', 'GROUPS_FINALS', 'GROUPS_KNOCKOUT')),
    ADD CONSTRAINT ck_tournament_stage CHECK (stage IS NULL OR stage IN ('GROUPS', 'FINALS', 'KNOCKOUT')),
    ADD CONSTRAINT ck_tournament_group_count CHECK (group_count IS NULL OR group_count >= 1),
    ADD COLUMN knockout_type VARCHAR(20),
    ADD CONSTRAINT ck_tournament_knockout_type CHECK (knockout_type IS NULL OR knockout_type IN ('SINGLE', 'DOUBLE'));

COMMENT ON COLUMN tournament.knockout_type IS 'La „Grupe + eliminare”: SINGLE (olimpic) sau DOUBLE (cu ramura de jos)';

ALTER TABLE tournament_group DROP CONSTRAINT ck_group_stage;
ALTER TABLE tournament_group
    ADD CONSTRAINT ck_group_stage CHECK (stage IN ('GROUPS', 'FINALS', 'KNOCKOUT'));

ALTER TABLE tournament_match
    ADD COLUMN bracket_slot VARCHAR(10);

COMMENT ON COLUMN tournament_match.bracket_slot IS 'Locul meciului în tabloul eliminatoriu: W<tur>-<nr>, L<tur>-<nr>, GF, GF2';
