-- Rulat automat de containerul PostgreSQL din docker-compose.yml, la prima pornire.
-- Creează baza separată folosită de testele automate (mvn test).
CREATE DATABASE ttm_test OWNER ttm ENCODING 'UTF8';
