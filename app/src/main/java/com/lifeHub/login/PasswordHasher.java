package com.lifeHub.login;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Local profile password verification only; not a server authentication system. */
public final class PasswordHasher {
    // SHA-1 PBKDF2 remains available on minSdk 24; work factor compensates for its speed.
    private static final int ITERATIONS = 1_300_000;
    private static final String PREFIX = "pbkdf2-sha1";
    private PasswordHasher() {}

    public static String hash(String password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return PREFIX + "$" + ITERATIONS + "$" + hex(salt) + "$" + hex(derive(password, salt));
    }

    public static boolean verify(String password, String encoded) {
        if (encoded == null || password == null || password.length() > 1024) return false;
        String[] fields = encoded.split("\\$", -1);
        if (fields.length != 4 || !PREFIX.equals(fields[0]) || !Integer.toString(ITERATIONS).equals(fields[1])) return false;
        try {
            byte[] salt = unhex(fields[2], 16);
            byte[] expected = unhex(fields[3], 32);
            return MessageDigest.isEqual(expected, derive(password, salt));
        } catch (IllegalArgumentException error) { return false; }
    }

    private static byte[] derive(String password, byte[] salt) {
        if (password == null || password.length() > 1024) throw new IllegalArgumentException("Invalid password length");
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Password hashing unavailable", error);
        } finally { spec.clearPassword(); }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }

    private static byte[] unhex(String value, int bytes) {
        if (value.length() != bytes * 2 || !value.matches("[0-9a-f]+")) throw new IllegalArgumentException();
        byte[] result = new byte[bytes];
        for (int i = 0; i < bytes; i++) result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        return result;
    }
}
