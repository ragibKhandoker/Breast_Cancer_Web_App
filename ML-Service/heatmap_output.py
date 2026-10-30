"""Make weights/heatmap.onnx: a copy of the ResNet50 (weights/model.onnx) with a class activation map (CAM) output.

Run: python heatmap_output.py           reads weights/model.onnx (never changes it), writes weights/heatmap.onnx

The website's score still comes from model.onnx exactly as before; the backend runs heatmap.onnx only to draw the
heatmap on the report page. Run this again whenever model.onnx is replaced.

The ResNet ends in global average pooling and one linear layer, so the classifier applied to every cell of the
last feature map gives each region's share of the final logits (Zhou et al., 2016, "Learning Deep Features for
Discriminative Localization"). That is a 1x1 convolution with the linear layer's weights; its spatial mean is
exactly the logits the model already returns, so the map explains this model's score and needs no gradients.

Output "heatmap": [1, 2, H/32, W/32] per-cell logits for [benign, malignant]. The backend shows
malignant - benign: positive cells pushed the result towards malignant. The script checks that the map's mean
reproduces the model's probabilities before saving.
"""
import numpy as np
import onnx
from onnx import helper, numpy_helper

OUTPUT = "heatmap"


def add_heatmap_output(model: onnx.ModelProto) -> bool:
    """Adds the output to the graph; False if it is already there."""
    graph = model.graph
    if any(o.name == OUTPUT for o in graph.output):
        return False
    producers = {out: node for node in graph.node for out in node.output}
    initializers = {init.name: init for init in graph.initializer}

    # Probabilities <- Softmax <- Gemm(view, fc.weight, fc.bias) <- Reshape <- ReduceMean(features)
    gemm = next(n for n in graph.node if n.op_type == "Gemm")
    node = producers[gemm.input[0]]
    while node.op_type in ("Reshape", "Flatten", "Squeeze"):
        node = producers[node.input[0]]
    if node.op_type not in ("ReduceMean", "GlobalAveragePool"):
        raise SystemExit(f"Expected global average pooling before the classifier, found {node.op_type}")
    features = node.input[0]
    transposed = any(a.name == "transB" and a.i == 1 for a in gemm.attribute)
    weight = numpy_helper.to_array(initializers[gemm.input[1]])
    weight = weight if transposed else weight.T  # [classes, channels]
    bias = numpy_helper.to_array(initializers[gemm.input[2]])

    graph.initializer.extend([
        numpy_helper.from_array(weight[:, :, None, None].astype(np.float32), "heatmap.weight"),
        numpy_helper.from_array(bias.astype(np.float32), "heatmap.bias"),
    ])
    graph.node.append(helper.make_node("Conv", [features, "heatmap.weight", "heatmap.bias"], [OUTPUT],
                                       name="heatmap_conv", kernel_shape=[1, 1]))
    graph.output.append(helper.make_tensor_value_info(OUTPUT, onnx.TensorProto.FLOAT, None))
    return True


def check(path) -> None:
    """The map's mean must give back the model's own probabilities."""
    import onnxruntime as ort

    session = ort.InferenceSession(str(path), providers=["CPUExecutionProvider"])
    shape = [d if isinstance(d, int) else 1 for d in session.get_inputs()[0].shape]
    image = np.random.default_rng(0).random(shape, dtype=np.float32)
    probabilities, heatmap = session.run(["probabilities", OUTPUT], {"image": image})
    logits = heatmap.mean(axis=(2, 3))
    softmax = np.exp(logits - logits.max()) / np.exp(logits - logits.max()).sum()
    if not np.allclose(softmax, probabilities, atol=1e-4):
        raise SystemExit(f"heatmap check failed: {softmax} vs {probabilities}")
    print(f"heatmap output {list(heatmap.shape)} reproduces the model's probabilities")


def main():
    from model import WEIGHTS_PATH

    source = WEIGHTS_PATH.with_suffix(".onnx")
    target = WEIGHTS_PATH.with_name("heatmap.onnx")
    if not source.exists():
        raise SystemExit(f"{source} not found. Run export_onnx.py first.")
    model = onnx.load(source)
    add_heatmap_output(model)
    temporary = target.with_suffix(".tmp")
    onnx.save(model, temporary)
    try:
        check(temporary)
    except BaseException:
        temporary.unlink()
        raise
    temporary.replace(target)
    print(f"wrote {target} from {source} ({source.name} is unchanged)")


if __name__ == "__main__":
    main()
