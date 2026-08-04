package com.pms.tenant.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Envelope encryption for tenant database passwords (ClientDatabase.
 * encryptedPassword) - see the "Secrets decision" in the Database-per-
 * Client Architecture plan. One root AES-256 key held only in the
 * deployment's environment (TENANT_DB_ENCRYPTION_KEY, same handling rigor
 * as app.security.jwt-secret - never in git, never logged), used to
 * encrypt/decrypt the many per-tenant passwords stored in the master
 * database. A tenant's plaintext password only ever exists in memory for
 * the instant a connection pool is built from it (see
 * TenantDataSourceRegistry) - never persisted or logged in plaintext.
 *
 * Format: AES/GCM/NoPadding, a fresh random 12-byte IV per encryption
 * (GCM's standard IV length), packed as IV || ciphertext(+16-byte tag) into
 * one byte array - self-contained, no separate IV column needed.
 */
@Service
public class TenantSecretService {

    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public TenantSecretService(@Value("${app.tenant-db.encryption-key}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "app.tenant-db.encryption-key must decode to exactly 32 bytes (AES-256) - got " + keyBytes.length);
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public byte[] encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(ciphertext, 0, packed, iv.length, ciphertext.length);
            return packed;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt tenant database secret", e);
        }
    }

    public String decrypt(byte[] packed) {
        try {
            byte[] iv = Arrays.copyOfRange(packed, 0, GCM_IV_LENGTH_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(packed, GCM_IV_LENGTH_BYTES, packed.length);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt tenant database secret", e);
        }
    }
}
