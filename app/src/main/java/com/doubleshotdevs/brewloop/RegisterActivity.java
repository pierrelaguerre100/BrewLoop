package com.doubleshotdevs.brewloop;

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
 * Customer registration, and recovery for an account whose registration was interrupted.
 *
 * <p>Registration creates a Firebase Authentication account and then the customer profile
 * at {@code Users/{uid}}. There is no role choice: every profile created here has the role
 * {@code customer} and zero points. If the account is created but the profile cannot be
 * saved, the screen switches to "finish setup" mode, where the user can retry saving the
 * profile without creating another account.</p>
 *
 * <p>Opened with {@link #EXTRA_COMPLETE_PROFILE} set to {@code true}, the screen starts in
 * that mode for the signed-in user. The profile is only ever created when none exists, so
 * an existing profile, role, or points balance is never overwritten.</p>
 */
public class RegisterActivity extends AppCompatActivity {

    /** Boolean extra: start in "finish setup" mode for the signed-in user. */
    public static final String EXTRA_COMPLETE_PROFILE = "completeProfile";

    private static final String STATE_COMPLETE_PROFILE = "completeProfileMode";
    private static final String TAG = "RegisterActivity";

    private final UserRepository userRepository = new UserRepository();

    private TextView title;
    private TextView intro;
    private TextInputLayout nameLayout;
    private TextInputLayout emailLayout;
    private TextInputLayout passwordLayout;
    private TextInputLayout confirmLayout;
    private TextInputEditText nameInput;
    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private TextInputEditText confirmInput;
    private Button submitButton;
    private Button signOutButton;
    private ProgressBar progress;
    private TextView errorText;

    /** True when the account exists and only the profile still needs to be saved. */
    private boolean completeProfileMode;

    /** True while an account or profile request is running. */
    private boolean busy;

    @Nullable
    private UserRepository.PendingLoad pendingCreation;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        title = findViewById(R.id.textRegisterTitle);
        intro = findViewById(R.id.textRegisterIntro);
        nameLayout = findViewById(R.id.layoutRegisterName);
        emailLayout = findViewById(R.id.layoutRegisterEmail);
        passwordLayout = findViewById(R.id.layoutRegisterPassword);
        confirmLayout = findViewById(R.id.layoutRegisterConfirm);
        nameInput = findViewById(R.id.inputRegisterName);
        emailInput = findViewById(R.id.inputRegisterEmail);
        passwordInput = findViewById(R.id.inputRegisterPassword);
        confirmInput = findViewById(R.id.inputRegisterConfirm);
        submitButton = findViewById(R.id.buttonRegister);
        signOutButton = findViewById(R.id.buttonRegisterSignOut);
        progress = findViewById(R.id.progressRegister);
        errorText = findViewById(R.id.textRegisterError);

        submitButton.setOnClickListener(v -> submit());
        signOutButton.setOnClickListener(v -> Session.signOut(this));

        completeProfileMode = savedInstanceState != null
                ? savedInstanceState.getBoolean(STATE_COMPLETE_PROFILE)
                : getIntent().getBooleanExtra(EXTRA_COMPLETE_PROFILE, false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser user = FirebaseAccess.auth().getCurrentUser();
        if (completeProfileMode && user == null) {
            // Signed out elsewhere; there is no account to finish setting up.
            Session.restartAtSignIn(this);
            return;
        }
        if (!completeProfileMode && user != null) {
            // An account is already signed in, so registering another is not allowed here.
            Session.restartAtSignIn(this);
            return;
        }
        applyMode();
        setBusy(false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (pendingCreation != null) {
            // The create-if-missing write may still finish; it is safe to repeat.
            pendingCreation.cancel();
            pendingCreation = null;
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_COMPLETE_PROFILE, completeProfileMode);
    }

    /** Validates the form, then registers or finishes setting up the account. */
    private void submit() {
        if (busy) {
            return;
        }
        errorText.setVisibility(View.GONE);
        String name = LoginActivity.textOf(nameInput);
        CredentialValidator.Error nameError = CredentialValidator.validateName(name);
        nameLayout.setError(errorMessage(nameError));

        if (completeProfileMode) {
            if (nameError == null) {
                saveProfile(name);
            }
            return;
        }

        String email = LoginActivity.textOf(emailInput);
        String password = LoginActivity.textOf(passwordInput);
        CredentialValidator.Error emailError = CredentialValidator.validateEmail(email);
        CredentialValidator.Error passwordError = CredentialValidator.validateNewPassword(
                password, LoginActivity.textOf(confirmInput));
        emailLayout.setError(errorMessage(emailError));
        boolean mismatch = passwordError == CredentialValidator.Error.PASSWORDS_DO_NOT_MATCH;
        passwordLayout.setError(mismatch ? null : errorMessage(passwordError));
        confirmLayout.setError(mismatch ? errorMessage(passwordError) : null);
        if (nameError != null || emailError != null || passwordError != null) {
            return;
        }

        setBusy(true);
        FirebaseAccess.auth()
                .createUserWithEmailAndPassword(CredentialValidator.normalizeEmail(email), password)
                .addOnCompleteListener(task -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    if (task.isSuccessful()) {
                        passwordInput.setText(null);
                        confirmInput.setText(null);
                        saveProfile(name);
                    } else {
                        Exception error = task.getException();
                        Log.w(TAG, "Account creation failed: "
                                + (error == null ? "unknown" : error.getClass().getSimpleName()));
                        setBusy(false);
                        showError(AuthErrorMessages.forException(error));
                    }
                });
    }

    /**
     * Creates the customer profile for the signed-in account if it does not exist yet.
     *
     * @param name the customer's full name
     */
    private void saveProfile(@NonNull String name) {
        setBusy(true);
        pendingCreation = userRepository.createCustomerProfileIfMissing(name, result -> {
            pendingCreation = null;
            if (result.isSuccess()) {
                // The sign-in screen routes by the stored role, which may belong to a profile
                // that already existed.
                Session.restartAtSignIn(this);
                return;
            }
            // The account exists but has no usable profile: offer a retry without creating
            // another account.
            completeProfileMode = true;
            applyMode();
            setBusy(false);
            showError(result.getStatus() == ProfileLoadStatus.NETWORK_ERROR
                    ? R.string.error_profile_save_network
                    : AuthErrorMessages.forProfileStatus(result.getStatus()));
        });
    }

    /** Shows the fields and text for the current mode. */
    private void applyMode() {
        int credentialVisibility = completeProfileMode ? View.GONE : View.VISIBLE;
        emailLayout.setVisibility(credentialVisibility);
        passwordLayout.setVisibility(credentialVisibility);
        confirmLayout.setVisibility(credentialVisibility);
        signOutButton.setVisibility(completeProfileMode ? View.VISIBLE : View.GONE);
        if (completeProfileMode) {
            FirebaseUser user = FirebaseAccess.auth().getCurrentUser();
            String email = user == null || user.getEmail() == null ? "" : user.getEmail();
            title.setText(R.string.title_complete_profile);
            intro.setText(getString(R.string.complete_profile_intro, email));
            submitButton.setText(R.string.action_save_profile);
        } else {
            title.setText(R.string.title_register);
            intro.setText(R.string.register_intro);
            submitButton.setText(R.string.action_register);
        }
    }

    /**
     * Shows an error below the form.
     *
     * @param message the error to show
     */
    private void showError(@StringRes int message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }

    /**
     * Returns the message for a validation problem.
     *
     * @param error the problem, or {@code null}
     * @return the message, or {@code null} if there is no problem
     */
    @Nullable
    private String errorMessage(@Nullable CredentialValidator.Error error) {
        return error == null ? null : getString(AuthErrorMessages.forValidation(error));
    }

    /**
     * Disables the controls while a request runs, so it cannot be submitted twice.
     *
     * @param isBusy whether a request is running
     */
    private void setBusy(boolean isBusy) {
        busy = isBusy;
        progress.setVisibility(isBusy ? View.VISIBLE : View.GONE);
        submitButton.setEnabled(!isBusy);
        signOutButton.setEnabled(!isBusy);
        nameInput.setEnabled(!isBusy);
        emailInput.setEnabled(!isBusy);
        passwordInput.setEnabled(!isBusy);
        confirmInput.setEnabled(!isBusy);
    }
}
