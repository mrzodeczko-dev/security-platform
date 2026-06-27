# Kubernetes — Security API Gateway

Helm chart deployujący cały stack: API Gateway, auth-service, user-service, MySQL, Redis.

## Wymagania

- [Minikube](https://minikube.sigs.k8s.io/docs/start/) — lokalny klaster Kubernetes
- [Docker Desktop](https://docs.docker.com/desktop/) — driver dla Minikube
- [Helm](https://helm.sh/docs/intro/install/) — menedżer pakietów Kubernetes
- [kubectl](https://kubernetes.io/docs/tasks/tools/) — CLI do Kubernetes (instaluje się z Minikube)

### Instalacja na Windows (PowerShell jako Administrator)

```powershell
# 1. Minikube
winget install Kubernetes.minikube

# 2. Helm
winget install Helm.Helm

# 3. kubectl (opcjonalnie — minikube kubectl działa też)
winget install Kubernetes.kubectl
```

## Szybki start (lokalnie)

```bash
# 1. Sklonuj repo i wejdź do katalogu
cd security-api-gateway

# 2. Uruchom cały stack jedną komendą
chmod +x k8s/local-setup.sh
./k8s/local-setup.sh

# 3. Port-forward do gateway (jeśli skrypt nie wyświetlił URL)
kubectl port-forward svc/sg-gateway 8085:8085 -n security-gateway-local

# 4. Testuj
curl http://localhost:8085/
```

## Ręczna instalacja

```bash
# Start Minikube
minikube start --cpus=4 --memory=4096 --driver=docker

# Instalacja Helm chart
helm install sg ./k8s/security-api-gateway \
    -f k8s/security-api-gateway/values-local.yaml \
    -n security-gateway-local \
    --create-namespace

# Sprawdź status
kubectl get pods -n security-gateway-local

# Port-forward
kubectl port-forward svc/sg-gateway 8085:8085 -n security-gateway-local
```

## Środowiska

| Plik | Środowisko | Opis |
|------|-----------|------|
| `values-local.yaml` | Lokalne (Minikube) | 1 replika, małe zasoby, sekrety testowe, bez ingress |
| `values.yaml` | Dev/Staging | 1 replika, średnie zasoby, bez HPA |
| `values-prod.yaml` | Produkcja | 3+ repliki, HPA, PDB, ingress z TLS, NetworkPolicy |

## Deploy na produkcję

```bash
helm install sg ./k8s/security-api-gateway \
    -f k8s/security-api-gateway/values-prod.yaml \
    -n security-gateway-prod \
    --create-namespace \
    --set secrets.jwtSecret=<TWOJ_BASE64_KLUCZ> \
    --set secrets.redisPassword=<SILNE_HASLO> \
    --set secrets.mysqlRootPassword=<SILNE_HASLO> \
    --set secrets.mysqlUser=<USER> \
    --set secrets.mysqlPassword=<SILNE_HASLO> \
    --set secrets.internalSecret=<SEKRET> \
    --set ingress.host=api.twojadomena.pl
```

## Przydatne komendy

```bash
# Status podów
kubectl get pods -n security-gateway-local

# Logi konkretnego serwisu
kubectl logs -f deployment/sg-gateway -n security-gateway-local
kubectl logs -f deployment/sg-auth-service -n security-gateway-local
kubectl logs -f deployment/sg-user-service -n security-gateway-local

# Opis poda (debug)
kubectl describe pod <pod-name> -n security-gateway-local

# Wejście do poda
kubectl exec -it <pod-name> -n security-gateway-local -- sh

# Usunięcie stacku
./k8s/local-teardown.sh
```

## Struktura Helm chart

```
k8s/security-api-gateway/
├── Chart.yaml
├── values.yaml              # dev defaults
├── values-local.yaml         # minikube
├── values-prod.yaml          # produkcja
└── templates/
    ├── _helpers.tpl
    ├── namespace.yaml
    ├── secrets.yaml
    ├── gateway-deployment.yaml
    ├── gateway-service.yaml
    ├── gateway-configmap.yaml
    ├── gateway-hpa.yaml       # tylko prod
    ├── gateway-pdb.yaml       # tylko prod
    ├── auth-deployment.yaml
    ├── auth-service.yaml
    ├── user-deployment.yaml
    ├── user-service.yaml
    ├── redis-deployment.yaml
    ├── redis-service.yaml
    ├── mysql-statefulset.yaml
    ├── mysql-service.yaml
    ├── ingress.yaml           # opcjonalny
    └── networkpolicy.yaml     # opcjonalny
```

## Kolejność startu

1. **MySQL** — StatefulSet z PersistentVolume
2. **Redis** — Deployment
3. **user-service** — czeka na MySQL (init container)
4. **auth-service** — łączy się z Redis i user-service
5. **API Gateway** — łączy się z auth-service, user-service i Redis
