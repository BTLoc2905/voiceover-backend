# Session & Routes — phạm vi đã duyệt ngày 23/10/2026

> **Kết quả mới nhất (23/10/2026):** Luồng Admin tạo/lưu/xuất bản → khách xem và bắt đầu UC04 đã được kiểm chứng trên MySQL và Directions thật. V12 đã áp dụng trên `voicemap_db`; nháp 11 điểm lưu được, xuất bản bị chặn. Sau khi backend đổi PID **20972 → 3936**, 5 kiểm tra giữ dữ liệu/khả dụng/bắt đầu Route đều qua. Các mục chờ ở các lượt cũ bên dưới được giữ làm lịch sử và đã được cập nhật kết quả ở lượt mới hơn. Phạm vi nghiệm thu giữ đúng các giới hạn nêu cuối tài liệu.

## 1. Nguồn yêu cầu gốc

Đối chiếu trực tiếp [PRD 1.0](./Nhom2_CNPM.docx), mục 3.2 F-02, 3.5 F-05, 4.3.3 UC03, 4.3.4 UC04 và 4.3.10 UC10. [Báo cáo khảo sát](./PRD_SOURCE_AUDIT.md) là mốc lịch sử, không thay thế kiểm chứng phiên bản hiện tại.

| Mã | Yêu cầu gốc và nguồn |
|---|---|
| G01 | UC10: Admin xem danh sách, tạo/sửa Session (tên, mô tả), quản lý các Route thuộc Session. Một Session nhiều Route; một Route thuộc một Session. |
| G02 | UC10/F-05: thêm/bớt POI và sắp xếp chuỗi điểm trong Route; lưu bằng DB. POI thuộc Route, không gắn trực tiếp Session. |
| G03 | UC10: trạng thái Nháp/Xuất bản; tạm đóng Session bằng đổi trạng thái và giữ dữ liệu. Session hoặc Route nháp không hiện cho khách. |
| G04 | F-02: lộ trình 2–10 POI đã xuất bản, có tọa độ và thứ tự; tính quãng đường/thời gian bằng Directions đi bộ. |
| G05 | UC04: khách xem lộ trình, chọn và bắt đầu dẫn đường tới điểm đầu tiên. UC03 là tùy chỉnh thêm/bớt/đổi thứ tự và tính lại đường đi. |
| G06 | F-05: chặn xóa POI thuộc Route **hoặc** Session đã xuất bản; cảnh báo đứt gãy lộ trình. Điều kiện này được điều chỉnh bởi B05 bên dưới. |

## 2. Quy tắc bổ sung/điều chỉnh do người dùng duyệt

Các mục B01–B10 là quyết định của người dùng trong cuộc trao đổi ngày 23/10/2026, không gán ngược thành nguyên văn PRD.

| Mã | Quy tắc được duyệt | Cách áp dụng |
|---|---|---|
| B01 | Nghiệm thu khách xem/bắt đầu thuộc UC04; tùy chỉnh thuộc UC03. | Nhãn màn hình và luồng kiểm chứng tách đúng UC. |
| B02 | Cho lưu Session/Route nháp dở; chỉ xuất bản Route mới bắt buộc 2–10 điểm hợp lệ, đã xuất bản, không trùng. | Nháp cho tên rỗng, 0/1/>10 điểm, POI chưa xuất bản và thứ tự có POI trùng. Mã POI vẫn phải tồn tại do khóa ngoại. Xuất bản yêu cầu tên và kiểm toàn bộ điều kiện. |
| B03 | Session cần ít nhất một Route hợp lệ đã xuất bản; không tự xuất bản Route nháp. | Xuất bản Route trước, Session sau. Các Route đã xuất bản đều được kiểm tra lại khi xuất bản Session để không đưa nội dung hỏng ra phục vụ. |
| B04 | Khách chỉ thấy Route khi Route **và** Session đều xuất bản. | Danh sách public và API bắt đầu/chặng đường kiểm tra lại. Route có điểm không hợp lệ bị loại khỏi danh sách. Lộ trình cá nhân có Route nguồn đã đóng không được khôi phục để bắt đầu. |
| B05 | Chặn xóa/chuyển nháp/ẩn POI làm hỏng Route đang phục vụ; báo rõ Route bị ảnh hưởng. | “Đang phục vụ” = cả Route và Session xuất bản, theo B04. Đây là điều chỉnh so với điều kiện **hoặc** ở G06. Cũng chặn sửa mất tọa độ hợp lệ. HTTP 409 chứa tên và ID Route. |
| B06 | Tạm đóng Session bằng Nháp, giữ dữ liệu. | Không xóa Session, Route hoặc điểm dừng; không tự đổi trạng thái Route con. |
| B07 | Bản đồ preview, marker số khớp danh sách. | Marker cập nhật ngay sau thêm/bớt/lên/xuống; chọn marker/dòng danh sách đồng bộ điểm nổi bật. Đường cũ không hiện khi đang tính thứ tự mới. |
| B08 | Hiện số điểm, quãng đường đi bộ, thời gian, audio/ngôn ngữ. | Directions thật; trạng thái audio/bản văn theo từng ngôn ngữ active và từng điểm. Thiếu audio không giả thành sẵn sàng và không ngăn xuất bản theo phạm vi đã duyệt. |
| B09 | Nút lên/xuống trước; kéo thả và offline đầy đủ để sau. | Dùng nút có nhãn truy cập; không đánh dấu offline hoàn chỉnh. |
| B10 | Backend kiểm quy tắc, DB thật, đọc lại/tải lại/khởi động lại. | JPA transaction lưu Session/Route/thứ tự. Kiểm thử service và nghiệm thu runtime được ghi riêng bên dưới. |

### Quy tắc triển khai phát sinh để giữ tính nhất quán

- Không chuyển Route hợp lệ xuất bản cuối cùng của Session đang xuất bản về Nháp. Thông báo yêu cầu tạm đóng Session trước; tránh Session xuất bản nhưng không còn Route hợp lệ.
- Không chuyển Route sang Session khác qua API cập nhật. Sai quan hệ Session/Route bị từ chối.
- Xóa POI còn được Route nháp tham chiếu cũng bị khóa ngoại chặn: thông báo tên Route và yêu cầu bỏ điểm khỏi Route trước. Đổi POI về Nháp/Ẩn khi Session đã đóng được phép; phải sửa lại trước khi tái xuất bản Session.
- Ghi trạng thái chuẩn `DRAFT`/`PUBLISHED`; đọc tương thích chữ hoa/thường của dữ liệu cũ. Không dùng `CLOSED` để tạm đóng.
- Khóa hàng Session và POI trong transaction khi xuất bản, cùng khóa POI khi sửa/xóa; thứ tự khóa POI tăng theo ID. Lưu lại thứ tự xóa/flush điểm cũ trước khi chèn để không xung đột `(route_id, stop_order)`.
- Preview/Directions không ghi DB, không giữ transaction ghi trong lúc chờ dịch vụ ngoài. Directions lỗi vẫn cho lưu nháp, báo không tính được đường; không tạo số liệu bằng đường thẳng.

## 3. Triển khai

- Admin: `/admin/sessions` trong Portal hiện tại; chọn Session → sửa thông tin → tạo/sửa Route → thêm/bớt/lên/xuống → lưu nháp hoặc xuất bản.
- [Dịch vụ nghiệp vụ](./src/main/java/com/voiceover/backend/services/RouteSessionService.java), [API](./src/main/java/com/voiceover/backend/controllers/SessionRoutesController.java), [bảo vệ POI](./src/main/java/com/voiceover/backend/services/PoiService.java).
- [Giao diện Admin](./voiceover-frontend/src/SessionRoutes.jsx), [bản đồ chung](./voiceover-frontend/src/OrderedRouteMap.jsx), [UC04](./voiceover-frontend/src/RouteJourney.jsx), [UC03 và chọn Route](./voiceover-frontend/src/RoutePlanner.jsx).
- Danh sách/public start lấy DB thật; bản tùy chỉnh riêng UC03 vẫn lưu thiết bị như phạm vi trước, không sửa Route gốc của Admin.

### API

| API | Mục đích |
|---|---|
| `GET /api/admin/sessions` | Session và các Route, thứ tự ID, số điểm, lỗi điều kiện xuất bản. |
| `POST /api/admin/sessions` | `{name, description, status}`; mặc định Nháp. |
| `PUT /api/admin/sessions/{id}` | Sửa và chuyển Nháp/Xuất bản. |
| `POST /api/admin/sessions/{id}/routes` | `{name, status, poiIds:[...]}`; tạo Route. |
| `PUT /api/admin/sessions/{id}/routes/{routeId}` | Sửa Route và thứ tự; kiểm đúng Session. |
| `GET /api/admin/pois/{id}/coverage` | Audio/văn bản theo các ngôn ngữ active; chỉ đọc. |
| `GET /api/public/routes` | Chỉ Route hợp lệ cùng Session đã xuất bản. |
| `POST /api/public/routes/customize` | Preview đi bộ (UC03 hoặc Admin), không lưu. |
| `POST /api/public/routes/{id}/start` | UC04: kiểm khả dụng hiện tại, nhận `{poiIds:[...]}` tùy chọn cho bản cá nhân, trả geometry/số liệu/điểm có thứ tự. |
| `GET /api/public/routes/{id}/leg?targetId=...&lat=...&lng=...` | Đường đi bộ từ GPS tới điểm kế tiếp; kiểm Route/Session/điểm đến còn khả dụng. |

Endpoint cũ POST Route với query `name` + body mảng ID được giữ tương thích; mặc định Nháp và dùng cùng validation.

## 4. Migration và dữ liệu khi clone

[V12](./src/main/resources/db/migration/V12__allow_incomplete_route_drafts.sql) bỏ giới hạn DB `stop_order <= 10`; giới hạn 2–10 chuyển về kiểm tra lúc xuất bản. Vẫn giữ số thứ tự dương và khóa thứ tự duy nhất. Không sửa migration cũ, không reset hay xóa DB.

Môi trường dùng Flyway chạy V12 khi khởi động với lịch sử migration hợp lệ. Nếu môi trường đã tắt Flyway để dùng schema cũ, DBA cần áp dụng đúng hai lệnh V12 trên DB đó trước khi lưu nháp quá 10 điểm. Không bật lại toàn bộ migration trên DB cũ khi chưa kiểm lịch sử.

Git lưu source/migration; Session/Route tạo bằng Admin nằm trong MySQL của môi trường đó, không tự đi theo `git push`/clone. Nhóm cần dùng chung DB hoặc xuất/nhập dữ liệu; lượt này không thêm seed Session/Route tự động.

## 5. Nghiệm thu

Luồng chuẩn: lưu nháp Session → lưu Route nháp 0/1 điểm → thêm 2 POI hợp lệ → xuất bản Route → khách vẫn không thấy khi Session nháp → xuất bản Session → khách thấy đúng thứ tự/số liệu → “Bắt đầu lộ trình” mở UC04, đi bộ tới điểm đầu theo GPS → đổi chặng/bỏ qua/kết thúc. Thử tải lại và khởi động lại để xác nhận ID/trạng thái/thứ tự không đổi.

Luồng chặn: xuất bản Route 0/1/11 điểm, trùng, POI nháp/ẩn, tọa độ sai; xuất bản Session chưa có Route hợp lệ; thay đổi POI đang phục vụ; bắt đầu Route sau khi Session tạm đóng; mất Directions không được hiện số liệu/đường giả.

### Trạng thái kiểm chứng

Kết quả build/test ngày 23/10/2026, khoảng 19:17 (UTC+7). Trạng thái runtime mới hơn được ghi ở mục “Nghiệm thu DB thật” bên dưới; các hàng tiến trình cũ là lịch sử:

| Tầng | Kết quả | Giới hạn bằng chứng |
|---|---|---|
| Backend | `mvnw.cmd -q test` qua: 24 test hiện hành, 0 lỗi. Gồm 10 publication, 2 POI protection, 3 HTTP contract, 6 UC03 và 3 Directions HTTP stub. | Repository Mockito, không phải MySQL. Không tính report/class test cũ còn trong `target`. |
| Frontend | `npm.cmd run build` qua; 5 test logic `node --test src/routePlan.test.js` qua. Lint mã thoát 0, 17 cảnh báo ở hai file có sẵn, không có cảnh báo ở các module mới. | Không chứng minh kết nối DB hay dịch vụ ngoài. |
| UI Admin với API fixture | Tạo/lưu Session nháp; chặn xuất bản chưa có Route; lưu Route 0 điểm; thêm hai điểm, đổi thứ tự, marker đúng số/tên; audio vi/en đổi tương ứng; xuất bản Route rồi Session; reload giữ thứ tự; không tràn ngang ở 390 và 1280 px. | Dữ liệu fixture nằm ở origin kiểm thử `127.0.0.1:5174`, không ghi MySQL. Đồng bộ chọn marker kiểm bằng sự kiện DOM; click vật lý qua browser automation có lỗi hit-test nên không dùng để kết luận thao tác bản đồ trên thiết bị thật. |
| UI khách với API fixture | Xem gợi ý → Bắt đầu → màn UC04 và chặng đến điểm đầu theo GPS giả lập → bỏ qua sang điểm thứ hai → hoàn thành. | Geometry/metrics của fixture, không phải Directions thật trong lượt UI này. |
| Backend đang chạy cổng 8080 | GET public routes 200, danh sách rỗng; GET public POIs 200, có 6 điểm; GET admin sessions 404. PID vẫn là tiến trình trước khi sửa. | Backend chưa nạp mã Session/Routes mới. Không dùng nó nghiệm thu chức năng mới. |
| Khởi động bản mới cổng 18080 | Đã thử với Flyway tắt, Hibernate validate để không sửa schema. Thất bại vì MySQL `Access denied for user 'root'@'localhost'`. | Terminal kiểm thử không có các biến kết nối trong terminal backend của người dùng. Không thử đoán mật khẩu. |
| MySQL lưu thật, V12 và restart | **Chưa kiểm chứng được.** Đã yêu cầu người dùng khởi động lại backend trong terminal đang có cấu hình DB đúng. | Chưa áp dụng V12, chưa tạo Session/Route trên MySQL, chưa chứng minh giữ dữ liệu qua restart. |

### Script kiểm chứng sau khi backend nạp bản mới

[verify-session-routes.ps1](./scripts/verify-session-routes.ps1) kiểm HTTP, tạo dữ liệu nghiệm thu Session/Route riêng, không tạo hoặc thay đổi POI nếu các guard hoạt động đúng; giữ lại dữ liệu để kiểm restart. Script bao gồm nháp 0/1/11 điểm, lỗi xuất bản, đúng thứ tự, lọc Route/Session, không tự xuất bản con, chặn POI và thông báo tên Route, tạm đóng/mở lại, UC04 start với Directions thật và GET đọc lại. Script sẽ dừng nếu V12 chưa có hoặc dịch vụ chưa chạy.

```powershell
# Ở thư mục gốc, sau khi backend đã chạy mã mới và DB đã có V12.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-session-routes.ps1
```

Lượt đầu in SessionId, RouteId, danh sách ExpectedPoiIds. Sau khi restart backend, chạy script với `-CheckPersisted` và ba giá trị đó để xác nhận trạng thái/thứ tự và bắt đầu Route vẫn hoạt động. Script đã sửa tương thích mảng JSON và UTF-8 trên Windows PowerShell 5 sau lượt chạy thực tế.

### Nghiệm thu DB thật sau khi người dùng khởi động lại backend

Ngày 23/10/2026: tiến trình cổng 8080 đã đổi từ PID 39884 sang **20972**, API Admin mới trả 200. Dữ liệu ban đầu chưa có Session/Route, có 6 POI published. Các kiểm tra dưới đây dùng backend mới, MySQL thật và Directions thật; không có fetch fixture trên origin `localhost:5174`.

| Hạng mục | Bằng chứng runtime |
|---|---|
| Tạo và lưu nháp dở | Script tạo Session **#1** tên rỗng và Route **#1** 0 điểm thành công; cập nhật 1 điểm thành công. |
| Chặn xuất bản thiếu điều kiện | Session chưa có Route xuất bản trả 400; Route 1 điểm hoặc 2 điểm trùng trả 400. |
| Nháp 11 điểm / V12 | Lượt đầu trả 409 do schema cũ; transaction không làm mất bản nháp 1 điểm. **Đã kiểm chứng thành công sau khi áp dụng V12**, xem mục xác nhận V12 bên dưới. |
| Admin sửa và đọc lại dữ liệu | Trên giao diện Admin, đặt tên Session tiếng Việt và mô tả; đặt tên Route, thêm POI, đưa lên/xuống rồi lưu. Reload giữ đúng tên có dấu và chuỗi **[2,1]**. |
| Preview bản đồ và audio/ngôn ngữ | Marker 1 = Công viên 23 tháng 9; marker 2 = Đại học Sài Gòn. Click marker 2 thật qua browser automation làm nổi bật dòng 2; lỗi hit-test của trang kiểm thử cũ không còn là giới hạn của lần này. Chọn vi/en hiển thị 0/2 audio, có văn bản và chưa có audio, khớp metadata DB. |
| Lọc xuất bản hai cấp | Xuất bản Route #1 khi Session nháp: public vẫn rỗng. Xuất bản Session: public xuất hiện Route #1 đúng thứ tự [2,1]. |
| Không tự xuất bản con | Tạo Route **#2** “Lộ trình nháp lưu dở”, 0 điểm. Lưu Session ở PUBLISHED vẫn giữ Route #2 DRAFT. |
| UC04 trên giao diện khách | Chọn Route thật → Bắt đầu → màn UC04 tới Công viên 23 tháng 9. API start trả 200, `WALKING`, **1.902 m**, **26 phút**, GeoJSON LineString **83 tọa độ**, đúng [2,1]. Chặng từ GPS trình duyệt tới điểm đầu hiển thị khoảng 6,99 km / 94 phút và đường trên bản đồ. |
| Tiến độ hành trình | Bỏ qua điểm đầu → hiển thị điểm 2/2 Đại học Sài Gòn → Hoàn thành → màn kết thúc lộ trình. |
| POI đang phục vụ | POST đổi POI #2 sang DRAFT/HIDDEN, sửa latitude null, DELETE POI #2 đều trả 409, kèm tên và ID Route #1. Đọc lại POI vẫn nguyên trạng thái/tọa độ/updatedAt; không lưu hay kích hoạt pipeline ở các thao tác bị chặn. |
| Route cuối của Session | Chuyển Route #1 về DRAFT trong Session PUBLISHED trả 409, yêu cầu tạm đóng Session trước. |
| Tạm đóng và mở lại | Bấm Tạm đóng Session trên UI: public không còn Route, cả start và leg trả 409. Session giữ tên/mô tả, Route #1 vẫn PUBLISHED [2,1], Route #2 vẫn DRAFT 0 điểm. Xuất bản Session lại thành công. |
| Qua khởi động lại sau khi có dữ liệu | **Đã qua** sau khi áp dụng V12 và restart backend: PID 20972 → 3936, 5 kiểm tra qua, tên/mô tả/trạng thái/thứ tự và Route nháp giữ nguyên. Xem mục xác nhận restart bên dưới. |

Dữ liệu nghiệm thu được giữ lại: **Session #1 “Phiên nghiệm thu Session & Routes”**, Route #1 **“Đi bộ: Công viên 23/9 → Đại học Sài Gòn”** đã xuất bản, chuỗi POI [2,1]; Route #2 nháp 0 điểm. Không xóa dữ liệu hoặc tạo thêm POI. Script tự động dừng ở ca nháp 11 điểm; các ca runtime phía sau được thực hiện qua UI/API trực tiếp như bảng trên, không ghi nhận cả script đã qua.

Sau khi backend có PID mới khác 20972, kiểm giữ dữ liệu bằng:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "& '.\scripts\verify-session-routes.ps1' -CheckPersisted -SessionId 1 -RouteId 1 -ExpectedPoiIds 2,1"
```

Sau khi V12 được áp dụng, dùng Route #2 nháp để kiểm lưu 11 điểm và chặn xuất bản, rồi trả về 0 điểm; không thay đổi Route #1 đang phục vụ. Kết quả mới nhất của nhánh này được ghi bên dưới.

### Xác nhận V12 trên DB thật, ngày 23/10/2026

Đối chiếu danh sách kết nối MySQL với các cổng TCP của tiến trình backend xác định database thực tế là **`voicemap_db`** trên `127.0.0.1:3306`. Người dùng đã chạy hai lệnh ALTER có tên database đầy đủ trong Workbench; ảnh Action Output lúc 19:54:11 cho thấy cả hai thành công, không có cảnh báo. Không kết luận nguyên nhân các truy vấn Workbench trước đó trả rỗng chỉ từ ảnh chụp.

Kiểm tra tiếp qua backend/MySQL thật:

- PUT Route #2 ở DRAFT với 11 điểm trả 200, `stopCount=11`; GET mới đọc lại đúng đủ 11 ID và đúng thứ tự.
- PUT cùng danh sách để PUBLISHED bị từ chối HTTP 400 vì vượt giới hạn 2–10. Đọc lại vẫn DRAFT, đủ 11 điểm; thao tác bị từ chối không làm mất bản nháp.
- Đã trả Route #2 về đúng bản nháp ban đầu: tên “Lộ trình nháp lưu dở”, 0 điểm.
- Session #1 và Route #1 giữ PUBLISHED, thứ tự [2,1]; public chỉ có Route #1, không lộ Route #2 nháp.

**B02 nhánh nháp >10 điểm đã kiểm chứng thành công. B10 nhánh restart cũng đã qua ở lượt xác nhận tiếp theo bên dưới.**

### Xác nhận dữ liệu qua restart sau khi tạo, ngày 23/10/2026

Người dùng đã khởi động lại backend trong terminal có cấu hình MySQL đúng. Cổng 8080 chuyển từ PID **20972** sang PID **3936**, xác nhận là tiến trình mới.

Đã chạy script `-CheckPersisted -SessionId 1 -RouteId 1 -ExpectedPoiIds 2,1`, mã thoát 0, **5/5 kiểm tra qua**:

1. Session #1 tồn tại và vẫn PUBLISHED.
2. Route #1 tồn tại và vẫn PUBLISHED.
3. Thứ tự POI vẫn chính xác [2,1].
4. Route đã lưu vẫn hiển thị trong API public cho khách.
5. Khách bắt đầu Route sau restart thành công, nhận geometry đi bộ LineString.

Tải lại trang Admin và GET đọc mới từ backend cũng xác nhận tên Session, mô tả tiếng Việt, tên Route giữ nguyên; Route #2 “Lộ trình nháp lưu dở” vẫn DRAFT, 0 điểm. Không phát sinh Session/Route/POI mới ở lượt kiểm tra restart.

**Hai bước nghiệm thu còn chờ (V12 và dữ liệu qua restart) đã hoàn tất.** Bằng chứng này áp dụng cho phạm vi Session/Routes đã duyệt và luồng UC04 xem/bắt đầu; không mở rộng thành kết luận hoàn tất toàn bộ đồ án hoặc các chức năng được hoãn bên dưới.

### Giới hạn phạm vi

Cập nhật sau phạm vi Session/Routes: chức năng nghe thủ công đã được người dùng xác nhận. Phần nối tự phát GPS của UC02 được triển khai và kiểm chứng riêng trong [UC02_GPS_IMPLEMENTATION.md](./UC02_GPS_IMPLEMENTATION.md); không dùng kết quả Session/Routes để thay thế nghiệm thu audio/GPS.

Sau restart để nạp UC02 (backend PID **38392**), đã chạy lại kiểm tra tồn tại Session #1, Route #1, thứ tự [2,1], hiển thị public và bắt đầu đi bộ: **5/5 qua**. Không chỉnh các bản ghi này trong lượt nghiệm thu UC02.

Chưa mở rộng phân quyền theo yêu cầu trước. Kéo thả/offline đầy đủ để sau. UC04 trong lượt này bổ sung bắt đầu dẫn đường GPS, chặng kế tiếp/bỏ qua/kết thúc và mở thuyết minh hiện tại; cơ chế UC02 tự phát, dwell/cooldown/chống chồng âm thanh vẫn là hạng mục riêng, không tuyên bố hoàn tất toàn bộ UC02/UC04 trong PRD.
