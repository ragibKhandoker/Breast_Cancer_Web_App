# Breast Cancer Detection Web App

A user uploads a mammogram and gets an estimated malignancy probability. Results and images are saved in MongoDB Atlas.

```
Browser ──▶ Spring Boot (:8081)
              ├─ serves the Vue website
              ├─ /api/predictions  REST API
              ├─ ONNX Runtime ── ML-Service/weights/model.onnx
              └─ MongoDB Atlas (predictions collection + GridFS for images)

ML-Service (Python) is only for training and exporting the model, not for running the site.
```

## Architecture (MVC)

| Layer | Where | What it does |
|---|---|---|
| **Model** | `Backend/.../model/` (`Prediction`, `MlPrediction`), `Backend/.../repository/` | Data stored in MongoDB Atlas and the data access to it |
| **View** | `Frontend/src/views/` (pages), `Frontend/src/components/` (layout, home, prediction, common) | The Vue website the user sees |
| **Controller** | `Backend/.../controller/` (`PredictionController` REST API, `SpaController` page routes) | Receives requests and returns JSON or pages |
| Service | `Backend/.../service/` (`PredictionService`, `OnnxPredictor`) | Business logic and model inference, called by controllers |
| Supporting | `Backend/.../exception/`, `Backend/.../config/`, `Frontend/src/services/` (API calls), `Frontend/src/router/` | Error handling, CORS, startup report, HTTP client, page routing |

Flow: View → `services/predictionService.js` → Controller → Service → Repository/Model → MongoDB, and back.

> **Disclaimer:** For research and education only. This is not a medical device and does not replace a radiologist.

## Requirements

- Java 21 (JDK). No Maven install needed: use the bundled `mvnw.cmd`.
- Node.js 20+
- Python 3.10+ (only to train or export the model)
- A MongoDB Atlas cluster (the free M0 tier is enough)

## 1. Connect MongoDB Atlas

1. Create a free cluster at https://cloud.mongodb.com.
2. **Database Access**: add a database user with a password.
3. **Network Access**: add your current IP address (or `0.0.0.0/0` for development only).
4. **Connect → Drivers**: copy the connection string.
5. Copy `Backend/.env.example` to `Backend/.env` and paste the string. Put the database name `breast_cancer` after `.net/`:
   ```
   MONGODB_URI=mongodb+srv://user:pass@cluster0.xxxxx.mongodb.net/breast_cancer?retryWrites=true&w=majority&serverSelectionTimeoutMS=5000
   ```
   If the password contains special characters such as `@ : / ? #`, URL-encode them (for example `@` becomes `%40`).
   The `predictions`, `fs.files` and `fs.chunks` collections are created on the first upload.

## 2. The model (ONNX)

`ML-Service/weights/model.onnx` is what the backend loads. A demo version is already exported. It is untrained, so its numbers are random, and the website shows a "Demo mode" warning.

To get real predictions, train on mammograms (for example CBIS-DDSM). Use a GPU, such as Google Colab or Kaggle:

```powershell
cd ML-Service
python -m venv .venv
.\.venv\Scripts\activate
pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu   # or the CUDA build on a GPU
pip install -r requirements.txt

# data/train/{benign,malignant}/*.png and data/val/{benign,malignant}/*.png
python train.py --data data --epochs 15     # -> weights/model.pt
python export_onnx.py                       # -> weights/model.onnx
```

Restart the backend to load the new model. If you train on Colab, copy `model.onnx` into `ML-Service/weights/`.

## 3. Run the website

**Single server (how users use it):**

```powershell
cd Frontend
npm install
npm run build          # builds the site into Backend/src/main/resources/static

cd ..\Backend
.\mvnw.cmd spring-boot:run
```

Open **http://localhost:8081**.

**Development with hot reload:** run the backend as above, then run `npm run dev` in `Frontend` and open http://localhost:5173. Vite forwards `/api` to port 8081.

Port 8080 is not used, because Apache/XAMPP runs there on this machine. To change the port, set `SERVER_PORT` in `.env`.

## API

| Method | Path | Description |
|---|---|---|
| POST | `/api/predictions` | multipart field `image` (PNG or JPG, ≤ 50 MB) → prediction |
| GET | `/api/predictions` | history, newest first |
| GET | `/api/predictions/{id}` | one prediction |
| GET | `/api/predictions/{id}/image` | the stored image |
| DELETE | `/api/predictions/{id}` | delete the record and its image |
