package com.doubleshotdevs.brewloop;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests which validated roles each screen access level allows.
 */
public class SessionAccessTest {

    @Test
    public void customerAccess_isOnlyForCustomers() {
        assertTrue(Session.Access.CUSTOMER.isGrantedTo(UserRole.CUSTOMER));
        assertFalse(Session.Access.CUSTOMER.isGrantedTo(UserRole.STAFF));
        assertFalse(Session.Access.CUSTOMER.isGrantedTo(UserRole.MANAGER));
    }

    @Test
    public void staffAccess_includesManagers() {
        assertTrue(Session.Access.STAFF.isGrantedTo(UserRole.STAFF));
        assertTrue(Session.Access.STAFF.isGrantedTo(UserRole.MANAGER));
        assertFalse(Session.Access.STAFF.isGrantedTo(UserRole.CUSTOMER));
    }

    @Test
    public void managerAccess_isOnlyForManagers() {
        assertTrue(Session.Access.MANAGER.isGrantedTo(UserRole.MANAGER));
        assertFalse(Session.Access.MANAGER.isGrantedTo(UserRole.STAFF));
        assertFalse(Session.Access.MANAGER.isGrantedTo(UserRole.CUSTOMER));
    }

    @Test
    public void unknownRole_isNeverGranted() {
        for (Session.Access access : Session.Access.values()) {
            assertFalse(access.name(), access.isGrantedTo(null));
            assertFalse(access.name(), access.isGrantedTo(UserRole.fromDatabaseValue("Manager")));
        }
    }
}
