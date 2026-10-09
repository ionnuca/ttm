-- =====================================================================
-- Crearea manuală a bazei de date, pentru un PostgreSQL instalat fără Docker.
-- Rulați ca superuser, de exemplu:
--     psql -U postgres -f database/create_database.sql
--
-- Tabelele NU se creează aici: le creează aplicația la prima pornire, prin Flyway,
-- din scripturile src/main/resources/db/migration/V*.sql.
-- Schimbați parola pentru orice mediu în afară de cel local.
-- =====================================================================

CREATE ROLE ttm WITH LOGIN PASSWORD 'ttm';

CREATE DATABASE ttm      OWNER ttm ENCODING 'UTF8';
CREATE DATABASE ttm_test OWNER ttm ENCODING 'UTF8';
