package com.doubleshotdevs.brewloop;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Submits a customer's order to {@code Orders} and {@code OrderItems}.
 *
 * <p>BrewLoop does not take payments: an order is only sent to staff, and the customer pays
 * at the counter. A submission:</p>
 * <ol>
 *     <li>requires a signed-in user whose own profile is a valid customer profile;</li>
 *     <li>checks the lines, then checks them against the current {@code MenuItems};</li>
 *     <li>writes the order and all of its lines in one atomic multi-location update, so
 *     either everything is saved or nothing is.</li>
 * </ol>
 *
 * <p>Duplicate orders are prevented with a caller-held order ID. Get one from
 * {@link #newOrderId()} when checkout starts and reuse it for every retry of that
 * checkout. Line IDs are derived from the order ID, so a repeated write targets the same
 * records. Before writing, the repository checks whether that order already exists; if it
 * does, the earlier attempt succeeded and {@link OrderSubmitResult.Status#ALREADY_PLACED}
 * is returned without writing again.</p>
 *
 * <p>New orders start with status {@link #STATUS_RECEIVED}, {@code rewardApplied} false,
 * and {@code pointsAwarded} false. Points are not awarded here: the rewards feature adds
 * them when staff mark the order Completed. Every submission ends with exactly one callback
 * on the main thread.</p>
 */
public class OrderRepository {

    /** Receives the result of a submission on the main thread. */
    public interface SubmitCallback {
        /**
         * Called once when the submission finishes.
         *
         * @param result the outcome
         */
        void onSubmitFinished(@NonNull OrderSubmitResult result);
    }

    /** Status of a newly placed order, from the team's data contract. */
    public static final String STATUS_RECEIVED = "Received";

    /** Value stored for a size or milk type that does not apply, from the data contract. */
    public static final String NOT_APPLICABLE = "n/a";

    /** How long to wait for the database before reporting an uncertain result. */
    public static final long DEFAULT_TIMEOUT_MILLIS = 20_000L;

    private static final String TAG = "OrderRepository";

    private final FirebaseAuth auth;
    private final FirebaseDatabase database;
    private final long timeoutMillis;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Creates a repository that uses the app's configured Firebase instances.
     */
    public OrderRepository() {
        this(FirebaseAccess.auth(), FirebaseAccess.database(), DEFAULT_TIMEOUT_MILLIS);
    }

    /**
     * Creates a repository with explicit dependencies.
     *
     * @param auth          the Firebase Authentication instance
     * @param database      the Realtime Database instance
     * @param timeoutMillis how long to wait before reporting an uncertain result
     */
    public OrderRepository(@NonNull FirebaseAuth auth, @NonNull FirebaseDatabase database,
                           long timeoutMillis) {
        this.auth = auth;
        this.database = database;
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * Creates a new, unique order ID without contacting the database.
     *
     * <p>Call this once when the customer starts checkout, keep the ID while the checkout
     * screen is open (including across configuration changes), and pass it to every
     * {@link #submitOrder} retry.</p>
     *
     * @return a new order ID
     */
    @NonNull
    public String newOrderId() {
        String key = database.getReference(FirebaseNodes.ORDERS).push().getKey();
        if (key == null) {
            throw new IllegalStateException("Could not create an order ID");
        }
        return key;
    }

    /**
     * Returns the ID of one line of an order. Line IDs are derived from the order ID, so
     * every retry of the same order writes the same line records.
     *
     * @param orderId   the order ID
     * @param lineIndex the zero-based position of the line in the order
     * @return the line's {@code orderItemID}
     */
    @NonNull
    public static String orderItemId(@NonNull String orderId, int lineIndex) {
        return orderId + "_" + (lineIndex + 1);
    }

    /**
     * Validates and submits an order for the signed-in customer.
     *
     * <p>The lines are copied; the caller's objects are not changed. Only {@code itemID},
     * {@code size}, {@code milkType}, {@code extraShots}, {@code quantity}, and
     * {@code itemTotal} are used from each line; IDs and ownership are set here.</p>
     *
     * @param orderId    an ID from {@link #newOrderId()}, reused for retries
     * @param pickupTime the pickup time in {@code HH:mm} format, already checked against the
     *                   shop hours with {@link PickupTimeValidator}
     * @param lines      the order lines
     * @param callback   receives the result on the main thread
     */
    public void submitOrder(@NonNull String orderId, @NonNull String pickupTime,
                            @NonNull List<OrderItem> lines, @NonNull SubmitCallback callback) {
        Submission submission = new Submission(orderId, callback);
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || !UserProfileParser.isValidUid(user.getUid())) {
            submission.finishLater(OrderSubmitResult.Status.NOT_SIGNED_IN, null);
            return;
        }
        if (!UserProfileParser.isValidUid(orderId)) {
            submission.finishLater(OrderSubmitResult.Status.ID_CONFLICT, null);
            return;
        }
        if (PickupTimeValidator.parse(pickupTime) == null) {
            submission.finishLater(OrderSubmitResult.Status.INVALID_PICKUP_TIME, null);
            return;
        }
        List<OrderItem> copies = copyLines(orderId, lines);
        OrderValidator.Problem problem = OrderValidator.checkLines(copies);
        if (problem != null) {
            submission.finishLater(OrderSubmitResult.Status.INVALID_ORDER, problem);
            return;
        }
        submission.start(user.getUid(), pickupTime, copies);
    }

    /**
     * Copies order lines, filling in the IDs and the "not applicable" defaults.
     *
     * @param orderId the order the lines belong to
     * @param lines   the caller's lines
     * @return new line objects ready to store
     */
    @NonNull
    static List<OrderItem> copyLines(@NonNull String orderId, @NonNull List<OrderItem> lines) {
        List<OrderItem> copies = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            OrderItem line = lines.get(i);
            if (line == null) {
                copies.add(null);
                continue;
            }
            OrderItem copy = new OrderItem(orderId, line.itemID,
                    orDefault(line.size), orDefault(line.milkType), Math.max(0, line.extraShots),
                    line.quantity, OrderValidator.toDollars(OrderValidator.toCents(line.itemTotal)));
            copy.orderItemID = orderItemId(orderId, i);
            copies.add(copy);
        }
        return copies;
    }

    /**
     * Builds the atomic multi-location update for an order and its lines.
     *
     * @param orderId    the order ID
     * @param uid        the customer's UID
     * @param pickupTime the pickup time
     * @param lines      validated lines from {@link #copyLines}
     * @return paths relative to the database root, mapped to the records to store
     */
    @NonNull
    static Map<String, Object> buildOrderUpdate(@NonNull String orderId, @NonNull String uid,
                                                @NonNull String pickupTime,
                                                @NonNull List<OrderItem> lines) {
        double total = OrderValidator.toDollars(OrderValidator.totalCents(lines));
        // Points this order is worth, using the rewards feature's own rule. They are awarded
        // later, when staff complete the order, so pointsAwarded starts false.
        Order order = new Order(uid, STATUS_RECEIVED, pickupTime, total,
                RewardsHelper.calculatePoints(total), false, false);
        order.orderID = orderId;

        Map<String, Object> update = new HashMap<>();
        update.put(FirebaseNodes.ORDERS + "/" + orderId, order);
        for (OrderItem line : lines) {
            update.put(FirebaseNodes.ORDER_ITEMS + "/" + line.orderItemID, line);
        }
        return update;
    }

    /**
     * Returns the value, or {@link #NOT_APPLICABLE} if it is blank.
     *
     * @param value a size or milk type
     * @return the stored value
     */
    @NonNull
    private static String orDefault(@Nullable String value) {
        return value == null || value.trim().isEmpty() ? NOT_APPLICABLE : value.trim();
    }

    /**
     * One submission that runs its steps in order and reports exactly once. All steps run
     * on the main thread, where Firebase delivers callbacks.
     */
    private final class Submission {

        private final String orderId;
        private final SubmitCallback callback;
        private boolean finished;

        private final Runnable timeout = () -> {
            Log.w(TAG, "Order submission timed out");
            finish(OrderSubmitResult.Status.UNCERTAIN, null);
        };

        Submission(@NonNull String orderId, @NonNull SubmitCallback callback) {
            this.orderId = orderId;
            this.callback = callback;
        }

        /**
         * Starts the checks and the write.
         *
         * @param uid        the signed-in customer's UID
         * @param pickupTime the pickup time
         * @param lines      lines that passed {@link OrderValidator#checkLines}
         */
        void start(@NonNull String uid, @NonNull String pickupTime,
                   @NonNull List<OrderItem> lines) {
            mainHandler.postDelayed(timeout, timeoutMillis);
            readOnce(FirebaseAccess.userProfile(database, uid), profile -> {
                ProfileLoadResult result = UserProfileParser.parse(uid, profile.getValue());
                User user = result.getUser();
                if (user == null || !user.isCustomer()) {
                    finish(OrderSubmitResult.Status.NOT_A_CUSTOMER, null);
                    return;
                }
                readOnce(database.getReference(FirebaseNodes.ORDERS).child(orderId), existing -> {
                    if (existing.exists()) {
                        Object owner = existing.child("userID").getValue();
                        finish(uid.equals(owner) ? OrderSubmitResult.Status.ALREADY_PLACED
                                : OrderSubmitResult.Status.ID_CONFLICT, null);
                        return;
                    }
                    readOnce(database.getReference(FirebaseNodes.MENU_ITEMS), menu -> {
                        OrderValidator.Problem problem =
                                OrderValidator.checkAgainstMenu(lines, menu.getValue());
                        if (problem != null) {
                            finish(OrderSubmitResult.Status.INVALID_ORDER, problem);
                            return;
                        }
                        write(uid, buildOrderUpdate(orderId, uid, pickupTime, lines));
                    });
                });
            });
        }

        /**
         * Writes the order and its lines in one atomic update.
         *
         * @param uid    the signed-in customer's UID
         * @param update the multi-location update
         */
        private void write(@NonNull String uid, @NonNull Map<String, Object> update) {
            if (finished) {
                // Timed out while checking: do not start a write the caller no longer expects.
                return;
            }
            database.getReference().updateChildren(update, (error, ref) -> {
                if (error == null) {
                    finish(OrderSubmitResult.Status.PLACED, null);
                } else if (error.getCode() == DatabaseError.PERMISSION_DENIED) {
                    // The rules refuse to overwrite an existing order. If a simultaneous
                    // attempt with the same ID saved it first, report that order as placed.
                    confirmExistingOrder(uid);
                } else {
                    Log.w(TAG, "Order write failed with code " + error.getCode());
                    finish(statusFor(error), null);
                }
            });
        }

        /**
         * After a refused write, checks whether this customer's order with the same ID was
         * already saved by another attempt.
         *
         * @param uid the signed-in customer's UID
         */
        private void confirmExistingOrder(@NonNull String uid) {
            readOnce(database.getReference(FirebaseNodes.ORDERS).child(orderId), existing -> {
                boolean ownOrder = existing.exists()
                        && uid.equals(existing.child("userID").getValue());
                if (!ownOrder) {
                    Log.w(TAG, "Order write was denied");
                }
                finish(ownOrder ? OrderSubmitResult.Status.ALREADY_PLACED
                        : OrderSubmitResult.Status.PERMISSION_DENIED, null);
            });
        }

        /**
         * Reads a location once, then continues with the next step, or finishes with the
         * matching failure if the read is refused.
         *
         * @param ref  the location to read
         * @param next the next step
         */
        private void readOnce(@NonNull DatabaseReference ref, @NonNull Step next) {
            ref.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (finished) {
                        return;
                    }
                    try {
                        next.run(snapshot);
                    } catch (RuntimeException e) {
                        Log.w(TAG, "Unexpected data while checking the order", e);
                        finish(OrderSubmitResult.Status.FAILED, null);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.w(TAG, "Order check read failed with code " + error.getCode());
                    finish(statusFor(error), null);
                }
            });
        }

        /**
         * Delivers a result that is already known, asynchronously.
         *
         * @param status  the status
         * @param problem the validation problem, if any
         */
        void finishLater(@NonNull OrderSubmitResult.Status status,
                         @Nullable OrderValidator.Problem problem) {
            mainHandler.post(() -> finish(status, problem));
        }

        /**
         * Delivers the result if this submission has not already finished.
         *
         * @param status  the status
         * @param problem the validation problem, if any
         */
        private void finish(@NonNull OrderSubmitResult.Status status,
                            @Nullable OrderValidator.Problem problem) {
            if (finished) {
                return;
            }
            finished = true;
            mainHandler.removeCallbacks(timeout);
            callback.onSubmitFinished(new OrderSubmitResult(status, orderId, problem));
        }
    }

    /**
     * Maps a database error to a submission status.
     *
     * @param error the database error
     * @return the matching status
     */
    @NonNull
    private static OrderSubmitResult.Status statusFor(@NonNull DatabaseError error) {
        switch (UserProfileParser.statusForDatabaseError(error.getCode())) {
            case PERMISSION_DENIED:
                return OrderSubmitResult.Status.PERMISSION_DENIED;
            case NETWORK_ERROR:
                return OrderSubmitResult.Status.UNCERTAIN;
            default:
                return OrderSubmitResult.Status.FAILED;
        }
    }

    /** One step of a submission that continues after a read. */
    private interface Step {
        /**
         * Continues the submission with the data that was read.
         *
         * @param snapshot the data
         */
        void run(@NonNull DataSnapshot snapshot);
    }
}
