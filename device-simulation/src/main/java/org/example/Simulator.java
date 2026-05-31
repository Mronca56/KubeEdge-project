package org.example;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Class that simulate the devices of an edge node and send regularly the data to the edge node.
 */
public class Simulator {
    // It connects to the local edge's mosquitto
    private static final String brokerUrl = System.getenv("BROKER_URL") != null ? System.getenv("BROKER_URL") : "tcp://localhost:1883";
    // Topics of the device twins
    private static final String CAMERA_TOPIC = "$hw/events/device/camera-01/twin/update";
    private static final String REGISTER_TOPIC = "$hw/events/device/register-01/twin/update";

    public static void main(String[] args) {
        Random r = new Random();
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            //Connection to broker MQTT
            MqttClient client = new MqttClient(brokerUrl, MqttClient.generateClientId());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);

            System.out.println("Connection attempt to broker Edge: " + brokerUrl);
            client.connect(options);
            System.out.println("Connection successful!");

            // Scheduler to execute regularly the creation and the publishing of the message
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

            // Task for the Camera (every 2 seconds)
            Runnable cameraTask = () -> {
                try {
                    // Building the KubeEdge DeviceTwin format
                    ObjectNode twin = objectMapper.createObjectNode();
                    twin.set("peopleCount", createTwinValue(objectMapper, String.valueOf(r.nextInt(50))));
                    twin.set("queueLength", createTwinValue(objectMapper, String.valueOf(r.nextInt(20))));
                    twin.set("suspiciosActivity", createTwinValue(objectMapper, String.valueOf(r.nextBoolean())));

                    ObjectNode root = objectMapper.createObjectNode();
                    root.set("twin", twin);

                    client.publish(CAMERA_TOPIC, new MqttMessage(objectMapper.writeValueAsBytes(root)));
                    System.out.println("Sent camera data");
                } catch (Exception e) {
                    System.err.println("Errore durante l'invio dei dati Camera: " + e.getMessage());
                }
            };

            // Task for the cash Register (every 3 seconds)
            Runnable registerTask = () -> {
                try {
                    int quant = r.nextInt(10); // Random quantity of products sold
                    // Building the KubeEdge DeviceTwin format
                    ObjectNode twin = objectMapper.createObjectNode();
                    twin.set("codeProduct", createTwinValue(objectMapper, "12345"));
                    twin.set("quantity", createTwinValue(objectMapper, String.valueOf(quant)));
                    twin.set("totalPrice", createTwinValue(objectMapper, String.valueOf(quant * 4.99)));

                    ObjectNode root = objectMapper.createObjectNode();
                    root.set("twin", twin);

                    client.publish(REGISTER_TOPIC, new MqttMessage(objectMapper.writeValueAsBytes(root)));
                    System.out.println("Sent register data");
                } catch (Exception e) {
                    System.err.println("Errore durante l'invio dei dati Cassa: " + e.getMessage());
                }
            };

            scheduler.scheduleAtFixedRate(cameraTask, 0, 2, TimeUnit.SECONDS);
            scheduler.scheduleAtFixedRate(registerTask, 0, 3, TimeUnit.SECONDS);

            //Graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutdown starting...");
                scheduler.shutdown(); // Prevents new tasks from starting
                try {
                    // Wait up to 2 seconds for the running tasks to finish
                    if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow(); // Then force closure
                    }

                    // Clean disconnection from MQTT
                    if (client.isConnected()) {
                        client.disconnect();
                        client.close();
                        System.out.println("MQTT client disconnected!");
                    }
                } catch (InterruptedException | MqttException e) {
                    System.err.println("Error during shutdown " + e.getMessage());
                    Thread.currentThread().interrupt();
                }
            }));

        } catch (MqttException e) {
            System.err.println("Fatal MQTT setup error on startup: " + e.getMessage());
        }
    }

    /**
     * Constructs a JSON object representing a digital twin property structure.
     *
     * @param mapper The Jackson ObjectMapper used to instantiate JSON nodes.
     * @param value  The string value to be assigned to the property.
     * @return An ObjectNode formatted with the structured "actual" and "metadata" fields.
     */
    private static ObjectNode createTwinValue(ObjectMapper mapper, String value) {

        // Create the 'actual' node and populate it with the provided value
        ObjectNode actual = mapper.createObjectNode();
        actual.put("value", value);

        // Create the 'metadata' node and set its type to "Updated"
        ObjectNode metadata = mapper.createObjectNode();
        metadata.put("type", "Updated");

        //Assemble the parent 'property' node by attaching the actual and metadata nodes
        ObjectNode property = mapper.createObjectNode();
        property.set("actual", actual);
        property.set("metadata", metadata);

        return property;
    }
}