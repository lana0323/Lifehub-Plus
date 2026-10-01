package com.lifeHub.login;

import org.junit.Test;
import static org.junit.Assert.*;

public class PasswordHasherTest {
    @Test public void saltsAndVerifiesWithoutStoringPlaintext() {
        String first = PasswordHasher.hash("demo-passphrase");
        String second = PasswordHasher.hash("demo-passphrase");
        assertNotEquals(first, second);
        assertFalse(first.contains("demo-passphrase"));
        assertTrue(PasswordHasher.verify("demo-passphrase", first));
        assertFalse(PasswordHasher.verify("wrong", first));
        assertFalse(PasswordHasher.verify("demo-passphrase", "invalid"));
    }
}
