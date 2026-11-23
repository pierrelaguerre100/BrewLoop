package com.doubleshotdevs.brewloop;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

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
 * an existing profile, role, or points balance is never overwritten. The requests run in
 * {@link RegistrationViewModel}, so they continue if the screen is recreated.</p>
 */
public class RegisterActivity extends AppCompatActivity {

    /** Boolean extra: start in "finish setup" mode for the signed-in user. */
    public static final String EXTRA_COMPLETE_PROFILE = "completeProfile";

    private RegistrationViewModel viewModel;

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

        viewModel = new ViewModelProvider(this).get(RegistrationViewModel.class);
        viewModel.initialize(getIntent().getBooleanExtra(EXTRA_COMPLETE_PROFILE, false));

        submitButton.setOnClickListener(v -> submit());
        signOutButton.setOnClickListener(v -> Session.signOut(this));
        viewModel.getState().observe(this, this::render);
    }

    @Override
    protected void onStart() {
        super.onStart();
        RegistrationViewModel.State state = viewModel.getState().getValue();
        boolean completeProfileMode = state != null && state.completeProfileMode;
        FirebaseUser user = FirebaseAccess.auth().getCurrentUser();
        if (completeProfileMode && user == null) {
            // Signed out elsewhere; there is no account to finish setting up.
            Session.restartAtSignIn(this);
        } else if (!completeProfileMode && user != null && !viewModel.isWorking()) {
            // An account is already signed in, so registering another is not allowed here.
            Session.restartAtSignIn(this);
        }
    }

    /** Validates the form, then registers or finishes setting up the account. */
    private void submit() {
        if (viewModel.isWorking()) {
            return;
        }
        errorText.setVisibility(View.GONE);
        String name = LoginActivity.textOf(nameInput);
        CredentialValidator.Error nameError = CredentialValidator.validateName(name);
        nameLayout.setError(errorMessage(nameError));

        RegistrationViewModel.State state = viewModel.getState().getValue();
        if (state != null && state.completeProfileMode) {
            if (nameError == null) {
                viewModel.saveProfileForSignedInUser(name.trim());
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
        viewModel.register(name.trim(), CredentialValidator.normalizeEmail(email), password);
    }

    /**
     * Shows the current registration state.
     *
     * @param state the state to show
     */
    private void render(@NonNull RegistrationViewModel.State state) {
        if (state.phase == RegistrationViewModel.Phase.SUCCEEDED
                || state.phase == RegistrationViewModel.Phase.SIGNED_OUT) {
            // The sign-in screen routes by the stored role, or shows the form again.
            Session.restartAtSignIn(this);
            return;
        }
        applyMode(state.completeProfileMode);
        if (state.completeProfileMode) {
            // The account exists, so the password fields are no longer needed.
            passwordInput.setText(null);
            confirmInput.setText(null);
        }
        setBusy(state.phase == RegistrationViewModel.Phase.WORKING);
        if (state.errorMessage != 0) {
            errorText.setText(state.errorMessage);
            errorText.setVisibility(View.VISIBLE);
        } else {
            errorText.setVisibility(View.GONE);
        }
    }

    /**
     * Shows the fields and text for a mode.
     *
     * @param completeProfileMode whether only the profile still needs to be saved
     */
    private void applyMode(boolean completeProfileMode) {
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
        progress.setVisibility(isBusy ? View.VISIBLE : View.GONE);
        submitButton.setEnabled(!isBusy);
        signOutButton.setEnabled(!isBusy);
        nameInput.setEnabled(!isBusy);
        emailInput.setEnabled(!isBusy);
        passwordInput.setEnabled(!isBusy);
        confirmInput.setEnabled(!isBusy);
    }
}
