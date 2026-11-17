-- =====================================================================
-- V14: numărul de finale e configurabil la „Grupe + finale”
--   Finala k primește locurile (k-1)·q+1 … k·q din fiecare grupă (q = qualifiers_per_group),
--   iar ultima finală pe toți cei rămași. Organizatorul alege la ce finale câștigătorul primește cât taxa.
-- =====================================================================

ALTER TABLE tournament
    ADD COLUMN final_count INTEGER,
    ADD CONSTRAINT ck_tournament_final_count CHECK (final_count IS NULL OR final_count >= 2);

COMMENT ON COLUMN tournament.final_count          IS 'La „Grupe + finale”: câte finale are etapa 2 (ultima primește jucătorii rămași)';
COMMENT ON COLUMN tournament.qualifiers_per_group IS 'Câți jucători din fiecare grupă trec în Finala 1 (la mai multe finale: în fiecare finală, în afară de ultima)';

ALTER TABLE tournament_group
    ADD COLUMN fee_refund BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN tournament_group.fee_refund IS 'La turneele comerciale: câștigătorul acestei finale primește cât taxa de participare';

-- până acum etapa 2 avea mereu două finale, iar câștigătorul Finalei 2 primea taxa
UPDATE tournament SET final_count = 2
WHERE id IN (SELECT tournament_id FROM tournament_group WHERE stage = 'FINALS');

UPDATE tournament_group g SET fee_refund = TRUE
FROM tournament t
WHERE t.id = g.tournament_id AND t.commercial AND g.stage = 'FINALS' AND g.position = 2;
