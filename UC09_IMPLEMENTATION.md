# UC09 — Kiểm duyệt và hiệu chỉnh bản dịch/audio

**Cập nhật: 24/10/2026.** Đối chiếu [PRD gốc](./Nhom2_CNPM.docx), UC09 và F-03. Tài liệu này mô tả phần triển khai trong working tree; API UC09 đã hoạt động sau khi restart backend.

**Lưu ý 26/10/2026:** Các đoạn bên dưới nhắc Piper là ảnh chụp lịch sử. Cấu hình hiện hành mặc định dùng Edge-TTS tạo MP3; Google Cloud TTS Standard tạo WAV chỉ khi chọn rõ. Xem [hướng dẫn pipeline mới](./PIPELINE_VERSIONING_IMPLEMENTATION.md#edge-tts--26102026).

## Phạm vi theo PRD

UC09 cho Admin chọn POI/ngôn ngữ, đọc và nghe nội dung hiện có, hiệu chỉnh và lưu bản dịch, hoặc yêu cầu tạo lại audio từ bản dịch đã lưu. Phần xử lý chạy nền, hiển thị tình trạng/lỗi, cho phép thử lại và không làm lại các ngôn ngữ khác. Dữ liệu thiếu phải được chỉ rõ.

PRD không yêu cầu thêm bước phê duyệt xuất bản riêng; không thêm cờ approval hay schema mới. Đây là màn thao tác nội dung Admin, không thay UC08 tự dịch/TTS khi lưu mô tả gốc. Phân quyền và bảo vệ API được giữ ở phạm vi đã hoãn trước đó, không được hiểu là đã hoàn tất an toàn xác thực.

## Cách dùng

1. Backend UC09 đã được restart và API đang hoạt động. Khi cập nhật mã UC09 về sau, chạy/restart backend từ `D:\voice_map_cnpm2` bằng `\.\mvnw.cmd spring-boot:run`; frontend tại `http://localhost:5174`.
2. Trong Admin chọn **Translations & Audio**. Mở trực tiếp `/admin/translations` nếu đang đứng ở trang khác.
3. Chọn POI, rồi chọn mã ngôn ngữ ở danh sách trái. Danh sách POI đọc từ `GET /api/admin/pois`; chi tiết chỉ tải khi chọn POI.
4. Mở phần mô tả gốc để đối chiếu. Có thể nghe file đang lưu hoặc nghe nháp bằng giọng của trình duyệt. Nghe thử Admin không gửi usage event.
5. Sửa văn bản rồi **Lưu bản dịch & tạo lại audio**. Backend lưu riêng translation của ngôn ngữ đó, đánh dấu `manualEdit`, xóa tham chiếu file cũ và đưa một task `TTS_ONLY` vào xử lý sau commit. Màn hình báo “đã nhận/đang xử lý”, không khẳng định audio hoàn tất trước khi worker trả kết quả.
6. **Tạo lại audio** chỉ dùng được khi không có thay đổi nháp; worker lấy văn bản đã lưu. Task lỗi vẫn giữ translation để thử lại sau khi khắc phục provider.
7. Tải lại trang để đọc lại trạng thái backend. Bản dịch bền vững trong MySQL; audio được lưu thành file có URL phiên bản mới.

Thay đổi chưa lưu được giữ khi polling; màn hình hỏi trước khi bỏ khi đổi POI/ngôn ngữ, tải lại, quay lại/đăng xuất. Bản sửa từ client khác được nhận biết bằng revision để tránh ghi đè. Giới hạn văn bản là 60.000 byte UTF-8.

## API và dữ liệu

| Method và path | Chức năng |
|---|---|
| `GET /api/admin/pois` | Danh sách POI cho bộ chọn (API hiện hữu) |
| `GET /api/admin/pois/{poiId}/translations` | Tóm tắt bản dịch, audio, provider và task theo tất cả ngôn ngữ |
| `GET /api/admin/pois/{poiId}/translations/{code}` | Chi tiết một ngôn ngữ |
| `PUT /api/admin/pois/{poiId}/translations/{code}` | Lưu nội dung đã sửa; body `{ "content": "...", "revision": "..." }`; trả `202 Accepted` khi đã nhận task |
| `POST /api/admin/pois/{poiId}/translations/{code}/audio` | Tạo lại từ nội dung đã lưu; body `{ "revision": "..." }`; trả `202 Accepted` |

Trạng thái task `PENDING → PROCESSING → COMPLETED/FAILED`. Audio không hỗ trợ TTS dùng `TEXT_ONLY`. Ngôn ngữ không hoạt động chỉ xem được; ngôn ngữ chưa có translation chưa cho hiệu chỉnh. `409` cho nội dung cũ hoặc đang có task trùng; `400` cho dữ liệu/ngôn ngữ không hợp lệ; lỗi lưu kho dữ liệu trả `503` kèm thông báo giữ bản nháp. Worker retry tối đa ba lần với lỗi mạng/timeout/408/429/5xx và backoff; lỗi cấu hình hoặc HTTP 4xx không retry vô ích. Không lưu đường dẫn giả khi provider lỗi.

Revision là fingerprint của nội dung và trạng thái translation, không dựa vào độ chính xác timestamp DB. POI được khóa trong lúc cập nhật/nạp snapshot công việc để điều phối với pipeline đầy đủ và sửa mô tả gốc. Task được phát sau commit. Khi service khởi động lại, task UC09 dở của tiến trình đơn được đánh dấu lỗi có thể thử lại; xử lý lease đa instance không nằm trong phạm vi. Pipeline toàn phần cũng khôi phục task `TRANSLATE_TTS` đang `PROCESSING` khi khởi động lại. Nếu TTS-only cho bản dịch Admin sửa tay đã thất bại, pipeline UC08 không tự xếp lại task; Admin chủ động thử lại trong UC09 để tránh vòng lỗi tự động.

TTS tạo WAV PCM có đường dẫn UUID mới; Google TTS chia văn bản theo giới hạn byte rồi ghép các frame PCM vào một WAV hoàn chỉnh; Piper đọc UTF-8 và bị giới hạn thời gian. Cấu hình optional qua `GOOGLE_CLOUD_API_KEY` hoặc `PIPER_EXECUTABLE`, `PIPER_MODEL`, `PIPER_LANGUAGE`; thư mục file qua `AUDIO_STORAGE`. Không có provider không biến thành “đã tạo file”: bản sửa vẫn lưu, task hiện lỗi cụ thể, Admin vẫn có thể nghe thử bằng giọng trình duyệt nếu ngôn ngữ/thiết bị hỗ trợ. Dịch vụ TTS thật chưa được kiểm chứng trong môi trường của người dùng ở lượt này.

Guide phía khách ưu tiên translation DB hoàn tất và audio DB hoàn tất cho ngôn ngữ chọn; thay đổi UC09 không kick off pipeline khi khách GET. Built-in narration hiện chỉ là fallback cho một số POI cụ thể khi DB chưa có nội dung phù hợp, không ghi đè bản sửa DB.

## File chính

- [TranslationReview.jsx](./voiceover-frontend/src/TranslationReview.jsx), [TranslationReview.css](./voiceover-frontend/src/TranslationReview.css): giao diện, lựa chọn, polling, draft guard và nghe thử.
- [AdminPortal.jsx](./voiceover-frontend/src/AdminPortal.jsx): menu Admin và điều hướng tới UC09.
- [TranslationReviewController.java](./src/main/java/com/voiceover/backend/controllers/TranslationReviewController.java), [TranslationReviewService.java](./src/main/java/com/voiceover/backend/services/TranslationReviewService.java): hợp đồng API và giao dịch nội dung.
- [ReviewAudioListener.java](./src/main/java/com/voiceover/backend/services/ReviewAudioListener.java), [ReviewAudioWorker.java](./src/main/java/com/voiceover/backend/services/ReviewAudioWorker.java), [AudioGenerationService.java](./src/main/java/com/voiceover/backend/services/AudioGenerationService.java): dispatch sau commit, xử lý/retry, sinh file audio.
- [PoiService.java](./src/main/java/com/voiceover/backend/services/PoiService.java), [PipelineService.java](./src/main/java/com/voiceover/backend/services/PipelineService.java): phục vụ narration cho khách và hợp tác với UC08.

## Kiểm chứng và giới hạn

- Backend: test tập trung pipeline/UC09/audio chạy 23 test, 0 lỗi; gồm dịch với source language khác vi và retry HTTP 503, nhận diện lỗi transient/non-retryable, kiểm file audio tồn tại, retry worker/WAV, nội dung Unicode dài, ghép WAV hợp lệ, provider thiếu và text-only.
- Frontend production build thành công; 24 test GPS/bộ phát/UC03 qua. Lint trước lượt cuối không có lỗi mới của UC09; còn 5 warning cũ trong demo không mount.
- UI đã thử trên fixture cô lập: có draft giữ khi lỗi lưu/409/poll, save chỉ ghi một ngôn ngữ, audio file dùng URL mới, regenerate dùng revision không gửi nội dung nháp, task lỗi có thể thử lại, trạng thái thiếu và text-only đúng; nghe thử không ghi playback event. Đây là mock API, không phải nghiệm thu MySQL.
- Sau khi restart, API thật trả 200 cho danh sách POI và UC09 index của cả 6 POI; mỗi POI có 16 bản dịch COMPLETED. Trang /admin/translations hiển thị đủ POI, nội dung và ngôn ngữ. Đã xác nhận đọc lại dữ liệu MySQL có sẵn; chưa lưu bản sửa mới để giữ nguyên dữ liệu demo.
- Snapshot thật trước lượt sửa: 14 ngôn ngữ có cờ TTS nhưng provider UNAVAILABLE; 2 ngôn ngữ không hỗ trợ TTS. Audio của cả 16 ngôn ngữ tại cả 6 POI là TEXT_ONLY, chưa có file TTS. Cần cấu hình Google Cloud hoặc Piper để nghiệm thu sinh file mới; provider thật chưa được kiểm chứng trong lượt sửa logic này. Khi kiểm tra môi trường ngày 24/10, không tìm thấy các biến cấu hình provider hoặc file `.env`.
