-- Custom public-website domain per client (e.g. clienta-hospital.com), used
-- by DomainTenantResolutionFilter to route an unauthenticated public-site
-- request to the right tenant database from its Host header alone - without
-- this, every public request (no JWT, so no clientId claim to read) falls
-- back to whichever client is coded DEFAULT (see TenantDataSourceRegistry.
-- defaultClientId()), so every client's public website showed the same
-- (DEFAULT's) content regardless of which domain the visitor actually hit.
ALTER TABLE client ADD COLUMN domain VARCHAR(255) NULL UNIQUE;
