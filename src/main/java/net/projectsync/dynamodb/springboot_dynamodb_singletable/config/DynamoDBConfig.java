package net.projectsync.dynamodb.springboot_dynamodb_singletable.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import java.net.URI;

@Configuration
public class DynamoDBConfig {

    @Value("${aws.dynamodb.endpoint:}")
    private String endpoint;

    // DEV (LOCAL DYNAMODB)
    @Bean
    @Profile("dev")
    public DynamoDbClient localDynamoDbClient() {
        return DynamoDbClient.builder()
                .region(Region.AP_SOUTH_1)
                // For LOCAL only (comment in prod)
                .endpointOverride(URI.create(endpoint)) // local only
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create("dummy", "dummy")
                        )
                )
                .build();
    }

    // PROD (REAL AWS)
    @Bean
    @Profile("prod")
    public DynamoDbClient prodDynamoDbClient() {
        return DynamoDbClient.builder()
                .region(Region.AP_SOUTH_1)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public DynamoDbEnhancedClient enhancedClient(DynamoDbClient client) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(client)
                .build();
    }
}

/**
 *  Options to run locally:
 *  1. Jar file
 *     - Go to
 *          https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBLocal.DownloadingAndRunning.html
 *     - Download jar file
 *          https://d1ni2b6xgvw0s0.cloudfront.net/v2.x/dynamodb_local_latest.zip
 *     - Extract and run command
 *          java -Djava.library.path=./DynamoDBLocal_lib -jar DynamoDBLocal.jar -sharedDb
 *     - Visualize tables in "NoSQL Workbench"
 *     - Cons: Data not persisted between restarts (need to explore more)
 *
 *  2. Docker image
 *      - docker run -p 8000:8000 amazon/dynamodb-local
 *      - Docker automatically downloads the image
 *      - Starts container
 *      - Runs DynamoDB locally
 *      - Visualize tables in "NoSQL Workbench"
 *      - Cons: Data not persisted between restarts (need to explore more)
 *
 *  3. NoSQL Workbench
 *      - Start DynamoDB local
 *      - Data persisted between restarts
 */