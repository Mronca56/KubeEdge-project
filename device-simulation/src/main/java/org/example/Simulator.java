package org.example;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.example.DTO.RawCameraDTO;
import org.example.DTO.RawRegisterDTO;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Simulator {

    private static final String brokerUrl = System.getenv("BROKER_URL") != null ? System.getenv("BROKER_URL") : "tcp://localhost:1883";

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
                    RawCameraDTO camera = new RawCameraDTO("MO01", LocalDateTime.now(), r.nextInt(50), r.nextInt(20), r.nextBoolean());
                    String camMessage = objectMapper.writeValueAsString(camera);

                    client.publish("MO/Camera", new MqttMessage(camMessage.getBytes()));
                    System.out.println("Sent camera data");
                } catch (Exception e) {
                    System.err.println("Errore durante l'invio dei dati Camera: " + e.getMessage());
                }
            };

            // Task per la Cassa (ogni 3 secondi)
            Runnable registerTask = () -> {
                try {
                    int quant = r.nextInt(10);
                    RawRegisterDTO register = new RawRegisterDTO("CMO", LocalDateTime.now(), 12345, quant, quant * 4.99);
                    String regMessage = objectMapper.writeValueAsString(register);

                    client.publish("MO/Register", new MqttMessage(regMessage.getBytes()));
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
}