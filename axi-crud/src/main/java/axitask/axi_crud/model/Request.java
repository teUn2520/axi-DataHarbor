package axitask.axi_crud.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;


@Data
@Entity
@Table(name="request")
public class Request {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String jsonData;
    @Column(columnDefinition = "TIMESTAMP(0)")
    private LocalDateTime responseDate;
    private Long processingTime;
    private Integer jsonKeys;
    private Integer xmlTags;
    private String externalId;
}
