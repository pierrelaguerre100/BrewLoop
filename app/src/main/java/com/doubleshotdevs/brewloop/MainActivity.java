package com.doubleshotdevs.brewloop;

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

    FirebaseDatabase firebaseDatabase;
    TextView textViewUsers;
    TextView textViewMenu;


    Button buttonUpload;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        firebaseDatabase = FirebaseAccess.database();
        usersReference = firebaseDatabase.getReference(FirebaseNodes.USERS);
        menuReference = firebaseDatabase.getReference(FirebaseNodes.LEGACY_MENU);

        textViewUsers = findViewById(R.id.textviewUsers);
        textViewMenu = findViewById(R.id.textviewMenu);
        buttonUpload = findViewById(R.id.buttonUpload);

        // The sample upload overwrites shared Users/Menu records in the live database,
        // so the button is disabled and no click listener is attached.
        buttonUpload.setEnabled(false);
        buttonUpload.setText(R.string.sample_upload_disabled);

        // Automatic starter reads are off. Users is never read in bulk: each user may read
        // only their own profile, through UserRepository. The legacy Menu display can be
        // restored with readData(menuReference, textViewMenu) once local emulator testing
        // of database access is available.
        }

    /**
      Writes the original placeholder Users and Menu records.

     <p>Not called anywhere: these writes replace existing records with the same keys
      in the shared live database. Kept only as a reference for the sample data format.</p>
     */
    @SuppressWarnings("unused")
    private void uploadSampleData() {
        addNewData(usersReference, "u001", "Cristiano Ronaldo, customer, 38 pts");
        addNewData(usersReference, "u002", "Jordan Lee, Staff, 0 pts");
        addNewData(usersReference, "u003", "Mike Ross, Manager, 0 pts");

        addNewData(menuReference, "m001", "Latte, Hot Coffee, $4.50");
        addNewData(menuReference, "m002", "Cold Brew, Iced, $5.00");
        addNewData(menuReference, "m003", "Blueberry Muffin, Pastry, $3.00");
    }
        private void addNewData(DatabaseReference databaseReference,String key, String value) {
            databaseReference.child(key).setValue(value).addOnSuccessListener(aVoid -> {
                Toast.makeText(MainActivity.this, "Successfully to add data", Toast.LENGTH_SHORT).show();

    }).addOnFailureListener(e -> {
        Toast.makeText(MainActivity.this, "Failed to add data", Toast.LENGTH_SHORT).show();
    });
}

private void readData(DatabaseReference databaseReference, TextView textView) {
    databaseReference.addValueEventListener(new ValueEventListener() {
        @Override
        public void onDataChange(@NonNull DataSnapshot snapshot) {
            if(snapshot.exists()){
                // getValue() without a target class never throws on structured records;
                // children are shown in key order.
                StringBuilder allValues = new StringBuilder();
                for (DataSnapshot childSnapshot : snapshot.getChildren()){
                    allValues.append(String.valueOf(childSnapshot.getValue())).append('\n');
                }
                textView.setText(allValues.toString());
            }
            else {
                textView.setText("");
                Log.e("FirebaseData", "No data found at this reference");
            }

        }

        @Override
        public void onCancelled(@NonNull DatabaseError error) {
            Toast.makeText(MainActivity.this, "Failed to get data", Toast.LENGTH_SHORT).show();

        }
    });
}
}