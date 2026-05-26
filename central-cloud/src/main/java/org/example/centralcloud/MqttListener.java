package org.example.centralcloud;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.example.DTO.AlertDTO;
import org.example.DTO.TelemetryDTO;
import org.example.centralcloud.Entity.AlertEntity;
import org.example.centralcloud.Entity.TelemetryEntity;
import org.example.centralcloud.Repository.AlertRepo;
import org.example.centralcloud.Repository.TelemetryRepo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

//RICORDARSI DI ISTANZIARE UN MONGODB NEL YAML

@Service
public class MqttListener {
    @Value("${mqtt.broker-url}")
    private String brokerUrl;

    @Value("${mqtt.client-id}")
    private String clientId;

    private final ObjectMapper objectMapper;
    private final AlertRepo alertRepo;
    private final TelemetryRepo telemetryRepo;

    private MqttClient client;

    public MqttListener(ObjectMapper objectMapper, AlertRepo alertRepo, TelemetryRepo telemetryRepo) {
        this.objectMapper = objectMapper;
        this.alertRepo = alertRepo;
        this.telemetryRepo = telemetryRepo;
    }

    @PostConstruct
    public void start(){
        try {
            //Connessione al broker MQTT
            this.client = new MqttClient(brokerUrl, clientId);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true); // Il client proverà a ricollegarsi da solo se cade la rete
            options.setCleanSession(true);

            System.out.println("Connection attempt to broker Edge: " + brokerUrl);
            client.connect(options);
            System.out.println("Connection successful!");

            //Subscribe al primo topic
            client.subscribe("MO/Alert", (topic, message) -> {
                //LOGICA DI ARRIVO ALERT
                try {
                    AlertDTO alertDTO = objectMapper.readValue(message.getPayload(), AlertDTO.class);
                    AlertEntity alertEntity = new AlertEntity();
                    //Per trasformare da un oggetto all'altro
                    BeanUtils.copyProperties(alertDTO, alertEntity);

                    //Salvo nel db
                    alertRepo.save(alertEntity);
                }catch (BeansException | JacksonException e){
                    System.err.println("Fatal error on subscribing: " + e.getMessage());
                }
            });

            //Subscribe al secondo topic
            client.subscribe("MO/Telemetry",(topic, message) -> {
                //LOGICA DI ARRIVO TELEMETRIE
                try {
                    TelemetryDTO telemetryDTO = objectMapper.readValue(message.getPayload(), TelemetryDTO.class);
                    TelemetryEntity telemetryEntity = new TelemetryEntity();

                    //Per trasformare da un oggetto all'altro
                    BeanUtils.copyProperties(telemetryDTO, telemetryEntity);

                    //Salvo nel db
                    telemetryRepo.save(telemetryEntity);
                }catch (BeansException | JacksonException e){
                    System.err.println("Fatal error on subscribing: " + e.getMessage());
                }
            });

        } catch (MqttException e) {
            System.err.println("Fatal MQTT setup error on startup: " + e.getMessage());
        }
    }

    @PreDestroy
    public void stop(){
        //Spegnimento del client
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
                client.close();
                System.out.println("MQTT client disconnected!");
            }
        } catch (MqttException e) {
            System.err.println("Error during MQTT disconnection: " + e.getMessage());
        }
    }
}
