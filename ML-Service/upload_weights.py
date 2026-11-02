"""Upload the trained files the backend needs at run time to a PRIVATE Hugging Face model repo.

The deployed backend (Backend/scripts/docker-entrypoint.sh) downloads exactly these files on start, so the server
does not depend on this laptop. Only the inference files go up: no datasets, no training checkpoints.

    pip install huggingface_hub
    hf auth login                       # paste a token with "write" access (huggingface.co/settings/tokens)
    python upload_weights.py your-name/breastcare-models
"""
import sys
from pathlib import Path

from huggingface_hub import HfApi

WEIGHTS = Path(__file__).parent / "weights"
# Same list as FILES in Backend/scripts/docker-entrypoint.sh.
FILES = [
    "model.onnx",
    "heatmap.onnx",
    "gate/gate.onnx",
    "gate/gate_metrics.json",
    "mammoscreen/crop.onnx",
    "mammoscreen/net0.onnx",
    "ensemble/ensemble.json",
    "firstplace/firstplace_fold0.onnx",
    "final/final.json",
]


def main() -> None:
    if len(sys.argv) != 2 or "/" not in sys.argv[1]:
        sys.exit("usage: python upload_weights.py <hf-user>/<repo-name>")
    repo = sys.argv[1]
    missing = [f for f in FILES if not (WEIGHTS / f).is_file()]
    if missing:
        sys.exit(f"missing in {WEIGHTS}: {', '.join(missing)}")

    api = HfApi()
    api.create_repo(repo, repo_type="model", private=True, exist_ok=True)
    if not api.repo_info(repo, repo_type="model").private:
        sys.exit(f"{repo} is public. Make it private on huggingface.co before uploading the weights.")
    total = sum((WEIGHTS / f).stat().st_size for f in FILES)
    print(f"Uploading {len(FILES)} files ({total / 1e6:.0f} MB) to the private repo {repo} ...")
    api.upload_folder(repo_id=repo, repo_type="model", folder_path=WEIGHTS, allow_patterns=FILES,
                      commit_message="Trained model files for the BreastCare backend")
    print(f"Done: https://huggingface.co/{repo}")


if __name__ == "__main__":
    main()
