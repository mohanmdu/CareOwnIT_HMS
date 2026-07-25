-- Patient photo, separate from Admission.photoPath (which only exists once a
-- patient is admitted) - shown on the new Patient Information (OP/IP) screen
-- even for OP-only patients who have never had an IP admission.
ALTER TABLE patient ADD COLUMN photo_path VARCHAR(255) NULL;
