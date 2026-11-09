package com.doubleshotdevs.brewloop;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/** Starts the customer menu. Firebase setup is handled by MenuActivity when configured. */
public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startActivity(new Intent(this, MenuActivity.class));
        finish();
    }
}
