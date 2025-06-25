package axitask.axi_crud.controller;

import axitask.axi_crud.DTO.FilterRequest;
import axitask.axi_crud.model.Request;
import axitask.axi_crud.service.ParsingService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;


@RestController
@AllArgsConstructor
@RequestMapping("api/v1/")
public class ParsingController {
    private final ParsingService parsingService;

    @PostMapping("request")
    public String saveRequest(@RequestBody String someXmlString) {
        return parsingService.saveRequest(someXmlString);
    }

    @GetMapping("page")
    public Page<Request> getRequestsByCategory(@ModelAttribute FilterRequest filterRequest,
                                               @PageableDefault(sort = "id", direction = Sort.Direction.DESC)
                                               Pageable pageable) {

        return parsingService.advancedFilterRequest(filterRequest, pageable);
    }
}
