-- =====================================================================
-- V2: mâna de joc și echipamentul jucătorului
-- Toate câmpurile sunt opționale (pentru jucătorii existenți rămân necompletate).
-- =====================================================================

ALTER TABLE player
    ADD COLUMN play_hand        VARCHAR(10),
    ADD COLUMN blade            VARCHAR(100),
    ADD COLUMN forehand_rubber  VARCHAR(100),
    ADD COLUMN backhand_rubber  VARCHAR(100),
    ADD CONSTRAINT ck_player_play_hand CHECK (play_hand IN ('RIGHT', 'LEFT'));

COMMENT ON COLUMN player.play_hand       IS 'Mâna de joc: RIGHT (dreapta) sau LEFT (stânga)';
COMMENT ON COLUMN player.blade           IS 'Lemnul paletei, de exemplu "Butterfly Viscaria"';
COMMENT ON COLUMN player.forehand_rubber IS 'Fața de pe forehand, de exemplu "Tenergy 05"';
COMMENT ON COLUMN player.backhand_rubber IS 'Fața de pe backhand';
