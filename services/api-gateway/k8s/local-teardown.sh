#!/usr/bin/env bash
# =============================================================================
# Usuwa caly stack z Minikube
# =============================================================================

set -euo pipefail

RELEASE_NAME="sg"
NAMESPACE="security-gateway-local"

GREEN='\033[0;32m'
NC='\033[0m'
info() { echo -e "${GREEN}[INFO]${NC}  $1"; }

info "Usuwam Helm release '$RELEASE_NAME'..."
helm uninstall "$RELEASE_NAME" -n "$NAMESPACE" 2>/dev/null || true

info "Usuwam PVC (dane MySQL)..."
kubectl delete pvc --all -n "$NAMESPACE" 2>/dev/null || true

info "Usuwam namespace '$NAMESPACE'..."
kubectl delete namespace "$NAMESPACE" 2>/dev/null || true

info "Gotowe. Stack usuniety."
echo ""
echo "Jesli chcesz tez zatrzymac Minikube:"
echo "  minikube stop"
echo ""
echo "Jesli chcesz usunac Minikube calkowicie:"
echo "  minikube delete"
