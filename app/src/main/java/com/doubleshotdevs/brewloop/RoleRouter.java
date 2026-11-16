package com.doubleshotdevs.brewloop;

import androidx.annotation.NonNull;

/**
 * Decides where the app goes after the signed-in user's profile has been loaded.
 *
 * <p>The decision uses only the validated profile stored at {@code Users/{uid}}, never a
 * role chosen in the app. Client-side routing is only a convenience: the database security
 * rules are what actually stop a customer from reaching staff data. This class has no
 * Android dependencies, so it is covered by local unit tests.</p>
 */
public final class RoleRouter {

    /** Where to send the user. */
    public enum Destination {
        /** Show the sign-in screen; nobody is signed in. */
        SIGN_IN,
        /** Open the customer home screen. */
        CUSTOMER_HOME,
        /**
         * The user is staff or a manager, but the staff order queue has not been added to
         * the project yet, so show an explicit "not available yet" state.
         */
        STAFF_UNAVAILABLE,
        /**
         * The account exists but its profile record does not, usually because registration
         * was interrupted. Offer to finish creating the customer profile.
         */
        COMPLETE_PROFILE,
        /** A temporary problem, such as no connection. Offer to try again. */
        RETRY,
        /**
         * The stored profile cannot be used, for example it is malformed, has an unsupported
         * role, or the read was denied. The shop owner must fix it in the Firebase Console.
         */
        CONTACT_SHOP
    }

    private RoleRouter() {
        // Static helpers only.
    }

    /**
     * Chooses the destination for a profile load result.
     *
     * @param result the result of {@link UserRepository#loadCurrentUserProfile}
     * @return where the app should go next
     */
    @NonNull
    public static Destination route(@NonNull ProfileLoadResult result) {
        switch (result.getStatus()) {
            case SUCCESS:
                User user = result.getUser();
                return user == null ? Destination.CONTACT_SHOP : routeForRole(user.getUserRole());
            case NOT_SIGNED_IN:
                return Destination.SIGN_IN;
            case MISSING:
                return Destination.COMPLETE_PROFILE;
            case NETWORK_ERROR:
            case UNKNOWN_ERROR:
                return Destination.RETRY;
            case MALFORMED:
            case UID_MISMATCH:
            case UNSUPPORTED_ROLE:
            case PERMISSION_DENIED:
            default:
                return Destination.CONTACT_SHOP;
        }
    }

    /**
     * Chooses the destination for a validated role.
     *
     * @param role the stored role, or {@code null} if it is unsupported
     * @return {@link Destination#CUSTOMER_HOME} for customers,
     *         {@link Destination#STAFF_UNAVAILABLE} for staff and managers, and
     *         {@link Destination#CONTACT_SHOP} otherwise
     */
    @NonNull
    static Destination routeForRole(UserRole role) {
        if (role == UserRole.CUSTOMER) {
            return Destination.CUSTOMER_HOME;
        }
        if (role != null && role.hasStaffAccess()) {
            return Destination.STAFF_UNAVAILABLE;
        }
        return Destination.CONTACT_SHOP;
    }
}
