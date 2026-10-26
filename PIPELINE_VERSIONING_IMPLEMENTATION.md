# Pipeline có phiên bản và task bền vững — 25/10/2026

## Phạm vi đã triển khai

Đợt này xử lý bước đầu của [backlog khảo sát](./BAO_CAO_DOI_CHIEU_PRD_2026-10-25.md): tính đúng của nguồn, task và kết quả UC08/UC09. Không đánh dấu toàn bộ pipeline/provider/audio/mobile hoặc các P0 khác đã hoàn tất.

- POI có `contentRevision` tăng khi fingerprint thay đổi. Fingerprint gồm mô tả gốc, mã ngôn ngữ gốc và danh sách protected terms chuẩn hóa; không phụ thuộc thứ tự đọc terms từ DB.
- Lưu POI, vô hiệu kết quả cũ và tạo task cho các ngôn ngữ cần xử lý nằm trong **cùng transaction**. Không còn chỉ dựa vào callback sau commit để tạo task.
- Task giữ snapshot nguồn/ngôn ngữ/terms, source revision/hash và revision bản dịch. Task cũ được kết thúc với thông báo đã thay thế khi nguồn đổi; yêu cầu cho nguồn mới vẫn được lưu, kể cả khi worker cũ còn đang gọi provider.
- Mỗi lần nhận task có claim token và lease 60 giây. Worker gia hạn mỗi 10 giây. Dispatcher poll DB mặc định mỗi giây, kể cả khi sự kiện sau commit bị mất hoặc executor đầy. Task PROCESSING hết lease có thể được nhận lại với token khác.
- Trước lưu translation, trước gọi lại provider, và trước hoàn tất/lưu lỗi audio, server kiểm token, nguồn còn hiện hành và task mới nhất. A → B → A vẫn là ba revision; hash giống nhau không cho worker A đầu tiên ghi kết quả.
- Lần gọi dịch/TTS được đếm riêng và lưu trước khi gọi provider; tối đa 3 lần mỗi bước, kể cả qua nhận lại task. Bản dịch đã lưu thành công được dùng lại khi chỉ còn bước audio.
- Bản dịch sửa tay được giữ khi nguồn đổi nhưng chuyển `STALE`, audio cũ bỏ tham chiếu. Admin rà soát rồi lưu lại; có thể giữ nguyên câu chữ để xác nhận. Không tự ghi đè bản sửa tay bằng máy dịch.
- Thiếu cấu hình TTS cho ngôn ngữ có hỗ trợ TTS là `FAILED`, giữ bản dịch và lỗi cụ thể. `TEXT_ONLY` dành cho ngôn ngữ bị tắt TTS trong DB hoặc không có giọng được ánh xạ trong provider đang chọn. Nghe chữ bằng browser vẫn có thể dùng nếu thiết bị hỗ trợ, nhưng không được coi là đã sinh file.
- Guide của POI đã có revision không lấy translation khác fingerprint hoặc built-in cũ. Guide đọc trong snapshot transaction để không ghép text cũ với metadata audio mới. API nearby chỉ đọc và lọc bán kính, không còn sửa mô tả/ảnh, seed hoặc kích hoạt pipeline.

## Điểm vào code

| Thành phần | File |
|---|---|
| Nguồn + task trong transaction, kiểm revision request, guide/nearby | [PoiService](./src/main/java/com/voiceover/backend/services/PoiService.java) |
| Fingerprint nguồn và terms | [ContentFingerprint](./src/main/java/com/voiceover/backend/services/ContentFingerprint.java) |
| Lập task, khóa POI, nhận/gia hạn task, kiểm trước ghi kết quả, UC09 | [TranslationReviewService](./src/main/java/com/voiceover/backend/services/TranslationReviewService.java) |
| Gọi dịch/TTS, retry và gia hạn lease | [PipelineService](./src/main/java/com/voiceover/backend/services/PipelineService.java) |
| Dispatch sự kiện và poll DB | [PipelineDispatcher](./src/main/java/com/voiceover/backend/services/PipelineDispatcher.java) |
| Xóa riêng file vừa sinh bị từ chối vì task cũ | [AudioGenerationService](./src/main/java/com/voiceover/backend/services/AudioGenerationService.java) |
| Giao diện STALE, xác nhận lại bản sửa tay | [TranslationReview](./voiceover-frontend/src/TranslationReview.jsx) |
| Schema bổ sung | [V14](./src/main/resources/db/migration/V14__versioned_content_jobs.sql) |

Worker/listener UC09 cũ được thay bằng worker/dispatcher dùng chung; không chạy hai đường xử lý song song.

## Edge-TTS — 26/10/2026

`TTS_PROVIDER=edge` là mặc định, tạo MP3 qua [edge-tts](https://github.com/rany2/edge-tts) trong helper Python [edge_tts_worker.py](./scripts/edge_tts_worker.py). Đây là thư viện bên thứ ba dùng dịch vụ online của Microsoft Edge; dịch vụ/giọng có thể bị giới hạn hoặc thay đổi. Không cần Google Cloud key hay billing cho nhánh này. `TTS_PROVIDER=google-standard` là lựa chọn riêng, vẫn tạo WAV bằng `voice.name` Standard cố định theo [danh sách giọng Google](https://cloud.google.com/text-to-speech/docs/list-voices-and-types); không tự chuyển sang provider khác hoặc gọi Google khi đang chọn Edge. Piper đã bỏ.

Trên Windows, từ thư mục dự án:

```powershell
python -m venv .venv-edge-tts
.\.venv-edge-tts\Scripts\python.exe -m pip install -r requirements-edge-tts.txt
$env:TTS_PROVIDER = 'edge'
# Nếu dùng Python ở chỗ khác, đặt EDGE_TTS_PYTHON thành đường dẫn tới executable trong venv.
.\mvnw.cmd spring-boot:run
```

Backend gọi Python với file nguồn UTF-8 tạm, timeout 90 giây (`EDGE_TTS_TIMEOUT_SECONDS`), nhận MP3 và thời lượng do `mutagen` kiểm tra. File được chuyển atomically vào `AUDIO_STORAGE`; bản dịch/task/audio vẫn dùng revision, retry và kiểm kết quả cũ như trước. Mã ngôn ngữ không có giọng trong ánh xạ provider hiện hành dùng `TEXT_ONLY`; thiếu Python/helper hoặc lỗi online được báo `FAILED`, giữ bản dịch. Migration [V15](./src/main/resources/db/migration/V15__enable_hindi_malay_tts.sql) đã bật `tts_supported` cho `ms`/`hi` mà không sửa migration cũ.

Phép thử độc lập và Java worker đã tạo MP3 vi/en thật ngày 26/10/2026. Một yêu cầu dịch độc lập từng nhận HTTP 429, nhưng luồng POI #7 sau đó dịch tiếng Anh thành công và tạo MP3 thật cho vi/en. Dịch vụ không key là endpoint không chính thức, nên kết quả có thể thay đổi hoặc bị giới hạn; không dùng nội dung giả để thay bản dịch thật.

Sau V15, Admin dùng API tạo lại audio riêng cho `hi` và `ms` trên POI DRAFT #7. Hai bản dịch đã lưu được giữ nguyên; task #134/#135 và audio đều `COMPLETED`, file MP3 được kiểm tra bằng `mutagen` và HTTP 200 `audio/mpeg`. Đối chiếu cả 16 ngôn ngữ của POI #7: bản dịch và MP3 đều `COMPLETED`, file có thời lượng và URL phục vụ được. Giao diện Admin hiển thị tên tiếng Anh từ mapping dùng chung với giao diện khách; trình phát file của Admin đã khởi chạy cho Hindi và Malay, chưa kiểm chứng bằng nghe trực tiếp.

## API và tương thích

Các path API hiện tại được giữ nguyên. GET POI và kết quả lưu POI bổ sung `contentRevision`.

- Khi cập nhật POI, client gửi lại `contentRevision` đã đọc. Nếu nguồn đã đổi, server trả `409 CONTENT_CONFLICT`; nội dung cũ không ghi đè nguồn mới. Form Admin đang có đã giữ trường này trong request; sau chấp nhận AI cũng cập nhật revision trả về.
- UC09 tiếp tục dùng `revision` của bản dịch. DTO có thêm source revision của POI/task; mã trạng thái translation bổ sung `STALE`.
- `PUT /api/admin/pois/{id}/translations/{code}` cho phép rà soát/lưu bản STALE và tạo task audio của đúng ngôn ngữ đó.
- `POST .../translations/{code}/audio` chỉ nhận bản dịch COMPLETED đúng fingerprint nguồn hiện tại; không dùng bản STALE hoặc nội dung nháp.
- Thay protected terms được ghi nhận khi lưu lại POI. Đợt này chưa thêm CRUD terms hoặc tự quét mọi POI khi sửa danh mục terms.
- Các cờ trạng thái task vẫn là PENDING/PROCESSING/COMPLETED/FAILED. Task bị thay thế dùng FAILED kèm lý do rõ, không được diễn giải thành lỗi provider.

## Áp dụng trên database đang dùng

**Cập nhật 26/10/2026:** backend đã lưu POI #7 với `contentRevision=1`, tạo task phiên bản hóa và hoàn tất pipeline trên database đang dùng. Điều này xác nhận schema V14 cần cho luồng đã có hiệu lực; chưa đọc trực tiếp bảng lịch sử Flyway để xác nhận bản ghi migration. Các bước dưới đây là hướng dẫn cho môi trường khác hoặc khi triển khai lại.

1. Dừng các backend bản cũ trước khi triển khai; không để worker cũ tiếp tục ghi dữ liệu trong lúc nâng cấp.
2. Chạy Flyway V14 theo lịch sử migration hiện có rồi khởi động bản mới. Nếu môi trường tắt Flyway, cần áp dụng đúng migration mới theo quy trình quản lý schema của môi trường đó.
3. POI cũ giữ revision 0 và dữ liệu đã có. V14 chỉ đánh FAILED các task cũ còn PENDING/PROCESSING vì chúng không có snapshot đáng tin cậy; không xóa text/audio hoàn tất.
4. Lưu lại một POI để khởi tạo revision đầu tiên và task có snapshot. Các bản máy dịch được xử lý theo fingerprint mới; bản sửa tay chuyển STALE để rà soát. UC09 với POI chưa có fingerprint sẽ yêu cầu lưu POI trước, thay vì đoán phiên bản của dữ liệu cũ.
5. Cấu hình provider và kho file trước nghiệm thu audio. Không có provider thì task TTS báo lỗi rõ; không tự xuất bản hoặc thêm approval gate trong đợt này.

## Kiểm chứng đã thực hiện

- **Backend: clean test, 78/78 qua**, 0 failure/error/skipped; gồm 73 test unit/HTTP stub và 5 test tích hợp MySQL thật.
- **MySQL 8.0.42 riêng trên localhost:13306:** chạy schema sạch đến V13, chèn dữ liệu legacy kiểm thử rồi nâng V14; dữ liệu manual/audio cũ giữ nguyên trước thao tác nguồn; STALE và xác nhận lại lưu được dưới CHECK/FK thật.
- Tạo POI có đủ 16 task PENDING; rollback tạo POI không để lại nguồn/task/sự kiện sau commit; rollback cập nhật nguồn khôi phục task cũ.
- Hai luồng cùng nhận một task chỉ có một owner. Sau cập nhật nguồn, kết quả owner cũ không ghi được.
- Đóng/mở lại Spring context với dữ liệu MySQL đã lưu và lease hết hạn: nhận lại task bằng token mới, dùng bản dịch đã lưu để tiếp tục audio. Đây là kiểm phục hồi context/DB; chưa phải thử kill JVM hoặc soak test đa instance dài hạn.
- Test worker có HTTP stub cho source language, protected term và 503; retry TTS, provider thiếu, task cũ trả file muộn và hết budget sau khôi phục.
- **Frontend:** production build qua; 29/29 test logic qua; lint thoát 0, còn 5 warning cũ trong màn demo không mount. Chưa nghiệm thu UI trên thiết bị thật.
- Chưa gọi Google/Gemini thật trong lượt này. Test MySQL dùng kết quả audio giả để kiểm transaction, không dùng nó làm bằng chứng chất lượng/file TTS provider thật. Generator WAV có bộ test HTTP stub/file riêng.

Test mặc định không cần MySQL:

```powershell
.\mvnw.cmd -o -B clean test
```

Test MySQL bật riêng:

```powershell
# Tài khoản cần quyền tạo/xóa DATABASE KIỂM THỬ; test tự dùng tên ngẫu nhiên voicemap_pipeline_test_*.
# URL chỉ chứa địa chỉ server; truyền thông tin truy cập qua biến môi trường của terminal.
$env:PIPELINE_TEST_MYSQL_URL = 'jdbc:mysql://127.0.0.1:3306/?useSSL=false&allowPublicKeyRetrieval=true'
$env:PIPELINE_TEST_DB_USER = 'tai-khoan-test'
# Đặt PIPELINE_TEST_DB_PASSWORD trong terminal theo cấu hình test của bạn.
.\mvnw.cmd -o -B '-Dpipeline.mysql.integration=true' clean test
```

[ContentPipelineMySqlTest](./src/test/java/com/voiceover/backend/services/ContentPipelineMySqlTest.java) chỉ tạo/xóa database có tên do test sinh; không gọi Flyway clean trên database ứng dụng. Không truyền URL có ký tự `&` trực tiếp qua Maven Wrapper Windows; dùng biến môi trường như trên.

## Phần tiếp theo

Luồng Edge-TTS đã nghiệm thu trên POI DRAFT #7 ở mức lưu POI → dịch thật → MP3/URL → Admin nghe thử. Chưa thử xuất bản và khách nghe; publish gate, fallback audio mặc định, monitoring và auth vẫn theo backlog riêng.

## Kết quả kiểm chứng Edge-TTS trên POI #7 — 26/10/2026

- Nguồn `vi`: “Đây là điểm tham quan thử nghiệm tại Thành phố Hồ Chí Minh. Du khách có thể nghe phần thuyết minh bằng tiếng Việt và tiếng Anh.” Bản lưu tiếng Việt giữ nguyên nguồn; bản dịch `en`: “This is a test attraction in Ho Chi Minh City. Visitors can listen to explanations in Vietnamese and English.” Cả hai có `translation=COMPLETED`, `audio=COMPLETED`.
- `vi`: `storage/audio/poi_7_vi_bb492472-c108-4485-b81a-ab950d69cbda.mp3`, 47.376 byte, 7,90 giây; URL `/storage/audio/poi_7_vi_bb492472-c108-4485-b81a-ab950d69cbda.mp3`.
- `en`: `storage/audio/poi_7_en_947762ad-66a4-43b7-83db-7f47a20345f5.mp3`, 46.080 byte, 7,68 giây; URL `/storage/audio/poi_7_en_947762ad-66a4-43b7-83db-7f47a20345f5.mp3`.
- Mutagen đọc được frame, 24 kHz và 48 kb/s ở cả hai file. Backend và frontend proxy trả HTTP 200 `audio/mpeg`; Range trả 206. Trang Translations & Audio hiện đúng nội dung, trạng thái và nút nghe file; trình phát HTML chuyển sang “Đang nghe thử” cho vi/en. Chưa xác nhận bằng tai chất lượng/giọng đọc.
- Có 14 file MP3 hợp lệ cho 14 ngôn ngữ `tts_supported=true`; `hi`/`ms` giữ `TEXT_ONLY` theo cờ cấu hình hiện hành, dù Edge có giọng. Tiếng Thái ban đầu gặp `NoAudioReceived` sau retry, sau đó yêu cầu tạo lại riêng ngôn ngữ thành công.
- Phát hiện POI mới thiếu `contentRevision` từ form bị HTTP 400 vì Jackson chọn constructor đầy đủ. `Poi` nay đánh dấu constructor rỗng bằng `@JsonCreator`; test Spring MVC với JSON form tối giản và toàn bộ 83 test backend qua (6 bài opt-in bỏ qua). Sau khi khởi động lại backend, POST `{}` trả lỗi nghiệp vụ `INVALID_INPUT` thay vì lỗi phân tích JSON; POI #7 và MP3 vi/en vẫn còn.
