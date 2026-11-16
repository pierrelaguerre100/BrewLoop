package com.doubleshotdevs.brewloop;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Pattern;

/**
 * Checks sign-in and registration input before it is sent to Firebase Authentication.
 *
 * <p>Each method returns an {@link Error} describing the first problem it finds, or
 * {@code null} when the value is acceptable. The checks are deliberately simple; Firebase
 * Authentication still has the final say on whether an email address or password is
 * accepted. This class has no Android dependencies, so it is covered by local unit tests.</p>
 */
public final class CredentialValidator {

    /** Shortest password Firebase Authentication accepts by default. */
    public static final int MIN_PASSWORD_LENGTH = 6;

    /** Longest full name accepted, matching the database rules. */
    public static final int MAX_NAME_LENGTH = 100;

    /** Longest email address accepted, as allowed by the email standard. */
    public static final int MAX_EMAIL_LENGTH = 254;

    /** One "@", no spaces, and at least one dot in the domain part. */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** A problem with one input field. */
    public enum Error {
        /** The full name is blank. */
        NAME_REQUIRED,
        /** The full name is longer than {@link #MAX_NAME_LENGTH}. */
        NAME_TOO_LONG,
        /** The email address is blank. */
        EMAIL_REQUIRED,
        /** The email address is not in a valid format or is too long. */
        EMAIL_INVALID,
        /** The password is empty. */
        PASSWORD_REQUIRED,
        /** The password is shorter than {@link #MIN_PASSWORD_LENGTH}. */
        PASSWORD_TOO_SHORT,
        /** The confirmation does not match the password. */
        PASSWORDS_DO_NOT_MATCH
    }

    private CredentialValidator() {
        // Static helpers only.
    }

    /**
     * Checks a full name entered during registration.
     *
     * @param name the entered name, which may be {@code null}
     * @return the problem found, or {@code null} if the name is acceptable
     */
    @Nullable
    public static Error validateName(@Nullable String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return Error.NAME_REQUIRED;
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            return Error.NAME_TOO_LONG;
        }
        return null;
    }

    /**
     * Checks an email address entered for sign-in or registration.
     *
     * @param email the entered email address, which may be {@code null}
     * @return the problem found, or {@code null} if the address is acceptable
     */
    @Nullable
    public static Error validateEmail(@Nullable String email) {
        String trimmed = email == null ? "" : email.trim();
        if (trimmed.isEmpty()) {
            return Error.EMAIL_REQUIRED;
        }
        if (trimmed.length() > MAX_EMAIL_LENGTH || !EMAIL_PATTERN.matcher(trimmed).matches()) {
            return Error.EMAIL_INVALID;
        }
        return null;
    }

    /**
     * Checks a password entered on the sign-in screen. Length is not checked here, so an
     * existing account with an older, shorter password can still sign in.
     *
     * @param password the entered password, which may be {@code null}
     * @return the problem found, or {@code null} if a password was entered
     */
    @Nullable
    public static Error validateSignInPassword(@Nullable String password) {
        return password == null || password.isEmpty() ? Error.PASSWORD_REQUIRED : null;
    }

    /**
     * Checks a new password and its confirmation entered during registration.
     *
     * @param password     the new password, which may be {@code null}
     * @param confirmation the repeated password, which may be {@code null}
     * @return the problem found, or {@code null} if the password is acceptable
     */
    @Nullable
    public static Error validateNewPassword(@Nullable String password,
                                            @Nullable String confirmation) {
        if (password == null || password.isEmpty()) {
            return Error.PASSWORD_REQUIRED;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return Error.PASSWORD_TOO_SHORT;
        }
        if (!password.equals(confirmation)) {
            return Error.PASSWORDS_DO_NOT_MATCH;
        }
        return null;
    }

    /**
     * Normalizes an email address for sending to Firebase Authentication.
     *
     * @param email the entered email address
     * @return the address without surrounding spaces
     */
    @NonNull
    public static String normalizeEmail(@NonNull String email) {
        return email.trim();
    }
}
