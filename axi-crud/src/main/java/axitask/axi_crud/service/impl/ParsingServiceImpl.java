package axitask.axi_crud.service.impl;

import axitask.axi_crud.DTO.ExternalResponse;
import axitask.axi_crud.DTO.FilterRequest;
import axitask.axi_crud.model.Request;
import axitask.axi_crud.repository.ParsingRepository;
import axitask.axi_crud.service.ParsingService;
import axitask.axi_crud.service.S3ExportService;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.web.client.RestTemplate;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Service
@AllArgsConstructor
@Primary
public class ParsingServiceImpl implements ParsingService {
    private final ParsingRepository parsingRepository;
    private final S3ExportService s3ExportService;
    private final ObjectMapper objectMapper;

    @Override
    public String saveRequest(String xmlRequest) {
        Request request = new Request();
        XmlMapper xmlMapper = new XmlMapper();

        Instant startTime;
        Instant endTime;

        int jsonKeys;
        int xmlTags;

        try {
            startTime = Instant.now();

            LocalDateTime localDateTime = LocalDateTime.now();
            localDateTime = localDateTime.withNano(0);

            JsonNode node = xmlMapper.readTree(xmlRequest.getBytes());
            String jsonString = objectMapper.writeValueAsString(node);

            jsonKeys = keysCounter(node);
            xmlTags = tagsCounter(xmlRequest);

            endTime = Instant.now();

            request.setJsonData(jsonString);
            request.setResponseDate(localDateTime);
            request.setJsonKeys(jsonKeys);
            request.setXmlTags(xmlTags);
            request.setProcessingTime(Math
                    .min(Duration
                            .between(startTime, endTime)
                            .toMillis(),
                            5000)
            );

            parsingRepository.save(request);

            return jsonString;
        }

        catch (Exception e) {
            return String.valueOf(new RuntimeException(e));
        }
    }

    @Override
    public List<Request> findAllRequests() {
        return parsingRepository.findAll();
    }

    @Override
    public Page<Request> advancedFilterRequest(FilterRequest filterRequest, Pageable pageable) {
        Page<Request> request;

        if (areAllFiltersEmpty(filterRequest)) {
            request = parsingRepository.findAll(pageable);
            return apiResponseEnrichment(request);
        }
        request = parsingRepository.findByAdvancedFilters(filterRequest, pageable);
        return apiResponseEnrichment(request);
    }

    @Override
    public String editDbAfterMigration(ExternalResponse[] request) {
        if (request == null || request.length == 0) {
            return "Нет данных для обновления.";
        }
        List<ExternalResponse> assignments = Arrays.asList(request);

        try {
            Map<Long, String> idToExternal = assignments.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(
                            resp -> Long.parseLong(resp.getId()),
                            ExternalResponse::getExternalId,
                            (existing, replacement) -> existing
                    ));

            Set<Long> idsToUpdate = idToExternal.keySet();

            List<Request> existingRequests = parsingRepository.findAllById(idsToUpdate);

            List<Request> requestsToUpdate = existingRequests.stream()
                    .filter(req -> idToExternal.containsKey(req.getId()))
                    .peek(req -> {
                        req.setExternalId(idToExternal.get(req.getId()));
                        req.setJsonData(null);
                    })
                    .collect(Collectors.toList());

            if (!requestsToUpdate.isEmpty()) {
                parsingRepository.saveAll(requestsToUpdate);
                return String.format("Успешно обновлено %d записей.", requestsToUpdate.size());
            }
            return "Не найдено соответствующих записей в базе для обновления.";

        } catch (NumberFormatException e) {
            throw new RuntimeException("Некорректный формат ID ", e);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при обработке данных: " + e.getMessage(), e);
        }
    }

    @Scheduled(fixedRate = 30000)
    private String checkExternalId() {
        int page = 0;
        int size = 100;
        boolean hasMore = true;

        FilterRequest filterRequest = FilterRequest.builder()
                .checkExternalId(true)
                .build();

        while (hasMore) {
            Pageable pageable = PageRequest.of(page, size);
            Page<Request> dataPage = parsingRepository.findByAdvancedFilters(filterRequest, pageable);

            if (dataPage == null || dataPage.isEmpty()) {
                hasMore = false;
                continue;
            }

            try {
                String jsonData = objectMapper.writeValueAsString(dataPage.getContent());
                ExternalResponse[] externalIds = s3ExportService.fetchAndUploadToS3(jsonData);
                if (dataPage.isLast()) {
                    hasMore = false;
                } else {
                    page++;
                }
                return editDbAfterMigration(externalIds);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Не удалось конвертировать данные в JSON.", e);
            }
        }

        return "Нет полей для миграции.";
    }

    private Page<Request> apiResponseEnrichment(Page<Request> request) {

        request.getContent().forEach(req -> {
            if (req.getJsonData() == null && req.getExternalId() != null) {
                String externalId = req.getExternalId();
                String jsonData = null;
                try {
                    jsonData = s3ExportService.readJsonAsNode(externalId);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                String normalizedJson = parseJsonString(jsonData);
                req.setJsonData(normalizedJson);
            }
        });

        return request;
    }

    private String parseJsonString(String jsonData) {
        String normalizedJson = jsonData
                .replace("\\\"", "\"")  // Заменяем \" на "
                .replaceAll("^\"|\"$", "");

        if (normalizedJson.startsWith("\"") && normalizedJson.endsWith("\"")) {
            normalizedJson = normalizedJson.substring(1, normalizedJson.length() - 1);
        }

        return normalizedJson;
    }

    private boolean areAllFiltersEmpty(FilterRequest filterRequest) {
        return filterRequest.getRequestDateFrom() == null &&
                filterRequest.getRequestDateTo() == null &&
                filterRequest.getMinProcessingTimeMs() == null &&
                filterRequest.getMaxXmlTags() == null &&
                filterRequest.getMinJsonKeys() == null &&
                filterRequest.getCheckExternalId() == null;
    }

    private static int keysCounter(JsonNode node) {
        int keysCount = 0;
        for (JsonNode child : node) {
            keysCount += 1;
        }

        return keysCount;
    }

    private static int tagsCounter(String xml) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));

        int tagsCount = 0;

        while (reader.hasNext()) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                tagsCount++;
                if (reader.isEndElement()) {
                    tagsCount++;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                tagsCount++;
            }
        }

        reader.close();
        return tagsCount;
    }
}
