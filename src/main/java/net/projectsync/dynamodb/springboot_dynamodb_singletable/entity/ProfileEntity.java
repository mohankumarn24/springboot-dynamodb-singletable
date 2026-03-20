package net.projectsync.dynamodb.springboot_dynamodb_singletable.entity;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@DynamoDbBean
@Data
public class ProfileEntity {

    private String pk;
    private String sk;

    private String profileId;
    private String profileName;

    private Boolean isDefault;
    private Boolean isDeleted;

    private String gsi3pk;
    private String gsi3sk;

    private String entityType;

    @DynamoDbPartitionKey
    public String getPk() { return pk; }

    @DynamoDbSortKey
    public String getSk() { return sk; }

    @DynamoDbSecondaryPartitionKey(indexNames = "GSI3")
    public String getGsi3pk() { return gsi3pk; }

    @DynamoDbSecondarySortKey(indexNames = "GSI3")
    public String getGsi3sk() { return gsi3sk; }

    // getters/setters
}
