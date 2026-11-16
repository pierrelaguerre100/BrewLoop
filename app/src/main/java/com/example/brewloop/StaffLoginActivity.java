package com.example.brewloop;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Locale;



/**
 * Temporary Week 2 login screen to test the staff dashboard independently.
 *
 * Staff must sign in with an account already created by the shop owner.
 */
public class StaffLoginActivity extends AppCompatActivity {

    private EditText inputEmail;
    private EditText inputPassword;
    private Button buttonStaffLogin;
    private ProgressBar progressBar;

    /** Creates the login form and connects the Sign In button. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_staff_login);

        inputEmail = findViewById(R.id.inputStaffEmail);
        inputPassword = findViewById(R.id.inputStaffPassword);
        buttonStaffLogin = findViewById(R.id.buttonStaffLogin);
        progressBar = findViewById(R.id.progressStaffLogin);

        buttonStaffLogin.setOnClickListener(v -> {
            if (validateInput()) {
                signIn();
            }
        });
    }

    /**
     * Checks that the staff email and password have been entered correctly.
     *
     * @return true if both input fields pass validation
     */
    private boolean validateInput() {
        String email = inputEmail.getText().toString().trim();
        String password = inputPassword.getText().toString();

        if (email.isEmpty()) {
            showToast("Please enter your email");
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showToast("Please enter a valid email");
            return false;
        }
        if (password.isEmpty()) {
            showToast("Please enter your password");
            return false;
        }
        return true;
    }

    /** Validates the email, password, role and signs the staff member in. */
    private void signIn() {
        String email = inputEmail.getText().toString().trim();
        String password = inputPassword.getText().toString();

        loading(true);

        FirebaseDatabase.getInstance()
                .getReference("Users")
                .orderByChild("email")
                .equalTo(email)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        loading(false);

                        // Stay on this screen if the email is not found.
                        if (!snapshot.exists()) {
                            showToast("Email not found");
                            return;
                        }

                        // Check every matching account for staff credentials.
                        for (DataSnapshot user : snapshot.getChildren()) {
                            // Read the password from Firebase.
                            Object passwordValue = user.child("password").getValue();

                            // Convert the value to a String.
                            String savedPassword = "";

                            if (passwordValue != null) {
                                savedPassword = passwordValue.toString();
                            }

                            // Read the user's role from Firebase.
                            Object roleValue = user.child("role").getValue();

                            String role = "";

                            if (roleValue != null) {
                                role = roleValue.toString();
                            }

                            // Check the password.
                            if (!password.equals(savedPassword)) {
                                showToast("Incorrect password");
                                return;
                            }

                            // Check if the user is staff.
                            if (!"staff".equalsIgnoreCase(role)) {
                                showToast("Access denied. Staff only.");
                                return;
                            }

                            // All checks passed.
                            openStaffDashboard();
                            return;
                        }

                        showToast("Incorrect staff email or password");
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        loading(false);
                        showToast("Database error: " + error.getMessage());
                    }
                });
    }

    /**
     * Saves the local demo login state and opens the Staff Dashboard.
     * This preference does not authenticate the user to Firebase.
     */
    private void openStaffDashboard() {
        SharedPreferences preferences =
                getSharedPreferences("staff_demo", MODE_PRIVATE);
        preferences.edit().putBoolean("logged_in", true).apply();

        showToast("Staff login successful!");
        startActivity(new Intent(this, StaffOrdersActivity.class));
        finish();
    }

    /**
     * Shows or hides the loading indicator while checking the database.
     *
     * @param isLoading true if the Firebase query is still running
     */
    private void loading(boolean isLoading) {
        if  (isLoading) {
            buttonStaffLogin.setVisibility(View.INVISIBLE);
            progressBar.setVisibility(View.VISIBLE);
        } else {
            buttonStaffLogin.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Displays a short Toast message.
     *
     * @param message message to display on the screen
     */
    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }
}
