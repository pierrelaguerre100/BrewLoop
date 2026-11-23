package com.doubleshotdevs.brewloop;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Runs customer registration and profile recovery for {@link RegisterActivity}, keeping the
 * request alive while the screen is recreated, for example by a rotation.
 *
 * <p>Registration creates the Firebase Authentication account, then creates the customer
 * profile only if none exists. Only one request runs at a time, so repeated taps cannot
 * create a second account. If the account is created but the profile cannot be saved, the
 * state switches to "finish setup" mode, where saving the profile can be retried without
 * creating another account. A result that arrives after a different account has signed in
 * is discarded.</p>
 */
public class RegistrationViewModel extends ViewModel {

    private static final String TAG = "RegistrationViewModel";

    /** What the registration screen should show. */
    public enum Phase {
        /** Waiting for the user to submit the form. */
        IDLE,
        /** An account or profile request is running; the form is disabled. */
        WORKING,
        /** The profile exists; the app should route by the stored role. */
        SUCCEEDED,
        /** The account this screen was working for is no longer signed in. */
        SIGNED_OUT
    }

    /** An immutable snapshot of the registration state. */
    public static final class State {
        @NonNull
        public final Phase phase;
        /** Whether only the profile still needs to be saved for the signed-in account. */
        public final boolean completeProfileMode;
        /** The error to show, or 0 for none. */
        @StringRes
        public final int errorMessage;

        State(@NonNull Phase phase, boolean completeProfileMode, @StringRes int errorMessage) {
            this.phase = phase;
            this.completeProfileMode = completeProfileMode;
            this.errorMessage = errorMessage;
        }
    }

    private final MutableLiveData<State> state = new MutableLiveData<>();
    private final FirebaseAuth auth;
    private final UserRepository userRepository;

    @Nullable
    private UserRepository.PendingLoad pendingCreation;
    private boolean cleared;

    /** Creates a view model that uses the app's configured Firebase instances. */
    public RegistrationViewModel() {
        this(FirebaseAccess.auth(), new UserRepository());
    }

    /**
     * Creates a view model with explicit dependencies.
     *
     * @param auth           the Firebase Authentication instance
     * @param userRepository the repository that creates the profile
     */
    RegistrationViewModel(@NonNull FirebaseAuth auth, @NonNull UserRepository userRepository) {
        this.auth = auth;
        this.userRepository = userRepository;
    }

    /**
     * Sets the starting mode the first time the screen is created. Later calls, such as
     * after a rotation, keep the current state.
     *
     * @param completeProfileMode whether to start in "finish setup" mode
     */
    public void initialize(boolean completeProfileMode) {
        if (state.getValue() == null) {
            state.setValue(new State(Phase.IDLE, completeProfileMode, 0));
        }
    }

    /**
     * Returns the observable registration state.
     *
     * @return the state, which always has a value after {@link #initialize}
     */
    @NonNull
    public LiveData<State> getState() {
        return state;
    }

    /**
     * Indicates whether a request is running.
     *
     * @return {@code true} while an account or profile request is running
     */
    public boolean isWorking() {
        State current = state.getValue();
        return current != null && current.phase == Phase.WORKING;
    }

    /**
     * Creates the Authentication account, then the customer profile.
     *
     * <p>The password is passed straight to Firebase Authentication and is not kept.</p>
     *
     * @param fullName the validated full name
     * @param email    the normalized email address
     * @param password the validated password
     */
    public void register(@NonNull String fullName, @NonNull String email,
                         @NonNull String password) {
        if (isWorking() || currentMode()) {
            return;
        }
        set(Phase.WORKING, false, 0);
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (cleared) {
                return;
            }
            FirebaseUser created = task.isSuccessful() && task.getResult() != null
                    ? task.getResult().getUser() : null;
            if (created != null) {
                saveProfile(fullName, created.getUid());
                return;
            }
            // Only a short code is logged; the message may contain the email address.
            Log.w(TAG, "Account creation failed: "
                    + AuthErrorMessages.diagnosticCode(task.getException()));
            set(Phase.IDLE, false, AuthErrorMessages.forException(task.getException()));
        });
    }

    /**
     * Creates the customer profile for the signed-in account if it does not exist yet. Used
     * in "finish setup" mode.
     *
     * @param fullName the validated full name
     */
    public void saveProfileForSignedInUser(@NonNull String fullName) {
        if (isWorking()) {
            return;
        }
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            set(Phase.SIGNED_OUT, true, 0);
            return;
        }
        saveProfile(fullName, user.getUid());
    }

    /**
     * Creates the profile and reports the outcome, unless a different account has signed in
     * in the meantime.
     *
     * @param fullName    the validated full name
     * @param expectedUid the account the profile is being created for
     */
    private void saveProfile(@NonNull String fullName, @NonNull String expectedUid) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || !expectedUid.equals(user.getUid())) {
            set(Phase.SIGNED_OUT, true, 0);
            return;
        }
        set(Phase.WORKING, currentMode(), 0);
        pendingCreation = userRepository.createCustomerProfileIfMissing(fullName, result -> {
            pendingCreation = null;
            if (cleared) {
                return;
            }
            FirebaseUser current = auth.getCurrentUser();
            if (current == null || !expectedUid.equals(current.getUid())) {
                set(Phase.SIGNED_OUT, true, 0);
            } else if (result.isSuccess()) {
                // The profile may be one that already existed; routing uses its stored role.
                set(Phase.SUCCEEDED, true, 0);
            } else {
                // The account exists but has no confirmed profile: offer a retry without
                // creating another account. A timed-out write may still finish, which is safe
                // because the retry returns the existing profile.
                set(Phase.IDLE, true, result.getStatus() == ProfileLoadStatus.NETWORK_ERROR
                        ? R.string.error_profile_save_network
                        : AuthErrorMessages.forProfileStatus(result.getStatus()));
            }
        });
    }

    /**
     * Returns whether the state is currently in "finish setup" mode.
     *
     * @return the current mode
     */
    private boolean currentMode() {
        State current = state.getValue();
        return current != null && current.completeProfileMode;
    }

    /**
     * Publishes a new state.
     *
     * @param phase               the phase
     * @param completeProfileMode whether only the profile still needs to be saved
     * @param errorMessage        the error to show, or 0 for none
     */
    private void set(@NonNull Phase phase, boolean completeProfileMode,
                     @StringRes int errorMessage) {
        state.setValue(new State(phase, completeProfileMode, errorMessage));
    }

    @Override
    protected void onCleared() {
        cleared = true;
        if (pendingCreation != null) {
            // The create-if-missing write may still finish; it is safe to repeat.
            pendingCreation.cancel();
            pendingCreation = null;
        }
    }
}
