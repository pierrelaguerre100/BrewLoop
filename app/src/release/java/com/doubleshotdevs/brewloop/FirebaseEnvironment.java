package com.doubleshotdevs.brewloop;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;

/**
 * Firebase environment for the release build: the shared {@code brewloop-137ab} project.
 *
 * <p>Firebase is initialized automatically from {@code google-services.json}. Emulator
 * routing code is not part of this variant.</p>
 */
public final class FirebaseEnvironment {

    private FirebaseEnvironment() {
        // Static access only.
    }

    /**
     * Leaves the automatically initialized shared Firebase project in place.
     *
     * @param context the application context (unused in this variant)
     */
    public static void configure(@NonNull Context context) {
        // Nothing to configure.
    }

    /**
     * Indicates whether Firebase is routed to the local emulators.
     *
     * @return {@code false}; release builds always use the shared project
     */
    public static boolean isLocalTestMode() {
        return false;
    }

    /**
     * Requests any permission needed to reach the local emulators.
     *
     * @param activity the screen that would show the request (unused in this variant)
     */
    public static void requestLocalNetworkAccess(@NonNull Activity activity) {
        // This build uses the shared project over the internet; nothing to request.
    }
}
