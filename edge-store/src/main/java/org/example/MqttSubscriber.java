package org.example;

import org.eclipse.paho.client.mqttv3.*;
import org.example.DTO.AlertDTO;
import org.example.DTO.RawCameraDTO;
import org.example.DTO.RawRegisterDTO;
import org.example.DTO.TelemetryDTO;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class MqttSubscriber {
    //Utilizzo due liste bloccanti per salvarmi
    private final BlockingQueue<String> cameraQueue = new LinkedBlockingQueue<>( 10);
    private final BlockingQueue<String> registerQueue = new LinkedBlockingQueue<>( 10);
    private static final String BROKER_HOST = System.getenv("BROKER_HOST") != null ? System.getenv("BROKER_HOST") : "localhost";
    private static final String BROKER_PORT = System.getenv("BROKER_PORT") != null ? System.getenv("BROKER_PORT") : "1883";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private MqttClient client;
    private Thread cameraThread;
    private Thread registerThread;

    DataAggregator dataAggregator = new DataAggregator();
    Map<Integer, Integer> scorte = new HashMap<>();

    public void refill(){
        scorte.clear();
        scorte.put(12345, 50);
    }

    public void start(){
        String brokerUrl = "tcp://" + BROKER_HOST + ":" + BROKER_PORT;
        refill();
        try {
            //Connessione al broker MQTT
            this.client = new MqttClient(brokerUrl, MqttClient.generateClientId());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true); // Il client proverà a ricollegarsi da solo se cade la rete
            options.setCleanSession(true);

            System.out.println("Connection attempt to broker Edge: " + brokerUrl);
            client.connect(options);
            System.out.println("Connection successful!");

            //Subscribe al primo topic
            client.subscribe("MO/Camera", new IMqttMessageListener() {
                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    cameraQueue.offer(new  String(message.getPayload()));
                }
            });
            //Subscribe al secondo topic
            client.subscribe("MO/Register", new IMqttMessageListener() {
                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    registerQueue.offer(new  String(message.getPayload()));
                }
            });

            //Avvio due thread separati per leggere dalla coda
            cameraThread = new Thread(this::processCamera);
            registerThread = new Thread(this::processRegister);
            cameraThread.start();
            registerThread.start();

        } catch (MqttException e) {
            System.err.println("Fatal MQTT setup error on startup: " + e.getMessage());
        }
    }

    public void stop(){
        //Interrompiamo i due thread
        if (cameraThread != null) cameraThread.interrupt();
        if (registerThread != null) registerThread.interrupt();
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

    //Funzioni per i due thread che aspettano che la coda si riempie per elaborare i dati
    private void processCamera() {
        // Il thread continua a girare finché non riceve un segnale di interruzione
        while (!Thread.currentThread().isInterrupted()){
            try {
                //Provo a leggere dalla coda
                String Camera = cameraQueue.poll(1, TimeUnit.MINUTES);
                //Se finisce il tempo restituisce null, vuol dire che il device è in errore
                if (Camera == null) {
                    AlertDTO alert = new AlertDTO("MO", LocalDateTime.now(),
                            "Critical", AlertDTO.Status.HARDWARE_FAILURE,
                            "HARDWARE FAILURE OF A CAMERA! MORE THAN 1 MINUTE WITHOUT DATA!");

                    String alertCamera = objectMapper.writeValueAsString(alert);
                    client.publish("MO/Alert", new MqttMessage(alertCamera.getBytes()));
                    System.out.println("Sent camera alert");
                } else {
                    //Se ho letto qualcosa controllo se ho attività sospette o coda lunga e mando alert
                    RawCameraDTO raw = objectMapper.readValue(Camera, RawCameraDTO.class);
                    if (raw.isSuspiciosActivity()) {
                        AlertDTO alert = new AlertDTO("MO", LocalDateTime.now(),
                                "Critical", AlertDTO.Status.SUSPECT_ACTIVITY,
                                "SUSPICIOUS ACTIVTY ON CAMERA!!");

                        String alertCamera = objectMapper.writeValueAsString(alert);
                        client.publish("MO/Alert", new MqttMessage(alertCamera.getBytes()));
                        System.out.println("Sent camera alert");
                    }
                    if (raw.getQueueLength() > 15) {
                        AlertDTO alert = new AlertDTO("MO", LocalDateTime.now(),
                                "Critical", AlertDTO.Status.LONG_QUEUE,
                                "MORE THAN 15 PEOPLE WAITING!");

                        String alertCamera = objectMapper.writeValueAsString(alert);
                        client.publish("MO/Alert", new MqttMessage(alertCamera.getBytes()));
                        System.out.println("Sent camera alert");
                    }

                    //Aggrego i raw in una lista, quando arrivo a 10 mando il report aggregato al cloud
                    TelemetryDTO tel = dataAggregator.getCameras(raw);
                    if (tel != null) {
                        String telemetry = objectMapper.writeValueAsString(tel);
                        client.publish("MO/Telemetry", new MqttMessage(telemetry.getBytes()));
                        System.out.println("Sent telemetry");
                    }

                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Fatal error on thread: " + e.getMessage());
                break;
            } catch (MqttException e) {
                System.err.println("Fatal error on publishing: " + e.getMessage());
            } catch (JacksonException e) {
                System.err.println("Fatal error on json: " + e.getMessage());
            }
        }
    }

    private void processRegister() {
        // Il thread continua a girare finché non riceve un segnale di interruzione
        while (!Thread.currentThread().isInterrupted()) {
            try {
                //Provo a leggere dalla coda
                String Register = registerQueue.poll(1, TimeUnit.MINUTES);
                //Se finisce il tempo restituisce null, vuol dire che il device è in errore
                if (Register == null) {
                    AlertDTO alert = new AlertDTO("MO", LocalDateTime.now(),
                            "Critical", AlertDTO.Status.HARDWARE_FAILURE,
                            "HARDWARE FAILURE OF A REGISTER! MORE THAN 1 MINUTE WITHOUT DATA!");

                    String alertRegister = objectMapper.writeValueAsString(alert);
                    client.publish("MO/Alert", new MqttMessage(alertRegister.getBytes()));
                    System.out.println("Sent register alert");
                } else {
                    //Se ho troppe vendite di un prodotto
                    RawRegisterDTO raw = objectMapper.readValue(Register, RawRegisterDTO.class);

                    if (scorte.get(raw.getCodeProduct()) <= 0) {
                        AlertDTO alert = new AlertDTO("MO", LocalDateTime.now(),
                                "Critical", AlertDTO.Status.LOW_STOCK,
                                "LOW STOCK OF THE PRODUCT");

                        String alertRegister = objectMapper.writeValueAsString(alert);
                        client.publish("MO/Alert", new MqttMessage(alertRegister.getBytes()));
                        System.out.println("Sent register alert");
                    }

                    //Passo alla classe aggregatrice per fare il report, se ritorna null vuol dire che non ho ancora abbastanza dati
                    TelemetryDTO tel = dataAggregator.getRegisters(raw);
                    if (tel != null) {
                        String telemetry = objectMapper.writeValueAsString(tel);
                        client.publish("MO/Telemetry", new MqttMessage(telemetry.getBytes()));
                        System.out.println("Sent telemetry");
                    }

                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Fatal error on thread: " + e.getMessage());
                break;
            } catch (MqttException e) {
                System.err.println("Fatal error on publishing: " + e.getMessage());
            } catch (JacksonException e) {
                System.err.println("Fatal error on json: " + e.getMessage());
            }
        }
    }
}