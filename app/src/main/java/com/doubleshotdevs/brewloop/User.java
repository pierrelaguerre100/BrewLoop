package com.doubleshotdevs.brewloop;

import androidx.annotation.Nullable;

import com.google.firebase.database.Exclude;

/**
 * A BrewLoop account profile stored at {@code Users/{userID}} in the Realtime Database.
 *
 * <p>The public fields are mapped directly by Firebase, so their names are the stored field
 * names. Helper methods are marked with {@link Exclude} so they are never written to the
 * database as extra fields.</p>
 */
public class User {

    /** Stored field name for {@link #userID}. */
    public static final String FIELD_USER_ID = "userID";
    /** Stored field name for {@link #fullName}. */
    public static final String FIELD_FULL_NAME = "fullName";
    /** Stored field name for {@link #email}. */
    public static final String FIELD_EMAIL = "email";
    /** Stored field name for {@link #role}. */
    public static final String FIELD_ROLE = "role";
    /** Stored field name for {@link #pointsBalance}. */
    public static final String FIELD_POINTS_BALANCE = "pointsBalance";

    /** Firebase Authentication UID; also the key of this record under {@code Users}. */
    public String userID;

    /** The customer's or employee's full name. */
    public String fullName;

    /** The sign-in email address. */
    public String email;

    /** One of {@code customer}, {@code staff}, or {@code manager}; see {@link UserRole}. */
    public String role;

    /** Current loyalty points balance; never negative. */
    public int pointsBalance;

    /**
     * Creates an empty profile. Required by Firebase to map stored records into objects.
     */
    public User() {
    }

    /**
     * Creates a profile without a UID.
     *
     * @param fullName      the user's full name
     * @param email         the sign-in email address
     * @param role          the stored role value, such as {@code "customer"}
     * @param pointsBalance the current points balance
     */
    public User(String fullName, String email, String role, int pointsBalance) {
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.pointsBalance = pointsBalance;
    }

    /**
     * Creates a complete profile.
     *
     * @param userID        the Firebase Authentication UID
     * @param fullName      the user's full name
     * @param email         the sign-in email address
     * @param role          the stored role value, such as {@code "customer"}
     * @param pointsBalance the current points balance
     */
    public User(String userID, String fullName, String email, String role, int pointsBalance) {
        this(fullName, email, role, pointsBalance);
        this.userID = userID;
    }

    /**
     * Returns this profile's role as a typed value.
     *
     * @return the role, or {@code null} if the stored value is missing or unsupported
     */
    @Exclude
    @Nullable
    public UserRole getUserRole() {
        return UserRole.fromDatabaseValue(role);
    }

    /**
     * Indicates whether this profile may use staff features. Managers qualify as well.
     *
     * @return {@code true} if the role is staff or manager
     */
    @Exclude
    public boolean hasStaffAccess() {
        UserRole userRole = getUserRole();
        return userRole != null && userRole.hasStaffAccess();
    }

    /**
     * Indicates whether this profile belongs to a customer.
     *
     * @return {@code true} if the role is customer
     */
    @Exclude
    public boolean isCustomer() {
        return getUserRole() == UserRole.CUSTOMER;
    }
}
