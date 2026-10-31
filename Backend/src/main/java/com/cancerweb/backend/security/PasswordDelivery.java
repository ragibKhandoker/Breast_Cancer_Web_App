package com.cancerweb.backend.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * The secrets behind "Forgot password?" requests:
 * <ul>
 * <li>request codes: random, shown to the visitor once; only their SHA-256 hash is stored;</li>
 * <li>references: a short label derived from a code, for reading out on the phone;</li>
 * <li>sealing a temporary password for the status page with AES-256-GCM, under a key derived from the server's
 * JWT secret, so the database never holds it readable and the admin pages cannot show it.</li>
 * </ul>
 */
@Component
public class PasswordDelivery {

    /** No look-alikes (0/O, 1/I/L). */
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public PasswordDelivery(AuthProperties auth) {
        this.key = new SecretKeySpec(sha256(concat(auth.secretBytes(), "|password-delivery".getBytes(StandardCharsets.UTF_8))), "AES");
    }

    /** "ABCDE-FGHJK-MNPQR-STUVW": 20 random characters, about 99 bits. */
    public String newCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            if (i > 0 && i % 5 == 0) {
                code.append('-');
            }
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** Upper case without spaces or dashes, so a code typed by hand still matches. */
    public static String normalise(String code) {
        return code == null ? "" : code.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    public String hash(String code) {
        return HexFormat.of().formatHex(sha256(("id|" + normalise(code)).getBytes(StandardCharsets.UTF_8)));
    }

    /** "PH-7KQ2M9": the same for the same code, and nothing about the code can be worked out from it. */
    public String reference(String code) {
        byte[] digest = sha256(("ref|" + normalise(code)).getBytes(StandardCharsets.UTF_8));
        StringBuilder ref = new StringBuilder("PH-");
        for (int i = 0; i < 6; i++) {
            ref.append(ALPHABET.charAt((digest[i] & 0xFF) % ALPHABET.length()));
        }
        return ref.toString();
    }

    public String seal(String plain) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(concat(iv, sealed));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not seal the temporary password", e);
        }
    }

    public String open(String sealed) {
        try {
            byte[] all = Base64.getDecoder().decode(sealed);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, 12));
            return new String(cipher.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not open the temporary password", e);
        }
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
