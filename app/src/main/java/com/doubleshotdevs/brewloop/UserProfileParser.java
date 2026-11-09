package com.doubleshotdevs.brewloop;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.DatabaseError;

import java.util.Map;

/**
 * Validates raw {@code Users/{uid}} data and converts it into a {@link User}.
 *
 * <p>This class works on the plain Java value returned by {@code DataSnapshot.getValue()},
 * such as a {@link Map}, a {@link String}, or a {@link Long}, instead of mapping straight
 * to {@code User.class}. That mapping throws on unexpected shapes, such as the starter's
 * plain text user records, while this parser reports a {@link ProfileLoadStatus}.
 * It has no Android dependencies, so it can be covered by local unit tests.</p>
 */
public final class UserProfileParser {

    /** Characters that Firebase Realtime Database does not allow in keys. */
    private static final String FORBIDDEN_KEY_CHARACTERS = ".#$[]/";

    /** Maximum key length that Firebase Realtime Database accepts, in UTF-8 bytes. */
    private static final int MAX_KEY_BYTES = 768;

    private UserProfileParser() {
        // Static helpers only.
    }

    /**
     * Validates a stored profile and converts it into a {@link User}.
     *
     * @param expectedUid the UID of the signed-in user whose record was read
     * @param rawValue    the value from {@code DataSnapshot.getValue()}, or {@code null} if
     *                    no record exists
     * @return a successful result with the profile, or a failure result whose status
     *         explains what was wrong
     */
    @NonNull
    public static ProfileLoadResult parse(@NonNull String expectedUid, @Nullable Object rawValue) {
        if (rawValue == null) {
            return ProfileLoadResult.failure(ProfileLoadStatus.MISSING);
        }
        if (!(rawValue instanceof Map)) {
            // Includes starter-era records stored as one text value.
            return ProfileLoadResult.failure(ProfileLoadStatus.MALFORMED);
        }
        Map<?, ?> record = (Map<?, ?>) rawValue;

        Object userId = record.get(User.FIELD_USER_ID);
        if (!(userId instanceof String) || ((String) userId).isEmpty()) {
            return ProfileLoadResult.failure(ProfileLoadStatus.MALFORMED);
        }
        if (!userId.equals(expectedUid)) {
            return ProfileLoadResult.failure(ProfileLoadStatus.UID_MISMATCH);
        }

        String fullName = nonBlankString(record.get(User.FIELD_FULL_NAME));
        String email = nonBlankString(record.get(User.FIELD_EMAIL));
        Integer points = nonNegativeInt(record.get(User.FIELD_POINTS_BALANCE));
        Object role = record.get(User.FIELD_ROLE);
        if (fullName == null || email == null || points == null || !(role instanceof String)) {
            return ProfileLoadResult.failure(ProfileLoadStatus.MALFORMED);
        }
        if (UserRole.fromDatabaseValue((String) role) == null) {
            return ProfileLoadResult.failure(ProfileLoadStatus.UNSUPPORTED_ROLE);
        }

        return ProfileLoadResult.success(
                new User(expectedUid, fullName, email, (String) role, points));
    }

    /**
     * Maps a Realtime Database error code to a profile load status.
     *
     * @param databaseErrorCode a code from {@link DatabaseError#getCode()}
     * @return {@link ProfileLoadStatus#PERMISSION_DENIED}, {@link ProfileLoadStatus#NETWORK_ERROR},
     *         or {@link ProfileLoadStatus#UNKNOWN_ERROR}
     */
    @NonNull
    public static ProfileLoadStatus statusForDatabaseError(int databaseErrorCode) {
        switch (databaseErrorCode) {
            case DatabaseError.PERMISSION_DENIED:
                return ProfileLoadStatus.PERMISSION_DENIED;
            case DatabaseError.NETWORK_ERROR:
            case DatabaseError.DISCONNECTED:
            case DatabaseError.UNAVAILABLE:
            case DatabaseError.MAX_RETRIES:
                return ProfileLoadStatus.NETWORK_ERROR;
            default:
                return ProfileLoadStatus.UNKNOWN_ERROR;
        }
    }

    /**
     * Checks whether a UID can safely be used as a {@code Users} child key.
     *
     * @param uid the candidate UID
     * @return {@code true} if it is non-empty, within the key length limit, and contains no
     *         characters forbidden in Realtime Database keys
     */
    public static boolean isValidUid(@Nullable String uid) {
        if (uid == null || uid.isEmpty()
                || uid.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_KEY_BYTES) {
            return false;
        }
        for (int i = 0; i < uid.length(); i++) {
            char c = uid.charAt(i);
            if (Character.isISOControl(c) || FORBIDDEN_KEY_CHARACTERS.indexOf(c) >= 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the value as a trimmed string if it is non-blank text.
     *
     * @param value the raw field value
     * @return the trimmed text, or {@code null} if the value is not non-blank text
     */
    @Nullable
    private static String nonBlankString(@Nullable Object value) {
        if (!(value instanceof String)) {
            return null;
        }
        String trimmed = ((String) value).trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Returns the value as an {@code int} if it is a whole, non-negative number in range.
     *
     * <p>Firebase returns whole numbers as {@link Long}. A {@link Double} such as
     * {@code 5.0} is accepted only when it has no fractional part.</p>
     *
     * @param value the raw field value
     * @return the integer value, or {@code null} if it is missing, fractional, negative,
     *         or larger than {@link Integer#MAX_VALUE}
     */
    @Nullable
    private static Integer nonNegativeInt(@Nullable Object value) {
        long whole;
        if (value instanceof Long || value instanceof Integer) {
            whole = ((Number) value).longValue();
        } else if (value instanceof Double) {
            double d = (Double) value;
            if (Double.isNaN(d) || Double.isInfinite(d) || d != Math.rint(d)) {
                return null;
            }
            if (d < 0 || d > Integer.MAX_VALUE) {
                return null;
            }
            whole = (long) d;
        } else {
            return null;
        }
        if (whole < 0 || whole > Integer.MAX_VALUE) {
            return null;
        }
        return (int) whole;
    }
}
