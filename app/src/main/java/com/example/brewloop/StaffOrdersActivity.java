package com.example.brewloop;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * The Fragment can be placed in the team's final navigation later.
 */
public class StaffOrdersActivity extends AppCompatActivity {

    /** Loads the staff screen or redirects to staff sign-in. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences preferences = getSharedPreferences("staff_demo", MODE_PRIVATE);
        boolean loggedIn = preferences.getBoolean("logged_in", false);

        if (!loggedIn) {
            Toast.makeText(this, "Please sign in as staff", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, StaffLoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_staff_orders);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.staffFragmentContainer, new StaffOrdersFragment())
                    .commit();
        }
    }
}
