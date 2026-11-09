package com.doubleshotdevs.brewloop;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Account roles supported by BrewLoop.
 *
 * <p>Customers register themselves and always start as {@link #CUSTOMER}. Staff and
 * manager roles are assigned only by the shop owner in the Firebase Console. A manager
 * has every staff capability in addition to any manager-only features.</p>
 */
public enum UserRole {

    /** A self-registered customer who places orders and collects points. */
    CUSTOMER("customer"),

    /** A shop employee who handles orders. */
    STAFF("staff"),

    /** A shop manager, who also has all staff capabilities. */
    MANAGER("manager");

    private final String databaseValue;

    UserRole(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    /**
     * Returns the exact text stored in the {@code role} field of a {@code Users} record.
     *
     * @return the lowercase database value for this role
     */
    @NonNull
    public String getDatabaseValue() {
        return databaseValue;
    }

    /**
     * Indicates whether this role may use staff features.
     *
     * @return {@code true} for {@link #STAFF} and {@link #MANAGER}
     */
    public boolean hasStaffAccess() {
        return this == STAFF || this == MANAGER;
    }

    /**
     * Indicates whether this role may use manager-only features.
     *
     * @return {@code true} only for {@link #MANAGER}
     */
    public boolean hasManagerAccess() {
        return this == MANAGER;
    }

    /**
     * Converts a stored {@code role} value into a role.
     *
     * <p>Matching is exact and case-sensitive, because the security rules compare the same
     * exact values. A value such as {@code "Staff"} is therefore unsupported and must be
     * corrected in the Firebase Console.</p>
     *
     * @param value the stored role text, which may be {@code null}
     * @return the matching role, or {@code null} if the value is missing or unsupported
     */
    @Nullable
    public static UserRole fromDatabaseValue(@Nullable String value) {
        if (value == null) {
            return null;
        }
        for (UserRole role : values()) {
            if (role.databaseValue.equals(value)) {
                return role;
            }
        }
        return null;
    }
}
