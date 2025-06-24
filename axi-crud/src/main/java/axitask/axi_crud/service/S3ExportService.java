package axitask.axi_crud.service;

import java.io.IOException;


public interface S3ExportService {
    public String buildAndUploadToS3(String id, byte[] content);
    public String readJsonAsNode(String key) throws IOException;
}
