package com.doubleshotdevs.brewloop;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseUser;

/**
 * Signed-in identity, role checks, and sign-out shared by every screen that needs a user.
 *
 * <p>The UID always comes from Firebase Authentication and the role always comes from the
 * user's own validated {@code Users/{uid}} record. A UID or role passed in an intent extra or
 * chosen in the app is never used for access decisions. These checks only decide which
 * screen to show; the database security rules are what actually protect the data.</p>
 *
 * <p>This class also ties the shared {@link Cart} to the account that filled it, so one
 * person's cart is never shown to, or submitted by, the next person who signs in.</p>
 */
public final class Session {

    /** The level of access a screen needs. */
    public enum Access {
        /** Customer screens, such as the cart and checkout. */
        CUSTOMER,
        /** Staff screens, such as the order queue. Managers also have this access. */
        STAFF,
        /** Manager-only screens, such as menu management. */
        MANAGER;

        /**
         * Indicates whether a validated role has this access.
         *
         * @param role the role from the user's validated profile, or {@code null} if unknown
         * @return {@code true} if the role may open screens that need this access
         */
        public boolean isGrantedTo(@Nullable UserRole role) {
            if (role == null) {
                return false;
            }
            switch (this) {
                case CUSTOMER:
                    return role == UserRole.CUSTOMER;
                case STAFF:
                    return role.hasStaffAccess();
                case MANAGER:
                default:
                    return role.hasManagerAccess();
            }
        }
    }

    /** Receives the validated profile once a screen's access check passes. */
    public interface AccessCallback {
        /**
         * Called once on the main thread when the signed-in user has the required access.
         *
         * @param profile the signed-in user's validated profile; {@code profile.userID} is
         *                the authenticated UID and {@code profile.getUserRole()} is non-null
         */
        void onAccessGranted(@NonNull User profile);
    }

    /** UID of the account the shared cart currently belongs to, or {@code null}. */
    @Nullable
    private static String cartOwnerUid;

    private Session() {
        // Static helpers only.
    }

    /**
     * Returns the signed-in user's UID, or sends the user back to the sign-in screen.
     *
     * <p>Call this from {@code onCreate} or {@code onStart} of any screen that shows
     * user-specific data. When it returns {@code null} the activity is already finishing and
     * must not load anything. This checks sign-in only; use {@link #requireAccess} when the
     * screen also depends on the user's role.</p>
     *
     * @param activity the screen that needs a signed-in user
     * @return the signed-in user's UID, or {@code null} if nobody is signed in
     */
    @Nullable
    public static String requireSignedInUid(@NonNull Activity activity) {
        FirebaseUser user = FirebaseAccess.auth().getCurrentUser();
        if (user == null || !UserProfileParser.isValidUid(user.getUid())) {
            restartAtSignIn(activity);
            return null;
        }
        return user.getUid();
    }

    /**
     * Loads the signed-in user's own profile and calls back only if its stored role has the
     * required access.
     *
     * <p>If nobody is signed in, the profile is missing, malformed, or cannot be loaded, the
     * role does not have the access, or a different account signs in before the profile
     * arrives, the app returns to the sign-in screen, which explains the problem and offers
     * a retry where one can help. Keep screen content hidden or disabled until the callback
     * runs, and cancel the returned handle in {@code onDestroy}.</p>
     *
     * @param activity the screen that needs the access
     * @param access   the access the screen needs
     * @param callback receives the validated profile on the main thread
     * @return a handle that cancels the check; the callback is not called after cancelling
     */
    @NonNull
    public static UserRepository.PendingLoad requireAccess(@NonNull Activity activity,
                                                           @NonNull Access access,
                                                           @NonNull AccessCallback callback) {
        String uid = requireSignedInUid(activity);
        if (uid == null) {
            return () -> { };
        }
        return new UserRepository().loadCurrentUserProfile(result -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            User profile = result.getUser();
            FirebaseUser current = FirebaseAccess.auth().getCurrentUser();
            boolean sameAccount = current != null && uid.equals(current.getUid())
                    && profile != null && uid.equals(profile.userID);
            if (result.isSuccess() && sameAccount && access.isGrantedTo(profile.getUserRole())) {
                callback.onAccessGranted(profile);
            } else {
                restartAtSignIn(activity);
            }
        });
    }

    /**
     * Makes the shared cart belong to an account, emptying it first if it was filled by a
     * different account.
     *
     * <p>Call this with the authenticated UID before a screen reads or changes the cart.</p>
     *
     * @param uid the signed-in user's UID
     */
    public static synchronized void claimCartFor(@NonNull String uid) {
        if (!uid.equals(cartOwnerUid)) {
            Cart.getInstance().clear();
            cartOwnerUid = uid;
        }
    }

    /**
     * Empties the shared cart only if it still belongs to the given account.
     *
     * <p>Use this from a callback that finishes after a delay, such as a successful order
     * submission, so a late result for one account never empties another account's
     * cart.</p>
     *
     * @param uid the UID the delayed operation was started for
     * @return {@code true} if the cart belonged to that account and was emptied
     */
    public static synchronized boolean clearCartIfOwnedBy(@NonNull String uid) {
        if (!uid.equals(cartOwnerUid)) {
            return false;
        }
        Cart.getInstance().clear();
        return true;
    }

    /**
     * Ends the session without changing screens: empties the shared cart, then signs out.
     * Use {@link #signOut} instead from any screen other than the sign-in screen.
     */
    public static void endSession() {
        synchronized (Session.class) {
            Cart.getInstance().clear();
            cartOwnerUid = null;
        }
        FirebaseAccess.auth().signOut();
    }

    /**
     * Signs out and returns to the sign-in screen, closing every other screen so no
     * user-specific data, including the cart, stays on the back stack.
     *
     * @param activity the screen the user signed out from
     */
    public static void signOut(@NonNull Activity activity) {
        endSession();
        restartAtSignIn(activity);
    }

    /**
     * Opens the sign-in screen as the only screen in a new task.
     *
     * @param activity the current screen, which is finished
     */
    public static void restartAtSignIn(@NonNull Activity activity) {
        Intent intent = new Intent(activity, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
}
