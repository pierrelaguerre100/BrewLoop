package com.doubleshotdevs.brewloop;

import android.os.Bundle;
import android.content.Intent;
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
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        firebaseDatabase = FirebaseDatabase.getInstance();
        usersReference = firebaseDatabase.getReference("Users");
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

        buttonUpload.setOnClickListener(v -> {
                    addNewData(usersReference, "u001", new User("Alex Carter", "alex.carter@example.com", "customer", 38));
                    addNewData(usersReference, "u002", new User("Jordan Lee", "jordan.lee@example.com", "staff", 0));
                    addNewData(usersReference, "u003", new User("Morgan Reyes", "morgan.reyes@example.com", "manager", 0));

                    addNewData(menuReference, "m001", new MenuProduct("Latte", "Hot Coffee", "Espresso with steamed milk", 4.50, true, true));
                    addNewData(menuReference, "m002", new MenuProduct("Cold Brew", "Iced", "Coffee over ice", 5.00, true, true));
                    addNewData(menuReference, "m003", new MenuProduct("Blueberry Muffin", "Pastry", "Baked fresh", 3.00, true, false));

                    addNewData(optionsReference, "o001", new CustomizationOption("size","Small", 0.00));
                    addNewData(optionsReference, "o002", new CustomizationOption("size","Large", 1.00));
                    addNewData(optionsReference, "o003", new CustomizationOption("shot","Extra shot",0.50));

                    addNewData(ordersReference, "ord100", new Order("u001","Completed", "08:15", 7.50, 7, false, true));
                    addNewData(ordersReference, "ord101", new Order("u001","Preparing", "09:05", 5.00, 5, false, false));
                    addNewData(ordersReference, "ord102", new Order("u001","Received", "09:20", 3.00, 3, false, false));

                    addNewData(orderItemsReference, "oir001", new OrderItem("ord100", "m001", "Small", "Whole", 0, 1, 4.50));
                    addNewData(orderItemsReference, "oir002", new OrderItem("ord100", "m003", "n/a", "n/a", 0, 1, 3.00));
                    addNewData(orderItemsReference, "oir003", new OrderItem("ord101", "m002", "Small", "Whole", 0, 1, 5.00));
                    addNewData(orderItemsReference, "oir004", new OrderItem("ord102", "m003", "n/a", "n/a", 0, 1, 3.00));

                    addNewData(pointsReference, "ord100_earn", new PointsTransaction("u001", "ord100", "earn", 7));

        });

             findViewById(R.id.buttonRewards).setOnClickListener(v ->
                     startActivity(new Intent(this, RewardsActivity.class)));

             findViewById(R.id.buttonOpenMenu).setOnClickListener(v ->
                startActivity(new Intent(this, MenuActivity.class)));

            readData(usersReference, textViewUsers, "USERS");
            readData(menuReference, textViewMenu, "MENU ITEMS");
            readData(optionsReference, textViewOptions, "CUSTOMIZATION OPTION");
            readData(ordersReference, textViewOrders, "ORDERS");
            readData(orderItemsReference, textViewOrderItems, "ORDER ITEMS");
            readData(pointsReference, textViewPoints, "POINTS TRANSACTION");
        }
        private void addNewData(DatabaseReference databaseReference,String key, Object value) {
            databaseReference.child(key).setValue(value).addOnSuccessListener(aVoid -> {
                Toast.makeText(MainActivity.this, "Successfully to add data", Toast.LENGTH_SHORT).show();

    }).addOnFailureListener(e -> {
        Toast.makeText(MainActivity.this, "Failed to add data", Toast.LENGTH_SHORT).show();
    });
}

private void readData(DatabaseReference databaseReference, TextView textView , String title) {
    databaseReference.addValueEventListener(new ValueEventListener() {
        @Override
        public void onDataChange(@NonNull DataSnapshot snapshot) {
            HashMap<String, String> dataMap = new HashMap<>();

            if(snapshot.exists()){
                for (DataSnapshot childSnapshot : snapshot.getChildren()){
                    StringBuilder fields = new StringBuilder();
                    for(DataSnapshot fieldSnapshot : childSnapshot.getChildren()){
                        fields.append(fieldSnapshot.getKey()).append("=").append(fieldSnapshot.getValue()).append("  ");
                    }
                    dataMap.put(childSnapshot.getKey(), childSnapshot.getKey() + ": " + fields);
                }
                StringBuilder allValues = new StringBuilder(title + "\n");
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