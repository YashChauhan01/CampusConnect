-- One row per matching run; doubles as the data set for performance evaluation.
CREATE TABLE match_rounds (
    id           UUID PRIMARY KEY,
    started_at   TIMESTAMPTZ      NOT NULL,
    pool_size    INTEGER          NOT NULL,
    edge_count   INTEGER          NOT NULL,
    pair_count   INTEGER          NOT NULL,
    total_weight DOUBLE PRECISION NOT NULL,
    duration_ms  DOUBLE PRECISION NOT NULL
);

CREATE TABLE matches (
    id          UUID PRIMARY KEY,
    round_id    UUID             NOT NULL REFERENCES match_rounds (id),
    student_a   UUID             NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    student_b   UUID             NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    status      VARCHAR(20)      NOT NULL CHECK (status IN ('PROPOSED', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'COMPLETED')),
    weight      DOUBLE PRECISION NOT NULL,
    knowledge   DOUBLE PRECISION NOT NULL,
    reciprocity DOUBLE PRECISION NOT NULL,
    breadth     DOUBLE PRECISION NOT NULL,
    proximity   DOUBLE PRECISION NOT NULL,
    accepted_a  BOOLEAN          NOT NULL DEFAULT FALSE,
    accepted_b  BOOLEAN          NOT NULL DEFAULT FALSE,
    declined_by UUID,
    created_at  TIMESTAMPTZ      NOT NULL,
    expires_at  TIMESTAMPTZ      NOT NULL,
    decided_at  TIMESTAMPTZ,
    CHECK (student_a <> student_b)
);

-- A student can be part of at most one live match at a time.
CREATE UNIQUE INDEX uq_match_live_a ON matches (student_a) WHERE status IN ('PROPOSED', 'ACCEPTED');
CREATE UNIQUE INDEX uq_match_live_b ON matches (student_b) WHERE status IN ('PROPOSED', 'ACCEPTED');
CREATE INDEX idx_matches_a ON matches (student_a, created_at DESC);
CREATE INDEX idx_matches_b ON matches (student_b, created_at DESC);
CREATE INDEX idx_matches_status_expiry ON matches (status, expires_at);

CREATE TABLE match_subjects (
    match_id   UUID   NOT NULL REFERENCES matches (id) ON DELETE CASCADE,
    subject_id BIGINT NOT NULL REFERENCES subjects (id),
    PRIMARY KEY (match_id, subject_id)
);
