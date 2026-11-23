package com.doubleshotdevs.brewloop;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns Firebase Authentication failures and validation errors into messages for the user,
 * and into short diagnostic codes that are safe to log.
 *
 * <p>Sign-in failures for an unknown email and a wrong password share one message, so the
 * screen does not reveal which email addresses have accounts.</p>
 */
public final class AuthErrorMessages {

    /**
     * Matches the server error code that Firebase puts in square brackets at the end of some
     * messages, for example {@code [ CONFIGURATION_NOT_FOUND ]}.
     */
    private static final Pattern BRACKETED_CODE = Pattern.compile("\\[\\s*([A-Z][A-Z0-9_]{2,60})\\s*]");

    private AuthErrorMessages() {
        // Static helpers only.
    }

    /**
     * Returns the message for a failed Firebase Authentication request.
     *
     * @param error the task's exception, which may be {@code null}
     * @return a string resource ID describing the failure
     */
    @StringRes
    public static int forException(@Nullable Exception error) {
        if (error instanceof FirebaseNetworkException) {
            return R.string.error_network;
        }
        if (error instanceof FirebaseTooManyRequestsException) {
            return R.string.error_too_many_attempts;
        }
        if (error instanceof FirebaseAuthException) {
            return forErrorCode(((FirebaseAuthException) error).getErrorCode());
        }
        String code = bracketedCode(error == null ? null : error.getMessage());
        return code == null ? R.string.error_auth_generic : forErrorCode(code);
    }

    /**
     * Returns the message for a Firebase Authentication error code.
     *
     * @param errorCode a code from {@link FirebaseAuthException#getErrorCode()}, or a server
     *                  code such as {@code CONFIGURATION_NOT_FOUND}
     * @return a string resource ID describing the failure
     */
    @StringRes
    static int forErrorCode(@NonNull String errorCode) {
        switch (errorCode) {
            case "ERROR_INVALID_EMAIL":
                return R.string.error_email_invalid;
            case "ERROR_WRONG_PASSWORD":
            case "ERROR_USER_NOT_FOUND":
            case "ERROR_INVALID_CREDENTIAL":
                return R.string.error_wrong_credentials;
            case "ERROR_EMAIL_ALREADY_IN_USE":
                return R.string.error_email_in_use;
            case "ERROR_WEAK_PASSWORD":
                return R.string.error_password_too_short;
            case "ERROR_USER_DISABLED":
                return R.string.error_account_disabled;
            case "ERROR_TOO_MANY_REQUESTS":
                return R.string.error_too_many_attempts;
            case "ERROR_OPERATION_NOT_ALLOWED":
            case "OPERATION_NOT_ALLOWED":
            case "CONFIGURATION_NOT_FOUND":
                // The Firebase project has not enabled Email/Password sign-in.
                return R.string.error_auth_not_enabled;
            case "ERROR_NETWORK_REQUEST_FAILED":
                return R.string.error_network;
            default:
                return R.string.error_auth_generic;
        }
    }

    /**
     * Returns a short code that identifies an Authentication failure without exposing the
     * email address, password, or token involved.
     *
     * <p>The result is the Firebase error code when there is one, otherwise the code in
     * square brackets at the end of the message, otherwise only the exception class name.
     * The full message is never returned because it can contain the email address.</p>
     *
     * @param error the task's exception, which may be {@code null}
     * @return a code such as {@code ERROR_OPERATION_NOT_ALLOWED}, safe to write to the log
     */
    @NonNull
    public static String diagnosticCode(@Nullable Exception error) {
        if (error == null) {
            return "unknown";
        }
        String type = error.getClass().getSimpleName();
        if (error instanceof FirebaseAuthException) {
            return type + "/" + ((FirebaseAuthException) error).getErrorCode();
        }
        String code = bracketedCode(error.getMessage());
        return code == null ? type : type + "/" + code;
    }

    /**
     * Extracts a server error code written in square brackets.
     *
     * @param message an exception message, which may be {@code null}
     * @return the code, or {@code null} if the message has none
     */
    @Nullable
    static String bracketedCode(@Nullable String message) {
        if (message == null) {
            return null;
        }
        Matcher matcher = BRACKETED_CODE.matcher(message);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Returns the message for an input validation problem.
     *
     * @param error the problem found by {@link CredentialValidator}
     * @return a string resource ID describing the problem
     */
    @StringRes
    public static int forValidation(@NonNull CredentialValidator.Error error) {
        switch (error) {
            case NAME_REQUIRED:
                return R.string.error_name_required;
            case NAME_TOO_LONG:
                return R.string.error_name_too_long;
            case EMAIL_REQUIRED:
                return R.string.error_email_required;
            case EMAIL_INVALID:
                return R.string.error_email_invalid;
            case PASSWORD_REQUIRED:
                return R.string.error_password_required;
            case PASSWORD_TOO_SHORT:
                return R.string.error_password_too_short;
            case PASSWORDS_DO_NOT_MATCH:
            default:
                return R.string.error_passwords_do_not_match;
        }
    }

    /**
     * Returns the message for a profile that could not be loaded or created.
     *
     * @param status the failure status
     * @return a string resource ID describing the problem
     */
    @StringRes
    public static int forProfileStatus(@NonNull ProfileLoadStatus status) {
        switch (status) {
            case NETWORK_ERROR:
                return R.string.error_profile_network;
            case PERMISSION_DENIED:
                return R.string.error_profile_denied;
            case MALFORMED:
            case UID_MISMATCH:
                return R.string.error_profile_malformed;
            case UNSUPPORTED_ROLE:
                return R.string.error_profile_role;
            case MISSING:
                return R.string.error_profile_missing;
            case NOT_SIGNED_IN:
                return R.string.error_not_signed_in;
            case UNKNOWN_ERROR:
            case SUCCESS:
            default:
                return R.string.error_profile_unknown;
        }
    }
}
