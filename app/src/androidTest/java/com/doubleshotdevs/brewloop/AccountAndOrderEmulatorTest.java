package com.doubleshotdevs.brewloop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * End-to-end checks of registration, profile recovery, order submission, and the database
 * security rules, against the local Firebase emulators with {@code database.rules.json}
 * loaded.
 *
 * <p>Runs only in the {@code emulator} build variant. Every account, profile, menu item, and
 * order is created for one test with random IDs and deleted afterwards. Staff profiles and
 * menu items are seeded with emulator administrator access, as the shop owner would do in
 * the Firebase Console.</p>
 */
@RunWith(AndroidJUnit4.class)
public class AccountAndOrderEmulatorTest {

    private static final long WAIT_SECONDS = 20;
    private static final String LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK";
    private static final String PICKUP = "09:30";

    private FirebaseAuth auth;
    private FirebaseDatabase database;

    private final List<Account> accounts = new ArrayList<>();
    private final List<String> seededPaths = new ArrayList<>();
    private final List<String> orderIds = new ArrayList<>();

    private String latteId;
    private String soldOutId;

    /** A test account's credentials, kept so tests can switch between users. */
    private static final class Account {
        final String email;
        final String password;
        String uid;

        Account(String email, String password) {
            this.email = email;
            this.password = password;
        }
    }

    /**
     * Skips the test unless this is the emulator build, then seeds two menu items.
     *
     * @throws Exception if seeding fails
     */
    @Before
    public void setUp() throws Exception {
        assumeTrue("Not the emulator build variant", FirebaseEnvironment.isLocalTestMode());
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .grantRuntimePermission(context.getPackageName(), LOCAL_NETWORK_PERMISSION);
        auth = FirebaseAccess.auth();
        database = FirebaseAccess.database();
        auth.signOut();

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        latteId = "test-latte-" + suffix;
        soldOutId = "test-soldout-" + suffix;
        seed(FirebaseNodes.MENU_ITEMS + "/" + latteId,
                "{\"name\":\"Latte\",\"category\":\"Hot Coffee\",\"description\":\"Test\","
                        + "\"basePrice\":4.5,\"isAvailable\":true,\"isCustomizable\":true}");
        seed(FirebaseNodes.MENU_ITEMS + "/" + soldOutId,
                "{\"name\":\"Cold Brew\",\"category\":\"Iced\",\"description\":\"Test\","
                        + "\"basePrice\":5.0,\"isAvailable\":false,\"isCustomizable\":true}");
    }

    /**
     * Deletes everything this test created, even if the test failed.
     *
     * @throws Exception if cleanup fails
     */
    @After
    public void cleanUp() throws Exception {
        if (auth == null) {
            return;
        }
        auth.signOut();
        for (String orderId : orderIds) {
            EmulatorAdmin.delete(FirebaseNodes.ORDERS + "/" + orderId);
            EmulatorAdmin.delete(FirebaseNodes.POINTS_TRANSACTION + "/" + orderId + "_earn");
            JSONObject items = EmulatorAdmin.getObject(FirebaseNodes.ORDER_ITEMS);
            if (items != null) {
                for (Iterator<String> keys = items.keys(); keys.hasNext(); ) {
                    String key = keys.next();
                    if (key.startsWith(orderId + "_")) {
                        EmulatorAdmin.delete(FirebaseNodes.ORDER_ITEMS + "/" + key);
                    }
                }
            }
        }
        for (String path : seededPaths) {
            EmulatorAdmin.delete(path);
        }
        for (Account account : accounts) {
            if (account.uid != null) {
                EmulatorAdmin.delete(FirebaseNodes.USERS + "/" + account.uid);
                EmulatorAdmin.deleteAccount(account.uid);
            }
        }
    }

    // ---- Registration and profile recovery ----

    @Test
    public void registration_createsCustomerProfileWithZeroPoints() throws Exception {
        Account account = newAccount();

        ProfileLoadResult result = createProfile("  Sam Rivera  ");

        assertEquals(ProfileLoadStatus.SUCCESS, result.getStatus());
        JSONObject stored = EmulatorAdmin.getObject(FirebaseNodes.USERS + "/" + account.uid);
        assertNotNull(stored);
        assertEquals(account.uid, stored.getString("userID"));
        assertEquals("Sam Rivera", stored.getString("fullName"));
        assertEquals(account.email, stored.getString("email"));
        assertEquals("customer", stored.getString("role"));
        assertEquals(0, stored.getInt("pointsBalance"));
        assertEquals(5, stored.length());
        assertEquals(ProfileLoadStatus.SUCCESS, loadProfile().getStatus());
    }

    @Test
    public void recovery_neverOverwritesAnExistingProfileOrPoints() throws Exception {
        Account account = newAccount();
        seedProfile(account, "customer", 38, "Existing Name");

        ProfileLoadResult result = createProfile("Someone Else");

        assertEquals(ProfileLoadStatus.SUCCESS, result.getStatus());
        User user = result.getUser();
        assertNotNull(user);
        assertEquals("Existing Name", user.fullName);
        assertEquals(38, user.pointsBalance);
        JSONObject stored = EmulatorAdmin.getObject(FirebaseNodes.USERS + "/" + account.uid);
        assertNotNull(stored);
        assertEquals(38, stored.getInt("pointsBalance"));
        assertEquals("Existing Name", stored.getString("fullName"));
    }

    @Test
    public void recovery_keepsAnExistingStaffRole() throws Exception {
        Account account = newAccount();
        seedProfile(account, "staff", 0, "Barista");

        ProfileLoadResult result = createProfile("Barista");

        assertEquals(ProfileLoadStatus.SUCCESS, result.getStatus());
        assertEquals(RoleRouter.Destination.STAFF_UNAVAILABLE, RoleRouter.route(result));
        JSONObject stored = EmulatorAdmin.getObject(FirebaseNodes.USERS + "/" + account.uid);
        assertNotNull(stored);
        assertEquals("staff", stored.getString("role"));
    }

    @Test
    public void missingProfile_routesToCompleteProfile() throws Exception {
        newAccount();

        assertEquals(RoleRouter.Destination.COMPLETE_PROFILE, RoleRouter.route(loadProfile()));
    }

    // ---- Security rules: profiles ----

    @Test
    public void rules_customerCannotAssignStaffRoleOrPoints() throws Exception {
        Account account = newAccount();
        DatabaseReference own = FirebaseAccess.userProfile(database, account.uid);

        assertDenied(own.setValue(profileMap(account, "staff", 0)));
        assertDenied(own.setValue(profileMap(account, "manager", 0)));
        assertDenied(own.setValue(profileMap(account, "customer", 100)));
        Map<String, Object> extraField = profileMap(account, "customer", 0);
        extraField.put("isAdmin", true);
        assertDenied(own.setValue(extraField));

        assertEquals(ProfileLoadStatus.SUCCESS, createProfile("Sam").getStatus());
        assertDenied(own.child(User.FIELD_ROLE).setValue("staff"));
        assertDenied(own.child(User.FIELD_POINTS_BALANCE).setValue(1000));
        assertDenied(own.setValue(profileMap(account, "customer", 0)));
        assertDenied(own.removeValue());
    }

    @Test
    public void rules_profilesArePrivateAndUsersCannotBeListed() throws Exception {
        Account first = newAccount();
        assertEquals(ProfileLoadStatus.SUCCESS, createProfile("First").getStatus());
        Account second = newAccount();
        assertEquals(ProfileLoadStatus.SUCCESS, createProfile("Second").getStatus());

        assertDenied(FirebaseAccess.userProfile(database, first.uid).get());
        assertDenied(database.getReference(FirebaseNodes.USERS).get());
        assertTrue(Tasks.await(FirebaseAccess.userProfile(database, second.uid).get(),
                WAIT_SECONDS, TimeUnit.SECONDS).exists());
    }

    @Test
    public void rules_onlyScopedOrderAndPointsReadsAreAllowed() throws Exception {
        Account account = newAccount();
        assertEquals(ProfileLoadStatus.SUCCESS, createProfile("Sam").getStatus());

        assertDenied(database.getReference(FirebaseNodes.ORDERS).get());
        assertDenied(database.getReference(FirebaseNodes.POINTS_TRANSACTION).get());
        assertDenied(database.getReference(FirebaseNodes.ORDER_ITEMS).get());
        assertDenied(database.getReference(FirebaseNodes.ORDERS)
                .orderByChild("userID").equalTo("someoneElse").get());

        Tasks.await(database.getReference(FirebaseNodes.ORDERS)
                .orderByChild("userID").equalTo(account.uid).get(), WAIT_SECONDS, TimeUnit.SECONDS);
        Tasks.await(database.getReference(FirebaseNodes.POINTS_TRANSACTION)
                .orderByChild("userID").equalTo(account.uid).get(), WAIT_SECONDS, TimeUnit.SECONDS);
        Tasks.await(database.getReference(FirebaseNodes.MENU_ITEMS).get(),
                WAIT_SECONDS, TimeUnit.SECONDS);
    }

    // ---- Order submission ----

    @Test
    public void order_isSavedAtomicallyWithReceivedStatusAndNoPointsAwarded() throws Exception {
        Account account = newCustomer();
        String orderId = newOrderId();

        OrderSubmitResult result = submit(orderId, PICKUP, Arrays.asList(
                line(latteId, 2, 9.00, "Large", "Oat", 1), line(latteId, 1, 4.50, null, null, 0)));

        assertEquals(OrderSubmitResult.Status.PLACED, result.getStatus());
        JSONObject order = EmulatorAdmin.getObject(FirebaseNodes.ORDERS + "/" + orderId);
        assertNotNull(order);
        assertEquals(orderId, order.getString("orderID"));
        assertEquals(account.uid, order.getString("userID"));
        assertEquals("Received", order.getString("status"));
        assertEquals(PICKUP, order.getString("pickupTime"));
        assertEquals(13.5, order.getDouble("totalPrice"), 0.0);
        assertEquals(13, order.getInt("pointsEarned"));
        assertFalse(order.getBoolean("pointsAwarded"));
        assertFalse(order.getBoolean("rewardApplied"));

        JSONObject first = EmulatorAdmin.getObject(FirebaseNodes.ORDER_ITEMS + "/" + orderId + "_1");
        JSONObject second = EmulatorAdmin.getObject(FirebaseNodes.ORDER_ITEMS + "/" + orderId + "_2");
        assertNotNull(first);
        assertNotNull(second);
        assertEquals(orderId, first.getString("orderID"));
        assertEquals("Large", first.getString("size"));
        assertEquals("Oat", first.getString("milkType"));
        assertEquals(1, first.getInt("extraShots"));
        assertEquals(2, first.getInt("quantity"));
        assertEquals("n/a", second.getString("size"));
        assertEquals(2, countItems(orderId));

        // The customer can read their own order and its items with scoped reads.
        assertTrue(Tasks.await(database.getReference(FirebaseNodes.ORDERS).child(orderId).get(),
                WAIT_SECONDS, TimeUnit.SECONDS).exists());
        assertEquals(2, Tasks.await(database.getReference(FirebaseNodes.ORDER_ITEMS)
                .orderByChild("orderID").equalTo(orderId).get(), WAIT_SECONDS, TimeUnit.SECONDS)
                .getChildrenCount());
    }

    @Test
    public void order_repeatedTapsAndRetriesNeverDuplicate() throws Exception {
        newCustomer();
        String orderId = newOrderId();
        List<OrderItem> lines = Collections.singletonList(line(latteId, 1, 4.50, "Small", "Whole", 0));

        // Two taps before either result arrives.
        List<OrderSubmitResult> taps = submitTwiceAtOnce(orderId, lines);
        assertTrue("First tap: " + taps.get(0).getStatus(), taps.get(0).isPlaced());
        assertTrue("Second tap: " + taps.get(1).getStatus(), taps.get(1).isPlaced());
        assertTrue("Exactly one tap writes the order",
                taps.get(0).getStatus() == OrderSubmitResult.Status.PLACED
                        ^ taps.get(1).getStatus() == OrderSubmitResult.Status.PLACED);

        // A later retry after an uncertain result finds the saved order.
        assertEquals(OrderSubmitResult.Status.ALREADY_PLACED, submit(orderId, PICKUP, lines).getStatus());
        assertEquals(1, countItems(orderId));
    }

    @Test
    public void order_invalidOrdersWriteNothing() throws Exception {
        newCustomer();

        assertInvalid(OrderValidator.Problem.EMPTY, Collections.emptyList());
        assertInvalid(OrderValidator.Problem.ITEM_UNAVAILABLE,
                Collections.singletonList(line(soldOutId, 1, 5.0, null, null, 0)));
        assertInvalid(OrderValidator.Problem.ITEM_NOT_ON_MENU,
                Collections.singletonList(line("test-missing-item", 1, 5.0, null, null, 0)));
        assertInvalid(OrderValidator.Problem.PRICE_CHANGED,
                Collections.singletonList(line(latteId, 2, 4.50, null, null, 0)));
        assertInvalid(OrderValidator.Problem.INVALID_QUANTITY,
                Collections.singletonList(line(latteId, 0, 4.50, null, null, 0)));

        String orderId = newOrderId();
        assertEquals(OrderSubmitResult.Status.INVALID_PICKUP_TIME, submit(orderId, "9:30",
                Collections.singletonList(line(latteId, 1, 4.5, null, null, 0))).getStatus());
        assertNull(EmulatorAdmin.getObject(FirebaseNodes.ORDERS + "/" + orderId));
    }

    @Test
    public void order_requiresSignedInCustomerWithValidProfile() throws Exception {
        List<OrderItem> lines = Collections.singletonList(line(latteId, 1, 4.5, null, null, 0));

        assertEquals(OrderSubmitResult.Status.NOT_SIGNED_IN,
                submit(newOrderId(), PICKUP, lines).getStatus());

        newAccount(); // No profile.
        assertEquals(OrderSubmitResult.Status.NOT_A_CUSTOMER,
                submit(newOrderId(), PICKUP, lines).getStatus());

        Account staff = newAccount();
        seedProfile(staff, "staff", 0, "Barista");
        String staffOrder = newOrderId();
        assertEquals(OrderSubmitResult.Status.NOT_A_CUSTOMER,
                submit(staffOrder, PICKUP, lines).getStatus());
        assertNull(EmulatorAdmin.getObject(FirebaseNodes.ORDERS + "/" + staffOrder));
    }

    // ---- Security rules: orders, user switching, and the existing points award ----

    @Test
    public void rules_ordersAreOwnedAndStatusOnlyMovesForwardByStaff() throws Exception {
        Account customer = newCustomer();
        String orderId = newOrderId();
        List<OrderItem> lines = Collections.singletonList(line(latteId, 2, 12.00, "Large", "Oat", 2));
        assertEquals(OrderSubmitResult.Status.PLACED, submit(orderId, PICKUP, lines).getStatus());
        DatabaseReference order = database.getReference(FirebaseNodes.ORDERS).child(orderId);

        // The customer cannot change status, totals, points, or create orders for others.
        assertDenied(order.child("status").setValue("Completed"));
        assertDenied(order.child("totalPrice").setValue(0.01));
        assertDenied(order.child("pointsAwarded").setValue(true));
        String forgedId = newOrderId();
        Map<String, Object> forged = new HashMap<>(OrderRepository.buildOrderUpdate(
                forgedId, "someoneElse", PICKUP, OrderRepository.copyLines(forgedId, lines)));
        assertDenied(database.getReference().updateChildren(forged));
        assertDenied(order.removeValue());

        // Switching to another customer: no access to the first customer's order.
        Account other = newCustomer();
        assertDenied(order.get());
        assertDenied(database.getReference(FirebaseNodes.ORDER_ITEMS)
                .orderByChild("orderID").equalTo(orderId).get());
        assertFalse(submit(orderId, PICKUP, lines).isPlaced());
        JSONObject stored = EmulatorAdmin.getObject(FirebaseNodes.ORDERS + "/" + orderId);
        assertNotNull(stored);
        assertEquals(customer.uid, stored.getString("userID"));
        assertEquals(1, countItems(orderId));

        // Staff move the order forward only.
        Account staff = newAccount();
        seedProfile(staff, "staff", 0, "Barista");
        assertDenied(order.child("status").setValue("Ready"));
        await(order.child("status").setValue("Preparing"));
        assertDenied(order.child("status").setValue("Received"));
        assertDenied(order.child("userID").setValue(staff.uid));
        await(order.child("status").setValue("Ready"));
        await(order.child("status").setValue("Completed"));

        // The rewards feature's existing award runs under the rules, once.
        RewardsHelper.awardPoints(orderId);
        // The order total is $12.00, worth 12 points at 1 point per whole dollar.
        waitForPoints(customer, 12);
        RewardsHelper.awardPoints(orderId);
        Thread.sleep(2_000);
        JSONObject profile = EmulatorAdmin.getObject(FirebaseNodes.USERS + "/" + customer.uid);
        assertNotNull(profile);
        assertEquals(12, profile.getInt("pointsBalance"));
        JSONObject awarded = EmulatorAdmin.getObject(FirebaseNodes.ORDERS + "/" + orderId);
        assertNotNull(awarded);
        assertTrue(awarded.getBoolean("pointsAwarded"));
        assertNotNull(EmulatorAdmin.getObject(
                FirebaseNodes.POINTS_TRANSACTION + "/" + orderId + "_earn"));

        // A retry by the owner after staff progress finds the order without resetting it.
        signIn(customer);
        assertEquals(OrderSubmitResult.Status.ALREADY_PLACED, submit(orderId, PICKUP, lines).getStatus());
        JSONObject afterRetry = EmulatorAdmin.getObject(FirebaseNodes.ORDERS + "/" + orderId);
        assertNotNull(afterRetry);
        assertEquals("Completed", afterRetry.getString("status"));
        assertNotNull(other.uid);
    }

    // ---- Helpers ----

    /**
     * Creates and signs in a new Authentication emulator account.
     *
     * @return the account
     * @throws Exception if the emulator does not respond
     */
    private Account newAccount() throws Exception {
        Account account = new Account("member1-" + UUID.randomUUID() + "@example.com",
                UUID.randomUUID().toString());
        accounts.add(account);
        FirebaseUser user = Tasks.await(auth.createUserWithEmailAndPassword(
                account.email, account.password), WAIT_SECONDS, TimeUnit.SECONDS).getUser();
        assertNotNull(user);
        account.uid = user.getUid();
        return account;
    }

    /**
     * Creates and signs in a new account with a registered customer profile.
     *
     * @return the account
     * @throws Exception if registration fails
     */
    private Account newCustomer() throws Exception {
        Account account = newAccount();
        assertEquals(ProfileLoadStatus.SUCCESS, createProfile("Test Customer").getStatus());
        return account;
    }

    /**
     * Signs in to an existing test account.
     *
     * @param account the account
     * @throws Exception if sign-in fails
     */
    private void signIn(Account account) throws Exception {
        Tasks.await(auth.signInWithEmailAndPassword(account.email, account.password),
                WAIT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Seeds a profile as the shop owner would, bypassing the rules.
     *
     * @param account the account
     * @param role    the role
     * @param points  the points balance
     * @param name    the full name
     * @throws Exception if seeding fails
     */
    private void seedProfile(Account account, String role, int points, String name) throws Exception {
        Map<String, Object> profile = profileMap(account, role, points);
        profile.put(User.FIELD_FULL_NAME, name);
        EmulatorAdmin.put(FirebaseNodes.USERS + "/" + account.uid, new JSONObject(profile).toString());
    }

    private static Map<String, Object> profileMap(Account account, String role, int points) {
        Map<String, Object> profile = new HashMap<>();
        profile.put(User.FIELD_USER_ID, account.uid);
        profile.put(User.FIELD_FULL_NAME, "Test Person");
        profile.put(User.FIELD_EMAIL, account.email);
        profile.put(User.FIELD_ROLE, role);
        profile.put(User.FIELD_POINTS_BALANCE, points);
        return profile;
    }

    private void seed(String path, String json) throws Exception {
        seededPaths.add(path);
        EmulatorAdmin.put(path, json);
    }

    private String newOrderId() {
        String orderId = new OrderRepository().newOrderId();
        orderIds.add(orderId);
        return orderId;
    }

    private static OrderItem line(String itemId, int quantity, double total, String size,
                                  String milk, int shots) {
        return new OrderItem(null, itemId, size, milk, shots, quantity, total);
    }

    private ProfileLoadResult createProfile(String name) throws InterruptedException {
        AtomicReference<ProfileLoadResult> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                new UserRepository().createCustomerProfileIfMissing(name, r -> {
                    result.set(r);
                    done.countDown();
                }));
        assertTrue("No profile result", done.await(WAIT_SECONDS, TimeUnit.SECONDS));
        return result.get();
    }

    private ProfileLoadResult loadProfile() throws InterruptedException {
        AtomicReference<ProfileLoadResult> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                new UserRepository().loadCurrentUserProfile(r -> {
                    result.set(r);
                    done.countDown();
                }));
        assertTrue("No profile result", done.await(WAIT_SECONDS, TimeUnit.SECONDS));
        return result.get();
    }

    private OrderSubmitResult submit(String orderId, String pickup, List<OrderItem> lines)
            throws InterruptedException {
        AtomicReference<OrderSubmitResult> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                new OrderRepository().submitOrder(orderId, pickup, lines, r -> {
                    result.set(r);
                    done.countDown();
                }));
        assertTrue("No submit result", done.await(WAIT_SECONDS + 5, TimeUnit.SECONDS));
        return result.get();
    }

    private List<OrderSubmitResult> submitTwiceAtOnce(String orderId, List<OrderItem> lines)
            throws InterruptedException {
        List<OrderSubmitResult> results = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch done = new CountDownLatch(2);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            OrderRepository repository = new OrderRepository();
            repository.submitOrder(orderId, PICKUP, lines, r -> {
                results.add(r);
                done.countDown();
            });
            repository.submitOrder(orderId, PICKUP, lines, r -> {
                results.add(r);
                done.countDown();
            });
        });
        assertTrue("Both taps must finish", done.await(WAIT_SECONDS + 5, TimeUnit.SECONDS));
        return results;
    }

    private void assertInvalid(OrderValidator.Problem expected, List<OrderItem> lines)
            throws Exception {
        String orderId = newOrderId();
        OrderSubmitResult result = submit(orderId, PICKUP, lines);
        assertEquals(OrderSubmitResult.Status.INVALID_ORDER, result.getStatus());
        assertEquals(expected, result.getProblem());
        assertFalse(result.isPlaced());
        assertNull(EmulatorAdmin.getObject(FirebaseNodes.ORDERS + "/" + orderId));
    }

    private int countItems(String orderId) throws Exception {
        JSONObject items = EmulatorAdmin.getObject(FirebaseNodes.ORDER_ITEMS);
        int count = 0;
        if (items != null) {
            for (Iterator<String> keys = items.keys(); keys.hasNext(); ) {
                JSONObject item = items.getJSONObject(keys.next());
                if (orderId.equals(item.optString("orderID"))) {
                    count++;
                }
            }
        }
        return count;
    }

    private void waitForPoints(Account account, int expected) throws Exception {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(WAIT_SECONDS);
        int points = -1;
        while (System.currentTimeMillis() < deadline) {
            JSONObject profile = EmulatorAdmin.getObject(FirebaseNodes.USERS + "/" + account.uid);
            points = profile == null ? -1 : profile.getInt("pointsBalance");
            if (points == expected) {
                return;
            }
            Thread.sleep(250);
        }
        fail("Expected " + expected + " points, found " + points);
    }

    private static void await(@NonNull Task<?> task) throws Exception {
        Tasks.await(task, WAIT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Checks that the security rules refuse a read or write.
     *
     * @param task the database task
     * @throws Exception if the task does not finish in time
     */
    private static void assertDenied(@NonNull Task<?> task) throws Exception {
        try {
            Tasks.await(task, WAIT_SECONDS, TimeUnit.SECONDS);
            fail("Expected the security rules to deny this request");
        } catch (ExecutionException e) {
            // Writes fail with DatabaseException; get() reads fail with a plain Exception.
            Throwable cause = e.getCause();
            String message = cause == null ? "" : String.valueOf(cause.getMessage());
            assertTrue("Unexpected failure: " + cause,
                    message.toLowerCase(java.util.Locale.US).contains("permission"));
        }
    }
}
