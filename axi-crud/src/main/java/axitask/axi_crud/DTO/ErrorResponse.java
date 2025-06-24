package axitask.axi_crud.DTO;

import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
public class ErrorResponse {
    String timestamp;
    String code;
    String message;
    String path;

    public ErrorResponse(String code, String message) {
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        this.code = code;
        this.message = message;
    }
}
