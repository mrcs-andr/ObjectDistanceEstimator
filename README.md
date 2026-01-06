# ObjectDistanceEstimator

ObjectDistanceEstimator is an Android application focused on real-time object detection and distance estimation using computer vision and deep learning. The project is designed to run fully on-device, targeting modern Android smartphones, and serves as a foundation for mobile perception systems similar to simplified ADAS (Advanced Driver Assistance Systems).

The application captures live camera frames, detects objects (with an initial focus on vehicles), and estimates their distance relative to the camera, displaying the results as an overlay in real time.

---

## 🚗 Features

* Real-time camera capture using **CameraX**
* Object detection using **YOLO** models exported to **ONNX**
* On-device inference with **ONNX Runtime** (CPU / NNAPI)
* Distance estimation relative to the camera
* Bounding box and distance overlay on live video
* Fully offline operation (no network required)

---

## 🛠️ Tech Stack

* **Android** (Java)
* **CameraX** for real-time image capture
* **ONNX Runtime Mobile** for neural network inference
* **YOLO** (ONNX format)
* **XML-based UI** (no Jetpack Compose)
* **Gradle** build system

---

## 📐 Architecture Overview

```
CameraX
   ↓
ImageAnalysis
   ↓
Pre-processing (resize, normalize)
   ↓
ONNX Runtime (YOLO inference)
   ↓
Post-processing (confidence threshold, NMS)
   ↓
Distance Estimation
   ↓
Overlay Rendering (bounding boxes + distance)
```

The architecture is modular, allowing easy replacement of the detection model, distance estimation logic, or tracking algorithms.

---

## 📱 Requirements

* Android Studio (Giraffe or newer recommended)
* Android SDK 24+
* Physical Android device recommended for best performance

---

## 🚀 Getting Started

1. Clone the repository:

   ```bash
   git clone https://github.com/<your-username>/ObjectDistanceEstimator.git
   ```

2. Open the project in **Android Studio**

3. Sync Gradle dependencies

4. Connect an Android device and run the app

---

## 📦 Model

* The object detection model is a **YOLO-based network** exported to **ONNX**.
* Typical input size: `640 x 640`
* The model runs fully on-device using ONNX Runtime.

> Note: The ONNX model file is not included in the repository by default.

---

## 📏 Distance Estimation

Distance estimation is based on monocular vision assumptions and camera parameters. The implementation is designed to be extensible, allowing future integration of:

* Camera calibration
* Object tracking (e.g. SORT, ByteTrack)
* Sensor fusion (IMU, depth sensors)

---

## 🔬 Use Cases

* Mobile computer vision research
* ADAS prototyping
* Embedded and real-time AI experimentation
* Educational projects in computer vision

---

## 🧭 Roadmap

* [ ] CameraX preview and analysis pipeline
* [ ] YOLO ONNX inference integration
* [ ] Bounding box rendering
* [ ] Distance estimation refinement
* [ ] Object tracking
* [ ] Performance optimization (NNAPI / quantization)

---

## 📄 License

This project is intended for research and educational purposes. License information can be added as needed.

---

## 👤 Author

**Marcos Antonio Andrade**
Software Engineer | Computer Vision | Embedded Systems
