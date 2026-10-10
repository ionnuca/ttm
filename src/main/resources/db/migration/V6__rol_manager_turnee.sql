-- =====================================================================
-- V6: rolul „Manager de turnee”
-- Poate crea, porni și conduce turneele (inclusiv corectarea rezultatelor după încheiere),
-- fără acces la utilizatori și la datele de contact ale jucătorilor.
-- =====================================================================

ALTER TABLE app_user DROP CONSTRAINT ck_app_user_role;
ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_role CHECK (role IN ('USER', 'TOURNAMENT_MANAGER', 'ADMIN'));

COMMENT ON COLUMN app_user.role IS 'USER (utilizator logat), TOURNAMENT_MANAGER (manager de turnee) sau ADMIN (administrator)';
