package com.doubleshotdevs.brewloop;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Launcher screen: signs customers, staff, and managers in, restores an existing session,
 * and opens the screen that matches the role stored in the user's profile.
 *
 * <p>Routing is driven by the Firebase Authentication state, so it also happens when a
 * sign-in finishes after the screen was recreated, for example by a rotation. Problems that
 * block routing, such as a missing profile or no connection, are shown in a status panel
 * with the actions that can fix them.</p>
 */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    private final UserRepository userRepository = new UserRepository();

    private View formGroup;
    private TextInputLayout emailLayout;
    private TextInputLayout passwordLayout;
    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private Button signInButton;
    private Button registerButton;
    private ProgressBar progress;
    private TextView formError;

    private View statusGroup;
    private TextView statusMessage;
    private Button managerMenuButton;
    private Button retryButton;
    private Button completeProfileButton;
    private Button statusSignOutButton;

    /** True while a sign-in request started by this screen is running. */
    private boolean signInInFlight;

    /** UID whose profile is being loaded or was last routed, or {@code null}. */
    @Nullable
    private String routedUid;

    /** The profile load in progress, cancelled when the screen stops. */
    @Nullable
    private UserRepository.PendingLoad pendingLoad;

    /** Routes whenever a user signs in, and shows the form when nobody is signed in. */
    private final FirebaseAuth.AuthStateListener authWatcher = firebaseAuth -> {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            if (!signInInFlight) {
                showForm();
            }
        } else if (!user.getUid().equals(routedUid)) {
            loadProfileAndRoute();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        formGroup = findViewById(R.id.groupLoginForm);
        emailLayout = findViewById(R.id.layoutLoginEmail);
        passwordLayout = findViewById(R.id.layoutLoginPassword);
        emailInput = findViewById(R.id.inputLoginEmail);
        passwordInput = findViewById(R.id.inputLoginPassword);
        signInButton = findViewById(R.id.buttonSignIn);
        registerButton = findViewById(R.id.buttonOpenRegister);
        progress = findViewById(R.id.progressLogin);
        formError = findViewById(R.id.textLoginError);

        statusGroup = findViewById(R.id.groupLoginStatus);
        statusMessage = findViewById(R.id.textLoginStatus);
        managerMenuButton = findViewById(R.id.buttonOpenManagerMenu);
        retryButton = findViewById(R.id.buttonLoginRetry);
        completeProfileButton = findViewById(R.id.buttonCompleteProfile);
        statusSignOutButton = findViewById(R.id.buttonLoginSignOut);

        signInButton.setOnClickListener(v -> attemptSignIn());
        registerButton.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
        managerMenuButton.setOnClickListener(v ->
                startActivity(new Intent(this, ManagerMenuActivity.class)));
        retryButton.setOnClickListener(v -> loadProfileAndRoute());
        completeProfileButton.setOnClickListener(v -> startActivity(
                new Intent(this, RegisterActivity.class)
                        .putExtra(RegisterActivity.EXTRA_COMPLETE_PROFILE, true)));
        statusSignOutButton.setOnClickListener(v -> {
            Session.endSession();
            showForm();
        });

        // Emulator build only: Android 17 needs this permission to reach the emulators.
        FirebaseEnvironment.requestLocalNetworkAccess(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Called immediately with the current user, which restores a saved session.
        FirebaseAccess.auth().addAuthStateListener(authWatcher);
    }

    @Override
    protected void onStop() {
        super.onStop();
        FirebaseAccess.auth().removeAuthStateListener(authWatcher);
        cancelPendingLoad();
        // The profile is loaded again when the screen returns.
        routedUid = null;
        setBusy(signInInFlight);
    }

    /** Validates the form and signs in with Firebase Authentication. */
    private void attemptSignIn() {
        if (signInInFlight || pendingLoad != null) {
            return;
        }
        String email = textOf(emailInput);
        String password = textOf(passwordInput);

        CredentialValidator.Error emailError = CredentialValidator.validateEmail(email);
        CredentialValidator.Error passwordError = CredentialValidator.validateSignInPassword(password);
        emailLayout.setError(emailError == null ? null
                : getString(AuthErrorMessages.forValidation(emailError)));
        passwordLayout.setError(passwordError == null ? null
                : getString(AuthErrorMessages.forValidation(passwordError)));
        formError.setVisibility(View.GONE);
        if (emailError != null || passwordError != null) {
            return;
        }

        signInInFlight = true;
        setBusy(true);
        FirebaseAccess.auth()
                .signInWithEmailAndPassword(CredentialValidator.normalizeEmail(email), password)
                .addOnCompleteListener(task -> {
                    if (isFinishing() || isDestroyed()) {
                        // A recreated screen routes through its Authentication listener.
                        return;
                    }
                    signInInFlight = false;
                    if (task.isSuccessful()) {
                        passwordInput.setText(null);
                        FirebaseUser user = FirebaseAccess.auth().getCurrentUser();
                        if (user != null && !user.getUid().equals(routedUid)) {
                            loadProfileAndRoute();
                        }
                    } else {
                        // Only a short code is logged; the message may contain the email.
                        Log.w(TAG, "Sign-in failed: "
                                + AuthErrorMessages.diagnosticCode(task.getException()));
                        setBusy(false);
                        showFormError(AuthErrorMessages.forException(task.getException()));
                    }
                });
    }

    /** Loads the signed-in user's own profile, then routes by its stored role. */
    private void loadProfileAndRoute() {
        cancelPendingLoad();
        FirebaseUser user = FirebaseAccess.auth().getCurrentUser();
        if (user == null) {
            showForm();
            return;
        }
        String uid = user.getUid();
        routedUid = uid;
        setBusy(true);
        statusGroup.setVisibility(View.GONE);
        formGroup.setVisibility(View.GONE);
        pendingLoad = userRepository.loadCurrentUserProfile(result -> onProfileLoaded(uid, result));
    }

    /**
     * Opens the screen for the user's role, or explains why it cannot.
     *
     * @param uid    the account the profile was loaded for
     * @param result the profile load result
     */
    private void onProfileLoaded(@NonNull String uid, @NonNull ProfileLoadResult result) {
        pendingLoad = null;
        setBusy(false);
        FirebaseUser current = FirebaseAccess.auth().getCurrentUser();
        if (current == null) {
            showForm();
            return;
        }
        if (!uid.equals(current.getUid())) {
            // Another account signed in while this profile was loading; ignore the old result.
            loadProfileAndRoute();
            return;
        }
        switch (RoleRouter.route(result)) {
            case CUSTOMER_HOME:
                Session.claimCartFor(uid);
                startActivity(new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                finish();
                break;
            case MANAGER_TOOLS:
                showStatus(R.string.status_manager_tools, false, false, true);
                break;
            case STAFF_UNAVAILABLE:
                showStatus(R.string.status_staff_unavailable, false, false, false);
                break;
            case COMPLETE_PROFILE:
                showStatus(R.string.status_profile_missing, false, true, false);
                break;
            case RETRY:
                showStatus(AuthErrorMessages.forProfileStatus(result.getStatus()), true, false, false);
                break;
            case SIGN_IN:
                showForm();
                break;
            case CONTACT_SHOP:
            default:
                showStatus(AuthErrorMessages.forProfileStatus(result.getStatus()),
                        false, false, false);
                break;
        }
    }

    /** Shows the sign-in form and hides the status panel. */
    private void showForm() {
        cancelPendingLoad();
        routedUid = null;
        setBusy(signInInFlight);
        statusGroup.setVisibility(View.GONE);
        formGroup.setVisibility(View.VISIBLE);
    }

    /**
     * Shows a message for a signed-in user who cannot be routed, with the actions that apply.
     *
     * @param message            the explanation to show
     * @param canRetry           whether to offer loading the profile again
     * @param canCompleteProfile whether to offer finishing an interrupted registration
     * @param canManageMenu      whether to offer the manager's menu management screen
     */
    private void showStatus(@StringRes int message, boolean canRetry, boolean canCompleteProfile,
                            boolean canManageMenu) {
        formGroup.setVisibility(View.GONE);
        statusGroup.setVisibility(View.VISIBLE);
        statusMessage.setText(message);
        retryButton.setVisibility(canRetry ? View.VISIBLE : View.GONE);
        completeProfileButton.setVisibility(canCompleteProfile ? View.VISIBLE : View.GONE);
        managerMenuButton.setVisibility(canManageMenu ? View.VISIBLE : View.GONE);
    }

    /**
     * Shows an error below the sign-in form.
     *
     * @param message the error to show
     */
    private void showFormError(@StringRes int message) {
        formError.setText(message);
        formError.setVisibility(View.VISIBLE);
    }

    /**
     * Disables the controls while a request runs, so it cannot be submitted twice.
     *
     * @param isBusy whether a request is running
     */
    private void setBusy(boolean isBusy) {
        progress.setVisibility(isBusy ? View.VISIBLE : View.GONE);
        signInButton.setEnabled(!isBusy);
        registerButton.setEnabled(!isBusy);
        emailInput.setEnabled(!isBusy);
        passwordInput.setEnabled(!isBusy);
        managerMenuButton.setEnabled(!isBusy);
        retryButton.setEnabled(!isBusy);
        completeProfileButton.setEnabled(!isBusy);
        statusSignOutButton.setEnabled(!isBusy);
    }

    /** Cancels the profile load in progress, if any. */
    private void cancelPendingLoad() {
        if (pendingLoad != null) {
            pendingLoad.cancel();
            pendingLoad = null;
        }
    }

    /**
     * Returns the text of an input field.
     *
     * @param input the field
     * @return its text, or an empty string
     */
    @NonNull
    static String textOf(@NonNull TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }
}
