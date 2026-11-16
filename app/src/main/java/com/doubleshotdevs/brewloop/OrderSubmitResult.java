package com.doubleshotdevs.brewloop;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Outcome of submitting an order through {@link OrderRepository}.
 */
public final class OrderSubmitResult {

    /** What happened to the order. */
    public enum Status {
        /** The order and its lines were saved. Clear the cart. */
        PLACED,
        /**
         * An order with this ID already exists for this customer, because an earlier attempt
         * succeeded even though its result was not received. Treat it as placed and clear
         * the cart; nothing new was written.
         */
        ALREADY_PLACED,
        /** Nobody is signed in. */
        NOT_SIGNED_IN,
        /** The signed-in account is not a customer with a valid profile. */
        NOT_A_CUSTOMER,
        /** The order lines failed validation; see {@link #getProblem()}. */
        INVALID_ORDER,
        /** The pickup time is missing or not in {@code HH:mm} format. */
        INVALID_PICKUP_TIME,
        /**
         * The order ID already belongs to another customer's order. Start a new checkout
         * attempt with a new ID.
         */
        ID_CONFLICT,
        /** Security rules refused a read or the write. Nothing was saved. */
        PERMISSION_DENIED,
        /** The database reported an error. Nothing was saved; keep the cart and retry. */
        FAILED,
        /**
         * No answer arrived in time. The write may still complete once the connection
         * returns. Keep the cart and retry with the same order ID, which finds an already
         * saved order instead of creating a second one.
         */
        UNCERTAIN
    }

    @NonNull
    private final Status status;
    @NonNull
    private final String orderId;
    @Nullable
    private final OrderValidator.Problem problem;

    /**
     * Creates a result.
     *
     * @param status  what happened
     * @param orderId the order ID the attempt used
     * @param problem the validation problem when {@code status} is
     *                {@link Status#INVALID_ORDER}, otherwise {@code null}
     */
    OrderSubmitResult(@NonNull Status status, @NonNull String orderId,
                      @Nullable OrderValidator.Problem problem) {
        this.status = status;
        this.orderId = orderId;
        this.problem = problem;
    }

    /**
     * Returns what happened to the order.
     *
     * @return the status
     */
    @NonNull
    public Status getStatus() {
        return status;
    }

    /**
     * Returns the order ID this attempt used.
     *
     * @return the order ID
     */
    @NonNull
    public String getOrderId() {
        return orderId;
    }

    /**
     * Returns the validation problem for an invalid order.
     *
     * @return the problem, or {@code null} if the status is not {@link Status#INVALID_ORDER}
     */
    @Nullable
    public OrderValidator.Problem getProblem() {
        return problem;
    }

    /**
     * Indicates whether the order is now saved, so the cart can be cleared.
     *
     * @return {@code true} for {@link Status#PLACED} and {@link Status#ALREADY_PLACED}
     */
    public boolean isPlaced() {
        return status == Status.PLACED || status == Status.ALREADY_PLACED;
    }
}
