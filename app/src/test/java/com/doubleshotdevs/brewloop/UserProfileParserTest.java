package com.doubleshotdevs.brewloop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.firebase.database.DatabaseError;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests that stored profile data is validated without throwing, using the same plain Java
 * shapes that {@code DataSnapshot.getValue()} returns.
 */
public class UserProfileParserTest {

    private static final String UID = "abcUID123";

    /**
     * Builds a valid stored customer record, as Firebase would return it.
     *
     * @return a mutable map of the record's fields
     */
    private static Map<String, Object> validRecord() {
        Map<String, Object> record = new HashMap<>();
        record.put("userID", UID);
        record.put("fullName", "Sam Rivera");
        record.put("email", "sam@example.com");
        record.put("role", "customer");
        record.put("pointsBalance", 0L);
        return record;
    }

    private static ProfileLoadStatus statusOf(Object raw) {
        return UserProfileParser.parse(UID, raw).getStatus();
    }

    @Test
    public void validRecord_isParsed() {
        ProfileLoadResult result = UserProfileParser.parse(UID, validRecord());

        assertTrue(result.isSuccess());
        User user = result.getUser();
        assertNotNull(user);
        assertEquals(UID, user.userID);
        assertEquals("Sam Rivera", user.fullName);
        assertEquals("sam@example.com", user.email);
        assertEquals("customer", user.role);
        assertEquals(0, user.pointsBalance);
    }

    @Test
    public void staffAndManagerRecords_areParsed() {
        Map<String, Object> staff = validRecord();
        staff.put("role", "staff");
        assertEquals(ProfileLoadStatus.SUCCESS, statusOf(staff));

        Map<String, Object> manager = validRecord();
        manager.put("role", "manager");
        manager.put("pointsBalance", 120L);
        ProfileLoadResult result = UserProfileParser.parse(UID, manager);
        assertTrue(result.isSuccess());
        assertTrue(result.getUser().hasStaffAccess());
        assertEquals(120, result.getUser().pointsBalance);
    }

    @Test
    public void missingRecord_isReportedAsMissing() {
        ProfileLoadResult result = UserProfileParser.parse(UID, null);
        assertEquals(ProfileLoadStatus.MISSING, result.getStatus());
        assertNull(result.getUser());
    }

    @Test
    public void starterTextRecord_isMalformedInsteadOfCrashing() {
        assertEquals(ProfileLoadStatus.MALFORMED, statusOf("Cristiano Ronaldo, customer, 38 pts"));
    }

    @Test
    public void nonObjectValues_areMalformed() {
        assertEquals(ProfileLoadStatus.MALFORMED, statusOf(42L));
        assertEquals(ProfileLoadStatus.MALFORMED, statusOf(true));
        assertEquals(ProfileLoadStatus.MALFORMED, statusOf(new ArrayList<>()));
    }

    @Test
    public void differentUserId_isUidMismatch() {
        Map<String, Object> record = validRecord();
        record.put("userID", "someoneElse");
        assertEquals(ProfileLoadStatus.UID_MISMATCH, statusOf(record));
    }

    @Test
    public void missingOrNonTextUserId_isMalformed() {
        Map<String, Object> missing = validRecord();
        missing.remove("userID");
        assertEquals(ProfileLoadStatus.MALFORMED, statusOf(missing));

        Map<String, Object> numeric = validRecord();
        numeric.put("userID", 123L);
        assertEquals(ProfileLoadStatus.MALFORMED, statusOf(numeric));
    }

    @Test
    public void missingOrBlankRequiredFields_areMalformed() {
        for (String field : new String[]{"fullName", "email", "role", "pointsBalance"}) {
            Map<String, Object> record = validRecord();
            record.remove(field);
            assertEquals("missing " + field, ProfileLoadStatus.MALFORMED, statusOf(record));
        }
        Map<String, Object> blankName = validRecord();
        blankName.put("fullName", "   ");
        assertEquals(ProfileLoadStatus.MALFORMED, statusOf(blankName));
    }

    @Test
    public void unsupportedRole_isReportedSeparately() {
        Map<String, Object> admin = validRecord();
        admin.put("role", "admin");
        assertEquals(ProfileLoadStatus.UNSUPPORTED_ROLE, statusOf(admin));

        Map<String, Object> capitalized = validRecord();
        capitalized.put("role", "Staff");
        assertEquals(ProfileLoadStatus.UNSUPPORTED_ROLE, statusOf(capitalized));
    }

    @Test
    public void nonTextRole_isMalformed() {
        Map<String, Object> record = validRecord();
        record.put("role", 1L);
        assertEquals(ProfileLoadStatus.MALFORMED, statusOf(record));
    }

    @Test
    public void pointsBalance_mustBeWholeNonNegativeInt() {
        Object[] invalid = {-1L, 2.5, "38", (long) Integer.MAX_VALUE + 1, Double.NaN, -0.5};
        for (Object points : invalid) {
            Map<String, Object> record = validRecord();
            record.put("pointsBalance", points);
            assertEquals("points " + points, ProfileLoadStatus.MALFORMED, statusOf(record));
        }

        Map<String, Object> wholeDouble = validRecord();
        wholeDouble.put("pointsBalance", 50.0);
        ProfileLoadResult result = UserProfileParser.parse(UID, wholeDouble);
        assertTrue(result.isSuccess());
        assertEquals(50, result.getUser().pointsBalance);
    }

    @Test
    public void databaseErrors_mapToStatuses() {
        assertEquals(ProfileLoadStatus.PERMISSION_DENIED,
                UserProfileParser.statusForDatabaseError(DatabaseError.PERMISSION_DENIED));
        assertEquals(ProfileLoadStatus.NETWORK_ERROR,
                UserProfileParser.statusForDatabaseError(DatabaseError.NETWORK_ERROR));
        assertEquals(ProfileLoadStatus.NETWORK_ERROR,
                UserProfileParser.statusForDatabaseError(DatabaseError.DISCONNECTED));
        assertEquals(ProfileLoadStatus.NETWORK_ERROR,
                UserProfileParser.statusForDatabaseError(DatabaseError.UNAVAILABLE));
        assertEquals(ProfileLoadStatus.UNKNOWN_ERROR,
                UserProfileParser.statusForDatabaseError(DatabaseError.UNKNOWN_ERROR));
    }

    @Test
    public void uidValidation_rejectsUnsafeKeys() {
        assertTrue(UserProfileParser.isValidUid("aZ09_-uid"));
        assertFalse(UserProfileParser.isValidUid(null));
        assertFalse(UserProfileParser.isValidUid(""));
        for (String bad : new String[]{"a.b", "a#b", "a$b", "a[b", "a]b", "a/b", "a\nb"}) {
            assertFalse(bad, UserProfileParser.isValidUid(bad));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void successStatus_cannotBeUsedAsFailure() {
        ProfileLoadResult.failure(ProfileLoadStatus.SUCCESS);
    }
}
