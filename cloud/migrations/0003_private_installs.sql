-- Only hashes of the temporary download capability and one-use enrollment are stored.
CREATE TABLE IF NOT EXISTS private_installs (
 hash TEXT PRIMARY KEY,
 enrollment TEXT NOT NULL REFERENCES passkey_enrollments(hash),
 expires INTEGER NOT NULL
);
