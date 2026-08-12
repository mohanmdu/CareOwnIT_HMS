package com.pms.superadmin.backup.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * AES-256-GCM encryption for backup dump files - same scheme as
 * com.pms.tenant.service.TenantSecretService (AES/GCM/NoPadding, fresh
 * random 12-byte IV per encryption, IV packed at the front of the output),
 * but a deliberately separate key (app.backup.encryption-key) and,
 * critically, stream-based rather than whole-file-in-memory: a multi-
 * gigabyte tenant dump must never be pulled entirely into a byte[] just to
 * encrypt it (see the "handle large databases efficiently" requirement).
 *
 * File format: 12-byte IV, then the GCM ciphertext (with its 16-byte
 * authentication tag appended, standard for this transformation) -
 * self-contained, no separate IV file/column needed.
 */
@Service
public class BackupEncryptionService {

    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int STREAM_BUFFER_SIZE = 64 * 1024;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public BackupEncryptionService(@Value("${app.backup.encryption-key}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalStateException("app.backup.encryption-key must decode to exactly 32 bytes (AES-256) - got " + keyBytes.length);
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public void encryptFile(Path plainFile, Path encryptedFile) {
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        random.nextBytes(iv);
        try (InputStream in = Files.newInputStream(plainFile);
                OutputStream rawOut = Files.newOutputStream(encryptedFile)) {
            rawOut.write(iv);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            try (CipherOutputStream cipherOut = new CipherOutputStream(rawOut, cipher)) {
                in.transferTo(cipherOut);
            }
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt backup file " + plainFile, e);
        }
    }

    public void decryptFile(Path encryptedFile, Path plainFile) {
        try (InputStream rawIn = Files.newInputStream(encryptedFile)) {
            byte[] iv = rawIn.readNBytes(GCM_IV_LENGTH_BYTES);
            if (iv.length != GCM_IV_LENGTH_BYTES) {
                throw new IllegalStateException("Encrypted backup file is truncated (missing IV): " + encryptedFile);
            }
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            try (CipherInputStream cipherIn = new CipherInputStream(rawIn, cipher);
                    OutputStream out = Files.newOutputStream(plainFile)) {
                byte[] buffer = new byte[STREAM_BUFFER_SIZE];
                int read;
                while ((read = cipherIn.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
        } catch (IOException | GeneralSecurityException e) {
            // GCM's authentication tag check fails here as an AEADBadTagException
            // (a GeneralSecurityException) if the file was corrupted or tampered
            // with - decryption failing IS the integrity check for a downloaded/
            // restored file, not a separate step.
            throw new IllegalStateException(
                    "Failed to decrypt backup file " + encryptedFile + " - it may be corrupted or tampered with", e);
        }
    }
}
