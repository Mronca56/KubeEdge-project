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

/**
 * Class for aggregation logics of the raw DTO received from the device twins.
 * It maintains two buffers of old messages and when it reaches enough of them it builds the telemetry to send to cloud.
 */
@Component
public class DataAggregator {
    private final List<RawCameraDTO> cameraList =  new LinkedList<>();
    private final List<RawRegisterDTO> registerList = new LinkedList<>();

    /**
     * Synchronized method called every time that a camera message arrives.
     * @param cameraDTO camera DTO arrived
     * @return Telemetry DTO if we have enough elements, null othervise
     */
    public synchronized TelemetryDTO getCameras(RawCameraDTO cameraDTO) {
        this.cameraList.add(cameraDTO);
        return checkAndAggregate();
    }

    /**
     * Synchronized method called every time that a register message arrives.
     * @param registerDTO register DTO arrived
     * @return Telemetry DTO if we have enough elements, null othervise
     */
    public synchronized TelemetryDTO getRegisters(RawRegisterDTO registerDTO) {
        this.registerList.add(registerDTO);
        return checkAndAggregate();
    }

    /**
     * Check the size of the camera buffer and if it has at least 10 elements it builds the telemetry
     * @return Telemetry DTO if we have enough elements, null othervise
     */
    private TelemetryDTO checkAndAggregate() {
        if(cameraList.size()>=10) {
            //It combines the last ten records and the cash records that are available
            int scan = cameraList.size() + registerList.size(); // Number of scans aggregated
            double rev = registerList.stream().mapToDouble(RawRegisterDTO::getTotalPrice).sum(); // Sum of total revenues of the scans
            double avg = cameraList.stream().mapToDouble(RawCameraDTO::getQueueLength).sum() / cameraList.size(); // Average recorded queue in the scans

            List<LocalDateTime> allDates = Stream.concat(
                    cameraList.stream().map(RawCameraDTO::getTimestamp),
                    registerList.stream().map(RawRegisterDTO::getTimestamp)
            ).toList();

            //Once everything is saved, it resets the lists and start aggregating new data again
            cameraList.clear();
            registerList.clear();

            return new TelemetryDTO("MO", Collections.min(allDates), Collections.max(allDates), scan, rev, avg);
        }else{
            return null;
        }
    }
}