package axitask.axi_S3master.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class B2ExportService {
    private final RestTemplate restTemplate;
    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    public ArrayNode fetchAndUploadToS3(String requestBody) {
        try {
            byte[] requestBytes = requestBody.getBytes(StandardCharsets.ISO_8859_1);
            String normalizedBody = new String(requestBytes, StandardCharsets.UTF_8);

            List<Map<String, Object>> requests = objectMapper.readValue(
                    normalizedBody,
                    new TypeReference<List<Map<String, Object>>>() {}
            );

            ArrayNode answers = objectMapper.createArrayNode();

            for (Map<String, Object> request : requests) {
                try {
                    Object jsonData = request.get("jsonData");
                    if (jsonData == null) {
                        throw new RuntimeException("Пустое значение 'jsonData'.");
                    }

                    String id = objectMapper.convertValue(request.get("id"), String.class);

                    String fileName = generateId();
                    String savePath = "records/" + fileName;

                    byte[] content;
                    content = objectMapper.writeValueAsBytes(jsonData);

                    uploadToS3(savePath, content);

                    answers.add(objectMapper.createObjectNode()
                            .put("id", id)
                            .put("externalId", fileName));

                } catch (Exception e) {
                    throw new RuntimeException("Ошибка в ходе обработки значения.", e);
                }
            }

            if (!answers.isEmpty()) {
                ResponseEntity<ArrayNode> response = restTemplate.postForEntity(
                        "http://localhost:8080/api/v1/update-db",
                        answers,
                        ArrayNode.class
                );
            }

            return objectMapper.createArrayNode();

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Ошибка в ходе обработки JSON.", e);
        } catch (Exception e) {
            throw new RuntimeException("Неизвестная ошибка.", e);
        }
    }

    public String readJsonAsNode(String key) throws IOException {
        try (ResponseInputStream<GetObjectResponse> s3Response = s3Client.getObject(
                GetObjectRequest.builder()
                        .bucket("axi-bucket")
                        .key("records/" + key)
                        .build())) {

            JsonNode jsonNode = objectMapper.readTree(s3Response);
            String jsonData = objectMapper.writeValueAsString(jsonNode);

            return jsonData;
        }
    }

    private void uploadToS3(String key, byte[] fileBytes) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket("axi-bucket")
                        .key(key)
                        .contentType("application/json; charset=UTF-8")
                        .contentEncoding("UTF-8")
                        .build(),
                RequestBody.fromBytes(fileBytes)
            );
    }

    private String generateId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
