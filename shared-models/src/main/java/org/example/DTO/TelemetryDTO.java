package org.example.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class TelemetryDTO {
    private String store;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private int scans;
    private double revenue;
    private double averageQueue;

}
