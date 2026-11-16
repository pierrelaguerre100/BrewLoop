package com.doubleshotdevs.brewloop;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Tests that routing uses only the validated stored role and load status.
 */
public class RoleRouterTest {

    private static ProfileLoadResult profileWithRole(String role) {
        return ProfileLoadResult.success(new User("uid1", "Sam", "sam@example.com", role, 0));
    }

    @Test
    public void customer_goesToCustomerHome() {
        assertEquals(RoleRouter.Destination.CUSTOMER_HOME,
                RoleRouter.route(profileWithRole("customer")));
    }

    @Test
    public void staffAndManager_getUnavailableState() {
        assertEquals(RoleRouter.Destination.STAFF_UNAVAILABLE,
                RoleRouter.route(profileWithRole("staff")));
        assertEquals(RoleRouter.Destination.STAFF_UNAVAILABLE,
                RoleRouter.route(profileWithRole("manager")));
    }

    @Test
    public void unsupportedRoleInSuccessfulResult_isNotRoutedAnywhereUseful() {
        // The parser rejects such records, but routing must still fail closed.
        assertEquals(RoleRouter.Destination.CONTACT_SHOP,
                RoleRouter.route(profileWithRole("Staff")));
    }

    @Test
    public void failures_mapToRecoveryActions() {
        assertEquals(RoleRouter.Destination.SIGN_IN,
                RoleRouter.route(ProfileLoadResult.failure(ProfileLoadStatus.NOT_SIGNED_IN)));
        assertEquals(RoleRouter.Destination.COMPLETE_PROFILE,
                RoleRouter.route(ProfileLoadResult.failure(ProfileLoadStatus.MISSING)));
        assertEquals(RoleRouter.Destination.RETRY,
                RoleRouter.route(ProfileLoadResult.failure(ProfileLoadStatus.NETWORK_ERROR)));
        assertEquals(RoleRouter.Destination.RETRY,
                RoleRouter.route(ProfileLoadResult.failure(ProfileLoadStatus.UNKNOWN_ERROR)));
    }

    @Test
    public void unusableProfiles_requireTheShop() {
        ProfileLoadStatus[] statuses = {
                ProfileLoadStatus.MALFORMED,
                ProfileLoadStatus.UID_MISMATCH,
                ProfileLoadStatus.UNSUPPORTED_ROLE,
                ProfileLoadStatus.PERMISSION_DENIED
        };
        for (ProfileLoadStatus status : statuses) {
            assertEquals(status.name(), RoleRouter.Destination.CONTACT_SHOP,
                    RoleRouter.route(ProfileLoadResult.failure(status)));
        }
    }

    @Test
    public void everyFailureStatus_hasAMessage() {
        for (ProfileLoadStatus status : ProfileLoadStatus.values()) {
            // Throws if a status were left without a message.
            AuthErrorMessages.forProfileStatus(status);
        }
        for (CredentialValidator.Error error : CredentialValidator.Error.values()) {
            AuthErrorMessages.forValidation(error);
        }
    }

    @Test
    public void unknownAndWrongPassword_shareOneMessage() {
        assertEquals(AuthErrorMessages.forErrorCode("ERROR_USER_NOT_FOUND"),
                AuthErrorMessages.forErrorCode("ERROR_WRONG_PASSWORD"));
        assertEquals(AuthErrorMessages.forErrorCode("ERROR_INVALID_CREDENTIAL"),
                AuthErrorMessages.forErrorCode("ERROR_WRONG_PASSWORD"));
        assertEquals(R.string.error_email_in_use,
                AuthErrorMessages.forErrorCode("ERROR_EMAIL_ALREADY_IN_USE"));
        assertEquals(R.string.error_auth_generic,
                AuthErrorMessages.forErrorCode("SOMETHING_NEW"));
    }
}
