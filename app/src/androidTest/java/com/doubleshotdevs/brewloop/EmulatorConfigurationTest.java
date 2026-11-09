package com.doubleshotdevs.brewloop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.FirebaseDatabase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Verifies, inside the running app process, that the {@code emulator} build uses only the
 * isolated {@code demo-brewloop} Firebase app and never the shared project.
 */
@RunWith(AndroidJUnit4.class)
public class EmulatorConfigurationTest {

    /** Skips the test unless this is the emulator build variant. */
    @Before
    public void requireEmulatorVariant() {
        assumeTrue("Not the emulator build variant", FirebaseEnvironment.isLocalTestMode());
    }

    @Test
    public void onlyTheDemoFirebaseAppIsInitialized() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        List<FirebaseApp> apps = FirebaseApp.getApps(context);

        assertEquals("Exactly one Firebase app expected", 1, apps.size());
        FirebaseOptions options = apps.get(0).getOptions();
        assertEquals(FirebaseApp.DEFAULT_APP_NAME, apps.get(0).getName());
        assertEquals("demo-brewloop", options.getProjectId());
        assertEquals("https://demo-brewloop-default-rtdb.firebaseio.com", options.getDatabaseUrl());
    }

    @Test
    public void authAndDatabaseUseTheDemoApp() {
        FirebaseApp demoApp = FirebaseApp.getInstance();

        assertSame(demoApp, FirebaseAccess.auth().getApp());
        assertSame(demoApp, FirebaseAccess.database().getApp());
    }

    /**
     * The database instance must stay the emulator-routed one after it has been used. The
     * Firebase SDK applies emulator settings on first use in a way that makes a later
     * {@code FirebaseDatabase.getInstance()} return a new, unrouted instance, so all access
     * must go through {@link FirebaseAccess#database()}.
     */
    @Test
    public void databaseInstanceIsStableAfterFirstUse() {
        FirebaseDatabase first = FirebaseAccess.database();
        first.getReference(FirebaseNodes.USERS); // Forces the SDK to apply emulator settings.

        assertSame(first, FirebaseAccess.database());
        assertTrue("Database must stay routed to the local emulator",
                FirebaseAccess.database().getReference().toString().startsWith("http://10.0.2.2:9000"));
    }
}
