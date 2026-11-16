package com.example.brewloop;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.brewloop.R;

/**
 * The Fragment can be placed in the team's final navigation later.
 */
public class StaffOrdersActivity extends AppCompatActivity {

    /** Loads the staff screen or redirects to staff sign-in. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean loggedIn = getSharedPreferences("staff_demo", MODE_PRIVATE)
                .getBoolean("logged_in", false);

        if (!loggedIn) {
            startActivity(new Intent(this, StaffLoginActivity.class));
            finish();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_staff_orders);

        View root = findViewById(R.id.staffHostRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets system = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(system.left, system.top, system.right, system.bottom);
            return insets;
        });

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.staffFragmentContainer, new StaffOrdersFragment())
                    .commit();
        }
    }
}
