-- =====================================================================
-- V8: varianta mare a pozei de profil, pentru vizualizare
-- content = miniatura pătrată (400 x 400); full_content = poza întreagă, latura mare de cel mult 1600 px.
-- Pozele încărcate înainte de V8 nu au varianta mare: se afișează miniatura.
-- =====================================================================

ALTER TABLE player_photo
    ADD COLUMN full_content BYTEA;

COMMENT ON COLUMN player_photo.full_content IS 'Poza întreagă (JPEG, latura mare de cel mult 1600 px), pentru vizualizarea mărită';
