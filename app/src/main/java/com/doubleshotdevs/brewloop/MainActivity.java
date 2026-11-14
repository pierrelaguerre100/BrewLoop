package com.doubleshotdevs.brewloop;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends AppCompatActivity {

    DatabaseReference usersReference;
    DatabaseReference menuReference;
    DatabaseReference optionsReference;
    DatabaseReference ordersReference;
    DatabaseReference orderItemsReference;
    DatabaseReference pointsReference;

    FirebaseDatabase firebaseDatabase;

    TextView textViewUsers;
    TextView textViewMenu;
    TextView textViewOptions;
    TextView textViewOrders;
    TextView textViewOrderItems;
    TextView textViewPoints;

    Button buttonUpload;

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

        textViewUsers = findViewById(R.id.textviewUsers);
        textViewMenu = findViewById(R.id.textviewMenu);
        textViewOptions = findViewById(R.id.textviewOptions);
        textViewOrders = findViewById(R.id.textviewOrders);
        textViewOrderItems = findViewById(R.id.textviewOrderItems);
        textViewPoints = findViewById(R.id.textviewPoints);
        buttonUpload = findViewById(R.id.buttonUpload);

        // Sample uploads overwrite shared records, so no writer is attached.
        buttonUpload.setEnabled(false);
        buttonUpload.setText(R.string.sample_upload_disabled);

        findViewById(R.id.buttonRewards).setOnClickListener(v ->
                startActivity(new Intent(this, RewardsActivity.class)));

        // Automatic starter reads remain disabled.
        // Load the authenticated user's profile through UserRepository.
        // Customer orders and points require user-scoped queries.
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