package org.example.centralcloud.Repository;

import org.example.centralcloud.Entity.TelemetryEntity;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Interface for crud operation of db telemetries entities
 */
@Repository
public interface TelemetryRepo extends MongoRepository<TelemetryEntity, String> {
    List<TelemetryEntity> findByStore(String store);

    @Aggregation(pipeline = { "{ '$group': { '_id': null, 'total': { $sum: '$revenue' } } }" })
    Double sumTotalRevenues();

    @Aggregation(pipeline = {
            "{ '$match': { 'store': ?0 } }",
            "{ '$group': { '_id': null, 'average': { '$avg': '$averageQueue' } } }"
    })
    Double averageQueueByStore(String store);
}
