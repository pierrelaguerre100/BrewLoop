package com.doubleshotdevs.brewloop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runtime checks of {@link UserRepository} against the local Firebase emulators.
 *
 * <p>Runs only in the {@code emulator} build variant
 * ({@code gradlew connectedEmulatorAndroidTest -Pbrewloop.testBuildType=emulator}) and is
 * skipped, before any Firebase call, in builds that use the shared project. Each test uses a
 * new Auth emulator account with a random email and password, writes only that account's
 * own {@code Users/{uid}} record in the Database emulator, and deletes both afterwards.</p>
 */
@RunWith(AndroidJUnit4.class)
public class UserRepositoryEmulatorTest {

    private static final long WAIT_SECONDS = 20;
    private static final String LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK";
    private static final String UNREACHABLE_APP_NAME = "unreachable-db";

    private FirebaseAuth auth;
    private FirebaseUser testUser;
    private DatabaseReference testRecord;

    /**
     * Skips the test unless Firebase is routed to a demo project's emulators, then grants the
     * local-network permission that Android 17 requires for reaching the emulator host.
     */
    @Before
    public void requireLocalEmulators() {
        assumeTrue("Not the emulator build variant", FirebaseEnvironment.isLocalTestMode());
        FirebaseOptions options = FirebaseApp.getInstance().getOptions();
        String projectId = options.getProjectId();
        String databaseUrl = options.getDatabaseUrl();
        assertTrue("Refusing to run against a non-demo project",
                projectId != null && projectId.startsWith("demo-"));
        assertTrue("Refusing to write outside the demo database",
                databaseUrl != null && databaseUrl.contains("://demo-"));

        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .grantRuntimePermission(context.getPackageName(), LOCAL_NETWORK_PERMISSION);

        auth = FirebaseAccess.auth();
        auth.signOut();
    }

    /**
     * Deletes this test's emulator record and account, signs out, and removes the
     * unreachable-database app if one was created.
     *
     * @throws Exception if the emulator cleanup does not complete
     */
    @After
    public void cleanUp() throws Exception {
        // Every step runs even if an earlier one fails, so a failed test cannot leave its
        // emulator account behind.
        try {
            if (testRecord != null) {
                Tasks.await(testRecord.removeValue(), WAIT_SECONDS, TimeUnit.SECONDS);
            }
        } finally {
            try {
                if (testUser != null) {
                    Tasks.await(testUser.delete(), WAIT_SECONDS, TimeUnit.SECONDS);
                }
            } finally {
                if (auth != null) {
                    auth.signOut();
                }
                for (FirebaseApp app : FirebaseApp.getApps(
                        InstrumentationRegistry.getInstrumentation().getTargetContext())) {
                    if (UNREACHABLE_APP_NAME.equals(app.getName())) {
                        app.delete();
                    }
                }
            }
        }
    }

    @Test
    public void signedOut_reportsNotSignedIn() throws Exception {
        assertEquals(ProfileLoadStatus.NOT_SIGNED_IN, loadOnce(new UserRepository()).getStatus());
    }

    @Test
    public void signUp_createsAccountInAuthEmulator() throws Exception {
        signUpTestUser();

        FirebaseUser currentUser = auth.getCurrentUser();
        assertNotNull(currentUser);
        assertEquals(testUser.getUid(), currentUser.getUid());
        assertTrue(UserProfileParser.isValidUid(testUser.getUid()));
    }

    @Test
    public void validRecord_loadsSuccessfully() throws Exception {
        signUpTestUser();
        writeOwnRecord(record(testUser.getUid(), "customer", 0L));

        ProfileLoadResult result = loadOnce(new UserRepository());

        assertEquals(ProfileLoadStatus.SUCCESS, result.getStatus());
        User user = result.getUser();
        assertNotNull(user);
        assertEquals(testUser.getUid(), user.userID);
        assertEquals("Emulator Tester", user.fullName);
        assertEquals("customer", user.role);
        assertEquals(0, user.pointsBalance);
        assertTrue(user.isCustomer());
    }

    @Test
    public void noRecord_isMissing() throws Exception {
        signUpTestUser();

        assertEquals(ProfileLoadStatus.MISSING, loadOnce(new UserRepository()).getStatus());
    }

    @Test
    public void starterTextRecord_isMalformed() throws Exception {
        signUpTestUser();
        writeOwnRecord("Test Person, customer, 38 pts");

        assertEquals(ProfileLoadStatus.MALFORMED, loadOnce(new UserRepository()).getStatus());
    }

    @Test
    public void recordForAnotherUid_isUidMismatch() throws Exception {
        signUpTestUser();
        writeOwnRecord(record("someOtherUid", "customer", 0L));

        assertEquals(ProfileLoadStatus.UID_MISMATCH, loadOnce(new UserRepository()).getStatus());
    }

    @Test
    public void unsupportedRole_isReported() throws Exception {
        signUpTestUser();
        writeOwnRecord(record(testUser.getUid(), "Staff", 0L));

        assertEquals(ProfileLoadStatus.UNSUPPORTED_ROLE,
                loadOnce(new UserRepository()).getStatus());
    }

    @Test
    public void databaseUnavailableWithAuthAvailable_isNetworkErrorOnce() throws Exception {
        signUpTestUser();
        UserRepository repository = new UserRepository(auth, unreachableDatabase(), 2_000L);

        CallbackRecorder recorder = new CallbackRecorder();
        repository.loadCurrentUserProfile(recorder);

        assertTrue("No callback received", recorder.awaitFirst());
        assertEquals(ProfileLoadStatus.NETWORK_ERROR, recorder.first.get().getStatus());
        recorder.assertNoFurtherCallbacks(3);
    }

    @Test
    public void lateDataAfterTimeout_isNotDelivered() throws Exception {
        signUpTestUser();
        writeOwnRecord(record(testUser.getUid(), "customer", 0L));
        // The timeout fires long before the emulator round trip can complete.
        UserRepository repository = new UserRepository(auth, FirebaseAccess.database(), 1L);

        CallbackRecorder recorder = new CallbackRecorder();
        repository.loadCurrentUserProfile(recorder);

        assertTrue("No callback received", recorder.awaitFirst());
        assertEquals(ProfileLoadStatus.NETWORK_ERROR, recorder.first.get().getStatus());
        recorder.assertNoFurtherCallbacks(3);
    }

    @Test
    public void cancelledLoad_deliversNothing() throws Exception {
        signUpTestUser();
        writeOwnRecord(record(testUser.getUid(), "customer", 0L));

        CallbackRecorder recorder = new CallbackRecorder();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                new UserRepository().loadCurrentUserProfile(recorder).cancel());

        recorder.assertNoFurtherCallbacks(3);
        assertEquals(0, recorder.count.get());
    }

    /**
     * Creates and signs in a new account in the Auth emulator, remembered for cleanup.
     *
     * @throws Exception if the emulator rejects the request or does not respond
     */
    private void signUpTestUser() throws Exception {
        String email = "member1-" + UUID.randomUUID() + "@example.com";
        String password = UUID.randomUUID().toString();
        testUser = Tasks.await(auth.createUserWithEmailAndPassword(email, password),
                WAIT_SECONDS, TimeUnit.SECONDS).getUser();
        assertNotNull(testUser);
    }

    /**
     * Writes a value at the signed-in test user's own {@code Users/{uid}} in the emulator.
     *
     * @param value the record to store
     * @throws Exception if the write does not complete
     */
    private void writeOwnRecord(Object value) throws Exception {
        testRecord = FirebaseAccess.userProfile(FirebaseAccess.database(), testUser.getUid());
        Tasks.await(testRecord.setValue(value), WAIT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Builds a stored profile record.
     *
     * @param userId the {@code userID} field value
     * @param role   the {@code role} field value
     * @param points the {@code pointsBalance} field value
     * @return the record fields
     */
    private static Map<String, Object> record(String userId, String role, long points) {
        Map<String, Object> record = new HashMap<>();
        record.put(User.FIELD_USER_ID, userId);
        record.put(User.FIELD_FULL_NAME, "Emulator Tester");
        record.put(User.FIELD_EMAIL, "tester@example.com");
        record.put(User.FIELD_ROLE, role);
        record.put(User.FIELD_POINTS_BALANCE, points);
        return record;
    }

    /**
     * Returns a database for a separate demo app, routed to a host port where nothing listens,
     * while Authentication stays on the working emulator.
     *
     * @return a database client that can never connect
     */
    private FirebaseDatabase unreachableDatabase() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        FirebaseApp app = FirebaseApp.initializeApp(context, new FirebaseOptions.Builder()
                .setProjectId("demo-brewloop")
                .setApplicationId("1:000000000000:android:0000000000000000")
                .setApiKey("demo-api-key")
                .setDatabaseUrl("https://demo-brewloop-default-rtdb.firebaseio.com")
                .build(), UNREACHABLE_APP_NAME);
        FirebaseDatabase database = FirebaseDatabase.getInstance(app);
        database.useEmulator("10.0.2.2", 9);
        return database;
    }

    /**
     * Runs one profile load and returns its result.
     *
     * @param repository the repository to load from
     * @return the delivered result
     * @throws InterruptedException if interrupted while waiting
     */
    private ProfileLoadResult loadOnce(UserRepository repository) throws InterruptedException {
        CallbackRecorder recorder = new CallbackRecorder();
        repository.loadCurrentUserProfile(recorder);
        assertTrue("No callback received", recorder.awaitFirst());
        return recorder.first.get();
    }

    /** Records every callback so tests can check that exactly one result is delivered. */
    private static final class CallbackRecorder implements UserRepository.ProfileCallback {
        final AtomicInteger count = new AtomicInteger();
        final AtomicReference<ProfileLoadResult> first = new AtomicReference<>();
        private final CountDownLatch firstDelivered = new CountDownLatch(1);

        @Override
        public void onProfileLoaded(@NonNull ProfileLoadResult result) {
            if (count.incrementAndGet() == 1) {
                first.set(result);
                firstDelivered.countDown();
            }
        }

        /**
         * Waits for the first callback.
         *
         * @return {@code true} if a callback arrived in time
         * @throws InterruptedException if interrupted while waiting
         */
        boolean awaitFirst() throws InterruptedException {
            return firstDelivered.await(WAIT_SECONDS, TimeUnit.SECONDS);
        }

        /**
         * Waits, then checks that no more than one callback was ever delivered.
         *
         * @param seconds how long to keep watching for late callbacks
         * @throws InterruptedException if interrupted while waiting
         */
        void assertNoFurtherCallbacks(long seconds) throws InterruptedException {
            assertFalse("Unexpected extra delivery",
                    new CountDownLatch(1).await(seconds, TimeUnit.SECONDS));
            assertTrue("More than one callback: " + count.get(), count.get() <= 1);
        }
    }
}
