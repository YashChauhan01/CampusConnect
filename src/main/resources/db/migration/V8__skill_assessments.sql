-- Result of an assessment: the level the student demonstrated (never above the claim).
ALTER TABLE student_skills
    ADD COLUMN verified_level VARCHAR(20) CHECK (verified_level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED'));
ALTER TABLE student_subjects
    ADD COLUMN verified_level VARCHAR(20) CHECK (verified_level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED'));

CREATE TABLE skill_assessments (
    id             UUID PRIMARY KEY,
    student_id     UUID         NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    kind           VARCHAR(10)  NOT NULL CHECK (kind IN ('SKILL', 'SUBJECT')),
    item_id        BIGINT       NOT NULL,
    topic          VARCHAR(100) NOT NULL,
    claimed_level  VARCHAR(20)  NOT NULL,
    questions      TEXT         NOT NULL,
    status         VARCHAR(10)  NOT NULL CHECK (status IN ('PENDING', 'GRADED')),
    score          INTEGER,
    verified_level VARCHAR(20),
    feedback       VARCHAR(1000),
    created_at     TIMESTAMPTZ  NOT NULL,
    graded_at      TIMESTAMPTZ
);
CREATE INDEX idx_assessments_student ON skill_assessments (student_id, created_at DESC);
