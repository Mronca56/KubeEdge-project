package org.example.edgenode;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.example.DTO.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Service
public class MqttSubscriber {
    //Da passare dal file yaml
    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    @Value("${mqtt.client.id}")
    private String clientId;

    private final DataAggregator dataAggregator;
    private final ObjectMapper objectMapper = new ObjectMapper();
    Map<Integer, Integer> stock = new HashMap<>();

    private MqttClient client;

    //Orari degli ultimi messaggi ricevuti dai device, da usare per vedere se vanno in errore
    private LocalDateTime lastCameraMsgTime = LocalDateTime.now();
    private LocalDateTime lastRegisterMsgTime = LocalDateTime.now();

    public MqttSubscriber(DataAggregator dataAggregator) {
        this.dataAggregator = dataAggregator;
    }

    public void refill(){
        stock.clear();
        stock.put(12345, 50);
    }

    @PostConstruct
    public void start(){
        refill();
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
            client.subscribe("MO/Camera", (topic, message) -> {
                lastCameraMsgTime = LocalDateTime.now();
                processCameraMsg(new String(message.getPayload()));
            });

            //Subscribe al secondo topic
            client.subscribe("MO/Register",(topic, message) -> {
                lastRegisterMsgTime = LocalDateTime.now();
                processRegisterMsg(new String(message.getPayload()));
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

    private void processCameraMsg(String message) {
        try {
            RawCameraDTO raw = objectMapper.readValue(message, RawCameraDTO.class);
            //Controllo se devo mandare degli alert
            if (raw.isSuspiciosActivity()) {
                AlertDTO alert = new AlertDTO("MO", LocalDateTime.now(),
                        "Critical", Status.SUSPECT_ACTIVITY,
                        "SUSPICIOUS ACTIVTY ON CAMERA!!"
                );
                client.publish("cloud/MO/Alert", new MqttMessage(objectMapper.writeValueAsBytes(alert)));
                System.out.println("Sent camera alert");
            }
            if (raw.getQueueLength() > 15) {
                AlertDTO alert = new AlertDTO("MO", LocalDateTime.now(),
                        "Critical", Status.LONG_QUEUE,
                        "MORE THAN 15 PEOPLE WAITING!"
                );
                client.publish("cloud/MO/Alert", new MqttMessage(objectMapper.writeValueAsBytes(alert)));
                System.out.println("Sent camera alert");
            }

            //Logica di aggregazione, gestita dalla classe apposita, se mi ritorna null vuol dire che non è ancora il momento di mandare i dati
            TelemetryDTO tel = dataAggregator.getCameras(raw);
            if (tel != null) {
                client.publish("cloud/MO/Telemetry", new MqttMessage(objectMapper.writeValueAsBytes(tel)));
                System.out.println("Sent telemetry");
            }

        }catch (MqttException |JacksonException e) {
            System.err.println("Fatal error on publishing: " + e.getMessage());
        }
    }

    private void processRegisterMsg(String message) {
        try {
            RawRegisterDTO raw = objectMapper.readValue(message, RawRegisterDTO.class);
            int current = stock.getOrDefault(raw.getCodeProduct(), 0);
            stock.replace(raw.getCodeProduct(), current - raw.getQuantity());     //Tolgo dalle scorte quelle vendute

            if (stock.get(raw.getCodeProduct()) <= 0) {
                AlertDTO alert = new AlertDTO("MO", LocalDateTime.now(),
                        "Critical", Status.LOW_STOCK,
                        "LOW STOCK OF THE PRODUCT"
                );
                //Il cloud dovrebbe quando riceve questo chiamare la funzione refill
                client.publish("cloud/MO/Alert", new MqttMessage(objectMapper.writeValueAsBytes(alert)));
                System.out.println("Sent register alert");
            }

            TelemetryDTO tel = dataAggregator.getRegisters(raw);
            if (tel != null) {
                client.publish("cloud/MO/Telemetry", new MqttMessage(objectMapper.writeValueAsBytes(tel)));
                System.out.println("Sent telemetry");
            }
        }catch (MqttException |JacksonException e) {
            System.err.println("Fatal error on publishing: " + e.getMessage());
        }
    }

    //Ogni minuto controllo se ho errori hardware
    @Scheduled(fixedDelay = 60000)
    public void scheduled() {
        try {
            LocalDateTime now = LocalDateTime.now();

            if (ChronoUnit.MINUTES.between(lastCameraMsgTime, now) >= 1) {
                AlertDTO alert = new AlertDTO("MO", LocalDateTime.now(),
                        "Critical", Status.HARDWARE_FAILURE,
                        "HARDWARE FAILURE OF A CAMERA! MORE THAN 1 MINUTE WITHOUT DATA!"
                );
                client.publish("cloud/MO/Alert", new MqttMessage(objectMapper.writeValueAsBytes(alert)));
                System.out.println("Sent Camera alert");
            }

            if (ChronoUnit.MINUTES.between(lastRegisterMsgTime, now) >= 1) {
                AlertDTO alert = new AlertDTO("MO", LocalDateTime.now(),
                        "Critical", Status.HARDWARE_FAILURE,
                        "HARDWARE FAILURE OF A REGISTER! MORE THAN 1 MINUTE WITHOUT DATA!"
                );
                client.publish("cloud/MO/Alert", new MqttMessage(objectMapper.writeValueAsBytes(alert)));
                System.out.println("Sent register alert");
            }
        }catch (MqttException |JacksonException e) {
            System.err.println("Fatal error on publishing: " + e.getMessage());
        }
    }
}