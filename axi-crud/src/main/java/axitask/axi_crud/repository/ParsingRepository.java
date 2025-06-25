package axitask.axi_crud.repository;

import axitask.axi_crud.DTO.FilterRequest;
import axitask.axi_crud.model.Request;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import jakarta.persistence.criteria.Predicate;

import java.util.ArrayList;
import java.util.List;


public interface ParsingRepository extends JpaRepository<Request, Long>, JpaSpecificationExecutor<Request> {
    default Page<Request> findByAdvancedFilters(FilterRequest filters, Pageable pageable) {
        return findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filters.getRequestDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("requestDate"), filters.getRequestDateFrom()));
            }

            if (filters.getRequestDateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("requestDate"), filters.getRequestDateTo()));
            }

            if (filters.getMinProcessingTimeMs() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("processingTime"), filters.getMinProcessingTimeMs()));
            }

            if (filters.getMaxXmlTags() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("xmlTags"), filters.getMaxXmlTags()));
            }

            if (filters.getMinJsonKeys() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("jsonKeys"), filters.getMinJsonKeys()));
            }

            if(filters.getCheckExternalId() != null) {
                predicates.add(cb.isNull(root.get("externalId")));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);
    }
}
