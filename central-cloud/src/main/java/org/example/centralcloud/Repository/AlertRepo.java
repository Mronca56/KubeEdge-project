package org.example.centralcloud.Repository;

import org.example.centralcloud.Entity.AlertEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Interface for crud operation of db alerts entities
 */
@Repository
public interface AlertRepo extends MongoRepository<AlertEntity, String> {
    List<AlertEntity> findByStore(String store);
}
