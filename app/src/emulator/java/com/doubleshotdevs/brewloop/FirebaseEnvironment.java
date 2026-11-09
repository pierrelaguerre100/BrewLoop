package com.doubleshotdevs.brewloop;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Firebase environment for the {@code emulator} build: the local Firebase Emulator Suite,
 * running the isolated {@code demo-brewloop} project.
 *
 * <p>This variant's manifest removes Firebase's automatic initializer, so the shared
 * project from {@code google-services.json} is never initialized. The default Firebase
 * app is created here with demo-only options, and Authentication and Realtime Database
 * are routed to the emulators before anything else can use them. If the emulators are
 * not running, requests fail; they never fall back to the shared project.</p>
 */
public final class FirebaseEnvironment {

    /**
     * Demo project ID shared with {@code firebase/.firebaserc}. The {@code demo-} prefix
     * makes the Emulator Suite refuse to contact any real Firebase project.
     */
    public static final String DEMO_PROJECT_ID = "demo-brewloop";

    /** Address of the development computer as seen from the standard Android emulator. */
    static final String EMULATOR_HOST = "10.0.2.2";

    /** Authentication emulator port; matches {@code firebase/firebase.json}. */
    static final int AUTH_EMULATOR_PORT = 9099;

    /** Realtime Database emulator port; matches {@code firebase/firebase.json}. */
    static final int DATABASE_EMULATOR_PORT = 9000;

    private static final String TAG = "FirebaseEnvironment";

    // Placeholder values for the demo project only. The emulators do not validate them,
    // and they identify no real Firebase app.
    private static final String DEMO_APPLICATION_ID = "1:000000000000:android:0000000000000000";
    private static final String DEMO_API_KEY = "demo-api-key";

    private FirebaseEnvironment() {
        // Static access only.
    }

    /**
     * Initializes the default Firebase app for the demo project and routes Authentication
     * and Realtime Database to the local emulators.
     *
     * @param context the application context
     * @throws IllegalStateException if a Firebase app was already initialized, which would
     *                               mean the shared project could be in use
     */
    public static void configure(@NonNull Context context) {
        if (!FirebaseApp.getApps(context).isEmpty()) {
            throw new IllegalStateException(
                    "Firebase was initialized before emulator routing; refusing to continue");
        }
        FirebaseOptions options = new FirebaseOptions.Builder()
                .setProjectId(DEMO_PROJECT_ID)
                .setApplicationId(DEMO_APPLICATION_ID)
                .setApiKey(DEMO_API_KEY)
                .setDatabaseUrl("https://" + DEMO_PROJECT_ID + "-default-rtdb.firebaseio.com")
                .build();
        FirebaseApp.initializeApp(context, options);

        FirebaseAuth.getInstance().useEmulator(EMULATOR_HOST, AUTH_EMULATOR_PORT);
        FirebaseDatabase.getInstance().useEmulator(EMULATOR_HOST, DATABASE_EMULATOR_PORT);

        Log.i(TAG, "Local test mode: Auth and Realtime Database use the emulators for "
                + DEMO_PROJECT_ID);
    }

    /**
     * Indicates whether Firebase is routed to the local emulators.
     *
     * @return {@code true}; this variant always uses the emulators
     */
    public static boolean isLocalTestMode() {
        return true;
    }
}
