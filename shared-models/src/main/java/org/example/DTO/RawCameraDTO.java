package org.example.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class RawCameraDTO {
    private String DeviceId;
    private LocalDateTime timestamp;
    private int peopleCount;
    private int queueLength;
    private boolean suspiciosActivity;
}
