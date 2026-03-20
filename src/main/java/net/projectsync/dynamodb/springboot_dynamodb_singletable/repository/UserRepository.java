package net.projectsync.dynamodb.springboot_dynamodb_singletable.repository;

import net.projectsync.dynamodb.springboot_dynamodb_singletable.dto.*;
import net.projectsync.dynamodb.springboot_dynamodb_singletable.entity.ProfileEntity;
import net.projectsync.dynamodb.springboot_dynamodb_singletable.entity.UserEntity;
import net.projectsync.dynamodb.springboot_dynamodb_singletable.util.CursorUtil;
import net.projectsync.dynamodb.springboot_dynamodb_singletable.util.UserMapper;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import java.util.*;

/**
 * Repository Layer → Direct interaction with DynamoDB
 *
 * Design:
 * - Single-table design: User + Profile stored in same table
 * - PK = USER#<userId>
 * - SK =
 *      - METADATA (User)
 *      - PROFILE#<profileId> (Profiles)
 *
 * GSIs:
 * - GSI1 → Email lookup
 * - GSI2 → Phone lookup
 * - GSI3 → Global profile query
 *
 * Important:
 * - Repository should NOT contain business logic
 * - Only persistence logic
 */
@Repository
public class UserRepository {

    private static final String TABLE = "UserProfileTable";

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<UserEntity> userTable;
    private final DynamoDbTable<ProfileEntity> profileTable;

    public UserRepository(DynamoDbEnhancedClient enhancedClient) {
        this.enhancedClient = enhancedClient;
        this.userTable = enhancedClient.table(TABLE, TableSchema.fromBean(UserEntity.class));
        this.profileTable = enhancedClient.table(TABLE, TableSchema.fromBean(ProfileEntity.class));
    }

    // ===========================
    // 1. CREATE USER (TRANSACTION)
    // ===========================

    /**
     * Creates:
     * - User item (PK = USER#id, SK = METADATA)
     * - Default profile (PK = USER#id, SK = PROFILE#id)
     *
     * DynamoDB:
     * - Uses TransactWriteItems → atomic operation
     */
    public UserResponse createUser(CreateUserRequest req) {

        String userId = UUID.randomUUID().toString();

        UserEntity user = UserMapper.toUser(req, userId);
        ProfileEntity profile = UserMapper.defaultProfile(userId);

        enhancedClient.transactWriteItems(
                TransactWriteItemsEnhancedRequest.builder()
                        .addPutItem(userTable, user)
                        .addPutItem(profileTable, profile)
                        .build()
        );

        return UserMapper.toUserResponse(user);
    }

    // =========================
    // 2. CREATE PROFILE
    // =========================

    /**
     * Adds a profile under a user
     *
     * DynamoDB:
     * - Transaction:
     *      - Insert profile
     *      - Update user (profilesCount)
     */
    public ProfileResponse createProfile(UserEntity user, CreateProfileRequest req) {

        String userId = user.getUserId();
        String profileId = UUID.randomUUID().toString();

        ProfileEntity profile = UserMapper.toProfile(userId, profileId, req);

        boolean makeDefault = Boolean.TRUE.equals(req.isDefault());

        ProfileEntity oldDefault = null;

        // Handle default profile switch
        if (makeDefault) {

            oldDefault = getProfile(userId, user.getDefaultProfileId());

            if (oldDefault != null) {
                oldDefault.setIsDefault(false);
            }

            profile.setIsDefault(true);

            user.setDefaultProfileId(profileId);
            user.setSelectedProfileId(profileId); // optional but good
        }

        // increment profile count
        user.setProfilesCount(user.getProfilesCount() + 1);

        // Build transaction
        TransactWriteItemsEnhancedRequest.Builder tx =
                TransactWriteItemsEnhancedRequest.builder();

        tx.addPutItem(profileTable, profile);
        tx.addUpdateItem(userTable, user);

        if (oldDefault != null) {
            tx.addUpdateItem(profileTable, oldDefault);
        }

        enhancedClient.transactWriteItems(tx.build());

        return UserMapper.toProfileResponse(profile);
    }

    // =========================
    // 3. UPDATE USER
    // =========================

    /**
     * Updates user (selectedProfileId, version)
     *
     * Learning:
     * - Demonstrates UpdateItem
     * - Can be enhanced with optimistic locking
     */
    public UserEntity updateUser(UserEntity user) {
        userTable.updateItem(user);
        return user; // return persisted entity
    }

    // =========================
    // 4. UPDATE PROFILE
    // =========================

    /**
     * Updates profile attributes
     */
    public ProfileEntity updateProfile(ProfileEntity profile) {

        profileTable.updateItem(profile);
        return profile;
    }

    // =========================
    // 5. DELETE PROFILE (SOFT)
    // =========================

    /**
     * Soft delete profile
     *
     * DynamoDB:
     * - Transaction:
     *      - Mark profile as deleted
     *      - Decrement profilesCount
     */
    public void deleteProfile(String userId, String profileId) {

        ProfileEntity profile = getProfile(userId, profileId);
        UserEntity user = getUserEntity(userId);

        profile.setIsDeleted(true);
        user.setProfilesCount(user.getProfilesCount() - 1);

        enhancedClient.transactWriteItems(
                TransactWriteItemsEnhancedRequest.builder()
                        .addUpdateItem(profileTable, profile)
                        .addUpdateItem(userTable, user)
                        .build()
        );
    }

    // =========================
    // 6. DELETE USER (SOFT)
    // =========================

    /**
     * Marks user as DELETED
     */
    public void deleteUser(String userId) {

        UserEntity user = getUserEntity(userId);

        if (user == null) {
            throw new IllegalStateException("User not found");
        }

        if ("DELETED".equals(user.getStatus())) {
            throw new IllegalStateException("User already deleted");
        }

        user.setStatus("DELETED");
        user.setEntityVersion(user.getEntityVersion() + 1);

        userTable.updateItem(user);
    }

    // Transaction version
    public void deleteUserAndAssociatedProfiles(String userId) {

        UserEntity user = getUserEntity(userId);

        if (user == null) {
            throw new IllegalStateException("User not found");
        }

        if ("DELETED".equals(user.getStatus())) {
            throw new IllegalStateException("User already deleted");
        }

        List<ProfileEntity> profiles = getAllProfilesRaw(userId);

        // DynamoDB transaction limit = 25 items
        if (profiles.size() > 24) {
            throw new IllegalStateException("Too many profiles for transaction");
        }

        user.setStatus("DELETED");
        user.setEntityVersion(user.getEntityVersion() + 1);

        TransactWriteItemsEnhancedRequest.Builder tx =
                TransactWriteItemsEnhancedRequest.builder();

        tx.addUpdateItem(userTable, user);

        for (ProfileEntity p : profiles) {
            p.setIsDeleted(true);
            tx.addUpdateItem(profileTable, p);
        }

        enhancedClient.transactWriteItems(tx.build());
    }


    // =========================
    // 7. LOGIN (GSI QUERY)
    // =========================

    /**
     * Find user by email using GSI1
     *
     * Note:
     * - GSI queries are eventually consistent
     */
    public UserEntity findByEmail(String email) {

        return userTable.index("GSI1")
                .query(r -> r.queryConditional(
                        QueryConditional.keyEqualTo(
                                Key.builder().partitionValue("EMAIL#" + email).build())))
                .stream()
                .flatMap(p -> p.items().stream())
                .findFirst()
                .orElse(null);
    }

    /**
     * Find user by phone using GSI2
     */
    public UserEntity findByPhone(String phone) {

        return userTable.index("GSI2")
                .query(r -> r.queryConditional(
                        QueryConditional.keyEqualTo(
                                Key.builder().partitionValue("PHONE#" + phone).build())))
                .stream()
                .flatMap(p -> p.items().stream())
                .findFirst()
                .orElse(null);
    }

    // =========================
    // 8. GET USER (PRIMARY KEY)
    // =========================

    /**
     * Fetch user using PK + SK
     *
     * PK = USER#id
     * SK = METADATA
     */
    public UserEntity getUserEntity(String userId) {

        return userTable.getItem(Key.builder()
                .partitionValue("USER#" + userId)
                .sortValue("METADATA")
                .build());
    }

    // =========================
    // 9. GET PROFILES (PAGINATION)
    // =========================

    /**
     * Fetch profiles using PK query
     *
     * Current:
     * - Fetch all items under PK
     * - Filter in memory
     *
     * Better (learning improvement):
     * - Use begins_with(SK, "PROFILE#")
     */
    public PageResponse<ProfileResponse> getProfiles(String userId,
                                                     String cursor,
                                                     int limit) {

        try {
            // Safe cursor handling
            Map<String, AttributeValue> lastKey =
                    (cursor == null) ? null : CursorUtil.decode(cursor);

            PageIterable<ProfileEntity> pages = profileTable.query(
                    QueryEnhancedRequest.builder()
                            .queryConditional(
                                    QueryConditional.sortBeginsWith(
                                            Key.builder()
                                                    .partitionValue("USER#" + userId)
                                                    .sortValue("PROFILE#") // only profiles
                                                    .build()
                                    )
                            )
                            .limit(limit)
                            .exclusiveStartKey(lastKey)
                            .build()
            );

            List<ProfileResponse> result = new ArrayList<>();
            Map<String, AttributeValue> nextKey = null;

            // Only fetch FIRST page (important for pagination)
            Page<ProfileEntity> page = pages.stream().findFirst().orElse(null);

            if (page != null) {

                for (ProfileEntity p : page.items()) {

                    // Skip non-profile items (extra safety)
                    if (p.getProfileId() == null) continue;

                    // Skip deleted profiles
                    if (Boolean.TRUE.equals(p.getIsDeleted())) continue;

                    result.add(UserMapper.toProfileResponse(p));
                }

                nextKey = page.lastEvaluatedKey();
            }

            // Safe encoding (fix for your 500 error)
            String nextCursor =
                    (nextKey == null || nextKey.isEmpty())
                            ? null
                            : CursorUtil.encode(nextKey);

            return new PageResponse<>(result, nextCursor);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    // ==================================
    // 10. ALL PROFILES & ACTIVE PROFILES
    // ==================================

    public List<ProfileEntity> getAllProfilesRaw(String userId) {

        return profileTable.query(r -> r.queryConditional(
                        QueryConditional.sortBeginsWith(
                                Key.builder()
                                        .partitionValue("USER#" + userId)
                                        .sortValue("PROFILE#")
                                        .build()
                        )
                ))
                .stream()
                .flatMap(p -> p.items().stream())
                .toList();
    }

    public List<ProfileResponse> getActiveProfiles(String userId) {

        return profileTable.query(r -> r.queryConditional(
                        QueryConditional.keyEqualTo(
                                Key.builder().partitionValue("USER#" + userId).build())))
                .stream()
                .flatMap(p -> p.items().stream())
                .filter(p -> !Boolean.TRUE.equals(p.getIsDeleted()))
                .map(UserMapper::toProfileResponse)
                .toList();
    }



    // =========================
    // 11. SELECTED PROFILE
    // =========================

    public ProfileResponse getSelectedProfile(String userId) {

        UserEntity user = getUserEntity(userId);

        ProfileEntity profile = getProfile(userId, user.getSelectedProfileId());

        return UserMapper.toProfileResponse(profile);
    }

    // =========================
    // 12. SCAN USERS (NOT SCALABLE)
    // =========================

    public List<UserResponse> scanUsers() {

        return userTable.scan()
                .items()
                .stream()
                .filter(u -> u.getUserId() != null) // filter invalid mapped items
                .map(UserMapper::toUserResponse)
                .toList();
    }

    // =========================
    // 13. GLOBAL PROFILE QUERY (GSI3)
    // =========================

    public List<ProfileResponse> queryAllProfiles() {

        return profileTable.index("GSI3")
                .query(r -> r.queryConditional(
                        QueryConditional.keyEqualTo(
                                Key.builder().partitionValue("PROFILE").build())))
                .stream()
                .flatMap(p -> p.items().stream())
                .map(UserMapper::toProfileResponse)
                .toList();
    }

    // =========================
    // HELPERS
    // =========================

    public ProfileEntity getProfile(String userId, String profileId) {

        return profileTable.getItem(Key.builder()
                .partitionValue("USER#" + userId)
                .sortValue("PROFILE#" + profileId)
                .build());
    }

    public void updateSelectedProfile(String userId, String profileId) {

        UserEntity user = getUserEntity(userId);
        user.setSelectedProfileId(profileId);

        userTable.updateItem(user);
    }
}