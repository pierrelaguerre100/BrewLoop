package com.doubleshotdevs.brewloop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Checks the database security rules with direct REST requests, sent either with no
 * authentication or with a real emulator ID token, so none of the app's own validation is
 * involved.
 *
 * <p>Runs only in the {@code emulator} build variant against the local emulators with
 * {@code database.rules.json} loaded. Every account and record uses random IDs and is deleted
 * afterwards. Staff, manager, and existing customer records are seeded with emulator
 * administrator access, as the shop owner would do in the Firebase Console.</p>
 */
@RunWith(AndroidJUnit4.class)
public class RulesRestEmulatorTest {

    private static final String LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK";

    private final List<String> createdPaths = new ArrayList<>();
    private final List<String> createdUids = new ArrayList<>();
    private String menuItemId;

    /**
     * Skips the test unless this is the emulator build, then seeds one menu item.
     *
     * @throws Exception if seeding fails
     */
    @Before
    public void setUp() throws Exception {
        assumeTrue("Not the emulator build variant", FirebaseEnvironment.isLocalTestMode());
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .grantRuntimePermission(context.getPackageName(), LOCAL_NETWORK_PERMISSION);
        menuItemId = "rest-latte-" + random();
        seed(FirebaseNodes.MENU_ITEMS + "/" + menuItemId,
                "{\"name\":\"Latte\",\"category\":\"Hot\",\"description\":\"Test\","
                        + "\"basePrice\":4.5,\"isAvailable\":true,\"isCustomizable\":true}");
    }

    /**
     * Deletes every record and account this test created, even if it failed.
     *
     * @throws Exception if cleanup fails
     */
    @After
    public void cleanUp() throws Exception {
        for (String path : createdPaths) {
            EmulatorAdmin.delete(path);
        }
        for (String uid : createdUids) {
            EmulatorAdmin.delete(FirebaseNodes.USERS + "/" + uid);
            EmulatorAdmin.deleteAccount(uid);
        }
    }

    @Test
    public void unauthenticatedRequests_areDenied() throws Exception {
        EmulatorAdmin.TestUser customer = newCustomer(0);
        String profile = FirebaseNodes.USERS + "/" + customer.uid;
        assertDenied(EmulatorAdmin.requestAs("GET", profile, null, null, null));
        assertDenied(EmulatorAdmin.requestAs("GET", FirebaseNodes.USERS, null, null, null));
        assertDenied(EmulatorAdmin.requestAs("GET", FirebaseNodes.MENU_ITEMS, null, null, null));
        assertDenied(EmulatorAdmin.requestAs("GET", FirebaseNodes.ORDERS, null, null, null));
        assertDenied(EmulatorAdmin.requestAs("PUT", FirebaseNodes.USERS + "/someone", null,
                "{\"userID\":\"someone\"}", null));
        assertDenied(EmulatorAdmin.requestAs("DELETE", profile, null, null, null));
    }

    @Test
    public void selfRegistration_onlyCreatesOwnCustomerProfileWithZeroPoints() throws Exception {
        EmulatorAdmin.TestUser user = newAccount();
        EmulatorAdmin.TestUser other = newAccount();
        String path = FirebaseNodes.USERS + "/" + user.uid;
        String uid = JSONObject.quote(user.uid);
        String email = JSONObject.quote(user.email);
        String valid = "{\"userID\":" + uid + ",\"fullName\":\"Sam\",\"email\":" + email
                + ",\"role\":\"customer\",\"pointsBalance\":0}";

        assertDenied(put(path, valid.replace("\"customer\"", "\"staff\""), user));
        assertDenied(put(path, valid.replace("\"customer\"", "\"manager\""), user));
        assertDenied(put(path, valid.replace(":0}", ":10}"), user));
        assertDenied(put(path, valid.replace(":0}", ":0.5}"), user));
        assertDenied(put(path, valid.replace(":0}", ":\"0\"}"), user));
        assertDenied(put(path, valid.replace(email, JSONObject.quote(other.email)), user));
        assertDenied(put(path, valid.replace(uid, JSONObject.quote(other.uid)), user));
        assertDenied(put(path, valid.replace("\"Sam\"", "42"), user));
        assertDenied(put(path, valid.replace("\"Sam\"", "\"\""), user));
        assertDenied(put(path, valid.replace("}", ",\"password\":\"secret1\"}"), user));
        assertDenied(put(path, "{\"userID\":" + uid + ",\"email\":" + email
                + ",\"role\":\"customer\",\"pointsBalance\":0}", user));
        // Writing another user's record is denied even with valid content.
        assertDenied(put(FirebaseNodes.USERS + "/" + other.uid, valid, user));
        assertEquals("null", EmulatorAdmin.get(path).trim());

        assertAllowed(put(path, valid, user));
        assertAllowed(EmulatorAdmin.requestAs("GET", path, null, null, user));
    }

    @Test
    public void existingProfile_cannotBeOverwrittenDeletedOrEscalated() throws Exception {
        EmulatorAdmin.TestUser customer = newCustomer(10);
        String path = FirebaseNodes.USERS + "/" + customer.uid;
        String original = EmulatorAdmin.get(path);

        String sameRecord = original;
        assertDenied(put(path, sameRecord, customer));
        assertDenied(EmulatorAdmin.requestAs("DELETE", path, null, null, customer));
        assertDenied(put(path + "/role", "\"manager\"", customer));
        assertDenied(put(path + "/fullName", "\"Someone Else\"", customer));
        assertDenied(put(path + "/email", JSONObject.quote("new@example.com"), customer));
        assertDenied(put(path + "/password", "\"secret1\"", customer));
        assertDenied(put(path + "/pointsBalance", "500", customer));
        assertDenied(put(path + "/pointsBalance", "9", customer));
        assertDenied(EmulatorAdmin.requestAs("DELETE", path + "/pointsBalance", null, null,
                customer));
        assertDenied(EmulatorAdmin.requestAs("PATCH", path, null,
                "{\"role\":\"staff\",\"pointsBalance\":100}", customer));

        assertEquals(new JSONObject(original).toString(),
                new JSONObject(EmulatorAdmin.get(path)).toString());
    }

    @Test
    public void profiles_arePrivateAndCannotBeListedOrQueried() throws Exception {
        EmulatorAdmin.TestUser first = newCustomer(0);
        EmulatorAdmin.TestUser second = newCustomer(0);
        EmulatorAdmin.TestUser manager = newStaff("manager");

        assertDenied(EmulatorAdmin.requestAs("GET", FirebaseNodes.USERS + "/" + first.uid,
                null, null, second));
        assertDenied(EmulatorAdmin.requestAs("GET", FirebaseNodes.USERS, null, null, first));
        assertDenied(EmulatorAdmin.requestAs("GET", FirebaseNodes.USERS,
                "orderBy=%22userID%22&equalTo=" + quoted(first.uid), null, first));
        // Staff and managers do not need to read customer profiles.
        assertDenied(EmulatorAdmin.requestAs("GET", FirebaseNodes.USERS + "/" + first.uid,
                null, null, manager));
        assertDenied(EmulatorAdmin.requestAs("GET", FirebaseNodes.USERS, null, null, manager));
    }

    @Test
    public void pointsBalance_onlyChangesInPermittedSteps() throws Exception {
        EmulatorAdmin.TestUser customer = newCustomer(60);
        EmulatorAdmin.TestUser other = newCustomer(60);
        EmulatorAdmin.TestUser staff = newStaff("staff");
        String balance = FirebaseNodes.USERS + "/" + customer.uid + "/pointsBalance";

        assertDenied(put(balance, "59", customer));
        assertDenied(put(balance, "61", customer));
        assertDenied(put(balance, "10.5", customer));
        assertDenied(put(balance, "10", other));
        assertDenied(put(balance, "40", staff));
        assertDenied(put(balance, "-1", staff));
        // Staff may only increase a balance, which awarding points for an order needs.
        assertAllowed(put(balance, "64", staff));
        assertEquals("64", EmulatorAdmin.get(balance).trim());
    }

    @Test
    public void reward_requiresAtomicRedemptionTiedToTheOrder() throws Exception {
        EmulatorAdmin.TestUser customer = newCustomer(60);
        String orderId = "rest-order-" + random();
        trackOrder(orderId);
        String order = orderJson(orderId, customer.uid, 4.5, 4, true);
        String redeem = "{\"userID\":" + JSONObject.quote(customer.uid) + ",\"orderID\":"
                + JSONObject.quote(orderId) + ",\"type\":\"redeem\",\"points\":-50}";
        String balancePath = FirebaseNodes.USERS + "/" + customer.uid + "/pointsBalance";
        String orderPath = FirebaseNodes.ORDERS + "/" + orderId;
        String redeemPath = FirebaseNodes.POINTS_TRANSACTION + "/" + orderId + "_redeem";

        // A free drink without paying points, or a redeem record without an order, is denied.
        assertDenied(put(orderPath, order, customer));
        assertDenied(put(redeemPath, redeem, customer));
        assertDenied(patchRoot(customer, "\"" + orderPath + "\":" + order + ",\""
                + balancePath + "\":10"));
        assertDenied(patchRoot(customer, "\"" + orderPath + "\":" + order + ",\""
                + redeemPath + "\":" + redeem));
        assertDenied(patchRoot(customer, "\"" + orderPath + "\":" + order + ",\""
                + redeemPath + "\":" + redeem + ",\"" + balancePath + "\":20"));
        assertEquals("null", EmulatorAdmin.get(orderPath).trim());
        assertEquals("60", EmulatorAdmin.get(balancePath).trim());

        // The order, the redeem record, and the 50-point deduction in one update succeed.
        assertAllowed(patchRoot(customer, "\"" + orderPath + "\":" + order + ",\""
                + redeemPath + "\":" + redeem + ",\"" + balancePath + "\":10"));
        assertEquals("10", EmulatorAdmin.get(balancePath).trim());
        assertTrue(EmulatorAdmin.getObject(orderPath).getBoolean("rewardApplied"));
    }

    @Test
    public void nonAtomicRedemption_isBlocked() throws Exception {
        // The current RewardsHelper.redeemReward deducts the points first and writes the
        // redeem record separately. The second step is refused, so a deduction can no longer
        // be turned into a reward without the order in the same update.
        EmulatorAdmin.TestUser customer = newCustomer(60);
        String orderId = "rest-order-" + random();
        trackOrder(orderId);
        String balancePath = FirebaseNodes.USERS + "/" + customer.uid + "/pointsBalance";
        assertAllowed(put(balancePath, "10", customer));
        assertDenied(put(FirebaseNodes.POINTS_TRANSACTION + "/" + orderId + "_redeem",
                "{\"userID\":" + JSONObject.quote(customer.uid) + ",\"orderID\":"
                        + JSONObject.quote(orderId) + ",\"type\":\"redeem\",\"points\":-50}",
                customer));
        assertDenied(put(FirebaseNodes.ORDERS + "/" + orderId,
                orderJson(orderId, customer.uid, 4.5, 4, true), customer));
    }

    @Test
    public void orders_mustBeOwnedWithPointsMatchingTheTotal() throws Exception {
        EmulatorAdmin.TestUser customer = newCustomer(0);
        EmulatorAdmin.TestUser other = newCustomer(0);
        EmulatorAdmin.TestUser staff = newStaff("staff");
        String orderId = "rest-order-" + random();
        trackOrder(orderId);
        String orderPath = FirebaseNodes.ORDERS + "/" + orderId;

        assertDenied(put(orderPath, orderJson(orderId, other.uid, 4.5, 4, false), customer));
        assertDenied(put(orderPath, orderJson(orderId, customer.uid, 4.5, 40, false), customer));
        assertDenied(put(orderPath, orderJson(orderId, customer.uid, 4.5, 3, false), customer));
        assertDenied(put(orderPath, orderJson(orderId, staff.uid, 4.5, 4, false), staff));
        assertDenied(put(orderPath, orderJson(orderId, customer.uid, 4.5, 4, false)
                .replace("\"Received\"", "\"Completed\""), customer));
        assertAllowed(put(orderPath, orderJson(orderId, customer.uid, 4.5, 4, false), customer));

        // Reads: own order and an own-UID query are allowed; anything broader is denied.
        assertAllowed(EmulatorAdmin.requestAs("GET", orderPath, null, null, customer));
        assertDenied(EmulatorAdmin.requestAs("GET", orderPath, null, null, other));
        assertAllowed(EmulatorAdmin.requestAs("GET", FirebaseNodes.ORDERS,
                "orderBy=%22userID%22&equalTo=" + quoted(customer.uid), null, customer));
        assertDenied(EmulatorAdmin.requestAs("GET", FirebaseNodes.ORDERS,
                "orderBy=%22userID%22&equalTo=" + quoted(other.uid), null, customer));
        assertDenied(EmulatorAdmin.requestAs("GET", FirebaseNodes.ORDERS, null, null, customer));
        assertAllowed(EmulatorAdmin.requestAs("GET", FirebaseNodes.ORDERS, null, null, staff));

        // Customers cannot change status; staff can only move it forward.
        assertDenied(put(orderPath + "/status", "\"Preparing\"", customer));
        assertDenied(put(orderPath + "/status", "\"Ready\"", staff));
        assertAllowed(put(orderPath + "/status", "\"Preparing\"", staff));
        assertDenied(put(orderPath + "/status", "\"Received\"", staff));
    }

    @Test
    public void earnedPoints_mustMatchTheCompletedOrder() throws Exception {
        EmulatorAdmin.TestUser customer = newCustomer(0);
        EmulatorAdmin.TestUser staff = newStaff("staff");
        String orderId = "rest-order-" + random();
        trackOrder(orderId);
        String orderPath = FirebaseNodes.ORDERS + "/" + orderId;
        seed(orderPath, orderJson(orderId, customer.uid, 12.75, 12, false)
                .replace("\"Received\"", "\"Completed\""));
        String balancePath = FirebaseNodes.USERS + "/" + customer.uid + "/pointsBalance";
        String earnPath = FirebaseNodes.POINTS_TRANSACTION + "/" + orderId + "_earn";

        assertDenied(patchRoot(staff, earnUpdate(earnPath, orderPath, balancePath, customer,
                orderId, 99, 99)));
        assertDenied(patchRoot(customer, earnUpdate(earnPath, orderPath, balancePath, customer,
                orderId, 12, 12)));
        assertAllowed(patchRoot(staff, earnUpdate(earnPath, orderPath, balancePath, customer,
                orderId, 12, 12)));
        assertEquals("12", EmulatorAdmin.get(balancePath).trim());
        // Points for an order are awarded only once.
        assertDenied(patchRoot(staff, earnUpdate(earnPath, orderPath, balancePath, customer,
                orderId, 12, 24)));
    }

    // Helpers

    /**
     * Builds the multi-path update that awards points for a completed order.
     *
     * @return the body of the update, without the surrounding braces
     */
    @NonNull
    private static String earnUpdate(String earnPath, String orderPath, String balancePath,
                                     EmulatorAdmin.TestUser customer, String orderId,
                                     int points, int newBalance) {
        return "\"" + earnPath + "\":{\"userID\":" + JSONObject.quote(customer.uid)
                + ",\"orderID\":" + JSONObject.quote(orderId) + ",\"type\":\"earn\",\"points\":"
                + points + "},\"" + orderPath + "/pointsEarned\":" + points + ",\"" + orderPath
                + "/pointsAwarded\":true,\"" + balancePath + "\":" + newBalance;
    }

    /**
     * Builds an order record.
     *
     * @return the order as JSON
     */
    @NonNull
    private static String orderJson(String orderId, String uid, double total, int points,
                                    boolean rewardApplied) {
        return "{\"orderID\":" + JSONObject.quote(orderId) + ",\"userID\":"
                + JSONObject.quote(uid) + ",\"status\":\"Received\",\"pickupTime\":\"09:30\","
                + "\"totalPrice\":" + total + ",\"pointsEarned\":" + points
                + ",\"rewardApplied\":" + rewardApplied + ",\"pointsAwarded\":false}";
    }

    /** Creates an Authentication account with no profile. */
    @NonNull
    private EmulatorAdmin.TestUser newAccount() throws Exception {
        EmulatorAdmin.TestUser user = EmulatorAdmin.signUp(
                "rest-" + random() + "@example.com", UUID.randomUUID().toString());
        createdUids.add(user.uid);
        return user;
    }

    /** Creates an account with a customer profile seeded by the administrator. */
    @NonNull
    private EmulatorAdmin.TestUser newCustomer(int points) throws Exception {
        return withProfile(newAccount(), "customer", points);
    }

    /** Creates an account with a staff or manager profile seeded by the administrator. */
    @NonNull
    private EmulatorAdmin.TestUser newStaff(@NonNull String role) throws Exception {
        return withProfile(newAccount(), role, 0);
    }

    @NonNull
    private EmulatorAdmin.TestUser withProfile(@NonNull EmulatorAdmin.TestUser user,
                                               @NonNull String role, int points) throws Exception {
        EmulatorAdmin.put(FirebaseNodes.USERS + "/" + user.uid, "{\"userID\":"
                + JSONObject.quote(user.uid) + ",\"fullName\":\"Test\",\"email\":"
                + JSONObject.quote(user.email) + ",\"role\":" + JSONObject.quote(role)
                + ",\"pointsBalance\":" + points + "}");
        return user;
    }

    private void seed(@NonNull String path, @NonNull String json) throws Exception {
        createdPaths.add(path);
        EmulatorAdmin.put(path, json);
    }

    /** Records an order's paths so they are deleted after the test. */
    private void trackOrder(@NonNull String orderId) {
        createdPaths.add(FirebaseNodes.ORDERS + "/" + orderId);
        createdPaths.add(FirebaseNodes.POINTS_TRANSACTION + "/" + orderId + "_redeem");
        createdPaths.add(FirebaseNodes.POINTS_TRANSACTION + "/" + orderId + "_earn");
    }

    private static int put(@NonNull String path, @NonNull String json,
                           @Nullable EmulatorAdmin.TestUser user) throws Exception {
        return EmulatorAdmin.requestAs("PUT", path, null, json, user);
    }

    /** Sends a multi-path update at the database root, applied atomically or not at all. */
    private static int patchRoot(@NonNull EmulatorAdmin.TestUser user, @NonNull String body)
            throws Exception {
        return EmulatorAdmin.requestAs("PATCH", "", null, "{" + body + "}", user);
    }

    @NonNull
    private static String quoted(@NonNull String value) {
        return "%22" + value + "%22";
    }

    @NonNull
    private static String random() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static void assertDenied(int code) {
        assertTrue("Expected the rules to deny the request, got HTTP " + code,
                code == 401 || code == 403);
    }

    private static void assertAllowed(int code) {
        assertEquals("Expected the rules to allow the request", 200, code);
    }
}
