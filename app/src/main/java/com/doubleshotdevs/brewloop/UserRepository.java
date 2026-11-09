package com.doubleshotdevs.brewloop;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * Loads the signed-in user's own profile from {@code Users/{uid}}.
 *
 * <p>Only the caller's own record is read; this class never reads the whole {@code Users}
 * node. It does not create profiles or change points balances. Every load ends with
 * exactly one callback on the main thread, including when the record is missing or
 * malformed, the read is denied, or the database cannot be reached.</p>
 */
public class UserRepository {

    /** Receives the result of a profile load on the main thread. */
    public interface ProfileCallback {
        /**
         * Called once when the load finishes.
         *
         * @param result the outcome, including the profile on success
         */
        void onProfileLoaded(@NonNull ProfileLoadResult result);
    }

    /** A profile load in progress that can be cancelled, for example in {@code onDestroy}. */
    public interface PendingLoad {
        /** Stops the load. The callback will not be called after this returns. */
        void cancel();
    }

    /** How long to wait for the database before reporting a network error. */
    public static final long DEFAULT_TIMEOUT_MILLIS = 15_000L;

    private static final String TAG = "UserRepository";

    private final FirebaseAuth auth;
    private final FirebaseDatabase database;
    private final long timeoutMillis;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Creates a repository that uses the app's configured Firebase instances.
     */
    public UserRepository() {
        this(FirebaseAccess.auth(), FirebaseAccess.database(), DEFAULT_TIMEOUT_MILLIS);
    }

    /**
     * Creates a repository with explicit dependencies.
     *
     * @param auth          the Firebase Authentication instance
     * @param database      the Realtime Database instance
     * @param timeoutMillis how long to wait for the database before reporting a network error
     */
    public UserRepository(@NonNull FirebaseAuth auth, @NonNull FirebaseDatabase database,
                          long timeoutMillis) {
        this.auth = auth;
        this.database = database;
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * Loads the profile of the currently signed-in user.
     *
     * <p>The Realtime Database SDK waits indefinitely while offline, so the load also ends
     * with {@link ProfileLoadStatus#NETWORK_ERROR} if no response arrives within the
     * timeout.</p>
     *
     * @param callback receives the result on the main thread
     * @return a handle that cancels the load
     */
    @NonNull
    public PendingLoad loadCurrentUserProfile(@NonNull ProfileCallback callback) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            return deliverLater(callback, ProfileLoadResult.failure(ProfileLoadStatus.NOT_SIGNED_IN));
        }
        String uid = firebaseUser.getUid();
        if (!UserProfileParser.isValidUid(uid)) {
            return deliverLater(callback, ProfileLoadResult.failure(ProfileLoadStatus.MALFORMED));
        }

        DatabaseReference profileRef = FirebaseAccess.userProfile(database, uid);
        SingleLoad load = new SingleLoad(profileRef, uid, callback);
        profileRef.addListenerForSingleValueEvent(load);
        mainHandler.postDelayed(load.timeout, timeoutMillis);
        return load;
    }

    /**
     * Posts a result that is already known, so callers always receive it asynchronously.
     *
     * @param callback the callback to notify
     * @param result   the result to deliver
     * @return a handle that cancels the delivery
     */
    @NonNull
    private PendingLoad deliverLater(@NonNull ProfileCallback callback,
                                     @NonNull ProfileLoadResult result) {
        Runnable delivery = () -> callback.onProfileLoaded(result);
        mainHandler.post(delivery);
        return () -> mainHandler.removeCallbacks(delivery);
    }

    /**
     * One profile read that finishes exactly once: with data, a database error, a timeout,
     * or cancellation. All transitions run on the main thread, where Firebase delivers
     * listener callbacks.
     */
    private final class SingleLoad implements ValueEventListener, PendingLoad {

        private final DatabaseReference profileRef;
        private final String uid;
        private final ProfileCallback callback;
        private boolean finished;

        final Runnable timeout = () -> {
            Log.w(TAG, "Profile load timed out");
            finish(ProfileLoadResult.failure(ProfileLoadStatus.NETWORK_ERROR));
        };

        SingleLoad(DatabaseReference profileRef, String uid, ProfileCallback callback) {
            this.profileRef = profileRef;
            this.uid = uid;
            this.callback = callback;
        }

        @Override
        public void onDataChange(@NonNull DataSnapshot snapshot) {
            ProfileLoadResult result;
            try {
                result = UserProfileParser.parse(uid, snapshot.getValue());
            } catch (RuntimeException e) {
                Log.w(TAG, "Profile data could not be read", e);
                result = ProfileLoadResult.failure(ProfileLoadStatus.MALFORMED);
            }
            if (!result.isSuccess()) {
                Log.w(TAG, "Profile load failed: " + result.getStatus());
            }
            finish(result);
        }

        @Override
        public void onCancelled(@NonNull DatabaseError error) {
            ProfileLoadStatus status = UserProfileParser.statusForDatabaseError(error.getCode());
            Log.w(TAG, "Profile read cancelled with code " + error.getCode() + ": " + status);
            finish(ProfileLoadResult.failure(status));
        }

        @Override
        public void cancel() {
            if (!finished) {
                finished = true;
                stopListening();
            }
        }

        /**
         * Delivers the result if this load has not already finished.
         *
         * @param result the result to deliver
         */
        private void finish(@NonNull ProfileLoadResult result) {
            if (finished) {
                return;
            }
            finished = true;
            stopListening();
            callback.onProfileLoaded(result);
        }

        /** Removes the database listener and the pending timeout. */
        private void stopListening() {
            mainHandler.removeCallbacks(timeout);
            profileRef.removeEventListener(this);
        }
    }
}
