package com.doubleshotdevs.brewloop;

/**
 * Outcome of loading the signed-in user's own profile from {@code Users/{uid}}.
 */
public enum ProfileLoadStatus {

    /** The profile was found and is valid. */
    SUCCESS,

    /** No user is signed in to Firebase Authentication. */
    NOT_SIGNED_IN,

    /**
     * No record exists for this UID. This can happen when customer registration was
     * interrupted after the account was created but before the profile was written.
     */
    MISSING,

    /**
     * A record exists but has the wrong shape, such as a starter-era text value, a missing
     * field, or a negative or non-integer points balance.
     */
    MALFORMED,

    /** The record's {@code userID} field does not match the signed-in UID. */
    UID_MISMATCH,

    /** The record's {@code role} is not {@code customer}, {@code staff}, or {@code manager}. */
    UNSUPPORTED_ROLE,

    /** Security rules refused the read. */
    PERMISSION_DENIED,

    /** The database could not be reached, or it did not respond in time. */
    NETWORK_ERROR,

    /** Any other database failure. */
    UNKNOWN_ERROR
}
