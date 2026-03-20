package net.projectsync.dynamodb.springboot_dynamodb_singletable.controller;

import jakarta.validation.Valid;
import net.projectsync.dynamodb.springboot_dynamodb_singletable.dto.*;
import net.projectsync.dynamodb.springboot_dynamodb_singletable.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * REST Controller for User + Profile APIs
 *
 * Design Notes:
 * - This controller is thin → delegates all business logic to service layer
 * - Follows REST conventions for resource-based URLs
 * - Represents a "User → Profiles" one-to-many relationship
 *
 * DynamoDB Context:
 * - User and Profile are stored in SINGLE TABLE
 * - Access patterns exposed via APIs:
 *      - Create user + default profile (transaction)
 *      - Add profiles
 *      - Query profiles (PK-based query)
 *      - Login via GSI (email/phone)
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    /**
     * 1. CREATE USER
     *
     * - Creates a user along with a default profile
     * - Uses DynamoDB transaction internally
     *
     * Learning:
     * - Demonstrates atomic writes (user + profile)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@RequestBody @Valid CreateUserRequest req) {
        return service.createUser(req);
    }

    /**
     * 2. CREATE PROFILE
     *
     * - Adds a new profile under a user
     * - Internally updates:
     *      - Profile item
     *      - User.profilesCount
     *
     * Learning:
     * - One-to-many relationship in single-table design
     */
    @PostMapping("/{userId}/profiles")
    @ResponseStatus(HttpStatus.CREATED)
    public ProfileResponse createProfile(@PathVariable String userId,
                              @RequestBody @Valid CreateProfileRequest req) {
        return service.createProfile(userId, req);
    }

    /**
     * 3. UPDATE USER
     *
     * - Typically used to update selectedProfileId
     * - Can also demonstrate optimistic locking using entityVersion
     *
     * Learning:
     * - Conditional writes (if versioning is used)
     */
    @PutMapping("/{userId}")
    @ResponseStatus(HttpStatus.OK)
    public UserResponse updateUser(@PathVariable String userId,
                                   @RequestBody UpdateUserRequest req) {
        return service.updateUser(userId, req);
    }

    /**
     * 4. UPDATE PROFILE
     *
     * - Updates profile attributes like name
     *
     * Learning:
     * - UpdateItem operation in DynamoDB
     */
    @PutMapping("/{userId}/profiles/{profileId}")
    @ResponseStatus(HttpStatus.OK)
    public ProfileResponse updateProfile(@PathVariable String userId,
                                         @PathVariable String profileId,
                                         @RequestBody @Valid UpdateProfileRequest req) {
        return service.updateProfile(userId, profileId, req);
    }

    /**
     * 5. DELETE PROFILE (SOFT DELETE)
     *
     * - Marks profile as deleted instead of removing it
     *
     * Learning:
     * - Soft delete pattern
     * - Important for audit/history use cases
     */
    @DeleteMapping("/{userId}/profiles/{profileId}")
    public ResponseEntity<Void> deleteProfile(@PathVariable String userId,
                                              @PathVariable String profileId) {
        service.deleteProfile(userId, profileId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 6. DELETE USER (SOFT DELETE)
     *
     * - Marks user as DELETED
     *
     * Learning:
     * - Avoids physical deletion → safer + reversible
     */
    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable String userId) {
        service.deleteUser(userId);
    }

    /**
     * 7. LOGIN (via email OR phone)
     *
     * - Uses GSI1 (email) or GSI2 (phone)
     *
     * Learning:
     * - Secondary indexes enable alternate access patterns
     * - GSI queries are eventually consistent
     */
    @GetMapping("/login")
    public UserResponse login(@RequestParam(required = false) String email,
                              @RequestParam(required = false) String phone) {
        return service.login(email, phone);
    }

    /**
     * 8. GET USER
     *
     * - Fetches user metadata using PK + SK
     *
     * Learning:
     * - Direct GetItem using primary key
     */
    @GetMapping("/{userId}")
    public UserResponse getUser(@PathVariable String userId) {
        return service.getUser(userId);
    }

    /**
     * 9. GET PROFILES (PAGINATED)
     *
     * - Queries all profiles under a user
     * - Uses cursor-based pagination (LastEvaluatedKey)
     *
     * Learning:
     * - Efficient pagination in DynamoDB
     * - Avoids OFFSET-based pagination (not supported)
     */
    @GetMapping("/{userId}/profiles")
    public PageResponse<ProfileResponse> getProfiles(
            @PathVariable String userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "5") int limit) {
        return service.getProfiles(userId, cursor, limit);
    }

    /**
     * 10. GET ACTIVE PROFILES
     *
     * - Returns only non-deleted profiles
     *
     * Learning:
     * - Filtering at application level
     * - Tradeoff vs modeling for query efficiency
     */
    @GetMapping("/{userId}/profiles/active")
    public List<ProfileResponse> getActiveProfiles(@PathVariable String userId) {
        return service.getActiveProfiles(userId);
    }

    /**
     * 11. GET SELECTED PROFILE
     *
     * - Fetches currently selected profile of a user
     *
     * Learning:
     * - Two-step access pattern:
     *      1. Get user
     *      2. Fetch profile by ID
     */
    @GetMapping("/{userId}/profiles/selected")
    public ProfileResponse getSelectedProfile(@PathVariable String userId) {
        return service.getSelectedProfile(userId);
    }

    /**
     * 12. ADMIN - GET ALL USERS
     *
     * - Uses Scan operation (NOT recommended for large datasets)
     *
     * Learning:
     * - Scan reads entire table → expensive
     * - Should be avoided in production
     */
    @GetMapping("/admin/all")
    public List<UserResponse> getAllUsers() {
        return service.getAllUsers();
    }

    /**
     * 13. GLOBAL PROFILE QUERY
     *
     * - Fetches profiles across all users using GSI3
     *
     * Learning:
     * - Global access pattern using secondary index
     */
    @GetMapping("/profiles/global")
    public List<ProfileResponse> getAllProfilesGlobal() {
        return service.getAllProfilesGlobal();
    }
}