-- Optional evidence reference for the existing medical-leave review requirement.
ALTER TABLE workforce_attendance_exceptions ADD COLUMN medical_reference varchar(500);
ALTER TABLE workforce_attendance_exceptions ADD CONSTRAINT workforce_interval_medical_reference_check
 CHECK (medical_reference IS NULL OR (kind = 'leave' AND length(btrim(medical_reference)) BETWEEN 1 AND 500));
