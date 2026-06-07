#!/bin/bash
set -e

CLUSTER_NAME="cloud-test"
CONFIG_FILE="kind-config.yaml"
CLOUD_MASTER_IP=172.25.0.10

echo "Creazione del cluster Kubernetes con kind..."
kind create cluster --name "$CLUSTER_NAME" --config "$CONFIG_FILE"

echo "Configurazione dell'accesso kubectl per l'utente root..."
sudo mkdir -p /root/.kube
sudo cp ~/.kube/config /root/.kube/config

echo "Connessione alla rete Docker"
docker network connect --ip $CLOUD_MASTER_IP edge-network "${CLUSTER_NAME}-control-plane"

# Patch per kindnet e kube-proxy
echo "Applicazione delle patch per evitare il deploy sui nodi edge..."
kubectl patch daemonset kube-proxy -n kube-system -p '{"spec": {"template": {"spec": {"affinity": {"nodeAffinity": {"requiredDuringSchedulingIgnoredDuringExecution": {"nodeSelectorTerms": [{"matchExpressions": [{"key": "node-role.kubernetes.io/edge", "operator": "DoesNotExist"}]}]}}}}}}}'
kubectl patch daemonset kindnet -n kube-system -p '{"spec": {"template": {"spec": {"affinity": {"nodeAffinity": {"requiredDuringSchedulingIgnoredDuringExecution": {"nodeSelectorTerms": [{"matchExpressions": [{"key": "node-role.kubernetes.io/edge", "operator": "DoesNotExist"}]}]}}}}}}}'

echo "Setup completato con successo!"