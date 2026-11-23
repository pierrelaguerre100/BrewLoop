package com.doubleshotdevs.brewloop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Tests the registration and sign-in error messages and the diagnostic codes that are
 * written to the log.
 */
public class AuthErrorMessagesTest {

    @Test
    public void disabledEmailSignIn_getsAnActionableMessage() {
        assertEquals(R.string.error_auth_not_enabled,
                AuthErrorMessages.forErrorCode("ERROR_OPERATION_NOT_ALLOWED"));
        assertEquals(R.string.error_auth_not_enabled,
                AuthErrorMessages.forErrorCode("CONFIGURATION_NOT_FOUND"));
    }

    @Test
    public void serverCodeInMessage_isRecognized() {
        Exception error = new Exception(
                "An internal error has occurred. [ CONFIGURATION_NOT_FOUND ]");
        assertEquals(R.string.error_auth_not_enabled, AuthErrorMessages.forException(error));
        assertEquals("Exception/CONFIGURATION_NOT_FOUND",
                AuthErrorMessages.diagnosticCode(error));
    }

    @Test
    public void diagnosticCode_neverIncludesTheMessageText() {
        Exception error = new Exception(
                "Failed for someone@example.com with secret-password");
        String code = AuthErrorMessages.diagnosticCode(error);
        assertEquals("Exception", code);
        assertFalse(code.contains("@"));
        assertEquals("unknown", AuthErrorMessages.diagnosticCode(null));
    }

    @Test
    public void bracketedCode_ignoresLowercaseText() {
        assertNull(AuthErrorMessages.bracketedCode("Sign-in failed [ someone@example.com ]"));
        assertNull(AuthErrorMessages.bracketedCode(null));
        assertEquals("OPERATION_NOT_ALLOWED",
                AuthErrorMessages.bracketedCode("Error [OPERATION_NOT_ALLOWED]"));
    }

    @Test
    public void unrecognizedFailure_keepsTheGenericMessage() {
        assertEquals(R.string.error_auth_generic,
                AuthErrorMessages.forException(new Exception("Unexpected")));
        assertEquals(R.string.error_auth_generic, AuthErrorMessages.forException(null));
    }
}
