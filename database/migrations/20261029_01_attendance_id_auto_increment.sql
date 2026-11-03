-- Align the existing MySQL attendance table with
-- com.varista.entity.Attendance's GenerationType.IDENTITY mapping.
ALTER TABLE attendance
    MODIFY COLUMN attendance_id BIGINT NOT NULL AUTO_INCREMENT;
