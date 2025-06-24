package axitask.axi_crud.service.impl;

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

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;


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
    public String editDbAfterMigration(Long id, String externalId) {
        if (id == null || externalId == null || externalId.isEmpty()) {
            return "Неверные параметры для обновления (ID или externalId отсутствуют)";
        }

        try {
            Optional<Request> optionalRequest = parsingRepository.findById(id);

            if (optionalRequest.isPresent()) {
                Request request = optionalRequest.get();
                request.setExternalId(externalId);
                request.setJsonData(null);

                parsingRepository.save(request);
                return String.format("Запись ID: %s успешно обновлена с externalId: %s", id, externalId);
            }

            return String.format("Запись с ID: %s не найдена в базе данных", id);

        } catch (NumberFormatException e) {
            throw new RuntimeException("Некорректный формат ID: " + id, e);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при обновлении записи ID: " + id, e);
        }
    }

    @Scheduled(fixedRate = 30000)
    private String checkExternalIdAndThrowToS3() {
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

            for (Request request : dataPage.getContent()) {
                try {
                    if (request.getExternalId() != null && !request.getExternalId().isEmpty()) {
                        continue;
                    }

                    String data = request.getJsonData();
                    String id = request.getId().toString();

                    byte[] content = objectMapper.writeValueAsBytes(data);

                    String externalId = s3ExportService.buildAndUploadToS3(id, content);
                    Long requestId = request.getId();

                    editDbAfterMigration(requestId, externalId);

                } catch (JsonProcessingException e) {
                    throw new axitask.axi_crud.exceptions.JsonProcessingException(
                            "JSON_CONVERSION_ERROR",
                            "Не удалось конвертировать данные в JSON." + e.getOriginalMessage(),
                            e
                    );

                } catch (Exception e) {
                    throw new RuntimeException("Ошибка при обработке записи ID: " + request.getId(), e);
                }
            }

            if (dataPage.isLast()) {
                hasMore = false;
            } else {
                page++;
            }
        }

        return "Миграция завершена";
    }

    private Page<Request> apiResponseEnrichment(Page<Request> request) {

        request.getContent().forEach(req -> {
            if (req.getJsonData() == null && req.getExternalId() != null) {
                String externalId = req.getExternalId();
                String jsonData = null;
                try {
                    jsonData = s3ExportService.readJsonAsNode(externalId);
                } catch (IOException e) {
                    throw new axitask.axi_crud.exceptions.JsonProcessingException(
                            "JSON_CONVERSION_ERROR",
                            "Не удалось конвертировать данные в JSON." + e,
                            e
                    );
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
