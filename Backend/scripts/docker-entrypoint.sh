#!/bin/sh
# Container start: make sure the trained model files are in /models, then start the backend.
#
# Hosts with a mounted disk (Docker compose, Kubernetes, a VM) put the files there themselves and this script only
# checks them. Hosts without one (Render) download them on every start from a private Hugging Face model repo:
#   HF_MODEL_REPO   e.g. your-name/breastcare-models   (the repo ML-Service/upload_weights.py filled)
#   HF_TOKEN        a read-only Hugging Face token (a secret on the host, never in Git)
# The files are the same ones the backend uses locally from ML-Service/weights; nothing is retrained or changed.
set -eu

MODELS_DIR=${MODELS_DIR:-/models}
FILES="model.onnx heatmap.onnx gate/gate.onnx gate/gate_metrics.json mammoscreen/crop.onnx mammoscreen/net0.onnx
ensemble/ensemble.json firstplace/firstplace_fold0.onnx final/final.json"

missing=""
for f in $FILES; do
  [ -s "$MODELS_DIR/$f" ] || missing="$missing $f"
done

if [ -n "$missing" ]; then
  if [ -z "${HF_MODEL_REPO:-}" ]; then
    # The rest of the site still works; analyses answer "not available" (503) until the files are there.
    echo "WARNING: model files missing in $MODELS_DIR:$missing" >&2
    echo "Starting without mammogram analysis. Mount the files, or set HF_MODEL_REPO (and HF_TOKEN) to download them." >&2
    exec java -jar /app/app.jar "$@"
  fi
  echo "Downloading model files from Hugging Face repo $HF_MODEL_REPO ..."
  for f in $missing; do
    mkdir -p "$MODELS_DIR/$(dirname "$f")"
    if [ -n "${HF_TOKEN:-}" ]; then
      curl -fsSL --retry 3 -H "Authorization: Bearer $HF_TOKEN" -o "$MODELS_DIR/$f.part" \
        "https://huggingface.co/$HF_MODEL_REPO/resolve/main/$f"
    else
      curl -fsSL --retry 3 -o "$MODELS_DIR/$f.part" "https://huggingface.co/$HF_MODEL_REPO/resolve/main/$f"
    fi
    mv "$MODELS_DIR/$f.part" "$MODELS_DIR/$f"
    echo "  $f ($(du -h "$MODELS_DIR/$f" | cut -f1))"
  done
fi

exec java -jar /app/app.jar "$@"
