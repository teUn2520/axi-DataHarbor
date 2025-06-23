package axitask.axi_crud.service;

import axitask.axi_crud.DTO.ExternalResponse;

import java.io.IOException;

public interface S3ExportService {
    public ExternalResponse[] fetchAndUploadToS3(String requestBody);
    public String readJsonAsNode(String key) throws IOException;
}
