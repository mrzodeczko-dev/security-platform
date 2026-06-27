#!/usr/bin/env bash
# =============================================================================
# Lokalny setup na Minikube
# Uruchamia caly stack: MySQL, Redis, user-service, auth-service, API Gateway
#
# Wymagania:
#   - minikube   (https://minikube.sigs.k8s.io/docs/start/)
#   - helm       (https://helm.sh/docs/intro/install/)
#   - kubectl    (instaluje sie z minikube)
#   - docker     (jako driver dla minikube)
#
# Uzycie:
#   chmod +x k8s/local-setup.sh
#   ./k8s/local-setup.sh
# =============================================================================

set -euo pipefail

RELEASE_NAME="sg"
NAMESPACE="security-gateway-local"
CHART_DIR="$(dirname "$0")/security-api-gateway"
VALUES_FILE="$CHART_DIR/values-local.yaml"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

# ---------------------------------------------------------------------------
# 1. Sprawdz wymagania
# ---------------------------------------------------------------------------
info "Sprawdzam wymagania..."

command -v minikube >/dev/null 2>&1 || error "minikube nie znaleziony. Zainstaluj: https://minikube.sigs.k8s.io/docs/start/"
command -v helm     >/dev/null 2>&1 || error "helm nie znaleziony. Zainstaluj: https://helm.sh/docs/intro/install/"
command -v kubectl  >/dev/null 2>&1 || error "kubectl nie znaleziony. Zainstaluj: https://kubernetes.io/docs/tasks/tools/"

info "Wszystkie narzedzia zainstalowane."

# ---------------------------------------------------------------------------
# 2. Uruchom Minikube (jesli nie dziala)
# ---------------------------------------------------------------------------
if minikube status --format='{{.Host}}' 2>/dev/null | grep -q "Running"; then
    info "Minikube juz dziala."
else
    info "Uruchamiam Minikube (moze potrwac 1-2 min)..."
    minikube start \
        --cpus=4 \
        --memory=4096 \
        --driver=docker \
        --kubernetes-version=stable
    info "Minikube uruchomiony."
fi

# ---------------------------------------------------------------------------
# 3. (Opcjonalnie) Zbuduj obraz gateway lokalnie w Minikube
# ---------------------------------------------------------------------------
# Odkomentuj ponizsze linie jesli chcesz budowac obraz gateway z kodu zrodlowego
# zamiast pobierac z Docker Hub:
#
# info "Buduje obraz api-gateway w Minikube Docker..."
# eval $(minikube docker-env)
# docker build -t mrzodeczko/api-gateway-service:1.0.0 .
# eval $(minikube docker-env --unset)

# ---------------------------------------------------------------------------
# 4. Stworz namespace
# ---------------------------------------------------------------------------
info "Tworze namespace $NAMESPACE..."
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

# ---------------------------------------------------------------------------
# 5. Zainstaluj / zaktualizuj Helm release
# ---------------------------------------------------------------------------
info "Instaluje Helm chart..."

helm upgrade --install "$RELEASE_NAME" "$CHART_DIR" \
    -f "$VALUES_FILE" \
    -n "$NAMESPACE" \
    --wait \
    --timeout 5m

# ---------------------------------------------------------------------------
# 6. Czekaj na pody
# ---------------------------------------------------------------------------
info "Czekam na gotowe pody..."

echo ""
echo "Status podow:"
kubectl get pods -n "$NAMESPACE" -o wide

echo ""
info "Czekam az wszystkie pody beda Running (max 5 min)..."
kubectl wait --for=condition=Ready pods --all -n "$NAMESPACE" --timeout=300s 2>/dev/null || {
    warn "Nie wszystkie pody sa gotowe. Sprawdz status:"
    echo ""
    kubectl get pods -n "$NAMESPACE"
    echo ""
    warn "Logi problematycznego poda: kubectl logs <pod-name> -n $NAMESPACE"
    warn "Opis poda: kubectl describe pod <pod-name> -n $NAMESPACE"
}

# ---------------------------------------------------------------------------
# 7. Dostep do API Gateway
# ---------------------------------------------------------------------------
echo ""
echo "============================================================"
info "Stack uruchomiony!"
echo "============================================================"
echo ""

# Pobierz URL gateway
GATEWAY_URL=$(minikube service "$RELEASE_NAME-gateway" -n "$NAMESPACE" --url 2>/dev/null || echo "")

if [ -n "$GATEWAY_URL" ]; then
    info "API Gateway dostepny pod: $GATEWAY_URL"
else
    info "Uzyj port-forward zeby uzyskac dostep:"
    echo ""
    echo "  kubectl port-forward svc/$RELEASE_NAME-gateway 8085:8085 -n $NAMESPACE"
    echo ""
    echo "  Potem otworz: http://localhost:8085"
fi

echo ""
info "Przydatne komendy:"
echo ""
echo "  # Status podow"
echo "  kubectl get pods -n $NAMESPACE"
echo ""
echo "  # Logi gateway"
echo "  kubectl logs -f deployment/$RELEASE_NAME-gateway -n $NAMESPACE"
echo ""
echo "  # Logi auth-service"
echo "  kubectl logs -f deployment/$RELEASE_NAME-auth-service -n $NAMESPACE"
echo ""
echo "  # Port-forward gateway"
echo "  kubectl port-forward svc/$RELEASE_NAME-gateway 8085:8085 -n $NAMESPACE"
echo ""
echo "  # Usun wszystko"
echo "  ./k8s/local-teardown.sh"
echo ""
