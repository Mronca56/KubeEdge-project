package org.example.edgenode;

import org.example.DTO.RawCameraDTO;
import org.example.DTO.RawRegisterDTO;
import org.example.DTO.TelemetryDTO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

@Component
public class DataAggregator {
    private final List<RawCameraDTO> cameraList =  new LinkedList<>();
    private final List<RawRegisterDTO> registerList = new LinkedList<>();

    public synchronized TelemetryDTO getCameras(RawCameraDTO cameraDTO) {
        this.cameraList.add(cameraDTO);
        return checkAndAggregate();
    }

    public synchronized TelemetryDTO getRegisters(RawRegisterDTO registerDTO) {
        this.registerList.add(registerDTO);
        return checkAndAggregate();
    }

    private TelemetryDTO checkAndAggregate() {
        if(cameraList.size()>=10) {
            //Aggrego gli ultimi dieci record e quelli di cassa che ci sono
            int scan = cameraList.size() + registerList.size();
            double rev = registerList.stream().mapToDouble(RawRegisterDTO::getTotalPrice).sum();
            double avg = cameraList.stream().mapToDouble(RawCameraDTO::getQueueLength).sum() / cameraList.size();

            List<LocalDateTime> allDates = Stream.concat(
                    cameraList.stream().map(RawCameraDTO::getTimestamp),
                    registerList.stream().map(RawRegisterDTO::getTimestamp)
            ).toList();

            //Una volta salvato tutto resetto le liste e ricomincio ad aggregare nuovi dati
            cameraList.clear();
            registerList.clear();

            return new TelemetryDTO("MO", Collections.min(allDates), Collections.max(allDates), scan, rev, avg);
        }else{
            return null;
        }
    }
}