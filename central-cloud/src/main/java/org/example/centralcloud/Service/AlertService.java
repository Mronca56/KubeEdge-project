package org.example.centralcloud.Service;

import org.example.centralcloud.Entity.AlertEntity;
import org.example.centralcloud.Entity.TelemetryEntity;
import org.example.centralcloud.Repository.AlertRepo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AlertService {
    private final AlertRepo alertRepo;
    public AlertService(AlertRepo alertRepo) {
        this.alertRepo = alertRepo;
    }

    public List<AlertEntity> findAll() {
        return alertRepo.findAll();
    }
    public List<AlertEntity> findByStore(String store) {
        return alertRepo.findByStore(store);
    }

    public void deleteAll() {
        alertRepo.deleteAll();
    }
}
