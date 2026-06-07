# KubeEdge Project: Edge-to-Cloud Architecture for Retail

The project aims to demonstrate the effectiveness and advantages of adopting **KubeEdge** for developing distributed architectures, highlighting its benefits compared to using a traditional Kubernetes cluster.
The application scenario simulates a global retail chain requiring a system for real-time monitoring, customer behavior analysis, and preventive maintenance.

The key concepts of the infrastructure are:
* The physical servers located in the stores run **EdgeCore**, hosting local applications and workloads.
* Hardware devices, such as cameras and cash registers, are modeled and managed as KubeEdge **Devices** (via Device Twins).
* Data processing (Edge Processing) for customer behavior analysis occurs locally, reducing latency and bandwidth consumption.
* The edge servers aggregate information, generating synthetic reports and alerts to be forwarded to the cloud.
* The central node runs **CloudCore**, acts as a global aggregator receiving data from all stores, and provides an interface to visualize metrics, manage alerts, and persist information in a database.

---

## Logical Division

The application code is structured into four main modules:

1. **Shared Models**: Contains the definitions of the Data Transfer Objects (DTOs) shared between the Edge and Cloud modules. Each payload mandatorily includes the store identifier (source) and the corresponding *datetime*. The managed models are:
    * **Camera Data**: Data regarding customer flow (people in the store, people in line, suspicious activity detection).
    * **Register Data**: Transaction data from the cash registers (product sold, quantity, final price).
    * **Report**: Aggregated data including the number of scans performed, the revenue in the reference period, and the average line length.
    * **Alert**: Critical notifications divided into four types (line too long, suspicious activities, low inventory, device malfunctions).

2. **Device Simulation**: Module dedicated to simulating the store's physical sensors. It connects to the MQTT client and cyclically generates dummy data (every 2 seconds for cameras, every 3 seconds for cash registers). The generated data is mapped into the correct structure for the *Device Twins* and published to the broker.

3. **Edge-node**: This is the edge node's application workload, optimized for resource-constrained environments. It manages two MQTT clients: one for receiving data from local Devices and one for communication with the Cloud. Its business logic includes:
    * Analyzing incoming streams to trigger real-time alerts if necessary.
    * Buffering local data to generate and send periodic reports.
    * A one-minute health-status check to verify the proper functioning of the Devices.

4. **Cloud-node**: Represents the central node (Control Plane) of the application. It handles the ingestion of aggregated data from the edge nodes and persists it in a **MongoDB** database. This data is then exposed and made accessible to end-users via an API.

---

## Cluster Architecture

The KubeEdge cluster consists of numerous Pods, strategically distributed between the Edge and Cloud components:

1. **Edge Tier**: Hosts the Pods responsible for device simulation and the store's application workload that aggregates and transmits data. To keep the architecture lightweight and optimized at the network edge, standard Kubernetes Services (and kube-proxy) are not used. Communication occurs instead via the native KubeEdge **EventBus** and the MQTT protocol, leveraging Device definitions and shadow state.

2. **Cloud Tier**: Manages all computationally intensive infrastructure and *stateful* workloads. Specifically, the Cloud maintains:
    * The definitions and state of the *Device Twins* replicated from the Edge.
    * The Persistent Volumes (PV) and Persistent Volume Claims (PVC) required for MongoDB storage.
    * An MQTT broker (Mosquitto) for message exchange.
    * The central node of the application.

   In terms of networking, **MongoDB** is exposed solely within the cluster via a dedicated Service (ClusterIP). The **broker** and the **central application**, however, use Services that expose them externally: the app must be accessible to end-users, while the broker must accept incoming connections from the edge nodes (which, from a network perspective, communicate with the KubeEdge cluster from the outside).