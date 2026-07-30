-- Add-on toggle: some hospital clients want the Doctor Queue Management
-- module (Reception Check-In, Doctor Dashboard), some don't. Defaults to
-- TRUE so this deployment (already using the feature) sees no behavior
-- change; a fresh client can switch it off from Clinic Settings.
ALTER TABLE clinic_settings ADD COLUMN doctor_queue_enabled BOOLEAN NOT NULL DEFAULT TRUE;
