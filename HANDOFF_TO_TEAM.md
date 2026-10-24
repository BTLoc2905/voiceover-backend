# VoiceMap — Bàn giao trạng thái và việc còn lại

Cập nhật: 24/10/2026. Tài liệu này dành cho thành viên nhận code sau khi đồng đội push/merge. Đối chiếu thêm yêu cầu chi tiết và bằng chứng đã có trong [PRD_SOURCE_AUDIT.md](./PRD_SOURCE_AUDIT.md); PRD gốc là [Nhom2_CNPM.docx](./Nhom2_CNPM.docx).

**Lưu ý tài liệu cũ:** phần UC07 trong `PRD_SOURCE_AUDIT.md` và [UC07_IMPLEMENTATION.md](./UC07_IMPLEMENTATION.md) chưa phản ánh lần tích hợp Gemini mới nhất. Hai tài liệu đó mô tả endpoint mẫu hoặc Gemini 2.5 Flash/timeout 30 giây; trạng thái thực tế sau đó được ghi trong mục UC07 của file bàn giao này. Dùng PRD gốc để đối chiếu yêu cầu, không lấy mô tả UC07 cũ làm trạng thái nghiệm thu hiện tại.

## Tóm tắt nhanh

Các lõi UC01–UC03 và phần quản trị Session/Route đã có; UC09 có màn kiểm duyệt dịch/audio; ứng dụng đã kết nối MySQL và chạy migration đến V13 trên máy phát triển. Tuy nhiên, **chưa thể coi đồ án hoàn tất**: AI UC07 chưa tạo được kết quả thành công với Gemini; UC04 chưa tự chuyển chặng/kết thúc; pipeline dịch/TTS và audio provider thật cần nghiệm thu; UC11 monitoring còn thiếu; dữ liệu MySQL demo không tự đi theo Git.

## Trạng thái theo use case

| UC | Trạng thái bàn giao | Việc còn lại chính |
|---|---|---|
| UC01 — GPS/bản đồ POI | Lõi bản đồ/GPS và danh sách POI công khai đã có | Sửa API nearby cũ cho đúng bán kính, bỏ side effect đọc/seed; kiểm tra từ chối quyền/GPS yếu trên thiết bị. Giữ bản đồ rộng và danh sách gần bạn 500 m. |
| UC02 — Thuyết minh đa ngôn ngữ | Lõi tự phát/nghe thủ công, hàng đợi và điều khiển đã có; người dùng xác nhận nghe thủ công được | Kiểm thiết bị thật, GPS/sai số, độ trễ và file TTS provider thật; không nhầm giọng đọc trình duyệt với file audio đã lưu. |
| UC03 — Tùy chỉnh Route | Lõi đã kiểm: thêm/bớt, lên/xuống, kiểm POI hợp lệ, tính lại quãng đường/thời gian | Route cá nhân phía khách dùng localStorage, không phải dữ liệu Route Admin đồng bộ nhiều thiết bị. Kéo thả/offline đầy đủ đã để sau. |
| UC04 — Xem Route/bắt đầu dẫn đường | Có Route public và dẫn đường theo chặng | Hoàn thiện tự xác nhận đến POI, tự chuyển chặng và tự kết thúc; bảo vệ khỏi GPS cũ/rung; định nghĩa phát hiện lệch tuyến và tính lại. |
| UC05 — Đăng nhập | Có form/login cơ bản | Chưa có phiên/token server và bảo vệ đầy đủ API Admin. Phần quyền đã được người dùng yêu cầu chưa làm; không tự mở rộng ngoài phạm vi. |
| UC06 — Quản lý POI | Có CRUD cơ bản, lưu DB, chặn một số thay đổi làm hỏng Route | Thêm chọn tọa độ trên map, upload ảnh thật, chọn ngôn ngữ gốc, hoàn thiện validate backend; nghiệm thu tạo/sửa/ẩn/xuất bản/xóa trên dữ liệu thử riêng. |
| UC07 — Gợi ý mô tả AI | Có UI/API nháp Gemini, lưu trạng thái và luồng sửa/nhận/bỏ; **chưa có lần gọi Gemini thành công được xác nhận** | Xử lý lỗi Gemini, bổ sung retry/backoff, thử model dự phòng cùng key, hiện lỗi provider cụ thể; nghiệm thu tạo nháp → sửa/nhận/bỏ và bảo đảm lỗi không đổi POI. Xem mục UC07 bên dưới. |
| UC08 — Dịch/TTS pipeline | Có pipeline và lưu bản dịch/audio/task | Sửa phục hồi/retry cho pipeline toàn phần, dùng đúng ngôn ngữ nguồn; thử Google/Piper thật và kiểm file audio hoàn chỉnh sau restart. |
| UC09 — Kiểm duyệt dịch/audio | Có UI/API lưu riêng bản dịch, nghe thử, trạng thái task/retry | Chưa xác nhận ghi bản sửa mới vào DB trong đợt nghiệm thu này; audio provider thật chưa được kiểm đủ. Nhiều ngôn ngữ hiện có thể chỉ TEXT_ONLY/UNAVAILABLE. |
| UC10 — Session/Routes Admin | Phạm vi đã duyệt và lõi đã kiểm: nháp, xuất bản, POI có thứ tự, preview, lọc public, giữ dữ liệu sau restart | Không làm lại nếu không có lỗi hồi quy cụ thể. Kéo thả/offline để sau. |
| UC11 — Giám sát/thống kê | Dashboard có một số số đếm | Bổ sung health DB/pipeline, task chờ/lỗi, error rate/latency, active users và thống kê theo ngôn ngữ/Route; thay nhãn trạng thái đang tĩnh bằng dữ liệu thật. |

## UC07 — tình trạng Gemini cần xử lý

- Backend gọi Gemini từ Java, không gọi trực tiếp từ trình duyệt. Key được đọc qua `GEMINI_API_KEY`; model qua `GEMINI_MODEL`.
- Kết quả người dùng đã thấy: `gemini-2.5-flash` trả HTTP 404; đổi sang `gemini-3.8-flash` thì lần đầu timeout 30 giây, lần sau UI báo Gemini tạm thời gặp sự cố (5xx). Chưa có phản hồi sinh mô tả thành công; **không đánh dấu UC07 hoàn tất**.
- Mã hiện tại gửi một request, không tự retry/fallback; lỗi 5xx hiển thị chung nên không có chi tiết provider để phân loại.
- Việc tiếp theo: 1) đọc/hiển thị an toàn mã lỗi và thông báo `error.message` từ Gemini (không log API key); 2) retry có giới hạn với exponential backoff cho timeout/408/429/5xx; 3) thử fallback model được Google AI Studio của chính key cho phép; 4) giữ nhập mô tả thủ công khi provider lỗi; 5) kiểm đầu ra/DB không bị cập nhật cho đến khi người quản trị bấm chấp nhận.
- Không đưa key thật vào Git, frontend, log, ảnh chụp hay tài liệu bàn giao. Không cần đổi key chỉ vì 5xx; kiểm tra trạng thái key/project trong AI Studio nếu lỗi tiếp diễn.

## Chạy dự án trên máy nhận bàn giao

1. Cài MySQL, tạo/chọn DB `voicemap_db`; kiểm tra thông tin đăng nhập MySQL trong `application.properties` hoặc cấu hình môi trường cục bộ.
2. Tạo file `.env` ở thư mục gốc (cùng cấp `pom.xml`) để chứa Gemini; `.env` được Git bỏ qua:

   ```env
   GEMINI_API_KEY=điền-key-Google-AI-Studio
   GEMINI_MODEL=gemini-3.8-flash
   GEMINI_TIMEOUT_SECONDS=60
   ```

   Dùng model mà AI Studio hiển thị cho project/key; tên model trên đây chỉ là cấu hình thử hiện tại, chưa được nghiệm thu thành công.

3. Từ PowerShell tại thư mục gốc chạy backend: `.\mvnw.cmd spring-boot:run`. Nếu mở terminal mới, đặt lại biến môi trường MySQL nếu không có trong cấu hình máy. Flyway tự chạy migration; V13 tạo bảng nháp AI.
4. Frontend: vào `voiceover-frontend`, chạy `npm.cmd install` nếu chưa có `node_modules`, sau đó `npm.cmd run dev`.
5. Kiểm tra backend/frontend đã chạy, thử trang khách, trang POI, `/admin/sessions` và `/admin/translations`.

Không gửi file `.env` cho nhóm. Chia sẻ tên biến và hướng dẫn lấy key; mỗi thành viên dùng key riêng.

## Dữ liệu DB và push code

Git lưu mã và migration, **không lưu các bản ghi MySQL đang có trên máy**. Migration V13 tạo cấu trúc nháp AI chứ không mang theo POI/Session/Route demo. Muốn thành viên khác có cùng dữ liệu cần xuất dữ liệu SQL đã rà soát hoặc tạo seed lặp lại được, kèm file ảnh/audio nếu chúng nằm trên ổ cục bộ. Không commit dump chứa thông tin cá nhân, mật khẩu hay key. Kiểm tra `.env` bị ignore trước khi push.

## Ranh giới PRD gốc và điều chỉnh đã duyệt

PRD gốc nằm trong `Nhom2_CNPM.docx`; bảng phân tích chi tiết tại [PRD_SOURCE_AUDIT.md](./PRD_SOURCE_AUDIT.md). Các điều chỉnh được người dùng duyệt trong phiên:

- Tùy chỉnh Route thuộc UC03; xem Route và bắt đầu dẫn đường thuộc UC04.
- Session/Route nháp được lưu dở; chỉ xuất bản Route mới bắt buộc 2–10 POI hợp lệ, đã xuất bản, không trùng. Session chỉ xuất bản khi có Route hợp lệ đã xuất bản; không tự xuất bản Route nháp.
- Khách chỉ thấy Route khi cả Session và Route đã xuất bản. Không cho thao tác POI làm hỏng Route đang phục vụ; lỗi nêu Route ảnh hưởng. Tạm đóng Session bằng chuyển về Nháp và giữ nguyên dữ liệu.
- Preview có marker số đồng bộ danh sách, số POI, quãng đường/thời gian đi bộ và coverage audio/ngôn ngữ. Sắp xếp bằng nút lên/xuống; kéo thả/offline đầy đủ để sau.
- Thanh bật/tắt tự phát và hộp chào đã được yêu cầu bỏ để ưu tiên diện tích bản đồ; giữ nghe thủ công/tự phát theo luồng đã duyệt.
- Chưa triển khai phần quyền theo yêu cầu người dùng; ghi đúng hạn chế xác thực, không tự biến thành phạm vi phải làm trước các use case.

## Đề xuất chia việc theo thứ tự

1. **UC07 Gemini**: lấy một lần sinh thành công, lỗi provider rõ ràng, retry/fallback an toàn.
2. **UC04**: tự chuyển chặng/kết thúc và kiểm thử GPS thật.
3. **UC08/UC09/UC02**: cấu hình provider TTS/dịch thật, kiểm file âm thanh và khôi phục tác vụ lỗi.
4. **UC06**: upload ảnh, chọn vị trí và validation hoàn chỉnh.
5. **UC11**: dashboard số liệu/health từ backend thật.
6. **Bàn giao DB + nghiệm thu**: seed/dump dữ liệu demo có kiểm soát, clone sạch, migration, test backend/frontend, chạy thử trên thiết bị và ghi biên bản.

Chi tiết kiểm thử đã chạy và các giới hạn bằng chứng nằm trong [PRD_SOURCE_AUDIT.md](./PRD_SOURCE_AUDIT.md). Không coi build thành công là bằng chứng provider bên thứ ba, GPS ngoài trời, SLA, hoặc dữ liệu DB máy khác đã nghiệm thu.
