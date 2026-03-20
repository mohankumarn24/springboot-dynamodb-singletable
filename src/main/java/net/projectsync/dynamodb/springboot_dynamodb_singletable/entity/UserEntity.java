package net.projectsync.dynamodb.springboot_dynamodb_singletable.entity;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@DynamoDbBean
@Data
public class UserEntity {

    private String pk;
    private String sk;

    private String userId;
    private String name;
    private String email;
    private String phone;

    private String status;
    private String defaultProfileId;
    private String selectedProfileId;

    private Integer profilesCount;
    private Integer entityVersion;

    private String gsi1pk;
    private String gsi1sk;

    private String gsi2pk;
    private String gsi2sk;

    private String entityType;

    @DynamoDbPartitionKey
    public String getPk() { return pk; }

    @DynamoDbSortKey
    public String getSk() { return sk; }

    @DynamoDbSecondaryPartitionKey(indexNames = "GSI1")
    public String getGsi1pk() { return gsi1pk; }

    @DynamoDbSecondarySortKey(indexNames = "GSI1")
    public String getGsi1sk() { return gsi1sk; }

    @DynamoDbSecondaryPartitionKey(indexNames = "GSI2")
    public String getGsi2pk() { return gsi2pk; }

    @DynamoDbSecondarySortKey(indexNames = "GSI2")
    public String getGsi2sk() { return gsi2sk; }

    // getters/setters
}