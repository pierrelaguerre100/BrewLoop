package com.doubleshotdevs.brewloop;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Loads and creates the signed-in user's own profile at {@code Users/{uid}}.
 *
 * <p>Only the caller's own record is read or written; this class never reads the whole
 * {@code Users} node. It never changes an existing profile, role, or points balance: a
 * profile is only ever created when no record exists. Every operation ends with exactly one
 * callback on the main thread, including when the record is missing or malformed, the
 * request is denied, or the database cannot be reached.</p>
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

    /**
     * UIDs whose profile creation timed out or was cancelled in this process before the
     * server answered. The database SDK keeps such a write pending locally, so a later
     * create-if-missing attempt would see that unconfirmed local copy as an existing profile.
     * While a UID is listed, that answer is reported as unconfirmed instead of as success.
     * Accessed only on the main thread.
     */
    private static final Set<String> UNCONFIRMED_CREATIONS = new HashSet<>();

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
     * Creates the signed-in user's customer profile if, and only if, no profile exists yet.
     *
     * <p>This is used both right after registration and to recover an account whose
     * registration was interrupted after the Authentication account was created. The new
     * record always has the role {@code customer} and zero points; there is no way to choose
     * another role. The write runs as a database transaction that gives up when any record
     * is already present, so an existing profile, role, or points balance is never
     * overwritten. In that case the existing record is validated and returned instead.</p>
     *
     * <p>If no answer arrives within the timeout the result is
     * {@link ProfileLoadStatus#NETWORK_ERROR}. The write may still complete later, which is
     * safe: calling this method again finds the record and returns it.</p>
     *
     * @param fullName the customer's full name; surrounding spaces are removed
     * @param callback receives the created or existing profile, or the failure, on the main
     *                 thread
     * @return a handle that stops the callback from being delivered
     */
    @NonNull
    public PendingLoad createCustomerProfileIfMissing(@NonNull String fullName,
                                                      @NonNull ProfileCallback callback) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            return deliverLater(callback, ProfileLoadResult.failure(ProfileLoadStatus.NOT_SIGNED_IN));
        }
        String uid = firebaseUser.getUid();
        String email = firebaseUser.getEmail();
        String trimmedName = fullName.trim();
        if (!UserProfileParser.isValidUid(uid) || email == null || email.isEmpty()
                || trimmedName.isEmpty()) {
            return deliverLater(callback, ProfileLoadResult.failure(ProfileLoadStatus.MALFORMED));
        }

        DatabaseReference profileRef = FirebaseAccess.userProfile(database, uid);
        ProfileCreation creation = new ProfileCreation(
                uid, newCustomerRecord(uid, trimmedName, email), callback);
        profileRef.runTransaction(creation);
        mainHandler.postDelayed(creation.timeout, timeoutMillis);
        return creation;
    }

    /**
     * Builds the record stored for a newly registered customer.
     *
     * @param uid      the Firebase Authentication UID
     * @param fullName the customer's trimmed full name
     * @param email    the sign-in email address from Firebase Authentication
     * @return the record fields: the customer role and a zero points balance
     */
    @NonNull
    static Map<String, Object> newCustomerRecord(@NonNull String uid, @NonNull String fullName,
                                                 @NonNull String email) {
        Map<String, Object> record = new HashMap<>();
        record.put(User.FIELD_USER_ID, uid);
        record.put(User.FIELD_FULL_NAME, fullName);
        record.put(User.FIELD_EMAIL, email);
        record.put(User.FIELD_ROLE, UserRole.CUSTOMER.getDatabaseValue());
        record.put(User.FIELD_POINTS_BALANCE, 0);
        return record;
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

        SingleLoad(@NonNull DatabaseReference profileRef, @NonNull String uid,
                   @NonNull ProfileCallback callback) {
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
            if (result.isSuccess() && UNCONFIRMED_CREATIONS.contains(uid)) {
                // The record may be this process's own unconfirmed write, not server data.
                result = ProfileLoadResult.failure(ProfileLoadStatus.NETWORK_ERROR);
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

    /**
     * One create-if-missing transaction that reports exactly once: with the new or existing
     * profile, a database error, a timeout, or cancellation.
     *
     * <p>{@link #doTransaction} runs on a background thread and may run several times, so it
     * only decides whether to write. Everything else runs on the main thread.</p>
     */
    private final class ProfileCreation implements Transaction.Handler, PendingLoad {

        private final String uid;
        private final Map<String, Object> newRecord;
        private final ProfileCallback callback;
        private boolean finished;
        /** True once the database has answered this transaction. */
        private boolean answered;
        /** True if this transaction was left pending and its UID was listed as unconfirmed. */
        private boolean listedUnconfirmed;

        final Runnable timeout = () -> {
            Log.w(TAG, "Profile creation timed out");
            markUnconfirmedIfPending();
            finish(ProfileLoadResult.failure(ProfileLoadStatus.NETWORK_ERROR));
        };

        ProfileCreation(@NonNull String uid, @NonNull Map<String, Object> newRecord,
                        @NonNull ProfileCallback callback) {
            this.uid = uid;
            this.newRecord = newRecord;
            this.callback = callback;
        }

        @NonNull
        @Override
        public Transaction.Result doTransaction(@NonNull MutableData currentData) {
            if (currentData.getValue() != null) {
                // A profile already exists: never overwrite it.
                return Transaction.abort();
            }
            currentData.setValue(newRecord);
            return Transaction.success(currentData);
        }

        @Override
        public void onComplete(@Nullable DatabaseError error, boolean committed,
                               @Nullable DataSnapshot snapshot) {
            ProfileLoadResult result;
            if (error != null) {
                ProfileLoadStatus status = UserProfileParser.statusForDatabaseError(error.getCode());
                Log.w(TAG, "Profile creation failed with code " + error.getCode() + ": " + status);
                result = ProfileLoadResult.failure(status);
            } else {
                // Committed: the new record. Not committed: the record that already existed.
                Object value = snapshot == null ? null : snapshot.getValue();
                try {
                    result = UserProfileParser.parse(uid, value);
                } catch (RuntimeException e) {
                    Log.w(TAG, "Existing profile could not be read", e);
                    result = ProfileLoadResult.failure(ProfileLoadStatus.MALFORMED);
                }
                if (!committed && !result.isSuccess()) {
                    Log.w(TAG, "Existing profile is unusable: " + result.getStatus());
                }
            }
            ProfileLoadResult answer = result;
            mainHandler.post(() -> {
                answered = true;
                if (listedUnconfirmed) {
                    // The server has now answered the earlier attempt for this account.
                    UNCONFIRMED_CREATIONS.remove(uid);
                    listedUnconfirmed = false;
                }
                ProfileLoadResult delivered = answer;
                if (error == null && !committed && UNCONFIRMED_CREATIONS.contains(uid)) {
                    // The "existing" record may be an earlier attempt's write that the server
                    // has not confirmed yet, so it cannot be reported as saved.
                    Log.w(TAG, "Existing profile is not confirmed by the server yet");
                    delivered = ProfileLoadResult.failure(ProfileLoadStatus.NETWORK_ERROR);
                }
                finish(delivered);
            });
        }

        @Override
        public void cancel() {
            markUnconfirmedIfPending();
            finished = true;
            mainHandler.removeCallbacks(timeout);
        }

        /**
         * Lists this account as having an unconfirmed creation if the database has not
         * answered yet. The database keeps retrying the write after the app stops waiting.
         */
        private void markUnconfirmedIfPending() {
            if (!answered && !finished && !listedUnconfirmed) {
                UNCONFIRMED_CREATIONS.add(uid);
                listedUnconfirmed = true;
            }
        }

        /**
         * Delivers the result if this operation has not already finished.
         *
         * @param result the result to deliver
         */
        private void finish(@NonNull ProfileLoadResult result) {
            if (finished) {
                return;
            }
            finished = true;
            mainHandler.removeCallbacks(timeout);
            callback.onProfileLoaded(result);
        }
    }
}
