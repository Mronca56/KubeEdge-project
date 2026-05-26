package org.example.centralcloud.Service;

import org.example.centralcloud.Entity.TelemetryEntity;
import org.example.centralcloud.Repository.TelemetryRepo;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;
import java.util.Optional;

public class TelemetryService {
    private final TelemetryRepo telemetryRepo;

    public TelemetryService(TelemetryRepo telemetryRepo) {
        this.telemetryRepo = telemetryRepo;
    }

    //Metodo per trovare tutte le analisi di un singolo store
    public List<TelemetryEntity> findByStore(String store) {
        return telemetryRepo.findByStore(store);
    }

    //Metodo per trovare il ricavo totale di tutti gli store
    public Double sumTotalRevenue() {
        Double sum = telemetryRepo.sumTotalRevenues();
        return sum != null ? sum : 0.0;
    }

    //Metodo per trovare la coda media per store
    public Double averageQueueByStore(String store) {
        Double average = telemetryRepo.averageQueueByStore(store);
        return average != null ? average : 0.0;
    }

    //Metodo per pulire tutte le telemetrie
    public void deleteAll() {
        telemetryRepo.deleteAll();
    }
}
