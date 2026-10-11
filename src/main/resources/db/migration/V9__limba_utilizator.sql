-- =====================================================================
-- V9: limba interfeței, aleasă de fiecare utilizator (ro / ru / en)
-- =====================================================================

ALTER TABLE app_user
    ADD COLUMN language VARCHAR(2) NOT NULL DEFAULT 'ro',
    ADD CONSTRAINT ck_app_user_language CHECK (language IN ('ro', 'ru', 'en'));

COMMENT ON COLUMN app_user.language IS 'Limba interfeței: ro (română), ru (rusă), en (engleză)';
