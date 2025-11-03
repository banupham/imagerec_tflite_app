# ImageRecTFLite (CameraX + TFLite Task ImageClassifier)

Ứng dụng Android nhận dạng ảnh **offline** theo thời gian thực.

### Model được dùng (tự động tải khi build CI)
- TF Hub: **MobileNetV3 Small (ImageNet, classification)** — file TFLite có **metadata** tương thích `ImageClassifier`.
  - Trang: https://tfhub.dev/google/lite-model/imagenet/mobilenet_v3_small_100_224/classification/5/default/1
  - CI sẽ tải với `?lite-format=tflite` và lưu thành `app/src/main/assets/model.tflite`.

### Chạy local
1. Cài JDK 17 và Android Studio (SDK 34, build-tools 34.0.0).
2. Tải model:
   ```bash
   curl -L "https://tfhub.dev/google/lite-model/imagenet/mobilenet_v3_small_100_224/classification/5/default/1?lite-format=tflite" -o app/src/main/assets/model.tflite
   ```
3. Build & run trên thiết bị thật (Android 8+).

### CI (GitHub Actions)
- Workflow `.github/workflows/android.yml` sẽ:
  1) Cài JDK 17 + Android SDK.
  2) Cài Gradle 8.7 rồi sinh Gradle Wrapper.
  3) **Tải model** từ TFHub vào thư mục assets.
  4) Build `assembleDebug` và upload APK artifact.

### Lưu ý giấy phép
- Kiểm tra điều khoản/giấy phép của model trên TFHub trước khi phát hành bản thương mại.

---
Nếu muốn đổi model (ví dụ EfficientNet-Lite0 int8), thay URL tải trong workflow.