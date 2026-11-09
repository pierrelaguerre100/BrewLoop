package com.doubleshotdevs.brewloop;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.doubleshotdevs.brewloop.PointsTransaction;
import com.doubleshotdevs.brewloop.R;
import com.doubleshotdevs.brewloop.RewardsHelper;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class RewardsActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "userID";

    private static final String DEFAULT_USER_ID = "u001";

    private TextView textViewBalance;
    private TextView textViewProgress;
    private ProgressBar progressBar;

    private final ArrayList<String> historyLines = new ArrayList<>();
    private ArrayAdapter<String> historyAdapter;

    private DatabaseReference balanceReference;
    private Query historyQuery;
    private ValueEventListener balanceListener;
    private ValueEventListener historyListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rewards);

        textViewBalance = findViewById(R.id.textViewBalance);
        textViewProgress = findViewById(R.id.textViewProgress);
        progressBar = findViewById(R.id.progressBarRewards);
        ListView listViewHistory = findViewById(R.id.listViewHistory);
        listViewHistory.setEmptyView(findViewById(R.id.textViewEmptyHistory));

        historyAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, historyLines);
        listViewHistory.setAdapter(historyAdapter);

        String userID = getIntent().getStringExtra(EXTRA_USER_ID);
        if (userID == null) {
            userID = DEFAULT_USER_ID;
        }

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        balanceReference = database.getReference("Users").child(userID).child("pointsBalance");
        historyQuery = database.getReference("PointsTransaction")
                .orderByChild("userID").equalTo(userID);

        balanceListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Integer balance = snapshot.getValue(Integer.class);
                showBalance(balance == null ? 0 : balance);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(RewardsActivity.this, "Could not load points", Toast.LENGTH_SHORT).show();
            }
        };

        historyListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                historyLines.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    PointsTransaction transaction = child.getValue(PointsTransaction.class);
                    if (transaction != null) {
                        historyLines.add(0, describe(transaction));
                    }
                }
                historyAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(RewardsActivity.this, "Could not load history", Toast.LENGTH_SHORT).show();
            }
        };
    }


    @Override
    protected void onStart() {
        super.onStart();
        balanceReference.addValueEventListener(balanceListener);
        historyQuery.addValueEventListener(historyListener);
    }


    @Override
    protected void onStop() {
        super.onStop();
        balanceReference.removeEventListener(balanceListener);
        historyQuery.removeEventListener(historyListener);
    }

    private void showBalance(int balance) {
        textViewBalance.setText(balance + " points");
        progressBar.setProgress(Math.min(balance, RewardsHelper.POINTS_FOR_FREE_DRINK));

        if (balance >= RewardsHelper.POINTS_FOR_FREE_DRINK) {
            textViewProgress.setText("You have a free drink! Redeem it at checkout.");
        } else {
            int remaining = RewardsHelper.POINTS_FOR_FREE_DRINK - balance;
            textViewProgress.setText(remaining + " more points until a free drink");
        }
    }

    private String describe(PointsTransaction transaction) {
        String action = "earn".equals(transaction.type) ? "Earned " : "Redeemed ";
        return action + Math.abs(transaction.points) + " points (order " + transaction.orderID + ")";
    }
}
