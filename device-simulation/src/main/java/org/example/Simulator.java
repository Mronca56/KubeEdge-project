package org.example;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.example.DTO.RawCameraDTO;
import org.example.DTO.RawRegisterDTO;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Simulator {

    private static final String brokerUrl = System.getenv("BROKER_URL") != null ? System.getenv("BROKER_URL") : "tcp://localhost:1883";
    private static final String CAMERA_TOPIC = "$hw/events/device/camera-01/twin/update";
    private static final String REGISTER_TOPIC = "$hw/events/device/register-01/twin/update";

    public static void main(String[] args) {
        Random r = new Random();
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            //Connessione al broker MQTT
            MqttClient client = new MqttClient(brokerUrl, MqttClient.generateClientId());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true); // Il client proverà a ricollegarsi da solo se cade la rete
            options.setCleanSession(true);

            System.out.println("Connection attempt to broker Edge: " + brokerUrl);
            client.connect(options);
            System.out.println("Connection successful!");

            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

            // Task per la Camera (ogni 2 secondi)
            Runnable cameraTask = () -> {
                try {
                    // Costruzione del formato DeviceTwin KubeEdge
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

            // Task per la Cassa (ogni 3 secondi)
            Runnable registerTask = () -> {
                try {
                    int quant = r.nextInt(10);
                    //Anche qui costruisco nel formato Device Twin
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
                scheduler.shutdown(); // Impedisce l'avvio di nuovi task
                try {
                    // Aspetta fino a 2 secondi che i task in esecuzione finiscano
                    if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow(); // Forza la chiusura
                    }

                    // Disconnessione pulita da MQTT
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

    private static ObjectNode createTwinValue(ObjectMapper mapper, String value) {
        ObjectNode actual = mapper.createObjectNode();
        actual.put("value", value);
        ObjectNode metadata = mapper.createObjectNode();
        metadata.put("type", "Updated");
        ObjectNode property = mapper.createObjectNode();
        property.set("actual", actual);
        property.set("metadata", metadata);
        return property;
    }
}