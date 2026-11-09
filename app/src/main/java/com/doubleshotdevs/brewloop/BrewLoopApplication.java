package com.doubleshotdevs.brewloop;

import android.app.Application;

/**
 * Application entry point. Configures Firebase before any activity can use it.
 */
public class BrewLoopApplication extends Application {

    /**
     * Selects the Firebase backend for this build variant, then keeps the configured
     * database instance. In the {@code emulator} variant this routes Authentication and
     * Realtime Database to the local emulators before any Firebase call is made.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseEnvironment.configure(this);
        FirebaseAccess.database();
    }
}
