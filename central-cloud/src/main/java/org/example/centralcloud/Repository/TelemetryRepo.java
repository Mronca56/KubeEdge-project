package org.example.centralcloud.Repository;

import org.example.centralcloud.Entity.TelemetryEntity;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TelemetryRepo extends MongoRepository<TelemetryEntity, String> {
    List<TelemetryEntity> findByStore(String store);

    @Aggregation(pipeline = { "{ '$group': { '_id': null, 'total': { $sum: '$revenue' } } }" })
    Double sumTotalRevenues();

    @Aggregation(pipeline = {
            "{ '$match': { 'store': ?0 } }" +
            "{ '$group': { '_id': null, 'average': { $average: '$averageQueue' } } }" })
    Double averageQueueByStore(String store);
}
