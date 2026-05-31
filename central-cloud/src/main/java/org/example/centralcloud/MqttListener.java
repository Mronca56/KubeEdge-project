package org.example.centralcloud;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
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

/**
 * This class represents the MQTT communication handler on a cloud node.
 * Upon startup, it connects to the MQTT client to communicate with the edge node.
 * Upon termination, it handles graceful degradation of MQTT client.
 */
@Service
public class MqttListener {
    // From YAML file
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

    /**
     * Runs automatically when the beans start up (@PostConstruct). It handles the connection to client and subscribes to edge topics.
     * Possible MQTT exception (not blocking) if the clients doesn't connect correctly.
     */
    @PostConstruct
    public void start(){
        try {
            // Connection to MQTT client
            this.client = new MqttClient(brokerUrl, clientId);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);

            // Setting up callback methods for when it connects to or reconnect with the broker
            client.setCallback(new MqttCallbackExtended() {
                /**
                 * On connection or reconnection it subscribes the client to the topics
                 * @param reconnect True if it's a reconnection (for debug)
                 */
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    System.out.println("Cloud App: Connection complete! Reconnect? " + reconnect);
                    try {
                        //Subscribe to first topic
                        client.subscribe("cloud/MO/Alert", 1, (topic, message) -> {
                            processAlert(new String(message.getPayload()));
                        });
                        //Subscribe to second topic
                        client.subscribe("cloud/MO/Telemetry", 1, (topic, message) -> {
                            processTelemetry(new String(message.getPayload()));
                        });

                    } catch (MqttException e) {
                        System.err.println("Error during subscribing: " + e.getMessage());
                    }
                }

                /**
                 * Notify if the connection is lost.
                 */
                @Override
                public void connectionLost(Throwable cause) {
                    System.out.println("Warning! Lost connection to broker MQTT" + cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {}

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {}
            });

            System.out.println("Connection attempt to broker Edge: " + brokerUrl);
            client.connect(options);
            System.out.println("Connection successful!");

        } catch (MqttException e) {
            System.err.println("Fatal MQTT setup error on startup: " + e.getMessage());
        }
    }

    /**
     * Graceful client's disconnection
     */
    @PreDestroy
    public void stop(){
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

    /**
     * Method to process alerts received, and to write them in the MongoDB
     * @param message payload read from the edge topic
     */
    private void processAlert(String message){
        try {
            System.out.println("Received Camera alert");
            AlertDTO alertDTO = objectMapper.readValue(message, AlertDTO.class);
            AlertEntity alertEntity = new AlertEntity();
            // To transform from one object to another
            BeanUtils.copyProperties(alertDTO, alertEntity);

            // Save in db
            alertRepo.save(alertEntity);
        }catch (BeansException | JacksonException e){
            System.err.println("Fatal error on subscribing: " + e.getMessage());
        }
    }

    /**
     * Method to process telemetries received, and to write them in the MongoDB
     * @param message payload read from the edge topic
     */
    private void processTelemetry(String message){
        try {
            System.out.println("Received Telemetry alert");
            TelemetryDTO telemetryDTO = objectMapper.readValue(message, TelemetryDTO.class);
            TelemetryEntity telemetryEntity = new TelemetryEntity();

            BeanUtils.copyProperties(telemetryDTO, telemetryEntity);

            // Save in DB
            telemetryRepo.save(telemetryEntity);
        }catch (BeansException | JacksonException e){
            System.err.println("Fatal error on subscribing: " + e.getMessage());
        }
    }
}
