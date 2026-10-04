-- Zones get planar coordinates (metres) so matching can reward physical proximity.
ALTER TABLE campus_zones ADD COLUMN x_m DOUBLE PRECISION;
ALTER TABLE campus_zones ADD COLUMN y_m DOUBLE PRECISION;

-- Demonstration layout; replace with the real campus map.
UPDATE campus_zones SET x_m = 0,   y_m = 0   WHERE name = 'Library';
UPDATE campus_zones SET x_m = 80,  y_m = 40  WHERE name = 'Computer Lab';
UPDATE campus_zones SET x_m = 200, y_m = -60 WHERE name = 'Cafeteria';
UPDATE campus_zones SET x_m = 40,  y_m = -30 WHERE name = 'Study Hall';

-- What a student wants from this session: free-text requirement and the subjects to work on.
ALTER TABLE presence ADD COLUMN requirements VARCHAR(300);

CREATE TABLE presence_seeking (
    student_id UUID   NOT NULL REFERENCES presence (student_id) ON DELETE CASCADE,
    subject_id BIGINT NOT NULL REFERENCES subjects (id),
    PRIMARY KEY (student_id, subject_id)
);
