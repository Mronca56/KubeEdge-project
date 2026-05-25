package org.example.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class AlertDTO {
    public enum Status {LONG_QUEUE, SUSPECT_ACTIVITY, LOW_STOCK, HARDWARE_FAILURE}

    private String StoreId;
    private LocalDateTime time;
    private String severity;
    private Status status;
    private String message;
}
