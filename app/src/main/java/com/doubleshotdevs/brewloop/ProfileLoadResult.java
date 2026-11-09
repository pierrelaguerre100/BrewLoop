package com.doubleshotdevs.brewloop;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Result of a profile load: a {@link ProfileLoadStatus}, plus the profile when loading
 * succeeded.
 */
public final class ProfileLoadResult {

    @NonNull
    private final ProfileLoadStatus status;
    @Nullable
    private final User user;

    private ProfileLoadResult(@NonNull ProfileLoadStatus status, @Nullable User user) {
        this.status = status;
        this.user = user;
    }

    /**
     * Creates a successful result.
     *
     * @param user the valid profile that was loaded
     * @return a result with status {@link ProfileLoadStatus#SUCCESS}
     */
    @NonNull
    public static ProfileLoadResult success(@NonNull User user) {
        return new ProfileLoadResult(ProfileLoadStatus.SUCCESS, user);
    }

    /**
     * Creates a failed result.
     *
     * @param status the failure status; must not be {@link ProfileLoadStatus#SUCCESS}
     * @return a result with no profile
     * @throws IllegalArgumentException if {@code status} is {@code SUCCESS}
     */
    @NonNull
    public static ProfileLoadResult failure(@NonNull ProfileLoadStatus status) {
        if (status == ProfileLoadStatus.SUCCESS) {
            throw new IllegalArgumentException("A successful result requires a profile");
        }
        return new ProfileLoadResult(status, null);
    }

    /**
     * Returns the outcome of the load.
     *
     * @return the load status
     */
    @NonNull
    public ProfileLoadStatus getStatus() {
        return status;
    }

    /**
     * Returns the loaded profile.
     *
     * @return the profile when the status is {@code SUCCESS}, otherwise {@code null}
     */
    @Nullable
    public User getUser() {
        return user;
    }

    /**
     * Indicates whether the profile loaded successfully.
     *
     * @return {@code true} if the status is {@code SUCCESS}
     */
    public boolean isSuccess() {
        return status == ProfileLoadStatus.SUCCESS;
    }
}
