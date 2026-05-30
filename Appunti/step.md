# Passaggi per dimostrazione funzionamento:
## Prerequisiti:
* **Docker** funzionante e avviato sulla macchina
* Una **connessione Docker** per creare la connessione edge-cloud. La subnet usata nella dimostrazione e nei file di configurazione è: _172.25.0.0/16_, con nodo cloud sull'indirizzo _172.25.0.10_ e nodo edge su _172.25.0.2_
```shell
  docker network create --subnet=<subnet> <nomerete>
  docker network connect --ip <ip> <nomerete> <nomenodo>
```
* Installati sulla macchina **Kind** (versione kubernetes: _v1.32.11_ per compatibilità con kubeegde) e **Keadm** (versione utilizzata: _v1.23.0_)

## Passaggi
### Nodo Cloud
1. Creazione di un nodo cluster kubernetes centrale, con la versione specificata. Connettere poi il nodo creato alla rete Docker
```shell
kind create cluster --name <nome> --config <nome-file-kind>
```
2. Creazione della cartella di kubernetes e dei file di configurazione per l'utente root.
```shell
sudo mkdir -p /root/.kube
sudo cp ~/.kube/config /root/.kube/config
```
3. Inizializzazione di CloudCore sul cluster. Alla fine devo avere i due pod cloudcore in stato running.
```shell
sudo keadm init --advertise-address=${CLOUD_MASTER_IP} --kubeedge-version=${KUBEEDGE_VERSION}
kubectl get pods -n kubeedge #Per verificare il corretto funzionamento
```
4. Patch dei daemon set per evitare che i daemon set del cloud girino nell'edge.
```shell
kubectl patch daemonset kube-proxy -n kube-system -p '{"spec": {"template": {"spec": {"affinity": {"nodeAffinity": {"requiredDuringSchedulingIgnoredDuringExecution": {"nodeSelectorTerms": [{"matchExpressions": [{"key": "node-role.kubernetes.io/edge", "operator": "DoesNotExist"}]}]}}}}}}}'
kubectl patch daemonset kindnet -n kube-system -p '{"spec": {"template": {"spec": {"affinity": {"nodeAffinity": {"requiredDuringSchedulingIgnoredDuringExecution": {"nodeSelectorTerms": [{"matchExpressions": [{"key": "node-role.kubernetes.io/edge", "operator": "DoesNotExist"}]}]}}}}}}}'
```
5. Generazione del token per il join del nodo edge:
```shell
sudo keadm gettoken
```

### Nodo Edge
1. Creazione di un nodo edge tramite il Dockerfile, che installa un ambiente minimale installando i pacchetti necessari tra cui Containerd e Keadm (immagine usata locale: edge-image, mentre nodo edge chiamato: edge-test).
```shell
docker run -d --name <nodo-edge-simulato> --privileged --network <nome-rete> --cgroupns=host -v /sys/fs/cgroup:/sys/fs/cgroup:rw -v edge-containerd:/var/lib/containerd <nome-image>
docker exec -it <nodo-edge-simulato> bash #Per entrare nella shell del container
```
2. Fare join del nodo al CloudCore con il comando. Alla fine devo avere edgecore in stato running e nel cloud devo vedere il nodo edge connesso e un pod mosquitto):
```shell
keadm join \
  --cloudcore-ipport=${CLOUD_MASTER_IP}:10000 \
  --edgenode-name=${EDGE_NODE_NAME} \
  --token=<token-edge> \
  --kubeedge-version=${KUBEEDGE_VERSION}
systemctl status edgecore #Per verificare il corretto funzionamento
kubectl get nodes #Lato cloud per vedere se l'edge è connesso
```

### Deployment
0. Controllare che nomi e indirizzi ip coincidano nei yaml, in particolare in sensor (ip nodo edge), store (ip nodo edge e ip nodo cloud:porta service moquitto cloud), e devices (nome del nodo edge).
1. Applicare nel cluster tutti i file di deployment (dalla root del progetto: Manifests, Manifests/Cloud, Manifests/Edge). Prima di procedere tutti i pod (5 in questo caso) devono essere in stato running (in caso di problemi guardare i log)
```shell
kubectl apply -f <percorso-file>
kubectl get pods -o wide #Per vedere se sono su edge o cloud
kubectl describe pods <nome-pod> #Per vedere dettagli pods
kubectl logs <nome-container> #Per vedere log contaner cloud
crictl logs <nome-container> #Per vedere log contaner edge
```
2. Connettersi da browser per verificare corretto funzionamento di tutto (http://localhost:8080/swagger-ui/index.html#/central-controller/).
```shell
kubectl port-forward service/cloud-service 8080:8080
```
3. Entrare nel mongodb e controllare i messaggi arrivati e scritti nei db:
```shell
kubectl exec -it deployment/mongo -- mongosh
db.Telemetries.countDocuments()
db.Alerts.countDocuments()
```
4. Simulare un distacco della rete. Nel mentre si vede come il cloud si sia effettivamente staccato dal broker ma funziona e vede le cose non aggiornate, e l'edge continua a funzionare senza problemi.
```shell
kubectl scale deployment <nome-deployment-mosquitto> --replicas=0
```
5. Riconnettere la rete all'edge. Mostrare che pian piano la connessione al cloud riprende e i dati tornano a fluire, recuperando tutti quelli persi. Se si torna sul db si nota un impennata di messaggi in arrivo nei momenti successivi.
```shell
kubectl scale deployment <nome-deployment-mosquitto> --replicas=1
```