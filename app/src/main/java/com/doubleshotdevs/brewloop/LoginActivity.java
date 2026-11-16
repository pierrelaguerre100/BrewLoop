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
import com.google.firebase.auth.FirebaseUser;

/**
 * Launcher screen: signs customers, staff, and managers in, restores an existing session,
 * and opens the screen that matches the role stored in the user's profile.
 *
 * <p>When a user is already signed in, the form is skipped and the stored profile is
 * loaded. Problems that block routing, such as a missing profile or no connection, are shown
 * in a status panel with the actions that can fix them.</p>
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
    private Button retryButton;
    private Button completeProfileButton;
    private Button statusSignOutButton;

    /** True while a sign-in request or profile load is running. */
    private boolean busy;

    /** The profile load in progress, cancelled when the screen stops. */
    @Nullable
    private UserRepository.PendingLoad pendingLoad;

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
        retryButton = findViewById(R.id.buttonLoginRetry);
        completeProfileButton = findViewById(R.id.buttonCompleteProfile);
        statusSignOutButton = findViewById(R.id.buttonLoginSignOut);

        signInButton.setOnClickListener(v -> attemptSignIn());
        registerButton.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
        retryButton.setOnClickListener(v -> loadProfileAndRoute());
        completeProfileButton.setOnClickListener(v -> startActivity(
                new Intent(this, RegisterActivity.class)
                        .putExtra(RegisterActivity.EXTRA_COMPLETE_PROFILE, true)));
        statusSignOutButton.setOnClickListener(v -> {
            FirebaseAccess.auth().signOut();
            showForm();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Restores a saved session, including after returning from registration.
        if (FirebaseAccess.auth().getCurrentUser() != null) {
            loadProfileAndRoute();
        } else {
            showForm();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        cancelPendingLoad();
        // The profile load is restarted in onStart, so the screen is never left busy.
        busy = false;
    }

    /** Validates the form and signs in with Firebase Authentication. */
    private void attemptSignIn() {
        if (busy) {
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

        setBusy(true);
        FirebaseAccess.auth()
                .signInWithEmailAndPassword(CredentialValidator.normalizeEmail(email), password)
                .addOnCompleteListener(task -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    if (task.isSuccessful()) {
                        passwordInput.setText(null);
                        loadProfileAndRoute();
                    } else {
                        // The exception class is logged; its message may contain the email.
                        Exception error = task.getException();
                        Log.w(TAG, "Sign-in failed: "
                                + (error == null ? "unknown" : error.getClass().getSimpleName()));
                        setBusy(false);
                        showFormError(AuthErrorMessages.forException(error));
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
        setBusy(true);
        statusGroup.setVisibility(View.GONE);
        formGroup.setVisibility(View.GONE);
        pendingLoad = userRepository.loadCurrentUserProfile(this::onProfileLoaded);
    }

    /**
     * Opens the screen for the user's role, or explains why it cannot.
     *
     * @param result the profile load result
     */
    private void onProfileLoaded(@NonNull ProfileLoadResult result) {
        pendingLoad = null;
        setBusy(false);
        switch (RoleRouter.route(result)) {
            case CUSTOMER_HOME:
                startActivity(new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                finish();
                break;
            case STAFF_UNAVAILABLE:
                showStatus(R.string.status_staff_unavailable, false, false);
                break;
            case COMPLETE_PROFILE:
                showStatus(R.string.status_profile_missing, false, true);
                break;
            case RETRY:
                showStatus(AuthErrorMessages.forProfileStatus(result.getStatus()), true, false);
                break;
            case SIGN_IN:
                showForm();
                break;
            case CONTACT_SHOP:
            default:
                showStatus(AuthErrorMessages.forProfileStatus(result.getStatus()), false, false);
                break;
        }
    }

    /** Shows the sign-in form and hides the status panel. */
    private void showForm() {
        cancelPendingLoad();
        setBusy(false);
        statusGroup.setVisibility(View.GONE);
        formGroup.setVisibility(View.VISIBLE);
    }

    /**
     * Shows a message for a signed-in user who cannot be routed, with the actions that apply.
     *
     * @param message         the explanation to show
     * @param canRetry        whether to offer loading the profile again
     * @param canCompleteProfile whether to offer finishing an interrupted registration
     */
    private void showStatus(@StringRes int message, boolean canRetry, boolean canCompleteProfile) {
        formGroup.setVisibility(View.GONE);
        statusGroup.setVisibility(View.VISIBLE);
        statusMessage.setText(message);
        retryButton.setVisibility(canRetry ? View.VISIBLE : View.GONE);
        completeProfileButton.setVisibility(canCompleteProfile ? View.VISIBLE : View.GONE);
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
        busy = isBusy;
        progress.setVisibility(isBusy ? View.VISIBLE : View.GONE);
        signInButton.setEnabled(!isBusy);
        registerButton.setEnabled(!isBusy);
        emailInput.setEnabled(!isBusy);
        passwordInput.setEnabled(!isBusy);
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
