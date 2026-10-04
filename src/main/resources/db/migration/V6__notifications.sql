CREATE TABLE notifications (
    id         UUID PRIMARY KEY,
    student_id UUID         NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    type       VARCHAR(40)  NOT NULL,
    title      VARCHAR(160) NOT NULL,
    body       VARCHAR(500) NOT NULL,
    link       VARCHAR(200),
    created_at TIMESTAMPTZ  NOT NULL,
    read_at    TIMESTAMPTZ
);
CREATE INDEX idx_notifications_student ON notifications (student_id, created_at DESC);
CREATE INDEX idx_notifications_unread ON notifications (student_id) WHERE read_at IS NULL;
