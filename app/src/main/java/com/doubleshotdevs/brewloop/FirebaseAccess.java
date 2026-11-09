package com.doubleshotdevs.brewloop;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Single access point for the Firebase services used by BrewLoop.
 *
 * <p>{@link BrewLoopApplication} configures the Firebase environment before any screen
 * starts, so the instances returned here already point at the correct backend: the shared
 * project for debug and release builds, or the local emulators for the {@code emulator}
 * build.</p>
 */
public final class FirebaseAccess {

    /**
     * The database instance configured at startup. It is kept because, once an
     * emulator-routed instance has been used, {@code FirebaseDatabase.getInstance()} returns
     * a new instance without the emulator routing.
     */
    private static FirebaseDatabase database;

    private FirebaseAccess() {
        // Static access only.
    }

    /**
     * Returns the Firebase Authentication instance.
     *
     * @return the configured {@link FirebaseAuth}
     */
    @NonNull
    public static FirebaseAuth auth() {
        return FirebaseAuth.getInstance();
    }

    /**
     * Returns the Realtime Database instance.
     *
     * <p>Always use this method instead of calling {@code FirebaseDatabase.getInstance()}
     * directly. The first call, made by {@link BrewLoopApplication} right after the Firebase
     * environment is configured, keeps that instance for the life of the process.</p>
     *
     * @return the configured {@link FirebaseDatabase}
     */
    @NonNull
    public static synchronized FirebaseDatabase database() {
        if (database == null) {
            database = FirebaseDatabase.getInstance();
        }
        return database;
    }

    /**
     * Returns a reference to one top-level node.
     *
     * @param nodeName a node name from {@link FirebaseNodes}
     * @return a reference to that node
     */
    @NonNull
    public static DatabaseReference node(@NonNull String nodeName) {
        return database().getReference(nodeName);
    }

    /**
     * Returns the reference to one user's profile record, {@code Users/{uid}}.
     *
     * @param database the database to read from
     * @param uid      the Firebase Authentication UID
     * @return a reference to that user's profile
     * @throws IllegalArgumentException if {@code uid} is empty or not a valid key
     */
    @NonNull
    public static DatabaseReference userProfile(@NonNull FirebaseDatabase database,
                                                @NonNull String uid) {
        if (!UserProfileParser.isValidUid(uid)) {
            throw new IllegalArgumentException("Invalid user ID for a database key");
        }
        return database.getReference(FirebaseNodes.USERS).child(uid);
    }
}
