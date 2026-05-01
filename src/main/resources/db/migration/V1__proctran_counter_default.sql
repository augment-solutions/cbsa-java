CREATE SEQUENCE IF NOT EXISTS proctran_counter_seq;

ALTER TABLE proctran
    ALTER COLUMN counter SET DEFAULT nextval('proctran_counter_seq');
