package com.cancerweb.backend.backup;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpStatus;

/**
 * The encrypted backup file: {@code MAGIC | version | IV | AES-256-CTR(gzip(lines)) | HMAC-SHA256}. The tag covers
 * everything before it (encrypt-then-MAC), so a changed or truncated file is refused before anything is decrypted.
 * Encryption and MAC keys are derived from the one secret in BACKUP_ENCRYPTION_KEY (32 random bytes, base64), which
 * is never stored with the backups.
 */
final class BackupCrypto {

    static final byte[] MAGIC = "BCBACKUP".getBytes(StandardCharsets.US_ASCII);
    static final byte VERSION = 1;
    static final int IV_LENGTH = 16;
    static final int TAG_LENGTH = 32;
    static final int HEADER_LENGTH = MAGIC.length + 1 + IV_LENGTH;

    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec macKey;

    private BackupCrypto(byte[] master) {
        this.encryptionKey = new SecretKeySpec(derive(master, "breastcare-backup-encryption"), "AES");
        this.macKey = new SecretKeySpec(derive(master, "breastcare-backup-mac"), "HmacSHA256");
    }

    /** Null when no key is configured; throws if one is configured but is not 32 bytes of base64. */
    static BackupCrypto fromBase64(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        byte[] master;
        try {
            master = Base64.getDecoder().decode(key.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("BACKUP_ENCRYPTION_KEY is not valid base64");
        }
        if (master.length != 32) {
            throw new IllegalStateException("BACKUP_ENCRYPTION_KEY must be 32 random bytes, base64-encoded");
        }
        return new BackupCrypto(master);
    }

    private static byte[] derive(byte[] master, String purpose) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(master, "HmacSHA256"));
            return mac.doFinal(purpose.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Writing side: plaintext goes into {@link #plaintext()}; {@link #finish()} writes the tag. */
    final class Writer implements AutoCloseable {
        private final OutputStream file;
        private final Mac mac;
        private final MessageDigest sha256;
        private final CipherOutputStream cipher;
        private boolean finished;

        Writer(OutputStream file) throws IOException {
            try {
                this.file = file;
                this.mac = Mac.getInstance("HmacSHA256");
                mac.init(macKey);
                this.sha256 = MessageDigest.getInstance("SHA-256");
                byte[] iv = new byte[IV_LENGTH];
                new SecureRandom().nextBytes(iv);
                OutputStream tee = new FilterOutputStream(file) {
                    @Override
                    public void write(int b) throws IOException {
                        mac.update((byte) b);
                        sha256.update((byte) b);
                        out.write(b);
                    }

                    @Override
                    public void write(byte[] b, int off, int len) throws IOException {
                        mac.update(b, off, len);
                        sha256.update(b, off, len);
                        out.write(b, off, len);
                    }

                    @Override
                    public void close() throws IOException {
                        flush(); // the file stays open for the tag
                    }
                };
                tee.write(MAGIC);
                tee.write(VERSION);
                tee.write(iv);
                Cipher c = Cipher.getInstance("AES/CTR/NoPadding");
                c.init(Cipher.ENCRYPT_MODE, encryptionKey, new IvParameterSpec(iv));
                this.cipher = new CipherOutputStream(tee, c);
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException(e);
            }
        }

        OutputStream plaintext() {
            return cipher;
        }

        /** Closes the encrypted part, appends the tag, and returns the SHA-256 of the whole file (hex). */
        String finish() throws IOException {
            cipher.close();
            byte[] tag = mac.doFinal();
            sha256.update(tag);
            file.write(tag);
            file.flush();
            finished = true;
            return java.util.HexFormat.of().formatHex(sha256.digest());
        }

        @Override
        public void close() throws IOException {
            if (!finished) {
                cipher.close();
            }
            file.close();
        }
    }

    Writer writer(OutputStream file) throws IOException {
        return new Writer(file);
    }

    /** Checks the header and the tag over the whole file; throws if the file was changed, cut short or is foreign. */
    void verify(Path path) throws IOException {
        long size = Files.size(path);
        if (size < HEADER_LENGTH + TAG_LENGTH) {
            throw new BackupException(HttpStatus.UNPROCESSABLE_CONTENT, "The backup file is too short to be valid.");
        }
        try (InputStream in = Files.newInputStream(path)) {
            byte[] header = in.readNBytes(MAGIC.length + 1);
            if (!Arrays.equals(Arrays.copyOf(header, MAGIC.length), MAGIC) || header[MAGIC.length] != VERSION) {
                throw new BackupException(HttpStatus.UNPROCESSABLE_CONTENT, "This is not a BreastCare backup file.");
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(macKey);
            mac.update(header);
            long remaining = size - header.length - TAG_LENGTH;
            byte[] buffer = new byte[64 * 1024];
            while (remaining > 0) {
                int n = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (n < 0) {
                    throw new BackupException(HttpStatus.UNPROCESSABLE_CONTENT, "The backup file is cut short.");
                }
                mac.update(buffer, 0, n);
                remaining -= n;
            }
            byte[] tag = in.readNBytes(TAG_LENGTH);
            if (!MessageDigest.isEqual(tag, mac.doFinal())) {
                throw new BackupException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "The backup failed its integrity check: it was changed, damaged, or made with another key.");
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The decrypted contents of a file already checked with {@link #verify}. */
    InputStream decrypt(Path path) throws IOException {
        long size = Files.size(path);
        InputStream in = Files.newInputStream(path);
        in.skipNBytes(MAGIC.length + 1);
        byte[] iv = in.readNBytes(IV_LENGTH);
        InputStream body = new BoundedInputStream(in, size - HEADER_LENGTH - TAG_LENGTH);
        try {
            Cipher c = Cipher.getInstance("AES/CTR/NoPadding");
            c.init(Cipher.DECRYPT_MODE, encryptionKey, new IvParameterSpec(iv));
            return new CipherInputStream(body, c);
        } catch (GeneralSecurityException e) {
            in.close();
            throw new IllegalStateException(e);
        }
    }

    /** Reads at most {@code limit} bytes, so the tag after the ciphertext is never fed to the cipher. */
    private static final class BoundedInputStream extends InputStream {
        private final InputStream in;
        private long remaining;

        BoundedInputStream(InputStream in, long limit) {
            this.in = in;
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int b = in.read();
            if (b >= 0) {
                remaining--;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int n = in.read(b, off, (int) Math.min(len, remaining));
            if (n > 0) {
                remaining -= n;
            }
            return n;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }
}
