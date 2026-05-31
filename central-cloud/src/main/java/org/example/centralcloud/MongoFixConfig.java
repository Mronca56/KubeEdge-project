package org.example.centralcloud;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * CANDIDATA ALLA RIMOZIONE
 */
@Configuration
public class MongoFixConfig {

    // Leggiamo la variabile che sappiamo per certo funzionare!
    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Bean
    public MongoClient mongoClient() {
        return MongoClients.create(mongoUri);
    }
}