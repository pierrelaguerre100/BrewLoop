package com.doubleshotdevs.brewloop;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthException;

/**
 * Turns Firebase Authentication failures and validation errors into messages for the user.
 *
 * <p>Sign-in failures for an unknown email and a wrong password share one message, so the
 * screen does not reveal which email addresses have accounts.</p>
 */
public final class AuthErrorMessages {

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
        return R.string.error_auth_generic;
    }

    /**
     * Returns the message for a Firebase Authentication error code.
     *
     * @param errorCode a code from {@link FirebaseAuthException#getErrorCode()}
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
            default:
                return R.string.error_auth_generic;
        }
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
