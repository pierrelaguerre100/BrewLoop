package com.doubleshotdevs.brewloop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests role parsing and the rule that managers inherit staff capabilities.
 */
public class UserRoleTest {

    @Test
    public void fromDatabaseValue_acceptsExactStoredValues() {
        assertEquals(UserRole.CUSTOMER, UserRole.fromDatabaseValue("customer"));
        assertEquals(UserRole.STAFF, UserRole.fromDatabaseValue("staff"));
        assertEquals(UserRole.MANAGER, UserRole.fromDatabaseValue("manager"));
    }

    @Test
    public void fromDatabaseValue_rejectsUnsupportedOrMiscasedValues() {
        assertNull(UserRole.fromDatabaseValue(null));
        assertNull(UserRole.fromDatabaseValue(""));
        assertNull(UserRole.fromDatabaseValue("Staff"));
        assertNull(UserRole.fromDatabaseValue(" customer"));
        assertNull(UserRole.fromDatabaseValue("admin"));
        assertNull(UserRole.fromDatabaseValue("owner"));
    }

    @Test
    public void databaseValue_roundTripsForEveryRole() {
        for (UserRole role : UserRole.values()) {
            assertEquals(role, UserRole.fromDatabaseValue(role.getDatabaseValue()));
        }
    }

    @Test
    public void managersInheritStaffAccess_customersDoNot() {
        assertTrue(UserRole.STAFF.hasStaffAccess());
        assertTrue(UserRole.MANAGER.hasStaffAccess());
        assertFalse(UserRole.CUSTOMER.hasStaffAccess());
    }

    @Test
    public void onlyManagersHaveManagerAccess() {
        assertTrue(UserRole.MANAGER.hasManagerAccess());
        assertFalse(UserRole.STAFF.hasManagerAccess());
        assertFalse(UserRole.CUSTOMER.hasManagerAccess());
    }

    @Test
    public void userHelpers_followStoredRole() {
        User manager = new User("uid1", "Mia", "mia@example.com", "manager", 0);
        assertTrue(manager.hasStaffAccess());
        assertFalse(manager.isCustomer());

        User customer = new User("uid2", "Sam", "sam@example.com", "customer", 0);
        assertFalse(customer.hasStaffAccess());
        assertTrue(customer.isCustomer());

        User unknown = new User("uid3", "Lee", "lee@example.com", "Manager", 0);
        assertNull(unknown.getUserRole());
        assertFalse(unknown.hasStaffAccess());
        assertFalse(unknown.isCustomer());
    }
}
