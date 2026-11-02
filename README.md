<div align="center">

# 🩺 BreastCare

**Breast Cancer Detection Web App**

![Vue](https://img.shields.io/badge/Frontend-Vue%203-42b883?logo=vuedotjs&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Backend-Spring%20Boot%204-6db33f?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)
![MongoDB](https://img.shields.io/badge/Database-MongoDB%20Atlas-47a248?logo=mongodb&logoColor=white)
![ONNX](https://img.shields.io/badge/ML-ONNX%20Runtime-005ced?logo=onnx&logoColor=white)

[🌐 Live Frontend](https://breastcare-fawn.vercel.app) · [⚙️ Live Backend](https://breastcare-backend-skx9.onrender.com)

</div>

A patient signs in, uploads a mammogram and gets an estimated malignancy probability. Results and images are stored in MongoDB Atlas and are visible only to the patient and the doctors they choose to share them with.

> [!WARNING]
> For research and education only. This is not a medical device and does not replace a radiologist.

## 📑 Table of Contents

- [Features](#-features)
- [Tech Stack](#-tech-stack)
- [Architecture](#-architecture)
- [Roles](#-roles)
- [The Model](#-the-model)
- [Getting Started](#-getting-started)
- [Security](#-security)
- [Tests](#-tests)
- [Deployment Guides](#-deployment-guides)

## ✨ Features

- **Mammogram analysis:** upload a CC or MLO view (PNG/JPEG) and get Benign/Malignant with probabilities, a risk category and a heatmap viewer.
- **Downloadable PDF report** for every scan.
- **Role-based accounts:** Patient, Doctor and Admin.
- **Doctor care flow:** patients share reports and request reviews; doctors can run detection for their patients.
- **Admin console:** user management, site overview, contact inbox, audit logs and encrypted backups.
- **Knowledge Hub:** 13 learning modules on breast cancer, with diagrams, a BI-RADS guide, glossary and FAQ.
- **Chatbot:** BreastCare Assistant answers breast-health questions (Knowledge Hub by default, optional Google Gemini).

## 🛠 Tech Stack

| Part | Technology |
|---|---|
| Frontend | Vue 3, Vite |
| Backend | Spring Boot 4, Java 21, Spring Security (JWT in HttpOnly cookie) |
| Database | MongoDB Atlas (GridFS for images) |
| ML inference | ONNX Runtime (CPU) |
| ML training | Python, PyTorch (Kaggle), used only to train and export models |
| Deployment | Vercel, Render, Docker, Kubernetes, GitHub Actions |

## 🏗 Architecture

```text
Browser ──▶ Spring Boot (:8081)
              ├─ serves the Vue website
              ├─ Spring Security: roles PATIENT / DOCTOR / ADMIN
              ├─ REST API (/api/auth, /api/predictions, /api/admin, /api/doctors ...)
              ├─ ONNX Runtime ── ML-Service/weights/*.onnx
              └─ MongoDB Atlas
```

The project follows MVC: **View** (`Frontend/src/views`), **Controller** (`Backend/.../controller`), **Model** (`Backend/.../model`, `repository`), plus Service and Security layers.

## 👥 Roles

| Role | Created by | Can |
|---|---|---|
| **Patient** | Sign up at `/register` | Upload mammograms; view, delete and share own reports |
| **Doctor** | Admin creates it or changes a user's role | View reports shared with them and write reviews |
| **Admin** | `ADMIN_EMAIL` / `ADMIN_PASSWORD` on first start | Manage users and the site; cannot read individual medical records |

## 🤖 The Model

The server runs a chain of checks and models on CPU (about 6 seconds per image):

1. **Colour check:** colour photos are refused (mammograms are grayscale).
2. **Mammogram gate:** refuses anything that is not a mammogram.
3. **Cancer estimate:** an ensemble of a fine-tuned ResNet50 (CBIS-DDSM), mammoscreen net0 and the RSNA 2023 1st-place model.
4. **Result:** *Malignant* if the final estimate is 50% or more, otherwise *Benign*.

On unseen INbreast patients the final combination reached **ROC-AUC 0.948** (BI-RADS 5-6 vs 1-3). INbreast is small, so the intervals are wide, and the estimate still misses cancers and raises false alarms.

> [!NOTE]
> Model files are git-ignored (about 390 MB) and must be copied to the server. Training notebooks are in `ML-Service/`.

## 🚀 Getting Started

### Requirements

- Java 21 (use the bundled `mvnw` / `mvnw.cmd`)
- Node.js 20+
- Python 3.10+ (only to train or export models)
- A MongoDB Atlas cluster (free M0 is enough)

### 1. Configure the backend

Copy `Backend/.env.example` to `Backend/.env` and set:

| Variable | Required | Purpose |
|---|---|---|
| `MONGODB_URI` | yes | Atlas connection string including database name `breast_cancer` |
| `JWT_SECRET` | yes | 48+ random characters, signs login tokens |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | first run | Creates the first administrator |
| `AUTH_COOKIE_SECURE` | production | Set `true` on HTTPS |
| `CORS_ORIGINS` | optional | Allowed frontend origins |
| `GEMINI_API_KEY` | optional | Enables Gemini answers in the chatbot |

### 2. Run (single server)

```powershell
cd Frontend
npm install
npm run build

cd ..\Backend
.\mvnw.cmd spring-boot:run
```

Open **http://localhost:8081**.

### Development with hot reload

Run the backend as above, then in `Frontend` run `npm run dev` and open http://localhost:5173.

## 🔒 Security

- Passwords hashed with BCrypt; login via JWT in an `HttpOnly`, `SameSite=Strict` cookie.
- CSRF protection on all POST/PATCH/DELETE requests.
- Logout revokes the token immediately.
- Every record lookup is filtered by owner or share list.
- Append-only, hash-chained audit log with encrypted backups.

> [!IMPORTANT]
> Known limits: no login rate limiting, password reset or email verification yet.

## 🧪 Tests

```powershell
cd Backend
.\mvnw.cmd test

cd ..\Frontend
npm test
```

## 📚 Deployment Guides

See `Backend/docs/` for Docker, Kubernetes, CI/CD, blockchain and audit/backup/privacy setup.
