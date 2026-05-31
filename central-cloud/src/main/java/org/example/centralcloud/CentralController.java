package org.example.centralcloud;

import org.example.centralcloud.Entity.AlertEntity;
import org.example.centralcloud.Entity.TelemetryEntity;
import org.example.centralcloud.Repository.AlertRepo;
import org.example.centralcloud.Service.AlertService;
import org.example.centralcloud.Service.TelemetryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Class to handle external connections and requests, to give a sort of interface
 */
@RestController
@RequestMapping("/api")
public class CentralController {
    AlertService alertService;
    TelemetryService telemetryService;

    public CentralController(AlertService alertService, TelemetryService telemetryService) {
        this.alertService = alertService;
        this.telemetryService = telemetryService;
    }

    @GetMapping("/store/{store}/alert")
    public List<AlertEntity> getAlerts(@PathVariable String store) {
        return alertService.findByStore(store);
    }
    @GetMapping("/store/{store}/telemetry")
    public List<TelemetryEntity> getTelemetry(@PathVariable String store) {
        return telemetryService.findByStore(store);
    }
    @GetMapping("/store/{store}/average-queue")
    public Double getAverageQueue(@PathVariable String store) {
        return telemetryService.averageQueueByStore(store);
    }

    @GetMapping("/total-revenue")
    public Double getTotalRevenue() {
        return telemetryService.sumTotalRevenue();
    }

    @GetMapping("/alerts")
    public List<AlertEntity> getAlerts() {
        return alertService.findAll();
    }
    @GetMapping("/telemetries")
    public List<TelemetryEntity> getTelemetries() {
        return telemetryService.findAll();
    }

    @DeleteMapping
    public void deleteAll() {
        alertService.deleteAll();
        telemetryService.deleteAll();
    }
}
