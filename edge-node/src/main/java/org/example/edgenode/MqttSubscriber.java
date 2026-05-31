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

/**
 * This class represents the MQTT communication handler on an edge node.
 * Upon startup, it connects to two MQTT clients: one to communicate with the device twins and one to communicate with the central node.
 * Upon termination, it handles graceful degradation of MQTT clients.
 */
@Service
public class MqttSubscriber {
    // From YAML file
    @Value("${edge.broker.url}")
    private String brokerEdgeUrl;
    @Value("${edge.client.id}")
    private String clientEdgeId;
    @Value("${cloud.broker.url}")
    private String brokerCloudUrl;
    @Value("${cloud.client.id}")
    private String clientCloudId;

    private MqttClient clientEdge;
    private MqttAsyncClient clientCloud; // Async to allow disconnections from the cloud without causing a crash.

    private final DataAggregator dataAggregator;
    private final ObjectMapper objectMapper = new ObjectMapper(); // JSON mapper
    Map<Integer, Integer> stock = new HashMap<>(); // Simulation of store's inventory (handle only one product for now!)

    // Timestamps of the most recent messages received from the devices, to be used to check for errors.
    private LocalDateTime lastCameraMsgTime = LocalDateTime.now();
    private LocalDateTime lastRegisterMsgTime = LocalDateTime.now();

    // Topics used by the device twins
    private static final String CAMERA_TOPIC = "$hw/events/device/camera-01/twin/update";
    private static final String REGISTER_TOPIC = "$hw/events/device/register-01/twin/update";

    public MqttSubscriber(DataAggregator dataAggregator) {
        this.dataAggregator = dataAggregator;
    }

    /**
     * This method simulates the restocking of the store's inventory
     */
    public void refill(){
        stock.clear();
        stock.put(12345, 50);
    }

    /**
     * Runs automatically when the beans start up (@PostConstruct). It handles the connection to clients and subscribes to device twin topics.
     * Possible MQTT exception (not blocking) if the clients doesn't connect correctly.
     */
    @PostConstruct
    public void start(){
        refill();
        try {
            //Connection to broker MQTT edge
            this.clientEdge = new MqttClient(brokerEdgeUrl, clientEdgeId);

            MqttConnectOptions optionsEdge = new MqttConnectOptions();
            optionsEdge.setAutomaticReconnect(true); // Client will attempt to reconnect automatically if connection is lost.
            optionsEdge.setCleanSession(true); // All pending publication deliveries for the client are removed when the client connects.

            // Definition of some functions that will automatically invoke whenever needed.
            clientEdge.setCallback(new MqttCallbackExtended() {
                /**
                 * On connection or reconnection it subscribes the client to the topics
                 * @param reconnect True if it's a reconnection (for debug)
                 */
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    System.out.println("Edge App: Connecting local broker. Reconnect? " + reconnect);
                    try {
                        // It registers the devices every time the local broker (re)starts.
                        clientEdge.subscribe(CAMERA_TOPIC, (topic, message) -> {
                            lastCameraMsgTime = LocalDateTime.now();
                            processCameraMsg(new String(message.getPayload()));
                        });

                        clientEdge.subscribe(REGISTER_TOPIC,(topic, message) -> {
                            lastRegisterMsgTime = LocalDateTime.now();
                            processRegisterMsg(new String(message.getPayload()));
                        });
                    } catch (MqttException e) {
                        System.err.println("Error subscribing to topics: " + e.getMessage());
                    }
                }

                /**
                 * Notify if the connection is lost.
                 */
                @Override
                public void connectionLost(Throwable cause) {
                    System.out.println("Edge App: Lost connection to local broker!");
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {}

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {}
            });

            System.out.println("Connection attempt to broker Edge: " + brokerEdgeUrl);
            clientEdge.connect(optionsEdge);
            System.out.println("Connection successful!");

            //Connection to broker MQTT cloud
            this.clientCloud = new MqttAsyncClient(brokerCloudUrl, clientCloudId);

            MqttConnectOptions optionsCloud = new MqttConnectOptions();
            optionsCloud.setAutomaticReconnect(true); // Client will attempt to reconnect automatically if connection is lost.
            optionsCloud.setCleanSession(false); // We want to maintain all pending publication deliveries if it disconnects.
            optionsCloud.setConnectionTimeout(10); // How long the client will wait when trying to connect to broker before giving up (in the edge we want it to be small).
            optionsCloud.setKeepAliveInterval(20); // Time period to ensure the connection is still alive (small in the edge).

            // Creation of a buffer in case of disconnection to not lose messages
            DisconnectedBufferOptions bufferOptions = new DisconnectedBufferOptions();
            bufferOptions.setBufferEnabled(true);
            bufferOptions.setBufferSize(100); // How many messages can keep while you're offline
            bufferOptions.setPersistBuffer(false);  // Kept in ram
            bufferOptions.setDeleteOldestMessages(false);

            clientCloud.setBufferOpts(bufferOptions);
            System.out.println("Connection attempt to broker Edge for Cloud: " + brokerCloudUrl);
            clientCloud.connect(optionsCloud).waitForCompletion();
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

    /**
     * Method to process messages of the cameras, and to send them to the cloud topic
     * @param message payload read from the device twin topic
     */
    private void processCameraMsg(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode twin = root.path("twin");

            // Translation from device payload to raw DTO data
            RawCameraDTO raw = new RawCameraDTO();
            raw.setDeviceId("camera-01");
            raw.setTimestamp(LocalDateTime.now());
            raw.setPeopleCount(twin.path("peopleCount").path("actual").path("value").asInt());
            raw.setQueueLength(twin.path("queueLength").path("actual").path("value").asInt());
            raw.setSuspiciosActivity(twin.path("suspiciosActivity").path("actual").path("value").asBoolean());

            // Check if there are some Alerts to sent and in case build them and publish them.
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

            //Aggregation logic: if it returns null, it means it's not yet time to send the data
            TelemetryDTO tel = dataAggregator.getCameras(raw);
            if (tel != null) {
                clientCloud.publish("cloud/MO/Telemetry", new MqttMessage(objectMapper.writeValueAsBytes(tel)));
                System.out.println("Sent telemetry");
            }

        }catch (MqttException |JacksonException e) {
            System.err.println("Fatal error on publishing: " + e.getMessage());
        }
    }

    /**
     * Method to process messages of the registers, and to send them to the cloud topic
     * @param message payload read from the device twin topic
     */
    private void processRegisterMsg(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode twin = root.path("twin");

            // Translation from device payload to raw DTO data
            RawRegisterDTO raw = new RawRegisterDTO();
            raw.setDeviceId("register-01");
            raw.setTimestamp(LocalDateTime.now());
            raw.setCodeProduct(twin.path("codeProduct").path("actual").path("value").asInt());
            raw.setQuantity(twin.path("quantity").path("actual").path("value").asInt());
            raw.setTotalPrice(twin.path("totalPrice").path("actual").path("value").asDouble());

            int current = stock.getOrDefault(raw.getCodeProduct(), 0);
            stock.replace(raw.getCodeProduct(), current - raw.getQuantity());     //Remove products from inventory

            // Check if there are some Alerts to sent and in case build them and publish them.
            if (stock.get(raw.getCodeProduct()) <= 0) {
                AlertDTO alert = new AlertDTO("MO", LocalDateTime.now(),
                        "Critical", Status.LOW_STOCK,
                        "LOW STOCK OF THE PRODUCT"
                );
                //Il cloud dovrebbe quando riceve questo chiamare la funzione refill
                clientCloud.publish("cloud/MO/Alert", new MqttMessage(objectMapper.writeValueAsBytes(alert)));
                System.out.println("Sent register alert");
            }

            //Aggregation logic: if it returns null, it means it's not yet time to send the data
            TelemetryDTO tel = dataAggregator.getRegisters(raw);
            if (tel != null) {
                clientCloud.publish("cloud/MO/Telemetry", new MqttMessage(objectMapper.writeValueAsBytes(tel)));
                System.out.println("Sent telemetry");
            }
        }catch (MqttException |JacksonException e) {
            System.err.println("Fatal error on publishing: " + e.getMessage());
        }
    }


    /**
     * Method that every minute checks if all the devices connected are running or they've had any problems.
     */
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