package net.projectsync.dynamodb.springboot_dynamodb_singletable.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class CursorUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String encode(Map<String, AttributeValue> map) {

        if (map == null || map.isEmpty()) {
            return null; // NOT an error → just no next page
        }

        try {
            Map<String, String> simpleMap = new HashMap<>();

            for (Map.Entry<String, AttributeValue> e : map.entrySet()) {
                simpleMap.put(e.getKey(), e.getValue().s());
            }

            String json = new ObjectMapper().writeValueAsString(simpleMap);
            return Base64.getEncoder().encodeToString(json.getBytes());

        } catch (Exception e) {
            throw new RuntimeException("Failed to encode cursor", e);
        }
    }

    public static Map<String, AttributeValue> decode(String cursor) {

        if (cursor == null || cursor.isBlank()) {
            return null;
        }

        try {
            String json = new String(Base64.getDecoder().decode(cursor));

            Map<String, String> simpleMap =
                    new ObjectMapper().readValue(json, Map.class);

            Map<String, AttributeValue> result = new HashMap<>();

            for (Map.Entry<String, String> e : simpleMap.entrySet()) {

                if (e.getValue() == null) continue;

                // Normalize key (important fix)
                String key = e.getKey().toUpperCase();

                if ("PK".equals(key)) {
                    result.put("pk", AttributeValue.builder().s(e.getValue()).build());
                } else if ("SK".equals(key)) {
                    result.put("sk", AttributeValue.builder().s(e.getValue()).build());
                } else {
                    // fallback (if already correct)
                    result.put(e.getKey(), AttributeValue.builder().s(e.getValue()).build());
                }
            }

            return result;

        } catch (Exception e) {
            throw new RuntimeException("Failed to decode cursor: " + cursor, e);
        }
    }
}