package axitask.axi_crud.DTO;

import lombok.Builder;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;


@Builder
@Data
public class FilterRequest {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime requestDateFrom;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime requestDateTo;
    private Integer minProcessingTimeMs;
    private Integer maxXmlTags;
    private Integer minJsonKeys;
    private Boolean checkExternalId;
    @Builder.Default
    private int page = 0;
    @Builder.Default
    private int size = 10;

    private String[] sort = {"id,desc"};
}
