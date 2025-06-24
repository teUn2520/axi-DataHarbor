package axitask.axi_crud.service.impl;

import axitask.axi_crud.service.S3ExportService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class S3ExportServiceImpl implements S3ExportService {
    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    @Override
    public String buildAndUploadToS3(String id, byte[] content) {
        try {
            String fileName = "record_" + id;
            String savePath = "records/" + fileName;

            if (doesRecordExist(savePath)) {
                return fileName;
            }

            uploadToS3(savePath, content);
            return fileName;
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при загрузке данных в S3", e);
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

    private boolean doesRecordExist(String key) {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket("axi-bucket")
                    .key(key)
                    .build();

            HeadObjectResponse response = s3Client.headObject(headObjectRequest);

            return true;

        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw new RuntimeException("Ошибка при проверке существования объекта в S3", e);
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
}
