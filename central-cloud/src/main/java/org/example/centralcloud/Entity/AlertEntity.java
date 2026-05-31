package org.example.centralcloud.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.DTO.Status;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Alert entity for table MongoDB
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Document(collection = "Alerts")
public class AlertEntity {

    @Id
    private String id;
    private String store;
    private LocalDateTime time;
    private String severity;
    private Status status;
    private String message;
}
