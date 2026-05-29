#!/bin/bash
echo "Avvio pulizia selettiva dell'infrastruttura..."

# 1. Elimina i deployment applicativi
kubectl delete deployment --all --timeout=30s

# 2. Forza la cancellazione dei pod rimasti bloccati in "Terminating" (I fantasmi)
echo "Sfratto forzato dei pod fantasmi..."
kubectl delete pods -all --grace-period=0 --force 2>/dev/null

# 3. Pulisci lo stato dei dati (PVC) se stai usando volumi persistenti
echo "Pulizia dei volumi persistenti..."
kubectl delete pvc --all
kubectl delete pv --all --ignore-not-found=true

# 4. Attendi che lo stato si stabilizzi
sleep 3

# 5. Ri-applica solo i manifest applicativi
echo "Ridistribuzione dei servizi..."
kubectl apply -f Manifests
kubectl apply -f Manifests/Cloud/
kubectl apply -f Manifests/Edge

echo "Ambiente ripristinato con successo senza riavviare KinD!"