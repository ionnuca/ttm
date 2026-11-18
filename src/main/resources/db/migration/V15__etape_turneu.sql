-- =====================================================================
-- V15: numărul de etape se alege la începerea turneului
--   O etapă: ROUND_ROBIN sau KNOCKOUT (tablou cu toți jucătorii, olimpic sau cu ramura de jos).
--   Două etape: GROUPS până la începerea etapei 2, când devine GROUPS_FINALS sau GROUPS_KNOCKOUT.
-- =====================================================================

ALTER TABLE tournament DROP CONSTRAINT ck_tournament_format;
ALTER TABLE tournament
    ADD CONSTRAINT ck_tournament_format
        CHECK (format IN ('ROUND_ROBIN', 'KNOCKOUT', 'GROUPS', 'GROUPS_FINALS', 'GROUPS_KNOCKOUT'));

COMMENT ON COLUMN tournament.knockout_type IS 'La tablou eliminatoriu (KNOCKOUT sau GROUPS_KNOCKOUT): SINGLE (olimpic) sau DOUBLE (cu ramura de jos)';
