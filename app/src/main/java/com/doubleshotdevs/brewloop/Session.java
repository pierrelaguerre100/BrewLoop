package com.doubleshotdevs.brewloop;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseUser;

/**
 * Sign-in checks and sign-out shared by every screen that needs a signed-in user.
 */
public final class Session {

    private Session() {
        // Static helpers only.
    }

    /**
     * Returns the signed-in user's UID, or sends the user back to the sign-in screen.
     *
     * <p>Call this from {@code onCreate} or {@code onStart} of any screen that shows
     * user-specific data. When it returns {@code null} the activity is already finishing and
     * must not load anything.</p>
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
     * Signs out and returns to the sign-in screen, closing every other screen so no
     * user-specific data stays on the back stack.
     *
     * @param activity the screen the user signed out from
     */
    public static void signOut(@NonNull Activity activity) {
        // The shared cart belongs to the signed-in user. Once the team's cart is added, it
        // must be cleared here, before the next person can sign in on this device.
        FirebaseAccess.auth().signOut();
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
