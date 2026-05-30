# Progetto KubeEdge

## Riassunto Gemini
### Kubernetes
In Kubernetes non dici al sistema cosa fare (approccio imperativo), ma descrivi lo stato desiderato (approccio dichiarativo) tramite file YAML. Il sistema opera su un Control Loop continuo: controlla lo stato attuale, lo confronta con lo stato desiderato (definito nei tuoi YAML) e agisce per correggere le differenze. Se dichiari che vuoi 3 repliche di un'app e una muore, K8s se ne accorge e ne avvia una nuova.

Un cluster Kubernetes è diviso in due macro-aree:
* **Control-plane**: Risiede solitamente nel cloud o in un data center stabile. Prende le decisioni globali.
  * API Server: Tutti i componenti comunicano solo con l'API Server.
  * etcd: Un database chiave-valore ultra-affidabile che salva lo stato dell'intero cluster.
  * Scheduler: Decide su quale nodo fisico/virtuale deve girare la tua applicazione in base alle risorse disponibili. 
  * Controller Manager: Esegue i control loop.
* **Worker Node**: Sono le macchine (o VM) su cui girano effettivamente i tuoi applicativi. Su ognuno di questi gira il **kubelet**: Prende ordini dall'API Server (es. "avvia questo container") e gli riporta costantemente lo stato di salute del nodo.

Le primitive principali utilizzate sono:
* **Pod**: È l'unità di base di K8s. Non distribuisci mai un container direttamente, ma lo "incapsuli" in un Pod.
* **Deployment**: È il controllore che gestisce i tuoi Pod. Definisce quante repliche vuoi, la versione dell'immagine Docker da usare e come effettuare gli aggiornamenti senza disservizi (Rolling Updates).
* **Service**: Poiché i Pod sono effimeri (nascono, muoiono, cambiano indirizzo IP), il Service fornisce un indirizzo IP fisso e un nome DNS stabile per permettere ai vari pezzi della tua applicazione di trovarsi e comunicare.

### KubeEdge
KubeEdge interviene per separare fisicamente i control-plane e Worker node. Nasce perché il Kubelet standard è troppo pesante per piccoli dispositivi IoT e, soprattutto, va nel panico se perde la connessione internet con l'API Server. KubeEdge sostituisce il Kubelet con un agente molto più leggero e indipendente chiamato **EdgeCore**.

Questo porta ad avere autonomia offline, basso consumo di risorse, e gestione nativa dei dispositivi tramite MQTT e Device Twins.

## Idea progetto
Lo scenario è una catena di negozi con diversi stabilimenti in tutto il mondo, di cui bisogna fare un controllo qualità in tempo reale e manutenzione preventiva.
* I server degli store eseguono EdgeCore con le applicazioni locali.
* Gli scanner dei barcodes e delle telecamere vengono gestite come device.
* Viene fatto processing locale per l'analisi del comportamento dei clienti.
* L'inventario e i report delle vendite vengono fatte a livello centralizzato.


Devo avere **Simulatori dei device**, script per generare i dati, simulando le telecamere e gli scanner dei codici a barre. Pubblicano questi dati su un broker di messaggistica locale (MQTT). Lo **store** è il nodo gestito da KubeEdge. Qui girerà il broker MQTT e l'applicazione Java Edge. L'app elabora i dati grezzi dei sensori in tempo reale (es. conta i clienti o aggrega gli scontrini). Il **Centro Direzionale** è il tuo cluster Kind dove gira CloudCore. Qui distribuirai la tua applicazione Java Cloud (una dashboard o un server centrale) che riceve solo i dati aggregati o gli allarmi critici dagli Store.

### Demo

1. Fase Normale: Fai partire il simulatore dei codici a barre. Fai vedere che l'edge-processor riceve le scansioni, elabora l'inventario e invia il report al cloud-dashboard ogni 10 secondi.
2. Il Taglio (Network Partition): Stacchi la rete al nodo Edge. Se stai usando Docker, puoi farlo facilmente scollegando il container EdgeCore dalla rete Docker condivisa con Kind (comando: docker network disconnect <nome_rete> <nome_container_edge>).
3. L'Autonomia Edge (Cosa mostrare):
   * Nel Cloud, fai vedere che il nodo Edge risulta NotReady su Kubernetes (kubectl get nodes). In un K8s normale, il Pod della tua app Edge verrebbe killato e riprogrammato altrove dopo 5 minuti.
   * Sull'Edge, fai vedere che il tuo simulatore continua a sparare dati MQTT e il tuo edge-processor continua a girare perfettamente, raccogliendo le scansioni del negozio anche se internet è assente. Lo Store non si ferma.
4. Il Ripristino: Ricolleghi la rete Docker. Kubernetes vede di nuovo il nodo Ready. Il tuo edge-processor può svuotare la cache locale e inviare i dati accumulati al Cloud. Inoltre, tramite i Device Twin di KubeEdge, lo stato dei sensori si riallineerà automaticamente con il Control Plane.


# Appunti
KubeEdge è un sistema open source che estende l'orchestrazione nativa delle applicazioni containerizzate e la gestione dei dispositivi agli host situati nell'Edge. È basato su Kubernetes e fornisce il supporto infrastrutturale fondamentale per la rete, la distribuzione delle applicazioni e la sincronizzazione dei metadati tra cloud ed edge. Gestire le applicazioni edge significa fare i conti con reti inaffidabili, limitazioni delle risorse, complessità nell'integrazione dei dispositivi e la necessità di mantenere strumenti di gestione distinti per l'edge e il cloud. Con KubeEdge è possibile usare le API standard di Kubernetes per gestire sia i carichi di lavoro nel cloud che quelli in edge, grazie alla gestione integrata dei dispositivi, alla funzionalità offline e alla sincronizzazione automatica.

Scenari reali in cui può essere utile:
* Manufatturiera intelligente: immaginando di avere tante fabbriche sparse per il mondo, con ogni stabilimento che possiede nodi edge che eseguono app di controllo qualità, predizione di manutenzione... sensori che necessitano processing a real-time. Con KubeEdge è possibile distribuire aggiornamenti a tutti i nodi periferici dello stabilimento da un piano di controllo cloud centralizzato, gestire la connettività dei dispositivi tramite MQTT e garantire la continuità operativa anche in caso di interruzioni di rete.
* Veicoli autonomi: se ho una rete di tanti veicoli, ognuno dei quali è essenzialmente un edge node che deve elaborare dati, fare decisioni locali e syncarsi con il cloud per update e ottimizzazione delle rotte. KubeEdge permette di trattarli come nodi k8s e gestire l'intera flotta usando i suoi tools.
* Edge retail:
* Infrastruttura smart city:
* IoT industriali: Quando bisogna distribuire un aggiornamento del firmware o implementare nuovi algoritmi di analisi, invece di aggiornare manualmente ogni singola sede, si può aggiornare una sola volta la definizione nel control plane cloud e KubeEdge la distribuisce automaticamente a tutti i nodi edge corrispondenti.

Benefici dell'utilizzo di KubeEdge:
* Posso usare tools di kubernetes nativamente nell'edge.
* Astrae device fisici in device twins e li gestisce in cloud.
* Disponibilità offline, i nodi edge funzionano anche quando staccati dal cloud, e si sincronizzeranno con esso una volta tornati online.
* Leggeri, adatti per ambienti edge con capacità limitate.
* Supporto MQTT per la comunicazione di device IoT.
* Sincronizzazione perfetta tra i componenti cloud e quelli edge.

### Componenti
1. **CloudCore**: Componente cloudside che gira nel cluster kubernetes. È il nodo centrale che gestisce tutti i nodi Edge e la comunicazione cloud-edge.
2. **EdgeCore**: Agente kubernetes leggero edgeside che gira su ogni nodo edge. Gestisce i container e comunica con il cloud.
3. **keadm**: tool di amministratore per installazione e gestione di KubeEdge.
4. **Gestione Device**: Approccio di KubeEdge per gestire IoT device e sensori. È in grado di gestire diversi tipi di dispositivi (sensori, telecamere, attuatori) tramite un'unica interfaccia.
5. **EdgeHub**: Il componente che gestisce la comunicazione Edge-Cloud (come un router tra edge e cloud, che gestisce interruzioni network in modo dolce).
6. **Edged**: Il rimpiazzo di kubelet leggero per i nodi edge.
7. **EventBus & ServiceBus**: Componenti che gestiscono rispettivamente comunicazione MQTT e HTTP
8. **Device Twins**: La rappresentazione cloud dei device edge. Un'immagine digitale del tuo dispositivo fisico rimane sincronizzata, consentendo alle applicazioni cloud di interagire con i dispositivi periferici come se fossero locali.

### WorkFlow
| Immagine                                                                                 | Testo                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
|:-----------------------------------------------------------------------------------------|:------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| <img alt="workflow edge.png" height="473" src="images/workflow%20edge.png" width="300"/> | 1. Deploy dell'applicazione con **kubectl** standard.<br/>2. **Cloudcore** processa il deployment e determina che nodo Edge deve riceverlo.<br/>3. **EdgeHub** sincronizza il workload dell'edge node con un **WebSocket** sicuro.<br/>4. **EdgeCore** gestisce il ciclo di vita dell'applicazione localmente su ogni nodo edge.<br/>5. Il flusso di dati del device va in **MQTT** per comunicazioni real-time.<br/>6. Lo status torna al cloud per monitoraggio e gestione. |

## Beehive
Beehive è un framework di messaggistica basato sui go-channel per la comunicazione tra i moduli di KubeEdge. Un modulo registrato su Beehive può comunicare con altri moduli Beehive se è noto il nome con cui è registrato l'altro modulo Beehive o il nome del gruppo a cui appartiene il modulo. Beehive supporta le seguenti operazioni sui moduli:
1. Aggiungi modulo
2. Aggiungi il modulo a un gruppo
3. Cleanup (rimuovi il modulo dal core beehive e dai gruppi)

Supporta inoltre le seguenti operazioni sui messaggi:
4. Invia a un gruppo/modulo
5. Ricevi da un modulo
6. Manda il Sync a un gruppo/modulo
7. Invia una risposta a un Sync

## Device Twins
I Device Twin (spesso denominati **digital device twin**) sono rappresentazioni software di dispositivi fisici — come i caricatori per veicoli elettrici — che rispecchiano l'identità, la configurazione, lo stato, i dati telemetrici e la cronologia di ciascun dispositivo in un sistema di backend. Un device twin costituisce *l'unica fonte di verità* riguardo alle caratteristiche del caricatore, alle sue impostazioni di configurazione e alle sue attività correnti.

I Device Twin semplificano la gestione e la sicurezza delle grandi flotte di caricatori connessi, consentendone il miglioramento nel tempo:
* Centralizzano la configurazione dei dispositivi e riducono gli errori di configurazione manuale.
* Consentono una risoluzione più rapida dei problemi grazie alla cronologia completa degli stati e degli eventi.
* Supportano la manutenzione predittiva utilizzando i trend telemetrici.
* Rendono più sicuri gli aggiornamenti del firmware grazie a un monitoraggio chiaro dello stato dei dispositivi.
* Migliorano l'affidabilità grazie al rilevamento tempestivo delle anomalie.
* Forniscono registrazioni verificabili ai fini della conformità e della risposta agli incidenti.

KubeEdge supporta la gestione dei dispositivi con l'aiuto di Kubernetes CRDs[^1] e dei Device Mapper corrispondenti a quello usato. Per definire il device si usano due elementi:
* **Device Model**: Un Device Model descrive le proprietà dei dispositivi di un determinato tipo. Un Device Model è un Physical Model che definisce le proprietà e i parametri dei dispositivi fisici.
```yaml
apiVersion: devices.kubeedge.io/v1beta1
kind: DeviceModel
metadata:
  name: beta1-model
spec:
  properties:
    - name: temp
      description: beta1-model
      type: INT
      accessMode: ReadWrite
      maximum: "100"
      minimum: "1"
      unit: "Celsius"
  protocol: modbus
```
* **Device Istance**: Rappresenta l'oggetto effettivo. Le specifiche del dispositivo sono statiche e comprendono l'elenco delle proprietà del dispositivo; descrivono i dettagli di ciascuna proprietà, inclusi nome, tipo e metodo di accesso.
```yaml
apiVersion: devices.kubeedge.io/v1beta1
kind: Device
metadata:
  name: beta1-device
spec:
  deviceModelRef:
    name: beta1-model
  nodeName: worker-node1
  properties:
    - name: temp
      collectCycle: 10000000000  # The frequency of reporting data to the cloud, once every 10 seconds
      reportCycle: 10000000000   # The frequency of data push to user applications or databases, once every 10 seconds
      reportToCloud: true
      desired:
        value: "30"
      pushMethod:
        mqtt:
          address: tcp://127.0.0.1:1883
          topic: temp
          qos: 0
          retained: false
        dbMethod:
          influxdb2:
            influxdb2ClientConfig:
              url: http://127.0.0.1:8086
              org: test-org
              bucket: test-bucket
            influxdb2DataConfig:
              measurement: stat
              tag:
                unit: temperature
              fieldKey: beta1test
      visitors:
        protocolName: modbus
        configData:
          register: "HoldingRegister"
          offset: 2
          limit: 1
          scale: 1
          isSwap: true
          isRegisterSwap: true
  protocol:
    protocolName: modbus
    configData:
      ip: 172.17.0.3
      port: 1502 
```
[^1]**CustomResourceDefinitions**: API che permette di definire risorse personalizzabili. L'API kubernetes si occupa poi di gestire la risorsa, senza dover scrivere un API server apposito.

## Primo deploy
(https://kubeedge.io/docs/setup/prerequisites/runtime#containerd)
### Nodo Cluster
1. Il nodo cloud deve contenere il control-plane del cluster Kubernetes (versione k8s 1.32 con 1.23.0, utilizzare --config file).
```shell
kind create cluster --name <nome>
kubectl get nodes -o wide #per vedere anche ip dei nodi
```
2. Serve poi installare keadm per poter utilizzare kubeedge. Se si vuole si possono esportare le variabili d'ambiente KUBEEDGE_VERSION e CLOUD_MASTER_IP (l'IP del control plane di kubernetes)
```shell
curl -L https://github.com/kubeedge/kubeedge/releases/download/${KUBEEDGE_VERSION}/keadm-${KUBEEDGE_VERSION}-linux-amd64.tar.gz | tar -xz
sudo mv keadm-${KUBEEDGE_VERSION}-linux-amd64/keadm/keadm /usr/local/bin/
```
3. (opzionale) Dato che lavoro con WSL devo fare in modo che veda kubernetes installato.
```shell
sudo mkdir -p /root/.kube
sudo cp ~/.kube/config /root/.kube/config
```
4. Inizializzazione di CloudCore sul cluster.
```shell
sudo keadm init --advertise-address=${CLOUD_MASTER_IP} --kubeedge-version=${KUBEEDGE_VERSION}
kubectl get pods -n kubeedge #Per verificare il corretto funzionamento
```
5. Generazione del token per l'edge:
```shell
sudo keadm gettoken
```
6. Patch dei daemon set per evitare che i daemon set del cloud girino nell'edge.
```shell
kubectl patch daemonset kube-proxy -n kube-system -p '{"spec": {"template": {"spec": {"affinity": {"nodeAffinity": {"requiredDuringSchedulingIgnoredDuringExecution": {"nodeSelectorTerms": [{"matchExpressions": [{"key": "node-role.kubernetes.io/edge", "operator": "DoesNotExist"}]}]}}}}}}}'
kubectl patch daemonset kindnet -n kube-system -p '{"spec": {"template": {"spec": {"affinity": {"nodeAffinity": {"requiredDuringSchedulingIgnoredDuringExecution": {"nodeSelectorTerms": [{"matchExpressions": [{"key": "node-role.kubernetes.io/edge", "operator": "DoesNotExist"}]}]}}}}}}}'
```
### Nodo Edge
1. Creazione di un nodo edge tramite il Dockerfile, che installa un ambiente minimale installando i pacchetti necessari tra cui Containerd e Keadm.
```shell
docker build -t <nome> .
docker run -d --name <nodo-edge-simulato> --privileged --network <nome*> --cgroupns=host -v /sys/fs/cgroup:/sys/fs/cgroup:rw -v edge-containerd:/var/lib/containerd <nome>
docker exec -it <nodo-edge-simulato> bash #Per entrare nella shell del container
```
2. Joinare il nodo al CloudCore con il comando (al posto dell\'ip è possibile mettere il nome della rete docker per evitare di ip fissi che cambiano):
```shell
keadm join \
  --cloudcore-ipport=${CLOUD_MASTER_IP}:10000 \
  --edgenode-name=${EDGE_NODE_NAME} \
  --token=<token-from-step-3> \
  --kubeedge-version=${KUBEEDGE_VERSION}
systemctl status edgecore #Per verificare il corretto funzionamento
kubectl get nodes #Lato cloud per vedere se l'edge è connesso
```
IN CASO DI ERRORI SU KEADM UTILIZZARE I SEGUENTI COMANDI (PER TOGLIERE QUALSIASI COSA INSTALLATA E FARE PULIZIA PER EVITARE CONFLITTI):
```shell
keadm reset --force
kubectl delete namespace kubeedge
```
Per quanto riguarda la rete docker bisogna crearne una apposita e collegarci sia il cloud che l'edge. Assegnare una subnet con ip fissi in modo che al riavvio dovrebbero restare uguali
```shell
docker network create --subnet=172.25.0.0/16 <nomerete>
docker network connect --ip 172.25.0.10 <nomerete> <nomenodo>
```
### Disconnessione in Docker Desktop (metodo 1, stacco tutto, faccio da wsl)
1. Individuare la rete Docker a cui è collegato il nodo edge
```shell
docker inspect <nome-nodo> -f '{{range $key, $value := .NetworkSettings.Networks}}{{$key}}{{end}}'
```
2. Una volta identificata la rete, scolleghiamo il container.
```shell
   docker network disconnect <nome-rete> <nome-nodo>
```
3. Riconnettere tutto quando si ha finito.
```shell
   docker network connect <nome-rete> <nome-nodo>
```

### Disconnessione in Docker Desktop (metodo 2, più realistico, interrompe la comunicazione cloud-edge, mantenendo quella locale)
1. Dentro il nodo edge, blocco il traffico in uscita verso la porta del cloud-core, tramite iptable
```shell
iptables -A OUTPUT -p tcp --dport 10000 -j DROP
```
2. Riconnettere tutto quando si ha finito.
```shell
   iptables -D OUTPUT -p tcp --dport 10000 -j DROP
```

Per risolvere errore mosquitto, nell'edge:
```shell
mkdir -p /var/lib/kubeedge/mqtt/data
chmod -R 777 /var/lib/kubeedge/mqtt

sed -i 's/mqttMode: 2/mqttMode: 1/g' /etc/kubeedge/config/edgecore.yaml
sed -i 's/mqttMode: 0/mqttMode: 1/g' /etc/kubeedge/config/edgecore.yaml
```

Per connettersi da browser:
```shell
kubectl port-forward service/cloud-service 8080:8080
```

Ogni volta che faccio ripartire la struttura serve fare il rollout del cloud. Se non funziona controllare che siano tutti up anche nell'edge. A volte lo store non invia correttamente.

Per entrare nel mongodb: kubectl exec -it deployment/mongo -- mongosh
Per contare: db.Telemetries.countDocuments()
db.Alerts.countDocuments()

Connessione e disconnessione
docker network disconnect edge-network cloud-node-control-plane
docker network connect --ip=172.25.0.10 edge-network cloud-node-control-plane

Una volta fatti i deployment per vedere i log dei nodi edge entrare nel nodo, utilizzare crictl ps per nomi container e poi crictl logs <nome-cont>