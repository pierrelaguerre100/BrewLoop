package com.doubleshotdevs.brewloop;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Checks a pickup time chosen at checkout.
 *
 * <p>Pickup times are stored as 24-hour {@code HH:mm} text, such as {@code "08:15"}, as in
 * the team's data contract. A time is accepted only if it is at least the lead time after
 * the current time and within the shop's opening hours on the same day. The opening hours
 * are passed in rather than fixed here, because the team has not yet agreed on them. This
 * class has no Android dependencies, so it is covered by local unit tests.</p>
 */
public final class PickupTimeValidator {

    /** Format of stored pickup times. */
    public static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("HH:mm", Locale.US);

    /** A reason a pickup time is rejected. */
    public enum Problem {
        /** No time was chosen, or it is not in {@code HH:mm} format. */
        INVALID_FORMAT,
        /** The time is in the past or sooner than the lead time allows. */
        TOO_SOON,
        /** The time is before opening or at or after closing. */
        OUTSIDE_SHOP_HOURS
    }

    private final LocalTime opensAt;
    private final LocalTime closesAt;
    private final int leadMinutes;

    /**
     * Creates a validator for one day's opening hours.
     *
     * @param opensAt     first time a pickup may be scheduled
     * @param closesAt    closing time; pickups must be earlier than this
     * @param leadMinutes minimum minutes between ordering and pickup; zero or more
     * @throws IllegalArgumentException if the shop does not open before it closes, or the
     *                                  lead time is negative
     */
    public PickupTimeValidator(@NonNull LocalTime opensAt, @NonNull LocalTime closesAt,
                               int leadMinutes) {
        if (!opensAt.isBefore(closesAt)) {
            throw new IllegalArgumentException("Opening time must be before closing time");
        }
        if (leadMinutes < 0) {
            throw new IllegalArgumentException("Lead time cannot be negative");
        }
        this.opensAt = opensAt;
        this.closesAt = closesAt;
        this.leadMinutes = leadMinutes;
    }

    /**
     * Checks a pickup time against the current time and the opening hours.
     *
     * @param pickupTime the chosen time in {@code HH:mm} format, which may be {@code null}
     * @param now        the current time of day
     * @return the problem found, or {@code null} if the time is acceptable
     */
    @Nullable
    public Problem check(@Nullable String pickupTime, @NonNull LocalTime now) {
        LocalTime time = parse(pickupTime);
        if (time == null) {
            return Problem.INVALID_FORMAT;
        }
        if (time.isBefore(opensAt) || !time.isBefore(closesAt)) {
            return Problem.OUTSIDE_SHOP_HOURS;
        }
        // Pickups are same-day, so an earliest time past midnight leaves nothing valid.
        LocalTime earliest = now.plusMinutes(leadMinutes);
        boolean wrappedPastMidnight = earliest.isBefore(now);
        if (wrappedPastMidnight || time.isBefore(earliest)) {
            return Problem.TOO_SOON;
        }
        return null;
    }

    /**
     * Parses a stored pickup time.
     *
     * @param pickupTime text in {@code HH:mm} format, which may be {@code null}
     * @return the time, or {@code null} if the text is not a valid time
     */
    @Nullable
    public static LocalTime parse(@Nullable String pickupTime) {
        if (pickupTime == null || pickupTime.length() != 5) {
            return null;
        }
        try {
            return LocalTime.parse(pickupTime, FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
