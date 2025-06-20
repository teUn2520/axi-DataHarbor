package axitask.axi_S3master.controller;

import axitask.axi_S3master.service.B2ExportService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;


@RestController
@RequestMapping("/api/export/")
@RequiredArgsConstructor
public class ExportController {
    private final B2ExportService exportService;

    @PostMapping("run")
    public ArrayNode runExport(@RequestBody String requestBody) {
        return exportService.fetchAndUploadToS3(requestBody);
    }

    @GetMapping("get/{key}")
    public JsonNode getS3Content(@PathVariable String key) {
        try {
            return exportService.readJsonAsNode(key);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
