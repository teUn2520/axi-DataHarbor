package axitask.axi_crud.service.impl;

import axitask.axi_crud.DTO.ExternalResponse;
import axitask.axi_crud.service.S3ExportService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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
public class S3ExportServiceImpl implements S3ExportService {
    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    @Override
    public ExternalResponse[] fetchAndUploadToS3(String requestBody) {
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
                return objectMapper
                        .treeToValue(answers, ExternalResponse[].class);
            }

            return new ExternalResponse[0];

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Ошибка в ходе обработки JSON.", e);
        } catch (Exception e) {
            throw new RuntimeException("Неизвестная ошибка.", e);
        }
    }

    @Override
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
