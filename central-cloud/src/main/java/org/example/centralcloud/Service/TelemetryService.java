package org.example.centralcloud.Service;

import org.example.centralcloud.Entity.TelemetryEntity;
import org.example.centralcloud.Repository.TelemetryRepo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Class for implements db operations on Telemetry
 */
@Service
public class TelemetryService {
    private final TelemetryRepo telemetryRepo;

    public TelemetryService(TelemetryRepo telemetryRepo) {
        this.telemetryRepo = telemetryRepo;
    }

    /**
     * Method for retrieving all telemetry data for a single store
     * @param store contains the name of the searched store
     * @return List of all telemetries of the searched store
     */
    public List<TelemetryEntity> findByStore(String store) {
        return telemetryRepo.findByStore(store);
    }

    /**
     * Method for calculating the total revenue of all stores
     * @return Double of total revenues if presents, 0 otherwise
     */
    public Double sumTotalRevenue() {
        Double sum = telemetryRepo.sumTotalRevenues();
        return sum != null ? sum : 0.0;
    }

    /**
     * Method for finding the average queue length per store
     * @param store contains the name of the searched store
     * @return Double of average queue if presents, 0 otherwise
     */
    public Double averageQueueByStore(String store) {
        Double average = telemetryRepo.averageQueueByStore(store);
        return average != null ? average : 0.0;
    }

    /**
     * Method to find all Telemetries collected
     * @return List of all telemetries
     */
    public List<TelemetryEntity> findAll(){
        return telemetryRepo.findAll();
    }

    /**
     * Method for clearing all telemetry data
     */
    public void deleteAll() {
        telemetryRepo.deleteAll();
    }
}
