package org.example.centralcloud.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

//Classe Alert per MongoDB
@AllArgsConstructor
@NoArgsConstructor
@Data
@Document(collection = "Alerts")
public class AlertEntity {
    public enum Status {LONG_QUEUE, SUSPECT_ACTIVITY, LOW_STOCK, HARDWARE_FAILURE}

    @Id
    private String id;
    private String StoreId;
    private LocalDateTime time;
    private String severity;
    private Status status;
    private String message;
}
