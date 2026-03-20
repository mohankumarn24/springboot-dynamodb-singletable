package net.projectsync.dynamodb.springboot_dynamodb_singletable.service;

import lombok.RequiredArgsConstructor;
import net.projectsync.dynamodb.springboot_dynamodb_singletable.dto.*;
import net.projectsync.dynamodb.springboot_dynamodb_singletable.entity.ProfileEntity;
import net.projectsync.dynamodb.springboot_dynamodb_singletable.entity.UserEntity;
import net.projectsync.dynamodb.springboot_dynamodb_singletable.repository.UserRepository;
import net.projectsync.dynamodb.springboot_dynamodb_singletable.util.UserMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service Layer → Contains BUSINESS LOGIC
 *
 * Responsibilities:
 * - Validation (user exists, active, limits, etc.)
 * - Business rules (max profiles, default profile protection)
 * - Delegates persistence to repository
 *
 * DynamoDB Context:
 * - Repository handles DB operations
 * - Service enforces rules BEFORE DB calls
 *
 * Design Principle:
 * - "Thin Controller, Smart Service, Dumb Repository"
 */
@Service
public class UserService {

    private final UserRepository repo;

    public UserService(UserRepository repo) {
        this.repo = repo;
    }

    // =========================
    // CREATE USER
    // =========================

    /**
     * Creates a new user
     *
     * Rules:
     * - Either email or phone must be provided
     *
     * DynamoDB:
     * - Repository performs transaction:
     *      User + Default Profile
     */
    public UserResponse createUser(CreateUserRequest req) {

        if (req.email() == null && req.phone() == null) {
            throw new IllegalArgumentException("Email or phone required");
        }

        // (Optional learning improvement: normalize email)
        // if (req.email() != null) {
        //     req = new CreateUserRequest(
        //         req.name(),
        //         req.email().toLowerCase().trim(),
        //         req.phone()
        //     );
        // }

        return repo.createUser(req);
    }

    // =========================
    // CREATE PROFILE
    // =========================

    /**
     * Adds a profile to a user
     *
     * Rules:
     * - User must be ACTIVE
     * - Max 5 profiles allowed
     *
     * DynamoDB:
     * - Transaction:
     *      - Insert profile
     *      - Increment profilesCount
     */
    public ProfileResponse createProfile(String userId, CreateProfileRequest req) {

        UserEntity user = validateActiveUser(userId);

        if (user.getProfilesCount() >= 5) {
            throw new IllegalStateException("Max profiles reached");
        }

        return repo.createProfile(user, req);
    }

    // =========================
    // UPDATE USER
    // =========================

    /**
     * Updates user (mainly selectedProfileId)
     *
     * Learning:
     * - Can demonstrate optimistic locking using entityVersion
     */
    public UserResponse updateUser(String userId, UpdateUserRequest req) {

        UserEntity user = repo.getUserEntity(userId);

        if (user == null) {
            throw new IllegalStateException("User not found");
        }

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new IllegalStateException("User not active");
        }

        if (req.name() == null && req.status() == null) {
            throw new IllegalArgumentException("Nothing to update");
        }

        // apply updates
        if (req.name() != null) {
            user.setName(req.name());
        }

        if (req.status() != null) {
            user.setStatus(req.status());
        }

        // version increment
        user.setEntityVersion(user.getEntityVersion() + 1);

        // use returned value
        UserEntity updatedUser = repo.updateUser(user);

        return UserMapper.toUserResponse(updatedUser);
    }

    // =========================
    // UPDATE PROFILE
    // =========================

    /**
     * Updates profile details (name, etc.)
     */
    public ProfileResponse updateProfile(String userId, String profileId, UpdateProfileRequest req) {

        // check nothing to update
        if (req.profileName() == null) {
            throw new IllegalArgumentException("Nothing to update");
        }

        ProfileEntity profile = validateActiveProfile(userId, profileId);
        // update field
        profile.setProfileName(req.profileName());
        ProfileEntity updated = repo.updateProfile(profile);
        return UserMapper.toProfileResponse(updated);
    }

    // =========================
    // DELETE PROFILE (SOFT)
    // =========================

    /**
     * Soft deletes a profile
     *
     * Rules:
     * - Cannot delete default profile
     * - If selected profile → fallback to default
     *
     * DynamoDB:
     * - Transaction:
     *      - Mark profile as deleted
     *      - Decrement profilesCount
     */
    public void deleteProfile(String userId, String profileId) {

        UserEntity user = validateActiveUser(userId);
        ProfileEntity profile = validateActiveProfile(userId, profileId);

        // ❌ Cannot delete default profile
        if (profileId.equals(user.getDefaultProfileId())) {
            throw new IllegalStateException("Cannot delete default profile");
        }

        // ❌ Cannot delete active (selected) profile
        if (profileId.equals(user.getSelectedProfileId())) {
            throw new IllegalStateException("Cannot delete active profile");
        }

        // ✅ Safe to delete
        repo.deleteProfile(userId, profileId);
    }

    // =========================
    // DELETE USER (SOFT)
    // =========================

    /**
     * Soft delete user
     *
     * Rule:
     * - Prevent duplicate delete
     */
    public void deleteUser(String userId) {

        UserEntity user = repo.getUserEntity(userId);

        if (user == null) {
            throw new IllegalStateException("User not found");
        }

        if ("DELETED".equals(user.getStatus())) {
            throw new IllegalStateException("User already deleted");
        }

        // soft delete
        user.setStatus("DELETED");

        // version increment (keep consistency with update APIs)
        user.setEntityVersion(user.getEntityVersion() + 1);

        repo.updateUser(user);
    }

    // =========================
    // LOGIN
    // =========================

    /**
     * Login using email OR phone
     *
     * DynamoDB:
     * - Uses GSI:
     *      - GSI1 → email
     *      - GSI2 → phone
     *
     * Note:
     * - GSI queries are eventually consistent
     */
    public UserResponse login(String email, String phone) {

        if (email == null && phone == null) {
            throw new IllegalArgumentException("Provide email or phone");
        }

        UserEntity user = (email != null)
                ? repo.findByEmail(email)
                : repo.findByPhone(phone);

        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new IllegalStateException("Invalid user");
        }

        return UserMapper.toUserResponse(user);
    }

    // =========================
    // GET USER
    // =========================

    /**
     * Fetch user metadata
     */
    public UserResponse getUser(String userId) {
        return UserMapper.toUserResponse(validateUser(userId));
    }

    // =========================
    // GET PROFILES
    // =========================

    /**
     * Paginated profile fetch
     *
     * Learning:
     * - Uses cursor-based pagination (LastEvaluatedKey)
     */
    public PageResponse<ProfileResponse> getProfiles(String userId, String cursor, int limit) {
        validateUser(userId);
        return repo.getProfiles(userId, cursor, limit);
    }

    /**
     * Fetch only active profiles (not deleted)
     */
    public List<ProfileResponse> getActiveProfiles(String userId) {
        validateUser(userId);
        return repo.getActiveProfiles(userId);
    }

    /**
     * Fetch selected profile of user
     */
    public ProfileResponse getSelectedProfile(String userId) {
        validateUser(userId);
        return repo.getSelectedProfile(userId);
    }

    // =========================
    // ADMIN
    // =========================

    /**
     * Scan all users (not scalable)
     *
     * Learning:
     * - Scan reads entire table → expensive
     */
    public List<UserResponse> getAllUsers() {
        return repo.scanUsers();
    }

    /**
     * Fetch all profiles globally using GSI
     */
    public List<ProfileResponse> getAllProfilesGlobal() {
        return repo.queryAllProfiles();
    }

    // =========================
    // VALIDATIONS
    // =========================

    /**
     * Validate user exists
     */
    private UserEntity validateUser(String userId) {

        UserEntity user = repo.getUserEntity(userId);

        if (user == null) {
            throw new IllegalStateException("User not found");
        }

        return user;
    }

    /**
     * Validate user exists AND is ACTIVE
     */
    private UserEntity validateActiveUser(String userId) {

        UserEntity user = validateUser(userId);

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new IllegalStateException("User not active");
        }

        return user;
    }

    /**
     * Validate profile exists and is not deleted
     */
    private ProfileEntity validateActiveProfile(String userId, String profileId) {

        ProfileEntity profile = repo.getProfile(userId, profileId);

        if (profile == null || Boolean.TRUE.equals(profile.getIsDeleted())) {
            throw new IllegalStateException("Profile not found");
        }

        return profile;
    }
}