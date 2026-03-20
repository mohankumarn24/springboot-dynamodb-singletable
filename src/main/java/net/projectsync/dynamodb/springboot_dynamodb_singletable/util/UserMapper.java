package net.projectsync.dynamodb.springboot_dynamodb_singletable.util;

import net.projectsync.dynamodb.springboot_dynamodb_singletable.dto.*;
import net.projectsync.dynamodb.springboot_dynamodb_singletable.entity.ProfileEntity;
import net.projectsync.dynamodb.springboot_dynamodb_singletable.entity.UserEntity;

public class UserMapper {

    private UserMapper() {}

    // ================= USER =================
    public static UserEntity toUser(CreateUserRequest req, String userId) {

        UserEntity u = new UserEntity();

        u.setPk("USER#" + userId);
        u.setSk("METADATA");

        u.setUserId(userId);
        u.setName(req.name());
        u.setEmail(req.email());
        u.setPhone(req.phone());

        u.setStatus("ACTIVE");
        u.setDefaultProfileId("p1");
        u.setSelectedProfileId("p1");

        u.setProfilesCount(1);
        u.setEntityVersion(1);

        if (req.email() != null) {
            u.setGsi1pk("EMAIL#" + req.email());
            u.setGsi1sk("USER#" + userId);
        }

        if (req.phone() != null) {
            u.setGsi2pk("PHONE#" + req.phone());
            u.setGsi2sk("USER#" + userId);
        }

        u.setEntityType("USER");

        return u;
    }

    public static UserResponse toUserResponse(UserEntity u) {
        return new UserResponse(
                u.getUserId(),
                u.getName(),
                u.getSelectedProfileId(),
                u.getDefaultProfileId(),
                u.getProfilesCount(),
                u.getStatus()
        );
    }

    // ================= PROFILE =================
    public static ProfileEntity defaultProfile(String userId) {

        ProfileEntity p = new ProfileEntity();

        p.setPk("USER#" + userId);
        p.setSk("PROFILE#p1");

        p.setProfileId("p1");
        p.setProfileName("Default");

        p.setIsDefault(true);
        p.setIsDeleted(false);

        p.setGsi3pk("PROFILE");
        p.setGsi3sk("USER#" + userId + "#p1");

        p.setEntityType("PROFILE");

        return p;
    }

    public static ProfileEntity toProfile(String userId, String profileId, CreateProfileRequest req) {

        ProfileEntity p = new ProfileEntity();

        p.setPk("USER#" + userId);
        p.setSk("PROFILE#" + profileId);

        p.setProfileId(profileId);
        p.setProfileName(req.profileName());

        p.setIsDefault(Boolean.TRUE.equals(req.isDefault())); // NEW
        p.setIsDeleted(false);

        p.setGsi3pk("PROFILE");
        p.setGsi3sk("USER#" + userId + "#" + profileId);

        p.setEntityType("PROFILE");

        return p;
    }

    public static ProfileResponse toProfileResponse(ProfileEntity p) {
        return new ProfileResponse(
                extractUserId(p.getPk()),   // p.getUserId(),
                p.getProfileId(),
                p.getProfileName(),
                Boolean.TRUE.equals(p.getIsDefault()),
                false
        );
    }

    /**
     * Instead of parsing PK every time, you can store userId directly as 'userId' attribute is already present in entity and then fetch it
     * p.setUserId(userId);
     * @param pk
     * @return
     */
    private static String extractUserId(String pk) {
        return pk.replace("USER#", "");
    }
}