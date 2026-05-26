package org.example.centralcloud.Repository;

import org.example.centralcloud.Entity.AlertEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AlertRepo extends MongoRepository<AlertEntity, String> {
}
