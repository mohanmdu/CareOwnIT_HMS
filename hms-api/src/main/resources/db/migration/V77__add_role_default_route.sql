-- Where a user with this role lands right after login, instead of the
-- default '/dashboard' (e.g. a booking-only "Patient" role should land on
-- the booking screen). Nullable - existing roles keep today's dashboard
-- landing behavior unchanged.
ALTER TABLE role ADD COLUMN default_route VARCHAR(255) NULL;
