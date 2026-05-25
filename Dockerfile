FROM ubuntu:22.04
ENV DEBIAN_FRONTEND=noninteractive
#Configurazione del sistema del nodo
RUN apt-get update && apt-get install -y --no-install-recommends  \
    systemd  \
    systemd-sysv  \
    curl  \
    containerd  \
    sudo  \
    tar  \
    mosquitto-clients  \
    ca-certificates  \
    iptables \
    nano \
    && rm -rf /var/lib/apt/lists/*
#Configurazione di Containerd con le impostazioni per kubeedge
RUN mkdir -p /etc/containerd && \
    containerd config default > /etc/containerd/config.toml && \
    sed -i 's/SystemdCgroup = true/SystemdCgroup = false/g' /etc/containerd/config.toml && \
    sed -i 's|registry.k8s.io/pause:3.6|kubeedge/pause:3.6|g' /etc/containerd/config.toml
#Configurazione di keadm
RUN curl -LO https://github.com/kubeedge/kubeedge/releases/download/v1.23.0/keadm-v1.23.0-linux-amd64.tar.gz && \
    tar -zxvf keadm-v1.23.0-linux-amd64.tar.gz && \
    mv keadm-v1.23.0-linux-amd64/keadm/keadm /usr/local/bin/ && \
    rm -rf keadm-v1.23.0-linux-amd64*
#Configurazione CNI di rete
RUN mkdir -p /opt/cni/bin /etc/cni/net.d && \
    curl -L "https://github.com/containernetworking/plugins/releases/download/v1.3.0/cni-plugins-linux-amd64-v1.3.0.tgz" | tar -C /opt/cni/bin -xz
COPY <<EOF /etc/cni/net.d/10-bridge.conf
{
  "cniVersion": "0.3.1",
  "name": "bridge",
  "type": "bridge",
  "bridge": "cni0",
  "isGateway": true,
  "ipMasq": true,
  "ipam": {
    "type": "host-local",
    "subnet": "10.244.1.0/24",
    "routes": [
      { "dst": "0.0.0.0/0" }
    ]
  }
}
EOF
COPY <<EOF /etc/cni/net.d/99-loopback.conf
{
  "cniVersion": "0.3.1",
  "name": "lo",
  "type": "loopback"
}
EOF
#Configurazione di mosquitto per il broker MQTT
RUN mkdir -p /var/lib/kubeedge/mqtt/data && chmod -R 777 /var/lib/kubeedge/mqtt
VOLUME [ "/sys/fs/cgroup" ]
STOPSIGNAL SIGRTMIN+3
CMD ["/lib/systemd/systemd"]