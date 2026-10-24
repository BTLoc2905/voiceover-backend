# VoiceMap — Bàn giao trạng thái và việc còn lại

Cập nhật: 24/10/2026. Tài liệu này dành cho thành viên nhận code sau khi đồng đội push/merge. Đối chiếu thêm yêu cầu chi tiết và bằng chứng đã có trong [PRD_SOURCE_AUDIT.md](./PRD_SOURCE_AUDIT.md); PRD gốc là [Nhom2_CNPM.docx](./Nhom2_CNPM.docx).

**Lưu ý tài liệu cũ:** phần UC07 trong `PRD_SOURCE_AUDIT.md` và [UC07_IMPLEMENTATION.md](./UC07_IMPLEMENTATION.md) chưa phản ánh lần tích hợp Gemini mới nhất. Hai tài liệu đó mô tả endpoint mẫu hoặc Gemini 2.5 Flash/timeout 30 giây; trạng thái thực tế sau đó được ghi trong mục UC07 của file bàn giao này. Dùng PRD gốc để đối chiếu yêu cầu, không lấy mô tả UC07 cũ làm trạng thái nghiệm thu hiện tại.

## Tóm tắt nhanh

Các lõi UC01–UC04 và phần quản trị Session/Route đã có; UC09 có màn kiểm duyệt dịch/audio; ứng dụng đã kết nối MySQL và chạy migration đến V13 trên máy phát triển. Tuy nhiên, **chưa thể coi đồ án hoàn tất**: AI UC07 chưa tạo được kết quả thành công với Gemini; UC04 cần nghiệm thu ngoài thực địa; pipeline dịch/TTS và audio provider thật cần nghiệm thu; UC11 monitoring còn thiếu; dữ liệu MySQL demo không tự đi theo Git.

## Trạng thái theo use case

| UC | Trạng thái bàn giao | Việc còn lại chính |
|---|---|---|
| UC01 — GPS/bản đồ POI | Lõi bản đồ/GPS và danh sách POI công khai đã có | Sửa API nearby cũ cho đúng bán kính, bỏ side effect đọc/seed; kiểm tra từ chối quyền/GPS yếu trên thiết bị. Giữ bản đồ rộng và danh sách gần bạn 500 m. |
| UC02 — Thuyết minh đa ngôn ngữ | Lõi tự phát/nghe thủ công, hàng đợi và điều khiển đã có; người dùng xác nhận nghe thủ công được | Kiểm thiết bị thật, GPS/sai số, độ trễ và file TTS provider thật; không nhầm giọng đọc trình duyệt với file audio đã lưu. |
| UC03 — Tùy chỉnh Route | Lõi đã kiểm: thêm/bớt, lên/xuống, kiểm POI hợp lệ, tính lại quãng đường/thời gian | Route cá nhân phía khách dùng localStorage, không phải dữ liệu Route Admin đồng bộ nhiều thiết bị. Kéo thả/offline đầy đủ đã để sau. |
| UC04 — Xem Route/bắt đầu dẫn đường | Đã hoàn thiện luồng ứng dụng: tự chuyển chặng/kết thúc, arrival GPS tin cậy, phát hiện lệch tuyến/tính lại Directions; giữ nghe, xác nhận/bỏ qua/dừng thủ công. 29 test frontend qua, build thành công. | Nghiệm thu tuyến thực tế nhiều POI, GPS và Directions trên thiết bị/mạng thật; xác minh độ trễ và trải nghiệm khi mất GPS/API lỗi. |
| UC05 — Đăng nhập | Có form/login cơ bản | Chưa có phiên/token server và bảo vệ đầy đủ API Admin. Phần quyền đã được người dùng yêu cầu chưa làm; không tự mở rộng ngoài phạm vi. |
| UC06 — Quản lý POI | Đã bổ sung nhập liệu bản đồ, upload ảnh, chọn ngôn ngữ và validation backend; 10 test UC06/Route qua | Còn nghiệm thu tạo/sửa/ẩn/xuất bản/xóa và ảnh sau restart trên dữ liệu kiểm thử riêng; không dùng Route demo. |
| UC07 — Gợi ý mô tả AI | Có UI/API nháp Gemini, lưu trạng thái và luồng sửa/nhận/bỏ; **chưa có lần gọi Gemini thành công được xác nhận** | Xử lý lỗi Gemini, bổ sung retry/backoff, thử model dự phòng cùng key, hiện lỗi provider cụ thể; nghiệm thu tạo nháp → sửa/nhận/bỏ và bảo đảm lỗi không đổi POI. Xem mục UC07 bên dưới. |
| UC08 — Dịch/TTS pipeline | Đã sửa source language; task phân biệt TRANSLATE_TTS/TTS_ONLY, tái sử dụng bản dịch đúng hash và chỉ bỏ qua khi audio hợp lệ; retry transient có timeout/backoff, lỗi từng ngôn ngữ không dừng các ngôn ngữ khác; task dang dở được phục hồi sau restart | Cần nghiệm thu Google Translation/TTS hoặc Piper thật, file sau restart và đồng bộ task/DB trên môi trường thật. |
| UC09 — Kiểm duyệt dịch/audio | UI/API lưu riêng bản dịch và task TTS_ONLY; worker retry lỗi transient có backoff, lỗi cấu hình/4xx không retry vô ích; bản dịch được giữ khi TTS lỗi | Chưa xác nhận ghi bản sửa mới vào DB trong đợt nghiệm thu này; audio provider thật chưa được kiểm đủ. Nhiều ngôn ngữ hiện có thể chỉ TEXT_ONLY/UNAVAILABLE. |
| UC10 — Session/Routes Admin | Phạm vi đã duyệt và lõi đã kiểm: nháp, xuất bản, POI có thứ tự, preview, lọc public, giữ dữ liệu sau restart | Không làm lại nếu không có lỗi hồi quy cụ thể. Kéo thả/offline để sau. |
| UC11 — Giám sát/thống kê | Dashboard có một số số đếm | Bổ sung health DB/pipeline, task chờ/lỗi, error rate/latency, active users và thống kê theo ngôn ngữ/Route; thay nhãn trạng thái đang tĩnh bằng dữ liệu thật. |

## UC08/09/02 — tiến độ pipeline và provider

- Đã sửa pipeline dịch dùng `sourceLanguageCode` của POI; cùng ngôn ngữ nguồn/đích thì giữ nguyên văn bản. Request dịch có timeout cấu hình được; lỗi mạng/timeout/408/429/5xx retry tối đa ba lần với backoff; lỗi cấu hình/4xx không retry vô ích. Lỗi một ngôn ngữ không chặn các ngôn ngữ khác.
- Bản dịch `COMPLETED` đúng source hash được dùng lại, không ghi đè nội dung Admin sửa tay; nếu chỉ thiếu/hỏng file audio thì tạo task `TTS_ONLY`, không dịch lại. Task TTS-only cho bản sửa tay đã thất bại không tự tạo vòng retry từ pipeline UC08; Admin có thể chủ động thử lại trong UC09. Chỉ coi audio hoàn tất khi file nằm trong audio storage và tồn tại; nếu file mất/provider chưa sẵn sàng thì không giữ trạng thái/file URL thành công giả.
- Task pipeline toàn phần `PROCESSING` được đưa lại vào hàng đợi khi backend khởi động; task UC09 vẫn phục hồi theo luồng riêng. TTS worker UC09 dùng cùng chính sách retry/backoff cho lỗi transient.
- Test backend toàn bộ: 60 test qua. Có test HTTP stub cho source language và retry 503, logic chọn TTS-only, file storage, worker và phục hồi task; đây không phải kiểm chứng dịch vụ bên thứ ba thật.
- **Provider thật chưa nghiệm thu:** môi trường hiện không có `GOOGLE_CLOUD_API_KEY`, `PIPER_EXECUTABLE`/`PIPER_MODEL`, cũng không có `.env`. Cần người dùng cấu hình provider hợp lệ rồi thử một ngôn ngữ trước; kiểm task, WAV/URL và file sau restart. Không ghi key vào Git/tài liệu.

## UC04 — tiến độ hoàn thiện

- Đã tự chuyển chặng và tự kết thúc Route sau khi GPS mới (không quá 5 giây), sai số ≤30 m, vị trí nằm chắc chắn trong bán kính POI liên tục 3 giây và có nhiều hơn một GPS fix.
- Chỉ tăng tiến độ một lần cho mỗi điểm. Nếu hai POI liên tiếp gần/chồng bán kính, yêu cầu người dùng xác nhận thủ công để tránh GPS rung làm bỏ qua điểm.
- Không tự chuyển trong lúc đang tải/phát thuyết minh. Các thao tác xác nhận đến, bỏ qua và dừng dẫn đường vẫn được giữ.
- Đã tính khoảng cách đến geometry đi bộ; sau 3 GPS fix đáng tin cậy lệch khỏi tuyến sẽ yêu cầu Directions tính lại từ vị trí hiện tại, có giới hạn gọi lại. Không thay lỗi Directions bằng đường thẳng; vẫn giữ POI đích và cho phép thử lại.
- Đã thêm test cho độ mới/độ chính xác GPS, dwell, chống chuyển nhầm, khoảng cách geometry và phát hiện lệch tuyến. Kiểm chứng hiện có: `node --test` — 29/29 qua; `npm.cmd run build` — thành công; `npm.cmd run lint` — không có cảnh báo trong các file UC04 vừa sửa, còn cảnh báo ở mã khác.
- **Còn nghiệm thu thực địa:** chạy tuyến tối thiểu 3 POI trên thiết bị/mạng thật; thử GPS rung/mất, các điểm gần nhau, audio đang phát, đi lệch tuyến và Directions lỗi. Chưa có bằng chứng cho các thử nghiệm thiết bị này.

## UC06 — tiến độ hoàn thiện nhập liệu POI

- Form Admin có bản đồ OpenStreetMap để chọn vị trí bằng click; marker, vòng bán kính và hai ô latitude/longitude cập nhật đồng bộ. Có thể sửa tọa độ trực tiếp.
- Ngôn ngữ gốc được chọn từ catalog `/api/public/languages`, không còn phải gõ mã tùy ý. Form hỗ trợ tạo/sửa, đổi DRAFT/PUBLISHED/HIDDEN và tiếp tục dùng các guard bảo vệ Route khi ẩn, xóa hoặc thay đổi POI.
- Upload thumbnail dùng endpoint multipart riêng, lưu ảnh PNG/JPEG vào `app.image.storage` (mặc định `storage/images`), đặt tên file an toàn và trả URL `/images/...`; static resource được nối với cùng thư mục. Server từ chối file sai định dạng, ảnh trên 5 MB hoặc ảnh vượt 24 megapixel.
- Backend kiểm tên, mô tả, vĩ độ/kinh độ, bán kính, trạng thái, thumbnail URL và mã ngôn ngữ đang hoạt động; không tự thay giá trị sai thành mặc định. Upload ảnh phải chọn POI đã lưu trước.
- Kiểm chứng: 10 test backend UC06/Route qua; toàn bộ backend 60/60 qua; frontend 29/29 qua, production build thành công. `npm run lint` không còn cảnh báo mới trong `AdminPortal.jsx`; vẫn còn cảnh báo cũ ở `VoiceMapDemoApp.jsx`.
- Kiểm tra môi trường ngày 24/10/2026: dịch vụ MySQL80 đang `Running`, TCP `127.0.0.1:3306` nhận kết nối. Đây mới xác nhận cổng MySQL lắng nghe, chưa xác nhận đăng nhập DB VoiceMap hay kết nối từ ứng dụng.
- Đã thử khởi động backend để nghiệm thu CRUD thật nhưng Maven build thất bại, nên chưa gọi được API và chưa thực hiện thao tác nào lên dữ liệu. Không có POI/Route demo nào bị sửa hoặc xóa.
- **Còn nghiệm thu dữ liệu thật:** sau khi sửa lỗi build và chạy backend thành công, tạo POI thử → tải lại → sửa → tải lại → đổi trạng thái/xóa và xác nhận file ảnh còn truy cập sau restart backend. Chỉ dùng POI thử riêng, không động tới Route demo.

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
2. **UC04**: nghiệm thu thực địa luồng tự chuyển chặng/kết thúc đã triển khai; ghi nhận GPS, Directions và trải nghiệm trên thiết bị thật.
3. **UC08/UC09/UC02**: cấu hình provider TTS/dịch thật, kiểm file âm thanh và khôi phục tác vụ lỗi.
4. **UC06**: upload ảnh, chọn vị trí và validation hoàn chỉnh.
5. **UC11**: dashboard số liệu/health từ backend thật.
6. **Bàn giao DB + nghiệm thu**: seed/dump dữ liệu demo có kiểm soát, clone sạch, migration, test backend/frontend, chạy thử trên thiết bị và ghi biên bản.

Chi tiết kiểm thử đã chạy và các giới hạn bằng chứng nằm trong [PRD_SOURCE_AUDIT.md](./PRD_SOURCE_AUDIT.md). Không coi build thành công là bằng chứng provider bên thứ ba, GPS ngoài trời, SLA, hoặc dữ liệu DB máy khác đã nghiệm thu.
