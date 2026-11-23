package com.doubleshotdevs.brewloop;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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
 *
 * <p>The Realtime Database emulator tells the client to reconnect to its own configured
 * host, {@code 127.0.0.1:9000}. Inside the Android emulator that address is the device
 * itself, so once the first connection goes idle every later database request stalls. Run
 * {@code adb reverse tcp:9000 tcp:9000} after the Android emulator starts so that address
 * reaches the computer running the Firebase emulators.</p>
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

    /**
     * Runtime permission that apps targeting Android 17 need to reach local-network
     * addresses such as {@link #EMULATOR_HOST}. Declared in this variant's manifest only.
     */
    static final String LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK";

    /** First Android API level (Android 17) that enforces {@link #LOCAL_NETWORK_PERMISSION}. */
    private static final int LOCAL_NETWORK_PERMISSION_API = 37;

    private static final int LOCAL_NETWORK_REQUEST_CODE = 9000;

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

    /**
     * Asks for the local-network permission if it has not been granted yet.
     *
     * <p>Without it, Android 17 blocks every connection to the emulator host and sign-in,
     * registration, and profile loads fail with a connection error. Earlier Android versions
     * do not have this permission, so nothing is requested there.</p>
     *
     * @param activity the screen that shows the system permission dialog
     */
    public static void requestLocalNetworkAccess(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT < LOCAL_NETWORK_PERMISSION_API) {
            return;
        }
        if (ContextCompat.checkSelfPermission(activity, LOCAL_NETWORK_PERMISSION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Requesting local network access for the emulators");
            ActivityCompat.requestPermissions(activity,
                    new String[] {LOCAL_NETWORK_PERMISSION}, LOCAL_NETWORK_REQUEST_CODE);
        }
    }
}
