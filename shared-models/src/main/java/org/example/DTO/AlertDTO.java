package org.example.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class AlertDTO {

    private String store;
    private LocalDateTime time;
    private String severity;
    private Status status;
    private String message;
}
