package org.example.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class RawRegisterDTO {
    private String DeviceId;
    private LocalDateTime timestamp;
    private int codeProduct;
    private int quantity;
    private double totalPrice;
}
