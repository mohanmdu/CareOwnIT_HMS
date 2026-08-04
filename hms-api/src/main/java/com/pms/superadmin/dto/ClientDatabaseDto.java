package com.pms.superadmin.dto;

// Deliberately no username/password fields at all - see ClientDatabase.
// encryptedPassword's own doc comment. This is a status projection for the
// Super Admin Clients screen, not a connection-details export.
public record ClientDatabaseDto(Long clientId, String host, int port, String schemaName, String status, String schemaVersion) {
}
