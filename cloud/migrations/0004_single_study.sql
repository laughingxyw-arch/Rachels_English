-- A single fixed profile; historical account identifiers stay in the database.
CREATE TABLE IF NOT EXISTS single_study_profile (
 id INTEGER PRIMARY KEY CHECK(id=1),
 account TEXT NOT NULL REFERENCES accounts(id)
);
