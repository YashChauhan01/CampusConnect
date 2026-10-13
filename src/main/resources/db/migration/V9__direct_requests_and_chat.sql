-- Direct study requests: a student can ask a recommended classmate to study together. Such a match is not created
-- by a matching round, and records who sent it (the sender has already accepted).
ALTER TABLE matches ALTER COLUMN round_id DROP NOT NULL;
ALTER TABLE matches ADD COLUMN requested_by UUID REFERENCES students (id) ON DELETE SET NULL;

-- Chat between the two students of a confirmed match, so they can agree where and when to meet.
CREATE TABLE match_messages (
    id         UUID PRIMARY KEY,
    match_id   UUID          NOT NULL REFERENCES matches (id) ON DELETE CASCADE,
    sender_id  UUID          NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    body       VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL
);

CREATE INDEX match_messages_by_match ON match_messages (match_id, created_at);
