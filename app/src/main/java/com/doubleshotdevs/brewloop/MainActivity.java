package com.doubleshotdevs.brewloop;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * Customer home screen, opened by {@link LoginActivity} after a customer signs in.
 *
 * <p>Shows the signed-in customer's own profile and links to the menu and rewards screens.
 * Only the customer's own {@code Users/{uid}} record is read, through
 * {@link UserRepository}. If the user signs out or a different account signs in, the screen
 * closes and the app returns to the sign-in screen.</p>
 */
public class MainActivity extends AppCompatActivity {

    DatabaseReference usersReference;
    DatabaseReference menuReference;
    DatabaseReference optionsReference;
    DatabaseReference ordersReference;
    DatabaseReference orderItemsReference;
    DatabaseReference pointsReference;

    FirebaseDatabase firebaseDatabase;

    private final UserRepository userRepository = new UserRepository();

    private TextView textViewWelcome;
    private TextView textViewPoints;
    private TextView textViewHomeError;
    private ProgressBar progressHome;
    private Button buttonMenu;
    private Button buttonRewards;
    private Button buttonRetry;

    /** UID of the customer this screen was opened for. */
    @Nullable
    private String signedInUid;

    @Nullable
    private UserRepository.PendingLoad pendingLoad;

    /** Closes this screen if the user signs out or the account changes. */
    private final FirebaseAuth.AuthStateListener accountWatcher = firebaseAuth -> {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (signedInUid != null && (user == null || !signedInUid.equals(user.getUid()))) {
            Session.restartAtSignIn(this);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main), (v, insets) -> {
                    Insets systemBars =
                            insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(
                            systemBars.left,
                            systemBars.top,
                            systemBars.right,
                            systemBars.bottom
                    );
                    return insets;
                }
        );

        firebaseDatabase = FirebaseAccess.database();
        usersReference = firebaseDatabase.getReference(FirebaseNodes.USERS);
        menuReference = firebaseDatabase.getReference("MenuItems");
        optionsReference = firebaseDatabase.getReference("CustomizationOption");
        ordersReference = firebaseDatabase.getReference("Orders");
        orderItemsReference = firebaseDatabase.getReference("OrderItems");
        pointsReference = firebaseDatabase.getReference("PointsTransaction");

        textViewWelcome = findViewById(R.id.textviewWelcome);
        textViewPoints = findViewById(R.id.textviewPoints);
        textViewHomeError = findViewById(R.id.textviewHomeError);
        progressHome = findViewById(R.id.progressHome);
        buttonMenu = findViewById(R.id.buttonMenu);
        buttonRewards = findViewById(R.id.buttonRewards);
        buttonRetry = findViewById(R.id.buttonHomeRetry);

        // The starter's sample upload button was removed with the starter screen, and there
        // is no sample-writing code. Automatic starter and bulk reads remain disabled; the
        // customer's own profile is loaded through UserRepository in onStart.
        buttonMenu.setOnClickListener(v ->
                startActivity(new Intent(this, MenuActivity.class)));
        buttonRewards.setOnClickListener(v -> {
            if (signedInUid != null) {
                startActivity(new Intent(this, RewardsActivity.class)
                        .putExtra(RewardsActivity.EXTRA_USER_ID, signedInUid));
            }
        });
        buttonRetry.setOnClickListener(v -> loadProfile());
        findViewById(R.id.buttonSignOut).setOnClickListener(v -> Session.signOut(this));
    }

    @Override
    protected void onStart() {
        super.onStart();
        String uid = Session.requireSignedInUid(this);
        if (uid == null) {
            return;
        }
        if (signedInUid != null && !signedInUid.equals(uid)) {
            // A different account signed in while this screen was hidden.
            Session.restartAtSignIn(this);
            return;
        }
        signedInUid = uid;
        FirebaseAccess.auth().addAuthStateListener(accountWatcher);
        loadProfile();
    }

    @Override
    protected void onStop() {
        super.onStop();
        FirebaseAccess.auth().removeAuthStateListener(accountWatcher);
        if (pendingLoad != null) {
            pendingLoad.cancel();
            pendingLoad = null;
        }
    }

    /** Loads the signed-in customer's own profile and shows it. */
    private void loadProfile() {
        if (pendingLoad != null) {
            pendingLoad.cancel();
        }
        progressHome.setVisibility(View.VISIBLE);
        textViewHomeError.setVisibility(View.GONE);
        buttonRetry.setVisibility(View.GONE);
        setCustomerActionsEnabled(false);
        pendingLoad = userRepository.loadCurrentUserProfile(this::onProfileLoaded);
    }

    /**
     * Shows the loaded profile, or returns to the sign-in screen if it is not a usable
     * customer profile for the account this screen was opened for.
     *
     * @param result the profile load result
     */
    private void onProfileLoaded(@NonNull ProfileLoadResult result) {
        pendingLoad = null;
        progressHome.setVisibility(View.GONE);
        RoleRouter.Destination destination = RoleRouter.route(result);
        User user = result.getUser();
        if (destination == RoleRouter.Destination.CUSTOMER_HOME && user != null
                && user.userID.equals(signedInUid)) {
            textViewWelcome.setText(getString(R.string.home_welcome, user.fullName));
            textViewPoints.setText(getResources().getQuantityString(
                    R.plurals.home_points, user.pointsBalance, user.pointsBalance));
            setCustomerActionsEnabled(true);
        } else if (destination == RoleRouter.Destination.RETRY) {
            textViewHomeError.setText(AuthErrorMessages.forProfileStatus(result.getStatus()));
            textViewHomeError.setVisibility(View.VISIBLE);
            buttonRetry.setVisibility(View.VISIBLE);
        } else {
            // Not a usable customer profile: the sign-in screen explains what to do.
            Session.restartAtSignIn(this);
        }
    }

    /**
     * Enables or disables the buttons that need a loaded customer profile.
     *
     * @param enabled whether the customer profile is loaded
     */
    private void setCustomerActionsEnabled(boolean enabled) {
        buttonMenu.setEnabled(enabled);
        buttonRewards.setEnabled(enabled);
    }

    /**
     * Displays records from an explicitly authorized database reference.
     *
     * <p>Supports structured records and legacy scalar values. This helper is
     * not called automatically and must not be used for bulk customer data.</p>
     *
     * @param databaseReference reference whose records may be displayed
     * @param textView destination for the formatted records
     * @param title heading displayed above the records
     */
    @SuppressWarnings("unused")
    private void readData(
            DatabaseReference databaseReference,
            TextView textView,
            String title
    ) {
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                StringBuilder allValues =
                        new StringBuilder(title).append('\n');

                if (snapshot.exists()) {
                    for (DataSnapshot childSnapshot : snapshot.getChildren()) {
                        allValues.append(childSnapshot.getKey()).append(": ");

                        if (childSnapshot.hasChildren()) {
                            for (DataSnapshot fieldSnapshot
                                    : childSnapshot.getChildren()) {
                                allValues.append(fieldSnapshot.getKey())
                                        .append("=")
                                        .append(String.valueOf(fieldSnapshot.getValue()))
                                        .append("  ");
                            }
                        } else {
                            allValues.append(
                                    String.valueOf(childSnapshot.getValue())
                            );
                        }

                        allValues.append('\n');
                    }
                } else {
                    Log.d("FirebaseData", "No data found at this reference");
                }

                textView.setText(allValues.toString());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("FirebaseData", "Database read failed", error.toException());
                Toast.makeText(
                        MainActivity.this,
                        "Failed to get data",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }
}
