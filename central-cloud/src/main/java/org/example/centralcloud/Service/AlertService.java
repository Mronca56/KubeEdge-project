package org.example.centralcloud.Service;

import org.example.centralcloud.Entity.AlertEntity;
import org.example.centralcloud.Entity.TelemetryEntity;
import org.example.centralcloud.Repository.AlertRepo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Class for implements db operations on Alerts
 */
@Service
public class AlertService {
    private final AlertRepo alertRepo;
    public AlertService(AlertRepo alertRepo) {
        this.alertRepo = alertRepo;
    }

    /**
     * Method to find all Alerts collected
     * @return
     */
    public List<AlertEntity> findAll() {
        return alertRepo.findAll();
    }

    /**
     * Method for retrieving all alerts for a single store
     * @param store contains the name of the searched store
     * @return List of all alerts of the searched store
     */
    public List<AlertEntity> findByStore(String store) {
        return alertRepo.findByStore(store);
    }

    /**
     * Method for clearing all telemetry data
     */
    public void deleteAll() {
        alertRepo.deleteAll();
    }
}
