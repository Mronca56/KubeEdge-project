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

            //Imposto i metodi di callback per quando mi connetto/riconnetto al broker
            client.setCallback(new MqttCallbackExtended() {

                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    System.out.println("Cloud App: Connection complete! Reconnect? " + reconnect);
                    try {
                        //Subscribe al primo topic
                        client.subscribe("cloud/MO/Alert", 1, (topic, message) -> {
                            processAlert(new String(message.getPayload()));
                        });
                        //Subscribe al secondo topic
                        client.subscribe("cloud/MO/Telemetry", 1, (topic, message) -> {
                            processTelemetry(new String(message.getPayload()));
                        });

                    } catch (MqttException e) {
                        System.err.println("Error during subscribing: " + e.getMessage());
                    }
                }

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

    private void processAlert(String message){
        try {
            System.out.println("Received Camera alert");
            AlertDTO alertDTO = objectMapper.readValue(message, AlertDTO.class);
            AlertEntity alertEntity = new AlertEntity();
            //Per trasformare da un oggetto all'altro
            BeanUtils.copyProperties(alertDTO, alertEntity);

            //Salvo nel db
            alertRepo.save(alertEntity);
        }catch (BeansException | JacksonException e){
            System.err.println("Fatal error on subscribing: " + e.getMessage());
        }
    }

    private void processTelemetry(String message){
        try {
            System.out.println("Received Telemetry alert");
            TelemetryDTO telemetryDTO = objectMapper.readValue(message, TelemetryDTO.class);
            TelemetryEntity telemetryEntity = new TelemetryEntity();

            //Per trasformare da un oggetto all'altro
            BeanUtils.copyProperties(telemetryDTO, telemetryEntity);

            //Salvo nel db
            telemetryRepo.save(telemetryEntity);
        }catch (BeansException | JacksonException e){
            System.err.println("Fatal error on subscribing: " + e.getMessage());
        }
    }
}
