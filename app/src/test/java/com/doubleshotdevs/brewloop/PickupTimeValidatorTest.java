package com.doubleshotdevs.brewloop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.time.LocalTime;

/**
 * Tests pickup time checks. The hours used here are example values only; the team has not
 * yet agreed on the shop's opening hours.
 */
public class PickupTimeValidatorTest {

    private final PickupTimeValidator validator =
            new PickupTimeValidator(LocalTime.of(7, 0), LocalTime.of(19, 0), 10);

    @Test
    public void invalidFormats() {
        assertEquals(PickupTimeValidator.Problem.INVALID_FORMAT, validator.check(null, LocalTime.NOON));
        assertEquals(PickupTimeValidator.Problem.INVALID_FORMAT, validator.check("8:15", LocalTime.NOON));
        assertEquals(PickupTimeValidator.Problem.INVALID_FORMAT, validator.check("25:00", LocalTime.NOON));
        assertEquals(PickupTimeValidator.Problem.INVALID_FORMAT, validator.check("ab:cd", LocalTime.NOON));
    }

    @Test
    public void pastAndTooSoon_areRejected() {
        LocalTime now = LocalTime.of(9, 0);
        assertEquals(PickupTimeValidator.Problem.TOO_SOON, validator.check("08:30", now));
        assertEquals(PickupTimeValidator.Problem.TOO_SOON, validator.check("09:09", now));
        assertNull(validator.check("09:10", now));
    }

    @Test
    public void outsideHours_isRejected() {
        LocalTime now = LocalTime.of(6, 0);
        assertEquals(PickupTimeValidator.Problem.OUTSIDE_SHOP_HOURS, validator.check("06:59", now));
        assertEquals(PickupTimeValidator.Problem.OUTSIDE_SHOP_HOURS, validator.check("19:00", now));
        assertNull(validator.check("07:00", now));
        assertNull(validator.check("18:59", now));
    }

    @Test
    public void lateNight_hasNoValidSameDayTime() {
        PickupTimeValidator allDay =
                new PickupTimeValidator(LocalTime.MIN, LocalTime.of(23, 59), 15);
        assertEquals(PickupTimeValidator.Problem.TOO_SOON,
                allDay.check("23:58", LocalTime.of(23, 50)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void closingBeforeOpening_isRejected() {
        new PickupTimeValidator(LocalTime.of(19, 0), LocalTime.of(7, 0), 0);
    }
}
