# VoiceMap — Đối chiếu PRD và bàn giao phần việc còn lại

**Cập nhật: 24/10/2026.** Nguồn yêu cầu: [Nhom2_CNPM.docx](./Nhom2_CNPM.docx), đặc biệt F-01–F-06 và UC01–UC11. Session/Routes/GPS đối chiếu commit **7c71eea** trên nhánh **feat/session-routes-gps-7c71eea**; UC09 được triển khai thêm trong working tree và chưa commit.

**Báo cáo này thay toàn bộ bản khảo sát cũ.** Không tiếp tục dùng các kết luận cũ như “chưa có GPS tự phát”, “chưa có giao diện Session/Route”, “chưa kết nối được MySQL” hoặc “chưa có test”: các phần đó đã thay đổi. PRD Word gốc được giữ nguyên.

## 1. Cách đọc và phạm vi bàn giao

- **Đã kiểm lõi:** chức năng chính đã có UI/API và đã kiểm chứng trong phạm vi ghi rõ; không đồng nghĩa hoàn thành mọi tiêu chí phi chức năng của PRD.
- **Có một phần:** đã có mã hoặc đã chạy được một số nhánh, vẫn còn thiếu luồng hoặc có lỗi cần sửa.
- **Chưa hoàn thiện:** API mẫu/khung xử lý chưa đủ để người dùng thực hiện use case từ đầu đến cuối.
- **Chưa kiểm chứng:** cần thử bằng thiết bị, dịch vụ hoặc môi trường thật; không tự đánh đồng với “chưa có code”.

Tài liệu phản ánh mã hiện tại, gồm UC09 vừa triển khai và giới hạn kiểm chứng ghi riêng bên dưới. Không tính phần trăm hoàn thành vì độ lớn các use case khác nhau.

**Yêu cầu giữ nguyên khi nhận việc:**

1. Tùy chỉnh Route thuộc **UC03**; xem Route và bắt đầu dẫn đường thuộc **UC04**.
2. Người dùng đã xác nhận nghe thuyết minh thủ công được. Không ghi “chưa có audio” chỉ vì một POI đang dùng giọng trình duyệt.
3. Chưa mở rộng phần quyền theo yêu cầu của người dùng. Ghi nhận hạn chế xác thực/bảo vệ API tại mục 7, không tự đưa thành việc đầu tiên chặn mọi use case.
4. Không làm lại UC03 và phạm vi Session/Routes đã nghiệm thu. Chỉ bổ sung các nhánh còn thiếu được liệt kê.
5. Các ngưỡng GPS, dwell, cooldown, bán kính tải trước là yêu cầu PRD; các điều chỉnh giao diện và quy tắc xuất bản được ghi riêng tại mục 5.

## 2. Tổng quan theo từng use case

| Use case trong PRD | Trạng thái hiện tại | Đã có | Còn thiếu / chưa đủ để kết luận hoàn tất |
|---|---|---|---|
| **UC01 — Định vị GPS và xem bản đồ POI lân cận** | Có lõi; còn hoàn thiện tính nhất quán | GPS liên tục; hiển thị vị trí/sai số; catalog chỉ POI xuất bản và tọa độ hợp lệ; danh sách 500 m kèm khoảng cách; bản đồ xem toàn bộ POI đã xuất bản, nút căn khung, cụm điểm trùng tọa độ; có thể chọn nghe trên marker | API nearby cũ vẫn không lọc bán kính và còn tự ghi/bổ sung dữ liệu; danh mục ngôn ngữ phía khách đang viết cố định; cần cảnh báo GPS yếu gọn trong giao diện mới, thử từ chối quyền/mất GPS trên thiết bị thật. Xem **T08** |
| **UC02 — Nghe và điều khiển thuyết minh đa ngôn ngữ** | Có lõi đã kiểm; chưa đủ toàn bộ audio/SLA | Nghe thủ công đã được người dùng xác nhận; một bộ phát chung cho file audio/giọng trình duyệt; tự phát khi đủ 3 giây, sai số ≤30 m, bán kính POI; hysteresis +20 m, cooldown 30 phút, ưu tiên gần nhất/hàng đợi; tải trước trong 300 m; pause/resume, tốc độ, bỏ qua; fallback/text-only có xử lý; usage start/end đã kiểm API/MySQL | Chưa nghiệm thu GPS/giọng đọc/độ trễ và file TTS provider thật trên thiết bị; tua giọng trình duyệt chỉ ước tính. Xem **T01, T02, T08, T10** |
| **UC03 — Tùy chỉnh lộ trình cá nhân** | **Đã kiểm lõi** | Thêm/bớt, lên/xuống; 2–10 POI xuất bản, tọa độ hợp lệ, không trùng; backend kiểm quy tắc; tính lại geometry/quãng đường/thời gian đi bộ; chỉ lưu lựa chọn đã xác nhận; khôi phục trên trình duyệt | Route cá nhân hiện lưu localStorage, không phải bản ghi Route Admin hay đồng bộ nhiều thiết bị; kéo thả và offline đầy đủ đã được để sau. Xem [UC03_IMPLEMENTATION.md](./UC03_IMPLEMENTATION.md) |
| **UC04 — Xem Route và bắt đầu dẫn đường** | Có một phần, cần tiếp tục | Route public đúng điều kiện xuất bản; xem thứ tự, tổng quãng đường/thời gian; start và chặng đi bộ từ GPS qua Directions thật; vị trí cập nhật; khi lỗi vẫn còn POI đích; có nghe, bỏ qua, điểm tiếp theo và kết thúc bằng nút | Chưa tự chuyển chặng/tự kết thúc khi đến POI; trạng thái “đã vào bán kính” hiện chỉ dựa khoảng cách, chưa dùng đủ điều kiện GPS tốt/cũ; tính lại chặng theo vị trí chưa phải phát hiện lệch polyline có tiêu chí rõ. Xem **T05** |
| **UC05 — Đăng nhập hệ thống** | Có đăng nhập cơ bản; chưa đủ phiên đăng nhập theo PRD | Form email/mật khẩu, đối chiếu tài khoản ACTIVE và BCrypt; hiển thị Portal; đăng xuất phía trình duyệt | Login trả profile và frontend lưu localStorage; chưa có phiên/token server, kiểm tra phiên trên API hoặc thu hồi phiên server. Phần quyền đang hoãn theo yêu cầu; xem **mục 7**, không coi login UI là đã bảo vệ API |
| **UC06 — Quản lý POI** | Có CRUD cơ bản; còn thiếu nhập liệu/kiểm tra | UI tạo/sửa, trạng thái Nháp/Xuất bản/Ẩn; tọa độ nhập tay, bán kính, URL thumbnail, mô tả; xác nhận xóa; lưu JPA; backend chặn sửa/ẩn/xóa làm hỏng Route đang phục vụ và nêu Route ảnh hưởng | Chưa chọn vị trí trên map, chưa upload thumbnail thật; form chưa có chọn ngôn ngữ gốc; validation backend cho POI độc lập chưa đầy đủ; cần kiểm đầy đủ tạo/sửa/ẩn/xóa rồi đọc lại DB. Xem **T06** |
| **UC07 — Gợi ý mô tả POI bằng AI** | **Chưa hoàn thiện; mới có endpoint mẫu** | Endpoint nhận tên/ghi chú, trả mô tả nháp mà không lưu DB | Nội dung hiện là String.format theo mẫu, chưa gọi AI sinh mô tả; form chưa có nút nhận/chỉnh/hủy bản nháp AI; chưa có timeout 15 giây và thử lỗi đúng luồng. API tạo ảnh ở service khác không thay thế UC07. Xem **T04** |
| **UC08 — Pipeline dịch và tạo audio tự động** | Có khung; vẫn còn việc pipeline toàn phần | Async executor, danh mục ngôn ngữ từ DB, protected terms, source hash, bản dịch/audio/task/log; Google TTS ghép WAV đủ văn bản và Piper/text-only; job UC09 phát sau commit, chống trùng theo POI/ngôn ngữ và phục hồi task UC09 dở khi restart | Dịch source language trong pipeline toàn phần vẫn mặc định vi; cơ chế phục hồi/retry task của pipeline toàn phần và nhánh TTS lỗi khi translation hash cũ chưa được giải quyết đầy đủ; chưa nghiệm thu provider thật nhiều ngôn ngữ. Xem **T01–T02** |
| **UC09 — Kiểm duyệt và hiệu chỉnh bản dịch/audio** | **Đã có lõi UI/API; đã đọc DB thật sau restart** | Chọn POI/ngôn ngữ; xem mô tả gốc, bản dịch, audio/task/lỗi; lưu riêng bản dịch một ngôn ngữ; tạo lại từ nội dung đã lưu; revision, polling, task/retry; API trả 6 POI, mỗi POI có 16 bản dịch hoàn tất | Chưa ghi bản sửa mới lên MySQL. Hiện 14 ngôn ngữ có cờ TTS nhưng provider UNAVAILABLE; audio cả 16 ngôn ngữ/POI là TEXT_ONLY. Cần cấu hình Google/Piper để nghiệm thu file mới khách nghe được. Không có migration. Xem [UC09_IMPLEMENTATION.md](./UC09_IMPLEMENTATION.md) |
| **UC10 — Quản lý Session và Route** | **Đã kiểm phạm vi được duyệt** | Tạo/sửa Session, Route, nháp dở, xuất bản có kiểm tra; thêm/bớt/lên/xuống; marker đánh số đồng bộ danh sách; preview đi bộ, quãng đường/thời gian, coverage ngôn ngữ/audio; public lọc cả Session và Route; chặn phá Route; lưu thật và còn sau restart | Không đồng nhất với hoàn tất UC04 tự chuyển chặng hoặc UC09 kiểm duyệt audio. Kéo thả/offline đầy đủ để sau; tạm đóng bằng về Nháp. Xem [SESSION_ROUTES_REQUIREMENTS.md](./SESSION_ROUTES_REQUIREMENTS.md) |
| **UC11 — Giám sát và thống kê sử dụng** | Có dashboard đếm cơ bản; còn phần lớn monitoring PRD | Tổng POI, POI xuất bản, tổng client từng ghi nhận, lượt nghe theo POI; polling 10 giây; start/end ghi qua API thật và loại preload khỏi số lượt | Thiếu trạng thái App/DB/Pipeline thật, số task chờ/xử lý/lỗi, error rate, response time, lỗi gần nhất; active users theo cửa sổ thời gian, theo ngôn ngữ, tỷ lệ auto/tap, Route phổ biến; nhãn “System operational” đang tĩnh; polling 10 giây chưa chứng minh SLA ≤10 giây. Xem **T07** |

### Những phần bạn nhận việc không cần làm lại

- Lõi UC03 thêm/bớt/đổi thứ tự và tính lại Route đi bộ.
- Lõi GPS tự phát, hàng đợi, chống chồng tiếng và cooldown của UC02.
- Toàn bộ phạm vi Admin Session/Routes đã được duyệt, trừ khi phát hiện lỗi hồi quy cụ thể.
- Việc ghi start/end và loại GET preload khỏi lượt nghe đã có. Khi mở rộng thống kê cần dùng lại dữ liệu/sự kiện này.
- Bản đồ khám phá hiển thị POI xuất bản và cụm trùng tọa độ đã có; danh sách gần bạn vẫn 500 m.

## 3. Các điểm kỹ thuật cần biết trước khi nhận việc

### 3.1. UI mới và API nearby cũ đang không đồng nhất

Màn khách đang dùng GET /api/public/pois rồi tính Haversine/lọc danh sách 500 m ở frontend. Nó không gọi GET /api/public/pois/nearby.

Trong [PoiService.java](./src/main/java/com/voiceover/backend/services/PoiService.java), getNearbyPublishedPois vẫn duyệt mọi POI xuất bản mà không áp maxRadiusMeters; còn refresh nội dung/ảnh, lưu và bổ sung dữ liệu mẫu khi ít điểm. Vì vậy không được kết luận API nearby đã đúng chỉ vì UI mới lọc đúng. T08 phải sửa API này mà vẫn giữ bản đồ xem toàn bộ POI theo điều chỉnh người dùng.

### 3.2. Nghe được bằng trình duyệt khác với có MP3 hoàn chỉnh trong kho file

- Giọng đọc trình duyệt đã hoạt động; giữ chức năng đó.
- [AudioGenerationService.java](./src/main/java/com/voiceover/backend/services/AudioGenerationService.java) đã chia văn bản và tạo WAV phiên bản mới đủ nội dung; test dùng HTTP stub/local, chưa thay thế kiểm Google/Piper thật.
- [PoiService.java](./src/main/java/com/voiceover/backend/services/PoiService.java) ưu tiên translation/audio DB hoàn tất; built-in chỉ còn là fallback có giới hạn cho một số POI.
- FilePath không rỗng chưa đủ: phải kiểm trạng thái COMPLETED, URL trả được file thực và nội dung được đọc đủ.
- Catalog có 16 ngôn ngữ active, 14 có cờ ttsSupported; cờ cấu hình không chứng minh thiết bị có giọng đọc hoặc đã sinh file cho toàn bộ ngôn ngữ.

### 3.3. API đã nhận yêu cầu không có nghĩa tác vụ async đã thành công

regenerateSingleLanguageAudio chạy async, nên thông báo HTTP hiện tại không bảo đảm đã lưu/sinh audio xong. Cần tách “đã nhận tác vụ”, “đang xử lý”, “hoàn tất”, “lỗi” và có cách đọc trạng thái thật. Áp dụng tương tự việc lưu POI rồi kích hoạt pipeline trong transaction.

### 3.4. Có schema/log không có nghĩa có Dashboard giám sát đầy đủ

[UsageEventRepository.java](./src/main/java/com/voiceover/backend/repositories/UsageEventRepository.java) đang đếm client duy nhất của toàn bộ dữ liệu, không phải số người đang hoạt động. Header xanh trên Portal là chuỗi tĩnh; dashboard chưa lấy health/latency/error-rate từ monitoring service.

### 3.5. Code trên GitHub không tự mang theo DB hiện tại

Chưa có file SQL xuất dữ liệu nghiệp vụ/seed đầy đủ cho nhóm. Migration V12 chỉ đổi cấu trúc kiểm tra stop_order; nó không chứa 6 POI hoặc Session/Route trên máy đang chạy. Dữ liệu route cá nhân và cooldown ở localStorage cũng không đi theo Git. T09 xử lý phần bàn giao môi trường này.

## 4. Danh sách việc để chia cho thành viên

Thứ tự dưới đây ưu tiên use case và các phụ thuộc hiện có. Có thể làm T04, T05, T06 song song với nhóm pipeline. Các gợi ý người nhận là cách chia việc cho nhóm, không phải yêu cầu triển khai ngay trong lượt viết báo cáo này.

### T01 — Nghiệm thu provider thật và nhánh phục vụ audio (UC02/UC08)

**Người nhận phù hợp:** backend/pipeline.

**Cần làm:**

- Cấu hình provider Google/Piper thật và kiểm thuyết minh dài hơn 200 ký tự qua URL HTTP: nghe đủ đầu/giữa/cuối, MIME/Range, và sau restart.
- Kiểm file thiếu/hỏng, text-only và fallback trên trình duyệt thật; trạng thái phân biệt đúng giọng trình duyệt với file TTS.
- Có URL phiên bản mới khi tạo lại; xem chính sách dọn file WAV cũ không còn được tham chiếu.

**Nghiệm thu còn lại:** provider thật sinh file dài đầy đủ và URL trả đúng file sau restart; lỗi TTS/file mất/text-only/fallback đúng; khách nhận bản Admin sửa. Generator WAV/URL mới và ưu tiên DB đã có test unit/môi trường cô lập.

**File bắt đầu:** [PipelineService.java](./src/main/java/com/voiceover/backend/services/PipelineService.java), [PoiService.java](./src/main/java/com/voiceover/backend/services/PoiService.java), [AppConfig.java](./src/main/java/com/voiceover/backend/config/AppConfig.java), [narrationApi.js](./voiceover-frontend/src/narrationApi.js), [narrationPlayer.js](./voiceover-frontend/src/narrationPlayer.js).

### T02 — Hoàn thiện tính đúng và khả năng phục hồi pipeline (ưu tiên cao, UC08)

**Người nhận phù hợp:** backend; phối hợp người làm T01.

**Cần làm:**

- Dùng sourceLanguageCode thật; không mặc định mọi văn bản đều tiếng Việt khi gọi dịch.
- Đối chiếu vòng đời task pipeline toàn phần với job UC09 (đã có PENDING → PROCESSING → COMPLETED/FAILED, phát sau commit, chống trùng, phục hồi task dở khi restart).
- Không bỏ qua TTS toàn phần lỗi chỉ vì bản dịch có cùng hash; phân biệt cần dịch lại và chỉ cần tạo lại audio.
- Retry tối đa 3 lần theo PRD, lỗi ngôn ngữ này không ngừng các ngôn ngữ khác; đặt timeout thực cho request.
- Giữ protected terms và bản sửa tay đúng quy tắc; thêm/ngừng ngôn ngữ không đòi sửa mã UI.
- API UC09 đã đọc task/lỗi và sửa một ngôn ngữ độc lập. Kiểm tích hợp lỗi/tác vụ đồng thời với pipeline toàn phần; UC11 vẫn thiếu monitoring.

**Nghiệm thu còn lại:** source language đúng, tác vụ pipeline toàn phần không mắc kẹt/lưu trùng, TTS lỗi có thể retry không cần dịch lại, và thử provider thật.

**File bắt đầu:** [PipelineService.java](./src/main/java/com/voiceover/backend/services/PipelineService.java), [PoiService.java](./src/main/java/com/voiceover/backend/services/PoiService.java), [AppConfig.java](./src/main/java/com/voiceover/backend/config/AppConfig.java), [PipelineTask.java](./src/main/java/com/voiceover/backend/entities/PipelineTask.java).

### T03 — Màn Translations & Audio (UC09; đã triển khai, nghiệm thu môi trường còn lại)

**Trạng thái:** menu, giao diện, API, lưu một ngôn ngữ, nghe thử, task/retry và xử lý lỗi đã có. Không cần thành viên khác làm lại màn hình.

**Cần làm:**

- Đã restart backend UC09 và xác nhận `/admin/translations` tải được; GET danh sách và chi tiết trả HTTP 200 cho 6 POI. Nếu bàn giao sang máy khác, restart backend sau khi lấy mã mới.
- Nếu cần nghiệm thu file giọng thật, cấu hình Google/Piper, chờ task `COMPLETED`, mở URL WAV và kiểm khách nhận nội dung đã lưu.
- Đã có: lựa chọn POI qua danh sách admin hiện hữu; GET index/detail; PUT/POST riêng; trạng thái task; phát lỗi lưu/provider; dirty draft không mất; retry task lỗi.

**Kiểm tra còn lại:** chưa thử ghi bản sửa mới lên DB để không đổi dữ liệu demo; cần cấu hình/thử Google hoặc Piper thật để xác nhận file audio được tạo.

**File bắt đầu:** [AdminPortal.jsx](./voiceover-frontend/src/AdminPortal.jsx), [VoiceMapController.java](./src/main/java/com/voiceover/backend/controllers/VoiceMapController.java), [PipelineService.java](./src/main/java/com/voiceover/backend/services/PipelineService.java), [TranslationRepository.java](./src/main/java/com/voiceover/backend/repositories/TranslationRepository.java), [AudioRepository.java](./src/main/java/com/voiceover/backend/repositories/AudioRepository.java).

### T04 — Làm AI gợi ý mô tả thật (UC07, có thể làm độc lập)

**Người nhận phù hợp:** một thành viên làm trọn UI + API AI.

**Cần làm:** thêm nút Gợi ý bằng AI trên form POI; lấy tên/ghi chú, gọi dịch vụ AI sinh văn bản; hiển thị bản nháp để sửa/chấp nhận/bỏ; timeout 15 giây và thông báo lỗi/nhập tay.

**Nghiệm thu:** nhấn gợi ý không đổi DB và không tạo task; hủy không lưu; chỉ Lưu POI mới ghi mô tả/kích hoạt UC08; lỗi/quá 15 giây vẫn nhập tay được. Không tính template String.format hoặc API sinh ảnh là đã hoàn thành UC07.

**File bắt đầu:** [AdminPortal.jsx](./voiceover-frontend/src/AdminPortal.jsx), [PoiService.java](./src/main/java/com/voiceover/backend/services/PoiService.java), [VoiceMapController.java](./src/main/java/com/voiceover/backend/controllers/VoiceMapController.java).

### T05 — Hoàn tất dẫn đường tự động (ưu tiên cao, UC04)

**Người nhận phù hợp:** frontend GPS/bản đồ.

**Cần làm:**

- Khi đến POI, cập nhật tiến độ/tự chuyển chặng đúng quy tắc; POI cuối tự kết thúc. Không chỉ đổi nhãn nút.
- Dùng GPS mới và đáng tin trước khi tự xác nhận đến nơi; tránh chuyển nhiều chặng do GPS rung hoặc POI trùng tọa độ.
- Phối hợp với bộ phát UC02 để không chồng tiếng/phát lặp; giữ tiến độ Route và cooldown audio là hai trạng thái riêng.
- Định nghĩa và phát hiện lệch tuyến theo geometry; tính lại từ vị trí hiện tại, tránh gọi Directions liên tục.
- Giữ nhánh bỏ qua/dừng, mất GPS, Directions lỗi và marker đích; không vẽ đường thẳng rồi gọi là đường đi bộ thực.

**Nghiệm thu:** mô phỏng và thử thực tuyến ít nhất 3 POI, đến từng điểm chỉ chuyển một lần, đến cuối tự kết thúc; sai số lớn/GPS cũ không tự xác nhận; đi lệch tính lại, API lỗi vẫn biết đích tiếp theo.

**File bắt đầu:** [RouteJourney.jsx](./voiceover-frontend/src/RouteJourney.jsx), [useTourLocation.js](./voiceover-frontend/src/useTourLocation.js), [useTourNarration.js](./voiceover-frontend/src/useTourNarration.js), [OrderedRouteMap.jsx](./voiceover-frontend/src/OrderedRouteMap.jsx), [RouteSessionService.java](./src/main/java/com/voiceover/backend/services/RouteSessionService.java).

### T06 — Hoàn thiện nhập liệu POI (UC06)

**Người nhận phù hợp:** frontend form/bản đồ + backend upload/validation.

**Cần làm:** chọn tọa độ trên bản đồ và đồng bộ ô nhập; upload thumbnail thật rồi lưu URL; chọn ngôn ngữ gốc từ catalog; validate tên/mô tả/tọa độ/bán kính/trạng thái/ngôn ngữ ở backend, không chỉ HTML; kiểm các lỗi lưu và form sửa dữ liệu.

**Nghiệm thu:** tạo → tải lại → sửa → tải lại → ẩn/xuất bản → xóa với POI kiểm thử; file ảnh còn sau restart; input API sai bị từ chối; các guard Route hiện có vẫn trả lỗi rõ và không làm mất dữ liệu. Kiểm trên dữ liệu riêng, không phá Route #1 đang dùng để demo.

**File bắt đầu:** [AdminPortal.jsx](./voiceover-frontend/src/AdminPortal.jsx), [PoiService.java](./src/main/java/com/voiceover/backend/services/PoiService.java), [VoiceMapController.java](./src/main/java/com/voiceover/backend/controllers/VoiceMapController.java).

### T07 — Làm monitoring và thống kê đủ PRD (UC11)

**Người nhận phù hợp:** backend thống kê + frontend Dashboard. **Phụ thuộc một phần:** task/lỗi thật từ T02.

**Cần làm:**

- Health App/DB/Pipeline, ba trạng thái xanh/vàng/đỏ theo dữ liệu thật; số task chờ/xử lý/lỗi, error rate, response time, lỗi gần nhất.
- Active users theo định nghĩa thời gian rõ; lượt nghe theo POI/ngôn ngữ, tỷ lệ AUTO_PLAY/TAP_POI, Route phổ biến.
- Bổ sung sự kiện mở app/chọn hoặc bắt đầu Route và dữ liệu cần tính active; tránh ghi trùng khi render/preload/refresh.
- Đồng bộ nhãn người dùng/lượt nghe: số client từng ghi nhận không phải active users, lượt nghe không phải số người nghe duy nhất.
- Xử lý API monitoring lỗi và dữ liệu rỗng; đo độ trễ cập nhật thay vì chỉ đặt interval 10 giây.

**Nghiệm thu:** một kịch bản sự kiện có số lượng biết trước đối chiếu đúng từng chỉ số; giả lập lỗi DB/pipeline làm trạng thái thay đổi đúng; số liệu mới xuất hiện trong thời gian PRD; refresh không tự tăng usage.

**File bắt đầu:** [AdminPortal.jsx](./voiceover-frontend/src/AdminPortal.jsx), [VoiceMapController.java](./src/main/java/com/voiceover/backend/controllers/VoiceMapController.java), [PlaybackEventController.java](./src/main/java/com/voiceover/backend/controllers/PlaybackEventController.java), [UsageEventRepository.java](./src/main/java/com/voiceover/backend/repositories/UsageEventRepository.java), [SystemLog.java](./src/main/java/com/voiceover/backend/entities/SystemLog.java).

### T08 — Đồng bộ API public, ngôn ngữ và các nhánh GPS (UC01/UC02)

**Người nhận phù hợp:** frontend/backend tích hợp.

**Cần làm:** sửa API nearby cũ lọc bán kính, bỏ ghi/seed/refresh nội dung khỏi luồng đọc; lấy ngôn ngữ khách từ API active thay danh sách cố định; cảnh báo GPS yếu/từ chối quyền gọn trong giao diện; kiểm fallback theo tình trạng audio và nội dung thực.

**Nghiệm thu:** API 499/501 m và POI nháp/ẩn đúng; GET không tự tạo/sửa POI hoặc gọi pipeline ngoài kế hoạch; không có GPS vẫn xem marker và nghe thủ công; sai số >30 m không tự phát và có trạng thái phù hợp; ngôn ngữ inactive không còn được chọn. Giữ nguyên bản đồ rộng, không đưa lại thanh bật/tắt/hộp chào đã bỏ.

**File bắt đầu:** [PoiService.java](./src/main/java/com/voiceover/backend/services/PoiService.java), [VoiceMapScreens.jsx](./voiceover-frontend/src/VoiceMapScreens.jsx), [useTourLocation.js](./voiceover-frontend/src/useTourLocation.js), [narrationApi.js](./voiceover-frontend/src/narrationApi.js).

### T09 — Bộ dữ liệu và hướng dẫn cho máy khác (việc bàn giao nhóm)

**Người nhận phù hợp:** người đang có MySQL chứa dữ liệu demo.

**Cần làm:** xuất dữ liệu nghiệp vụ SQL hoặc seed có thể tái lập; ghi thứ tự chạy migration/import, cấu hình MySQL và lệnh chạy backend/frontend; nếu có file ảnh/audio local thì bàn giao chúng cùng đường dẫn. Không nhầm migration schema với bản xuất dữ liệu. Kiểm lại dữ liệu mẫu/tọa độ trước khi dùng để nghiệm thu GPS thực.

**Nghiệm thu:** một thành viên clone nhánh đúng vào máy/DB mới, làm theo tài liệu và nhìn thấy POI, Session/Route, thứ tự, bản dịch cần demo; restart vẫn còn; không cần database trên máy tác giả. File SQL này hiện **chưa được xuất**, không ghi là đã có.

**File bắt đầu:** [README.md](./README.md), [db/migration/](./src/main/resources/db/migration/), [SESSION_ROUTES_REQUIREMENTS.md](./SESSION_ROUTES_REQUIREMENTS.md).

### T10 — Kiểm chứng cuối và cấu hình phát hành (sau các luồng chức năng)

**Cần làm:**

- Kiểm migration DB sạch và DB nâng cấp đến V12, cấu hình môi trường dev/test/release; hiện source đang bật Flyway và dùng ddl-auto=update, không phải cấu hình validate như báo cáo cũ.
- Chạy CI JDK 21, bổ sung kiểm frontend trong quy trình CI nếu nhóm yêu cầu; smoke test Docker/backend/frontend và storage trên môi trường triển khai.
- Thử GPS và giọng đọc trên thiết bị thật, browser policy, mất kết nối; đo audio đã preload <1,5 giây, trường hợp còn lại tối đa 3 giây; đo AI ≤15 giây.
- Kiểm lỗi dịch vụ bên thứ ba không làm ngừng chức năng còn lại. Uptime ≥99,5% cần bằng chứng vận hành theo thời gian, không dùng unit test để khẳng định.
- Đối chiếu sản phẩm web responsive hiện tại với yêu cầu Mobile App của đề tài; repo hiện chưa có dự án mobile riêng.

**Nghiệm thu:** có biên bản/kết quả và môi trường tái lập; mục chưa có điều kiện đo phải ghi chưa kiểm chứng, không đánh dấu hoàn tất theo cảm tính.

**File bắt đầu:** [pom.xml](./pom.xml), [maven.yml](./.github/workflows/maven.yml), [Dockerfile](./Dockerfile), [application.properties](./src/main/resources/application.properties), [voiceover-frontend/](./voiceover-frontend/).

### Gợi ý chia nhóm

| Nhánh việc | Phần nhận | Phụ thuộc chính |
|---|---|---|
| Backend nội dung | T01 + T02 | Cấu hình dịch vụ dịch/TTS thật; dữ liệu kiểm thử riêng |
| Admin nội dung | UC09 đã có; T04 | Provider thật để nghiệm thu audio; T04 có thể làm độc lập |
| GPS/bản đồ/POI | T05 + T06 + T08 | Dùng lại UC02/UC03/UC10 đã có; thống nhất thay đổi chung ở PoiService/AdminPortal |
| Tích hợp và nghiệm thu | T07 + T09 + T10 | Phối hợp các nhóm trên; không coi file build hoặc schema migration là dữ liệu demo |

## 5. Phân biệt PRD gốc và điều chỉnh đã duyệt

### Yêu cầu gốc dùng để đối chiếu

- F-01/UC01–UC02: GPS liên tục, POI gần 500 m, bán kính mặc định 50 m, accuracy ≤30 m, dwell ≥3 giây, rời vùng +20 m, cooldown 30 phút, ưu tiên/hàng đợi, preload 300 m và điều khiển nghe.
- F-02/UC03–UC04: Route 2–10 POI xuất bản, có thứ tự, Directions đi bộ, tùy chỉnh tính lại đường, dẫn đường/chặng và tự chuyển khi đến.
- F-03/UC08–UC09: dịch/TTS nhiều ngôn ngữ, protected terms, xử lý nền, trạng thái/retry, sửa riêng một ngôn ngữ, text-only cho ngôn ngữ không hỗ trợ.
- F-05/UC05–UC07/UC10: đăng nhập, quản lý POI/ảnh, AI chỉ tạo nháp, Session/Route.
- F-06/UC11 và mục 2.4: monitoring/usage, độ trễ cập nhật, audio/AI, GPS và độ khả dụng.

### Quy tắc đã bổ sung hoặc làm rõ trong phiên này

1. Session/Route nháp được lưu dở; Route nháp có thể 0/1/>10 điểm. Chỉ khi xuất bản mới bắt buộc 2–10 POI hợp lệ, đã xuất bản và không trùng.
2. Session chỉ xuất bản khi có ít nhất một Route hợp lệ đã xuất bản; không tự xuất bản Route nháp.
3. Khách chỉ thấy Route khi cả Route và Session xuất bản; start/leg cũng kiểm lại khả dụng.
4. Chặn xóa/ẩn/chuyển POI về nháp hoặc làm tọa độ không hợp lệ nếu phá Route đang phục vụ; nêu rõ Route ảnh hưởng. POI còn được Route nháp tham chiếu hiện cũng phải bỏ khỏi Route trước khi xóa.
5. Tạm đóng Session bằng về Nháp, giữ dữ liệu. Route xuất bản hợp lệ cuối cùng trong Session đang mở phải được xử lý cùng quy tắc giữ Session hợp lệ.
6. Preview marker đánh số đồng bộ danh sách, hiển thị số điểm/đường đi bộ/thời gian/coverage audio/ngôn ngữ; dùng nút lên/xuống.
7. Bỏ thanh bật/tắt tự phát và hộp chào, vào thẳng bản đồ; chỉ hiện thao tác bắt đầu nghe khi browser chặn.
8. Bản đồ khám phá xem mọi POI xuất bản hợp lệ; danh sách gần bạn vẫn 500 m. Điểm trùng tọa độ có cụm chọn từng điểm, không tự sửa DB.
9. GPS quá 5 giây coi là cũ; khi tab ẩn không bắt đầu lượt tự động mới; cooldown lưu theo thiết bị/trình duyệt. Đây là lựa chọn kỹ thuật hiện tại, không thay ngưỡng PRD.
10. Telemetry lỗi không dừng nghe; chống gửi lặp hiện chỉ trong một tiến trình, TTL 30 phút/tối đa 2.048 khóa, chưa có bảo đảm đa instance hoặc hàng đợi gửi bền vững.

Chi tiết: [Session/Routes](./SESSION_ROUTES_REQUIREMENTS.md), [UC03](./UC03_IMPLEMENTATION.md), [UC02 GPS](./UC02_GPS_IMPLEMENTATION.md).

## 6. Bằng chứng hiện có và dữ liệu để nghiệm thu

### Kiểm thử/build đã thực hiện trong phiên

- **Backend:** các kết quả theo phạm vi được ghi ở từng tài liệu; UC09 có test service/worker/controller/generator, chưa thay thế kiểm tra tích hợp MySQL/provider thật.
- **Frontend:** 24 trường hợp có kết quả qua: GPS 9, bộ phát 10, Route 5. Build production thành công, gồm lượt trước khi push nhánh; lint không có lỗi, còn 5 cảnh báo ở demo không được mount.
- **API/MySQL thật:** đọc được 6 POI, 16 ngôn ngữ active; có Session/Route đã lưu. Backend đã restart và có API playback mới.
- **Usage:** đã kiểm preload không tăng lượt; một AUTO_PLAY tăng đúng một lượt, gửi lại không tăng, AUDIO_COMPLETED không tăng số lượt bắt đầu. Các bản ghi QA được mô tả trong tài liệu UC02, không diễn giải thành số người dùng thật.
- **Session/Routes:** V12 trên DB thật; lưu nháp 11 điểm được, xuất bản bị chặn, phục hồi về nháp rỗng; chặn POI gây hỏng Route; tạm đóng/mở lại Session, public lọc đúng.
- **Sau restart:** 5/5 kiểm tra Session #1/Route #1/thứ tự [2,1]/public/start trả LineString đi bộ qua.
- **UI:** kiểm GPS/bộ phát bằng mô phỏng; bản đồ có 5 marker đại diện 6 POI, popup chọn riêng hai điểm trùng tọa độ, khung nhìn 390/1.280 px. Nghe thủ công đã được người dùng xác nhận riêng.
- UC09: 7 test worker/WAV và 24 test frontend hồi quy đã qua; frontend production build thành công. UI fixture không phải API/MySQL thật. Xem [UC09_IMPLEMENTATION.md](./UC09_IMPLEMENTATION.md).

### Snapshot dữ liệu đọc lại ngày 23/10/2026

| Dữ liệu | Trạng thái đọc từ backend |
|---|---|
| POI | 6 điểm đều PUBLISHED, ID 1–6 |
| Ngôn ngữ | 16 active, 14 có cờ ttsSupported |
| Session #1 | PUBLISHED |
| Route #1 | PUBLISHED, POI theo thứ tự **[2,1]**, hiện trong API public |
| Route #2 | DRAFT, 0 điểm, không hiện trong API public |
| MySQL hiện tại | voicemap_db trên máy phát triển; dữ liệu không tự đi theo Git |

Không sửa các bản ghi demo này chỉ để chạy thử nhánh phá dữ liệu; dùng POI/Route/DB kiểm thử riêng. Hai POI có cùng tọa độ đã được thể hiện thành cụm trên map, vẫn cần rà lại chất lượng tọa độ trước khi nghiệm thu đi bộ thật.

### Chưa có đủ bằng chứng

- Pipeline hoàn chỉnh từ POI bất kỳ → nhiều ngôn ngữ → file audio đủ nội dung → khách nghe trên thiết bị thật.
- UC09 ghi/đọc lại bản sửa bằng MySQL sau restart backend mới và thử file TTS từ provider thật.
- UC07 AI sinh văn bản thực trong 15 giây.
- UC04 tự đi qua các chặng đến khi tự kết thúc.
- Dashboard monitoring/active users/SLA đầy đủ.
- Fresh clone/DB mới có dữ liệu giống máy hiện tại, SQL export, triển khai Docker và kết quả CI cho toàn bộ thay đổi mới.
- Mục tiêu GPS ngoài trời ≤5 m, độ trễ audio, uptime ≥99,5%.

## 7. Phần hoãn hoặc không được tự tính là bắt buộc hoàn tất ngay

- **Quyền và bảo vệ phiên/API:** đang hoãn theo yêu cầu tập trung use case. Tuy vậy phải ghi đúng hiện trạng UC05: profile localStorage không phải phiên xác thực server, và API Admin chưa được bảo vệ đầy đủ. Không mô tả hệ thống đã sẵn sàng quản trị an toàn chỉ vì có form login.
- **Kéo thả và offline đầy đủ:** đã để sau trong phạm vi Session/Routes. Hiện không có service worker/gói offline đầy đủ; localStorage Route và preload tạm không tương đương offline hoàn chỉnh. Không bắt làm kéo thả để nghiệm thu bản dùng nút lên/xuống đã duyệt.
- **Cảnh báo tự động khi lỗi vượt ngưỡng:** PRD ghi dự kiến v1.1; không đưa vào bắt buộc v1.0.
- **Gửi email đặt lại mật khẩu:** chưa có; không tự coi là đầu việc bắt buộc khi chưa chốt phần “nếu hỗ trợ”.
- Các phần import OSM, tự tìm/tạo ảnh và Piper là nhánh bổ sung có trong code, không thay thế nghiệm thu AI gợi ý mô tả/ảnh upload/TTS theo use case chính.

## 8. Điểm vào mã nguồn để người nhận bắt đầu

| Khu vực | File chính |
|---|---|
| App đang được chạy thật | [main.jsx](./voiceover-frontend/src/main.jsx) → [VoiceMapScreens.jsx](./voiceover-frontend/src/VoiceMapScreens.jsx) hoặc [AdminPortal.jsx](./voiceover-frontend/src/AdminPortal.jsx) |
| GPS/tự phát/nghe | [autoNarration.js](./voiceover-frontend/src/autoNarration.js), [useTourLocation.js](./voiceover-frontend/src/useTourLocation.js), [useTourNarration.js](./voiceover-frontend/src/useTourNarration.js), [narrationPlayer.js](./voiceover-frontend/src/narrationPlayer.js), [NarrationAudioScreen.jsx](./voiceover-frontend/src/NarrationAudioScreen.jsx) |
| Route khách | [RoutePlanner.jsx](./voiceover-frontend/src/RoutePlanner.jsx), [routePlan.js](./voiceover-frontend/src/routePlan.js), [RouteJourney.jsx](./voiceover-frontend/src/RouteJourney.jsx) |
| Session/Routes Admin | [SessionRoutes.jsx](./voiceover-frontend/src/SessionRoutes.jsx), [SessionRoutesController.java](./src/main/java/com/voiceover/backend/controllers/SessionRoutesController.java), [RouteSessionService.java](./src/main/java/com/voiceover/backend/services/RouteSessionService.java) |
| UC09 bản dịch/audio | [TranslationReview.jsx](./voiceover-frontend/src/TranslationReview.jsx), [TranslationReviewController.java](./src/main/java/com/voiceover/backend/controllers/TranslationReviewController.java), [TranslationReviewService.java](./src/main/java/com/voiceover/backend/services/TranslationReviewService.java) |
| Directions đi bộ | [WalkingDirectionsService.java](./src/main/java/com/voiceover/backend/services/WalkingDirectionsService.java), [OrderedRouteMap.jsx](./voiceover-frontend/src/OrderedRouteMap.jsx) |
| POI/AI/pipeline | [VoiceMapController.java](./src/main/java/com/voiceover/backend/controllers/VoiceMapController.java), [PoiService.java](./src/main/java/com/voiceover/backend/services/PoiService.java), [PipelineService.java](./src/main/java/com/voiceover/backend/services/PipelineService.java) |
| Usage/statistics | [PlaybackEventController.java](./src/main/java/com/voiceover/backend/controllers/PlaybackEventController.java), [UsageEventRepository.java](./src/main/java/com/voiceover/backend/repositories/UsageEventRepository.java) |
| Schema/kiểm chứng | [migration/](./src/main/resources/db/migration/), [src/test/](./src/test/), [verify-session-routes.ps1](./scripts/verify-session-routes.ps1) |

**Lưu ý:** [VoiceMapDemoApp.jsx](./voiceover-frontend/src/VoiceMapDemoApp.jsx) không được mount trong entry point hiện tại. Chức năng có ở file demo không được dùng để kết luận ứng dụng thật đã hoàn thiện.
