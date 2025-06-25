package axitask.axi_crud.service;

import axitask.axi_crud.DTO.FilterRequest;
import axitask.axi_crud.model.Request;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public interface ParsingService {
    String saveRequest (String xmlRequest);
    Page<Request> advancedFilterRequest(FilterRequest filterRequest, Pageable pageable);
    String editDbAfterMigration (Long id, String externalId);
}
