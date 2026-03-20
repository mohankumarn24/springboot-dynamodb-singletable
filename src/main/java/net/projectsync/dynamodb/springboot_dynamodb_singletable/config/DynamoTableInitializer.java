package net.projectsync.dynamodb.springboot_dynamodb_singletable.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

@Configuration
@Slf4j
public class DynamoTableInitializer {

    private static final String TABLE = "UserProfileTable";

    @Bean
    @Order(1)
    CommandLineRunner createTable(DynamoDbClient dynamoDbClient) {

        return args -> {

            if (!tableExists(dynamoDbClient, TABLE)) {

                /**
                 * Single Table Design
                 *
                 * PK = pk  (USER#<id>)
                 * SK = sk  (METADATA / PROFILE#<id>)
                 *
                 * GSIs:
                 * - GSI1 → email lookup
                 * - GSI2 → phone lookup
                 * - GSI3 → global profiles
                 */
                dynamoDbClient.createTable(CreateTableRequest.builder()
                        .tableName(TABLE)
                        .billingMode(BillingMode.PAY_PER_REQUEST)

                        // ================= PRIMARY KEY =================
                        .keySchema(
                                KeySchemaElement.builder()
                                        .attributeName("pk")
                                        .keyType(KeyType.HASH)
                                        .build(),
                                KeySchemaElement.builder()
                                        .attributeName("sk")
                                        .keyType(KeyType.RANGE)
                                        .build()
                        )

                        // ================= ATTRIBUTES =================
                        .attributeDefinitions(
                                AttributeDefinition.builder()
                                        .attributeName("pk")
                                        .attributeType(ScalarAttributeType.S)
                                        .build(),
                                AttributeDefinition.builder()
                                        .attributeName("sk")
                                        .attributeType(ScalarAttributeType.S)
                                        .build(),

                                // GSI1 → EMAIL
                                AttributeDefinition.builder()
                                        .attributeName("gsi1pk")
                                        .attributeType(ScalarAttributeType.S)
                                        .build(),
                                AttributeDefinition.builder()
                                        .attributeName("gsi1sk")
                                        .attributeType(ScalarAttributeType.S)
                                        .build(),

                                // GSI2 → PHONE
                                AttributeDefinition.builder()
                                        .attributeName("gsi2pk")
                                        .attributeType(ScalarAttributeType.S)
                                        .build(),
                                AttributeDefinition.builder()
                                        .attributeName("gsi2sk")
                                        .attributeType(ScalarAttributeType.S)
                                        .build(),

                                // GSI3 → GLOBAL PROFILES
                                AttributeDefinition.builder()
                                        .attributeName("gsi3pk")
                                        .attributeType(ScalarAttributeType.S)
                                        .build(),
                                AttributeDefinition.builder()
                                        .attributeName("gsi3sk")
                                        .attributeType(ScalarAttributeType.S)
                                        .build()
                        )

                        // ================= GSIs =================
                        .globalSecondaryIndexes(

                                // GSI1 → Find user by email
                                GlobalSecondaryIndex.builder()
                                        .indexName("GSI1")
                                        .keySchema(
                                                KeySchemaElement.builder()
                                                        .attributeName("gsi1pk")
                                                        .keyType(KeyType.HASH)
                                                        .build(),
                                                KeySchemaElement.builder()
                                                        .attributeName("gsi1sk")
                                                        .keyType(KeyType.RANGE)
                                                        .build()
                                        )
                                        .projection(Projection.builder()
                                                .projectionType(ProjectionType.ALL)
                                                .build())
                                        .build(),

                                // GSI2 → Find user by phone
                                GlobalSecondaryIndex.builder()
                                        .indexName("GSI2")
                                        .keySchema(
                                                KeySchemaElement.builder()
                                                        .attributeName("gsi2pk")
                                                        .keyType(KeyType.HASH)
                                                        .build(),
                                                KeySchemaElement.builder()
                                                        .attributeName("gsi2sk")
                                                        .keyType(KeyType.RANGE)
                                                        .build()
                                        )
                                        .projection(Projection.builder()
                                                .projectionType(ProjectionType.ALL)
                                                .build())
                                        .build(),

                                // GSI3 → Query all profiles globally
                                GlobalSecondaryIndex.builder()
                                        .indexName("GSI3")
                                        .keySchema(
                                                KeySchemaElement.builder()
                                                        .attributeName("gsi3pk")
                                                        .keyType(KeyType.HASH)
                                                        .build(),
                                                KeySchemaElement.builder()
                                                        .attributeName("gsi3sk")
                                                        .keyType(KeyType.RANGE)
                                                        .build()
                                        )
                                        .projection(Projection.builder()
                                                .projectionType(ProjectionType.ALL)
                                                .build())
                                        .build()
                        )
                        .build());

                waitForTable(dynamoDbClient, TABLE);
                log.info("UserProfileTable created");

            } else {
                log.info("UserProfileTable already exists");
            }
        };
    }

    // ================= HELPERS =================

    private boolean tableExists(DynamoDbClient client, String tableName) {
        try {
            client.describeTable(DescribeTableRequest.builder()
                    .tableName(tableName)
                    .build());
            return true;
        } catch (ResourceNotFoundException e) {
            return false;
        }
    }

    private void waitForTable(DynamoDbClient client, String tableName) {
        client.waiter().waitUntilTableExists(
                DescribeTableRequest.builder()
                        .tableName(tableName)
                        .build()
        );
    }
}