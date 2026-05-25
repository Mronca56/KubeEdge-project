package org.example;

import java.util.concurrent.CountDownLatch;

public class Main {
    public static void main(String[] args) {
        MqttSubscriber mqttSubscriber = new MqttSubscriber();
        mqttSubscriber.start();
        //Per tenere in vita il thread finche non lo spegniamo noi
        CountDownLatch keepAliveLatch = new CountDownLatch(1);

        //Per fare uno shutdown pulito e non forzato
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Ricevuto segnale di spegnimento (SIGTERM). Avvio spegnimento pulito...");

            mqttSubscriber.stop();
            // Qui dovresti idealmente chiamare un metodo subscriber.stop()
            // che si preoccupa di:
            // 1. Chiamare client.disconnect() in modo pulito
            // 2. Interrompere i thread processCamera e processRegister (es. thread.interrupt())

            keepAliveLatch.countDown(); // Sblocca il main thread
            System.out.println("Spegnimento completato.");
        }));

        try {
            // Il main thread si blocca qui in attesa che il latch arrivi a 0 (cioè allo spegnimento)
            keepAliveLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}