package org.example.centralcloud.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

//Classe Telemetry per MongoDB
@AllArgsConstructor
@NoArgsConstructor
@Data
@Document(collection = "Telemetries")
public class TelemetryEntity {
    @Id
    private String id;

    private String StoreId;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private int scans;
    private double revenue;
    private double averageQueue;
}
