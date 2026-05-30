package org.example.edgenode;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.example.DTO.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Service
public class MqttSubscriber {
    //Da passare dal file yaml
    @Value("${edge.broker.url}")
    private String brokerEdgeUrl;
    @Value("${edge.client.id}")
    private String clientEdgeId;
    @Value("${cloud.broker.url}")
    private String brokerCloudUrl;
    @Value("${cloud.client.id}")
    private String clientCloudId;
    //Mi servono due client differenti, uno per comunicare con i device e uno per inviare al cloud
    private MqttClient clientEdge;
    private MqttAsyncClient clientCloud;

    private final DataAggregator dataAggregator;
    private final ObjectMapper objectMapper = new ObjectMapper();
    Map<Integer, Integer> stock = new HashMap<>();

    //Orari degli ultimi messaggi ricevuti dai device, da usare per vedere se vanno in errore
    private LocalDateTime lastCameraMsgTime = LocalDateTime.now();
    private LocalDateTime lastRegisterMsgTime = LocalDateTime.now();

    private static final String CAMERA_TOPIC = "$hw/events/device/camera-01/twin/update";
    private static final String REGISTER_TOPIC = "$hw/events/device/register-01/twin/update";

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
            this.clientEdge = new MqttClient(brokerEdgeUrl, clientEdgeId);
            this.clientCloud = new MqttAsyncClient(brokerCloudUrl, clientCloudId);

            MqttConnectOptions optionsEdge = new MqttConnectOptions();
            optionsEdge.setAutomaticReconnect(true); // Il client proverà a ricollegarsi da solo se cade la rete
            optionsEdge.setCleanSession(true);

            System.out.println("Connection attempt to broker Edge: " + brokerEdgeUrl);
            clientEdge.connect(optionsEdge);
            System.out.println("Connection successful!");

            MqttConnectOptions optionsCloud = new MqttConnectOptions();
            optionsCloud.setAutomaticReconnect(true); // Il client proverà a ricollegarsi da solo se cade la rete
            optionsCloud.setCleanSession(false);
            optionsCloud.setConnectionTimeout(10);
            optionsCloud.setKeepAliveInterval(20);

            DisconnectedBufferOptions bufferOptions = new DisconnectedBufferOptions();
            bufferOptions.setBufferEnabled(true);
            bufferOptions.setBufferSize(100); // Quanti messaggi tenere in memoria mentre sei offline
            bufferOptions.setPersistBuffer(false); // False = li tiene in RAM (va benissimo per la demo)
            bufferOptions.setDeleteOldestMessages(false);

            clientCloud.setBufferOpts(bufferOptions);

            System.out.println("Connection attempt to broker Edge for Cloud: " + brokerCloudUrl);
            clientCloud.connect(optionsCloud).waitForCompletion();
            System.out.println("Connection successful!");

            //Subscribe al primo topic
            clientEdge.subscribe(CAMERA_TOPIC, (topic, message) -> {
                lastCameraMsgTime = LocalDateTime.now();
                processCameraMsg(new String(message.getPayload()));
            });

            //Subscribe al secondo topic
            clientEdge.subscribe(REGISTER_TOPIC,(topic, message) -> {
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
            if (clientEdge != null && clientEdge.isConnected()) {
                clientEdge.disconnect();
                clientEdge.close();
                System.out.println("MQTT client disconnected!");
            }
            if (clientCloud != null && clientCloud.isConnected()) {
                clientCloud.disconnect().waitForCompletion();
                clientCloud.close();
                System.out.println("MQTT client disconnected!");
            }
        } catch (MqttException e) {
            System.err.println("Error during MQTT disconnection: " + e.getMessage());
        }
    }

    private void processCameraMsg(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode twin = root.path("twin");

            // Ricostruisco il DTO originale dal payload KubeEdge
            RawCameraDTO raw = new RawCameraDTO();
            raw.setDeviceId("camera-01");
            raw.setTimestamp(LocalDateTime.now());
            raw.setPeopleCount(twin.path("peopleCount").path("actual").path("value").asInt());
            raw.setQueueLength(twin.path("queueLength").path("actual").path("value").asInt());
            raw.setSuspiciosActivity(twin.path("suspiciosActivity").path("actual").path("value").asBoolean());

            //Controllo se devo mandare degli alert
            if (raw.isSuspiciosActivity()) {
                AlertDTO alert = new AlertDTO("MO", LocalDateTime.now(),
                        "Critical", Status.SUSPECT_ACTIVITY,
                        "SUSPICIOUS ACTIVTY ON CAMERA!!"
                );
                clientCloud.publish("cloud/MO/Alert", new MqttMessage(objectMapper.writeValueAsBytes(alert)));
                System.out.println("Sent camera alert");
            }
            if (raw.getQueueLength() > 15) {
                AlertDTO alert = new AlertDTO("MO", LocalDateTime.now(),
                        "Critical", Status.LONG_QUEUE,
                        "MORE THAN 15 PEOPLE WAITING!"
                );
                clientCloud.publish("cloud/MO/Alert", new MqttMessage(objectMapper.writeValueAsBytes(alert)));
                System.out.println("Sent camera alert");
            }

            //Logica di aggregazione, gestita dalla classe apposita, se mi ritorna null vuol dire che non è ancora il momento di mandare i dati
            TelemetryDTO tel = dataAggregator.getCameras(raw);
            if (tel != null) {
                clientCloud.publish("cloud/MO/Telemetry", new MqttMessage(objectMapper.writeValueAsBytes(tel)));
                System.out.println("Sent telemetry");
            }

        }catch (MqttException |JacksonException e) {
            System.err.println("Fatal error on publishing: " + e.getMessage());
        }
    }

    private void processRegisterMsg(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode twin = root.path("twin");

            RawRegisterDTO raw = new RawRegisterDTO();
            raw.setDeviceId("register-01");
            raw.setTimestamp(LocalDateTime.now());
            raw.setCodeProduct(twin.path("codeProduct").path("actual").path("value").asInt());
            raw.setQuantity(twin.path("quantity").path("actual").path("value").asInt());
            raw.setTotalPrice(twin.path("totalPrice").path("actual").path("value").asDouble());

            int current = stock.getOrDefault(raw.getCodeProduct(), 0);
            stock.replace(raw.getCodeProduct(), current - raw.getQuantity());     //Tolgo dalle scorte quelle vendute

            if (stock.get(raw.getCodeProduct()) <= 0) {
                AlertDTO alert = new AlertDTO("MO", LocalDateTime.now(),
                        "Critical", Status.LOW_STOCK,
                        "LOW STOCK OF THE PRODUCT"
                );
                //Il cloud dovrebbe quando riceve questo chiamare la funzione refill
                clientCloud.publish("cloud/MO/Alert", new MqttMessage(objectMapper.writeValueAsBytes(alert)));
                System.out.println("Sent register alert");
            }

            TelemetryDTO tel = dataAggregator.getRegisters(raw);
            if (tel != null) {
                clientCloud.publish("cloud/MO/Telemetry", new MqttMessage(objectMapper.writeValueAsBytes(tel)));
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
                clientCloud.publish("cloud/MO/Alert", new MqttMessage(objectMapper.writeValueAsBytes(alert)));
                System.out.println("Sent Camera alert");
            }

            if (ChronoUnit.MINUTES.between(lastRegisterMsgTime, now) >= 1) {
                AlertDTO alert = new AlertDTO("MO", LocalDateTime.now(),
                        "Critical", Status.HARDWARE_FAILURE,
                        "HARDWARE FAILURE OF A REGISTER! MORE THAN 1 MINUTE WITHOUT DATA!"
                );
                clientCloud.publish("cloud/MO/Alert", new MqttMessage(objectMapper.writeValueAsBytes(alert)));
                System.out.println("Sent register alert");
            }
        }catch (MqttException |JacksonException e) {
            System.err.println("Fatal error on publishing: " + e.getMessage());
        }
    }
}