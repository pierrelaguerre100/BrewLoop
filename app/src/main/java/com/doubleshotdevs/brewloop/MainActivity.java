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

import java.util.HashMap;

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
        firebaseDatabase = FirebaseDatabase.getInstance();
        usersReference = firebaseDatabase.getReference("Users");
        menuReference = firebaseDatabase.getReference("Menu");

        textViewUsers = findViewById(R.id.textviewUsers);
        textViewMenu = findViewById(R.id.textviewMenu);
        buttonUpload = findViewById(R.id.buttonUpload);

        buttonUpload.setOnClickListener(v -> {
                    addNewData(usersReference, "u001", "Cristiano Ronaldo, customer, 38 pts");
                    addNewData(usersReference, "u002", "Jordan Lee, Staff, 0 pts");
                    addNewData(usersReference, "u003", "Mike Ross, Manager, 0 pts");

                    addNewData(menuReference, "m001", "Latte, Hot Coffee, $4.50");
                    addNewData(menuReference, "m002", "Cold Brew, Iced, $5.00");
                    addNewData(menuReference, "m003", "Blueberry Muffin, Pastry, $3.00");
                });

            readData(usersReference, textViewUsers);
            readData(menuReference, textViewMenu);
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
            HashMap<String, String> dataMap = new HashMap<>();

            if(snapshot.exists()){
                for (DataSnapshot childSnapshot : snapshot.getChildren()){
                    dataMap.put(childSnapshot.getKey(),childSnapshot.getValue(String.class));
                    System.out.println("Data print");
                }
                StringBuilder allValues = new StringBuilder();
                for (String value : dataMap.values()){
                    allValues.append(value).append('\n');
                    textView.setText(allValues.toString());
                }

            }
            else {
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