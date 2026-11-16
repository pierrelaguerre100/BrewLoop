package com.doubleshotdevs.brewloop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Tests the sign-in and registration input checks.
 */
public class CredentialValidatorTest {

    @Test
    public void name_blankOrMissing_isRequired() {
        assertEquals(CredentialValidator.Error.NAME_REQUIRED, CredentialValidator.validateName(null));
        assertEquals(CredentialValidator.Error.NAME_REQUIRED, CredentialValidator.validateName("   "));
    }

    @Test
    public void name_overLimit_isTooLong() {
        StringBuilder name = new StringBuilder();
        for (int i = 0; i <= CredentialValidator.MAX_NAME_LENGTH; i++) {
            name.append('a');
        }
        assertEquals(CredentialValidator.Error.NAME_TOO_LONG,
                CredentialValidator.validateName(name.toString()));
    }

    @Test
    public void name_normal_isAccepted() {
        assertNull(CredentialValidator.validateName("  Sam Rivera "));
    }

    @Test
    public void email_checks() {
        assertEquals(CredentialValidator.Error.EMAIL_REQUIRED, CredentialValidator.validateEmail(""));
        assertEquals(CredentialValidator.Error.EMAIL_INVALID, CredentialValidator.validateEmail("sam"));
        assertEquals(CredentialValidator.Error.EMAIL_INVALID, CredentialValidator.validateEmail("sam@host"));
        assertEquals(CredentialValidator.Error.EMAIL_INVALID, CredentialValidator.validateEmail("s am@host.com"));
        assertEquals(CredentialValidator.Error.EMAIL_INVALID, CredentialValidator.validateEmail("a@@b.com"));
        assertNull(CredentialValidator.validateEmail(" sam@example.com "));
    }

    @Test
    public void signInPassword_onlyRequiresAValue() {
        assertEquals(CredentialValidator.Error.PASSWORD_REQUIRED,
                CredentialValidator.validateSignInPassword(""));
        assertNull(CredentialValidator.validateSignInPassword("abc"));
    }

    @Test
    public void newPassword_checks() {
        assertEquals(CredentialValidator.Error.PASSWORD_REQUIRED,
                CredentialValidator.validateNewPassword(null, null));
        assertEquals(CredentialValidator.Error.PASSWORD_TOO_SHORT,
                CredentialValidator.validateNewPassword("12345", "12345"));
        assertEquals(CredentialValidator.Error.PASSWORDS_DO_NOT_MATCH,
                CredentialValidator.validateNewPassword("123456", "123457"));
        assertNull(CredentialValidator.validateNewPassword("123456", "123456"));
    }

    @Test
    public void normalizeEmail_trimsSpaces() {
        assertEquals("sam@example.com", CredentialValidator.normalizeEmail("  sam@example.com\n"));
    }
}
