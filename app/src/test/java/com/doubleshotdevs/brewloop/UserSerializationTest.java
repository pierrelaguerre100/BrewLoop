package com.doubleshotdevs.brewloop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.firebase.database.core.utilities.encoding.CustomClassMapper;

import org.junit.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tests that {@link User} serializes to exactly the agreed {@code Users/{uid}} fields,
 * using the same mapper Firebase applies when an object is written with {@code setValue}.
 */
public class UserSerializationTest {

    @Test
    public void user_serializesOnlyContractFields() {
        User user = new User("uid1", "Sam Rivera", "sam@example.com", "customer", 0);

        Map<?, ?> stored = (Map<?, ?>) CustomClassMapper.convertToPlainJavaTypes(user);
        assertNotNull(stored);

        Set<String> expected = new HashSet<>();
        expected.add(User.FIELD_USER_ID);
        expected.add(User.FIELD_FULL_NAME);
        expected.add(User.FIELD_EMAIL);
        expected.add(User.FIELD_ROLE);
        expected.add(User.FIELD_POINTS_BALANCE);
        assertEquals(expected, stored.keySet());
        assertEquals("customer", stored.get(User.FIELD_ROLE));
        Number points = (Number) stored.get(User.FIELD_POINTS_BALANCE);
        assertNotNull(points);
        assertEquals(0, points.intValue());
    }

    @Test
    public void storedRecord_mapsBackToUser() {
        User original = new User("uid1", "Sam Rivera", "sam@example.com", "manager", 75);
        Object stored = CustomClassMapper.convertToPlainJavaTypes(original);

        User copy = CustomClassMapper.convertToCustomClass(stored, User.class);
        assertNotNull(copy);

        assertEquals(original.userID, copy.userID);
        assertEquals(original.fullName, copy.fullName);
        assertEquals(original.email, copy.email);
        assertEquals(original.role, copy.role);
        assertEquals(original.pointsBalance, copy.pointsBalance);
    }
}
