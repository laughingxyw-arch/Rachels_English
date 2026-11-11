CREATE TABLE IF NOT EXISTS passkey_profiles (
 account TEXT PRIMARY KEY REFERENCES accounts(id), mode TEXT NOT NULL CHECK(mode IN ('owner','probe'))
);
CREATE TABLE IF NOT EXISTS passkey_enrollments (
 hash TEXT PRIMARY KEY, account TEXT NOT NULL REFERENCES passkey_profiles(account), expires INTEGER NOT NULL, used INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS passkeys (
 id TEXT PRIMARY KEY, account TEXT NOT NULL REFERENCES passkey_profiles(account), public_key TEXT NOT NULL,
 counter INTEGER NOT NULL, transports TEXT NOT NULL, created INTEGER NOT NULL, last_used INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS passkeys_account ON passkeys(account);
CREATE TABLE IF NOT EXISTS passkey_challenges (
 id TEXT PRIMARY KEY, challenge TEXT NOT NULL, purpose TEXT NOT NULL, account TEXT, mode TEXT NOT NULL,
 enrollment TEXT, expires INTEGER NOT NULL, consumed INTEGER NOT NULL DEFAULT 0, claim TEXT
);
CREATE INDEX IF NOT EXISTS passkey_challenges_expiry ON passkey_challenges(expires);
CREATE TABLE IF NOT EXISTS study_sessions (
 hash TEXT PRIMARY KEY, account TEXT NOT NULL REFERENCES passkey_profiles(account), expires INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS study_sessions_expiry ON study_sessions(expires);
-- Private aliases preserve uploads from the owner's earlier installation.
CREATE TABLE IF NOT EXISTS study_aliases (
 source TEXT PRIMARY KEY REFERENCES accounts(id), target TEXT NOT NULL REFERENCES passkey_profiles(account)
);
