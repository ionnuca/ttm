-- =====================================================================
-- V12: utilizatorii de încredere se înscriu la turnee fără confirmarea unui organizator
-- =====================================================================

ALTER TABLE app_user
    ADD COLUMN trusted BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN app_user.trusted IS 'Înscrierea la turnee nu are nevoie de confirmare (setat de administrator)';
