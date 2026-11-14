# Sưu liệu tiến trình 1.0 — Định vị và hiển thị POI

Đối chiếu ngày 09/11/2026 với source nhánh `develop`, commit `8246558`.

Tài liệu giải thích chức năng, ý nghĩa từng luồng dữ liệu và thuật toán xử lý để lần theo code khi trình bày đồ án. Phạm vi gồm 1.1–1.4 trong DFD mức 2 do người dùng cung cấp; chỉ mô tả điểm giao tiếp với tiến trình 2.0 và 3.0. Không thay đổi mã ứng dụng, API hoặc database.

Số dòng là vị trí bắt đầu của phần code liên quan ở phiên bản trên; có thể thay đổi sau khi sửa source. Một tiến trình DFD là một nhóm xử lý nghiệp vụ, không nhất thiết tương ứng với một file hoặc một hàm.

## 1. Chức năng và phạm vi

Tiến trình 1.0 nhận vị trí thiết bị, tải bản đồ nền và danh mục POI đã xuất bản, tính khoảng cách để hiển thị các POI trong phạm vi 500 m, đồng thời đánh giá POI đủ điều kiện tự phát thuyết minh. Việc tải/phát nội dung thuyết minh thuộc tiến trình 3.0; tính đường đi bộ thuộc tiến trình 2.0.

Căn cứ nghiệp vụ trong [PRD](../../Nhom2_CNPM.docx): F-01, US-01, US-02, UC01 và phần nhận diện POI tự phát của UC02. Các phạm vi đã loại bỏ Session và UC07 không ảnh hưởng đến phần xử lý vị trí này. Tài liệu Word đang lưu vẫn còn các mục thuộc phạm vi cũ; tài liệu này không chỉnh sửa Word.

| Mã | Chức năng | Đầu vào chính | Đầu ra chính | Điểm bắt đầu trong code |
|---|---|---|---|---|
| 1.1 | Xác định vị trí hiện tại | Yêu cầu mở ứng dụng; quyền và dữ liệu định vị của thiết bị | Vị trí hiện tại hoặc trạng thái lỗi GPS | [Hook lấy vị trí](../../voiceover-frontend/src/useTourLocation.js:3) |
| 1.2 | Truy xuất và hiển thị bản đồ nền | Vị trí/khung nhìn bản đồ; dữ liệu bản đồ từ dịch vụ ngoài | Bản đồ nền và lớp vị trí người dùng | [Component bản đồ](../../voiceover-frontend/src/VoiceMapScreens.jsx:58) |
| 1.3 | Xác định và hiển thị POI lân cận | Danh mục POI đã xuất bản; vị trí hiện tại | POI kèm khoảng cách; danh sách trong 500 m | [Tải POI](../../voiceover-frontend/src/VoiceMapScreens.jsx:350), [lọc lân cận](../../voiceover-frontend/src/VoiceMapScreens.jsx:369) |
| 1.4 | Kiểm tra điều kiện tự phát | Vị trí, độ chính xác, thời điểm GPS; POI và bán kính; trạng thái lượt ghé/bộ phát | POI được chọn để đề nghị tự phát hoặc không có POI | [Máy trạng thái nhận diện](../../voiceover-frontend/src/autoNarration.js:37), [vòng đánh giá](../../voiceover-frontend/src/useTourNarration.js:23) |

Ứng dụng khách thực sự được gắn vào giao diện tại [điểm khởi động React](../../voiceover-frontend/src/main.jsx:7). Các trạng thái dùng chung của khách bắt đầu tại [component ứng dụng khách](../../voiceover-frontend/src/VoiceMapScreens.jsx:326).

## 2. Từ điển dữ liệu

### 2.1. Vị trí thiết bị — GPSFix

| Trường | Ý nghĩa | Đơn vị/kiểm tra |
|---|---|---|
| `lat` | Vĩ độ hiện tại của người dùng | Độ; số hữu hạn trong [-90, 90] |
| `lng` | Kinh độ hiện tại của người dùng | Độ; số hữu hạn trong [-180, 180] |
| `accuracy` | Độ chính xác ước tính do thiết bị cung cấp | Mét; không âm; tự phát chỉ chấp nhận không quá 30 m |
| `timestamp` | Thời điểm ghi nhận vị trí | Mili giây Unix; dùng để phát hiện bản tin GPS cũ |

Dữ liệu được tạo tại [cập nhật vị trí](../../voiceover-frontend/src/useTourLocation.js:8). Kiểm tra đủ điều kiện tự phát nằm tại [kiểm tra GPS](../../voiceover-frontend/src/autoNarration.js:21).

GPS hợp lệ để lưu vào trạng thái giao diện và GPS đủ tốt để tự phát là hai mức kiểm tra khác nhau. Vị trí có sai số lớn hơn 30 m vẫn có thể được hiển thị và dùng để tính khoảng cách, nhưng không được kích hoạt tự phát.

### 2.2. POI và dữ liệu khoảng cách

| Dữ liệu | Ý nghĩa |
|---|---|
| `id` | Mã POI; frontend yêu cầu số nguyên an toàn và lớn hơn 0 |
| `name` | Tên POI phục vụ hiển thị |
| `latitude`, `longitude` | Tọa độ cố định của POI, khác với vị trí hiện tại của du khách |
| `status` | Chỉ nhận POI có trạng thái `PUBLISHED` cho catalog khách |
| `radiusM` | Bán kính kích hoạt riêng của POI; nhánh nhận diện dùng 50 m nếu giá trị không dương |
| `thumbnailUrl` | Đường dẫn ảnh dùng trong danh sách POI, nếu có |
| `distanceMeters` | Khoảng cách thẳng giữa vị trí người dùng và POI, tính bằng Haversine; không phải quãng đường đi bộ |
| `mapPois` | Danh mục POI hợp lệ kèm khoảng cách nếu đã có GPS; bản đồ dùng danh sách này để vẽ marker |
| `pois` | Danh sách lân cận sau khi lọc khoảng cách không quá 500 m và sắp xếp từ gần đến xa |

Ánh xạ bảng POI nằm tại [thực thể POI](../../src/main/java/com/voiceover/backend/entities/Poi.java:8). Tọa độ dùng `BigDecimal` và được trả về JSON dạng chuỗi tại [khai báo tọa độ](../../src/main/java/com/voiceover/backend/entities/Poi.java:22); mã tính khoảng cách chuyển chúng thành số. Không sửa hoặc ghi lại tọa độ database trong tiến trình này.

### 2.3. Dữ liệu bản đồ và sự kiện sử dụng

- **Bản đồ nền:** các ảnh tile được Leaflet tải theo mức zoom và ô bản đồ; không phải danh sách POI lấy từ database. Địa chỉ tile được cấu hình tại [lớp bản đồ nền](../../voiceover-frontend/src/VoiceMapScreens.jsx:72).
- **`APP_OPEN`:** sự kiện mở ứng dụng; dùng định danh lượt mở trong `sessionStorage` để chống gửi trùng cùng lượt ở phía ứng dụng. `sessionStorage` ở đây là bộ nhớ trình duyệt, không phải nghiệp vụ quản lý Session đã bỏ khỏi phạm vi.
- **`APP_ACTIVE`:** nhịp báo ứng dụng đang hoạt động khi trang đang hiển thị; mỗi nhịp có định danh sự kiện riêng.
- **`clientId`:** định danh ẩn danh được lưu trong `localStorage`, gửi lên backend và ánh xạ thành `anonClientId`; không phải tài khoản hay bằng chứng chắc chắn về một người thật duy nhất.
- **`playbackId`:** tên trường dùng chung để định danh một lần phát hoặc một sự kiện ứng dụng. Với `APP_OPEN`/`APP_ACTIVE`, trường này không có nghĩa đang phát audio.
- **`occurredAt`:** thời điểm backend tạo bản ghi sự kiện; khác với thời điểm của bản tin GPS.

Cấu trúc nhận sự kiện nằm tại [đầu vào sự kiện](../../src/main/java/com/voiceover/backend/controllers/PlaybackEventController.java:26), cấu trúc lưu tại [thực thể sự kiện sử dụng](../../src/main/java/com/voiceover/backend/entities/UsageEvent.java:8).

## 3. Ý nghĩa toàn bộ 15 luồng dữ liệu trong sơ đồ

Các mã L01–L15 được đặt cho sưu liệu, không phải mã có sẵn trong source. Dòng có ghi “khác cách chia trong code” không được hiểu là đã có một hàm riêng thực hiện đúng mũi tên đó.

| Mã | Nguồn → Đích và tên luồng | Ý nghĩa/dữ liệu trao đổi | Vị trí code hoặc đối chiếu |
|---|---|---|---|
| L01 | Du khách → 1.1: Yêu cầu xem bản đồ | Người dùng truy cập giao diện khách. Code khởi tạo màn hình bản đồ và bắt đầu đăng ký lấy vị trí; không có API nghiệp vụ riêng tên “xem bản đồ”. | [Màn hình mặc định](../../voiceover-frontend/src/VoiceMapScreens.jsx:326), [gọi hook vị trí](../../voiceover-frontend/src/VoiceMapScreens.jsx:340) |
| L02 | 1.1 → GPS: Yêu cầu cung cấp vị trí | Đăng ký theo dõi định vị qua Geolocation API của trình duyệt; đề nghị độ chính xác cao, timeout 15 giây, không dùng bản tin cache theo tùy chọn `maximumAge: 0`. | [Đăng ký theo dõi GPS](../../voiceover-frontend/src/useTourLocation.js:8), [tùy chọn](../../voiceover-frontend/src/useTourLocation.js:17) |
| L03 | GPS → 1.1: Tọa độ và độ chính xác GPS | Callback trả `latitude`, `longitude`, `accuracy`, `timestamp`; hệ thống kiểm tra rồi lưu thành GPSFix. Lỗi/quyền bị từ chối đi vào callback lỗi. | [Đọc và kiểm tra vị trí](../../voiceover-frontend/src/useTourLocation.js:9), [xử lý lỗi](../../voiceover-frontend/src/useTourLocation.js:14) |
| L04 | 1.1 → 1.2: Vị trí hiện tại | Truyền `location` để bản đồ có thể căn vị trí và vẽ điểm người dùng. Bản đồ vẫn được tạo trước khi GPS trả kết quả. | [Truyền vị trí cho bản đồ](../../voiceover-frontend/src/VoiceMapScreens.jsx:273), [căn vị trí](../../voiceover-frontend/src/VoiceMapScreens.jsx:107) |
| L05 | 1.1 → 1.3: Vị trí hiện tại | Dùng `lat`, `lng` của du khách để tính lại khoảng cách tới từng POI khi vị trí hoặc catalog thay đổi. | [Tạo dữ liệu khoảng cách](../../voiceover-frontend/src/VoiceMapScreens.jsx:369), [Haversine](../../voiceover-frontend/src/autoNarration.js:6) |
| L06 | 1.1 → 1.4: Vị trí và độ chính xác GPS | Truyền đầy đủ GPSFix, gồm cả `timestamp`, để đánh giá vị trí mới/cũ, sai số và thời gian ở trong vùng. | [Truyền vị trí cho cơ chế tự phát](../../voiceover-frontend/src/VoiceMapScreens.jsx:341), [cập nhật nhận diện](../../voiceover-frontend/src/useTourNarration.js:26) |
| L07 | 1.2 → MAP: Yêu cầu bản đồ | Leaflet phát sinh các yêu cầu HTTP lấy tile ảnh bản đồ theo khung nhìn/zoom. Trong 1.0 đây là tải bản đồ nền, chưa phải yêu cầu Directions tính đường đi. | [Khởi tạo bản đồ](../../voiceover-frontend/src/VoiceMapScreens.jsx:71), [địa chỉ tile](../../voiceover-frontend/src/VoiceMapScreens.jsx:72) |
| L08 | MAP → 1.2: Dữ liệu bản đồ nền | Dịch vụ trả tile ảnh và Leaflet ghép chúng thành nền bản đồ. Phần thư viện xử lý nhận tile không nằm trong một controller của dự án. | [Gắn lớp tile vào bản đồ](../../voiceover-frontend/src/VoiceMapScreens.jsx:72) |
| L09 | 1.2 → 1.3: Dữ liệu bản đồ nền | Có thể hiểu ở mức giao diện là nền để thể hiện POI. **Khác cách chia trong code:** thuật toán xác định POI gần không đọc ảnh tile; bản đồ nền và kết quả khoảng cách được kết hợp khi vẽ giao diện. | [Tính khoảng cách độc lập](../../voiceover-frontend/src/VoiceMapScreens.jsx:369), [vẽ marker POI](../../voiceover-frontend/src/VoiceMapScreens.jsx:131) |
| L10 | D1 → 1.3: Thông tin POI đã xuất bản | Backend đọc bảng POI, lấy các bản ghi `PUBLISHED` có tọa độ hợp lệ và trả catalog; frontend kiểm tra lại trước khi dùng. Luồng đi qua API/service/repository, không phải frontend kết nối MySQL trực tiếp. | [Frontend gọi API](../../voiceover-frontend/src/VoiceMapScreens.jsx:355), [controller](../../src/main/java/com/voiceover/backend/controllers/VoiceMapController.java:77), [service](../../src/main/java/com/voiceover/backend/services/RouteSessionService.java:71), [repository](../../src/main/java/com/voiceover/backend/repositories/PoiRepository.java:13) |
| L11 | 1.3 → 1.4: POI lân cận và bán kính kích hoạt | Cung cấp POI, tọa độ, trạng thái và `radiusM` để đánh giá vùng kích hoạt. **Code thực tế truyền toàn bộ catalog hợp lệ**, không truyền riêng danh sách đã lọc 500 m; khoảng cách được cơ chế tự phát tính lại. | [Đầu vào catalog](../../voiceover-frontend/src/VoiceMapScreens.jsx:341), [lọc POI hợp lệ](../../voiceover-frontend/src/autoNarration.js:51), [tính khoảng cách/bán kính](../../voiceover-frontend/src/autoNarration.js:55) |
| L12 | 1.3 → Du khách: Vị trí và POI lân cận | Hiển thị chấm vị trí, marker POI, vòng bán kính kích hoạt và danh sách POI trong 500 m kèm khoảng cách. Hiện marker trên bản đồ dùng toàn bộ catalog hợp lệ, còn danh sách bên dưới dùng kết quả lọc 500 m. | [Vẽ người dùng](../../voiceover-frontend/src/VoiceMapScreens.jsx:115), [vẽ POI](../../voiceover-frontend/src/VoiceMapScreens.jsx:131), [danh sách](../../voiceover-frontend/src/VoiceMapScreens.jsx:301), [lọc 500 m](../../voiceover-frontend/src/VoiceMapScreens.jsx:370) |
| L13 | 1.1 → P2: Vị trí hiện tại | Cùng một trạng thái `location` được cấp cho màn hình chọn lộ trình và màn hình dẫn đường; tiến trình 2.0 dùng vị trí đó để xử lý nghiệp vụ riêng. Không cần ghi GPS vào database để chuyển dữ liệu giữa hai tiến trình. | [Truyền cho màn hình lộ trình](../../voiceover-frontend/src/VoiceMapScreens.jsx:404), [truyền cho dẫn đường](../../voiceover-frontend/src/VoiceMapScreens.jsx:407) |
| L14 | 1.4 → P3: POI đủ điều kiện tự phát | Chọn một POI hợp lệ gần nhất và gửi mã POI cho bộ phát cùng ngôn ngữ hiện tại và loại kích hoạt `AUTO_PLAY`. Đủ điều kiện/đặt chỗ chưa có nghĩa audio đã phát thành công. | [Chọn POI](../../voiceover-frontend/src/autoNarration.js:87), [bàn giao bộ phát](../../voiceover-frontend/src/useTourNarration.js:34) |
| L15 | 1.3 → D4: Sự kiện sử dụng | Ý nghĩa nghiệp vụ là ghi nhận mở/duy trì sử dụng ứng dụng. **Khác cách chia trong code:** các sự kiện `APP_OPEN`, `APP_ACTIVE` được phát từ vòng đời ứng dụng khách, không được tạo mỗi lần tính POI gần. Không chứa tọa độ GPS và không phải lượt nghe. | [Vòng đời ghi nhận](../../voiceover-frontend/src/VoiceMapScreens.jsx:342), [tạo sự kiện](../../voiceover-frontend/src/narrationApi.js:17), [gửi API](../../voiceover-frontend/src/narrationApi.js:54), [lưu database](../../src/main/java/com/voiceover/backend/controllers/PlaybackEventController.java:55) |

## 4. Thuật toán xử lý tiến trình 1.1

### Chức năng

Theo dõi vị trí liên tục trong thời gian ứng dụng khách được gắn trên trang, chuẩn hóa dữ liệu vị trí và cung cấp cho các phần xử lý tiếp theo.

### Các bước và nơi xử lý

| Bước | Xử lý | Code |
|---|---|---|
| 1 | Tạo trạng thái vị trí rỗng và thông báo lỗi rỗng. | [Khởi tạo state](../../voiceover-frontend/src/useTourLocation.js:4) |
| 2 | Kiểm tra trình duyệt có hỗ trợ Geolocation API. Nếu không, báo thiết bị không hỗ trợ. | [Kiểm tra API](../../voiceover-frontend/src/useTourLocation.js:7) |
| 3 | Đăng ký theo dõi vị trí; quá trình cấp quyền do trình duyệt xử lý. | [Theo dõi vị trí](../../voiceover-frontend/src/useTourLocation.js:8) |
| 4 | Với mỗi kết quả, kiểm tra tọa độ và độ chính xác là số hữu hạn, đúng miền giá trị. | [Kiểm tra dữ liệu](../../voiceover-frontend/src/useTourLocation.js:9) |
| 5 | Nếu hợp lệ, lưu GPSFix và xóa lỗi; nếu không hợp lệ, xóa vị trí và đặt lỗi. | [Lưu kết quả](../../voiceover-frontend/src/useTourLocation.js:10) |
| 6 | Nếu bị từ chối quyền hoặc không nhận được vị trí, xóa vị trí và hiển thị thông báo tương ứng. | [Callback lỗi](../../voiceover-frontend/src/useTourLocation.js:14) |
| 7 | Khi component bị gỡ, hủy đăng ký theo dõi vị trí. | [Dọn watcher](../../voiceover-frontend/src/useTourLocation.js:18) |

```text
KHI ứng dụng khách được gắn vào trang:
    vị_trí := rỗng
    NẾU không có API định vị:
        đặt lỗi không hỗ trợ và dừng đăng ký
    NGƯỢC LẠI đăng ký theo dõi vị trí

MỖI KHI nhận kết quả:
    đọc vĩ_độ, kinh_độ, độ_chính_xác, thời_điểm
    NẾU tọa độ/sai số không hợp lệ:
        xóa vị_trí; đặt lỗi
    NGƯỢC LẠI:
        lưu vị_trí; xóa lỗi

KHI nhận lỗi định vị:
    xóa vị_trí; đặt thông báo phù hợp

KHI ứng dụng khách bị gỡ:
    hủy đăng ký theo dõi
```

Không có GPS thì catalog và bản đồ vẫn có thể được tải, người dùng vẫn có thể chọn POI để nghe thủ công. Danh sách lân cận cần có khoảng cách để đưa POI vào kết quả 500 m.

## 5. Thuật toán xử lý tiến trình 1.2

### Chức năng

Tạo bản đồ nền, tải các ô bản đồ và vẽ lớp vị trí người dùng. Trong tiến trình này không dùng dịch vụ Directions để quyết định POI nào gần người dùng.

| Bước | Xử lý | Code |
|---|---|---|
| 1 | Tạo bản đồ Leaflet một lần; ban đầu dùng tâm mặc định `[10.7769, 106.7009]`, zoom 13. | [Khởi tạo bản đồ](../../voiceover-frontend/src/VoiceMapScreens.jsx:69) |
| 2 | Gắn lớp tile OpenStreetMap; Leaflet tự tải tile theo khung nhìn và zoom. | [Lớp bản đồ nền](../../voiceover-frontend/src/VoiceMapScreens.jsx:72) |
| 3 | Cung cấp các thao tác phóng to, thu nhỏ, về vị trí và căn toàn bộ POI. | [Các thao tác bản đồ](../../voiceover-frontend/src/VoiceMapScreens.jsx:77) |
| 4 | Khi vị trí/danh mục thay đổi, cập nhật lớp người dùng và POI. Có các cờ để tránh căn khung lặp lại không cần thiết. | [Cập nhật lớp](../../voiceover-frontend/src/VoiceMapScreens.jsx:101) |
| 5 | Vẽ chấm vị trí người dùng khi có GPS; khi phù hợp, căn khung toàn bộ POI và vị trí người dùng. | [Chấm người dùng](../../voiceover-frontend/src/VoiceMapScreens.jsx:115), [căn khung POI](../../voiceover-frontend/src/VoiceMapScreens.jsx:120) |
| 6 | Khi component bản đồ bị gỡ, hủy bản đồ và reset tham chiếu. | [Dọn bản đồ](../../voiceover-frontend/src/VoiceMapScreens.jsx:94) |

```text
TẠO bản đồ tại tâm mặc định
GẮN lớp bản đồ nền

KHI khung nhìn hoặc zoom thay đổi:
    thư viện tải các tile cần thiết

KHI vị trí hoặc POI thay đổi:
    cập nhật các lớp dữ liệu hiển thị
    nếu có vị trí: vẽ điểm người dùng
    nếu cần: căn khung các điểm

KHI bản đồ bị gỡ: giải phóng bản đồ
```

Source chưa có nhánh cảnh báo lỗi tile riêng trong component bản đồ. Không mô tả một chức năng tự thay nhà cung cấp bản đồ hoặc bản đồ offline như thể đã được triển khai.

## 6. Thuật toán xử lý tiến trình 1.3

### Chức năng

Đọc catalog POI được phép phục vụ khách, tính khoảng cách và tạo kết quả hiển thị. Đây là luồng đang được màn hình khách sử dụng thực tế.

### 6.1. Lấy catalog từ database

1. Frontend gọi `GET /api/public/pois` tại [yêu cầu tải catalog](../../voiceover-frontend/src/VoiceMapScreens.jsx:355).
2. API chuyển xử lý cho service tại [controller catalog](../../src/main/java/com/voiceover/backend/controllers/VoiceMapController.java:77).
3. Service đọc POI trạng thái `PUBLISHED` và loại tọa độ không hợp lệ tại [đọc POI công khai](../../src/main/java/com/voiceover/backend/services/RouteSessionService.java:71). Repository truy vấn theo trạng thái tại [repository POI](../../src/main/java/com/voiceover/backend/repositories/PoiRepository.java:13).
4. Frontend kiểm tra phản hồi có phải mảng, sau đó lọc lại trạng thái, mã và tọa độ bằng [kiểm tra POI](../../voiceover-frontend/src/autoNarration.js:14).
5. Khi lỗi, frontend đặt thông báo và xóa catalog; khi tải xong, kết thúc trạng thái loading tại [xử lý phản hồi](../../voiceover-frontend/src/VoiceMapScreens.jsx:356).
6. Catalog được tải lại mỗi 30 giây nếu trang đang hiển thị và khi quay lại tab; yêu cầu cũ bị hủy khi có yêu cầu mới hoặc component bị gỡ tại [quản lý vòng tải](../../voiceover-frontend/src/VoiceMapScreens.jsx:353).

### 6.2. Tính POI lân cận và hiển thị

```text
KHI catalog hoặc vị trí thay đổi:
    danh_sách_bản_đồ := []
    CHO mỗi POI hợp lệ:
        nếu có vị trí:
            khoảng_cách := Haversine(vị_trí, tọa_độ_POI)
        nếu chưa có vị trí:
            khoảng_cách := chưa xác định
        thêm {POI, khoảng_cách} vào danh_sách_bản_đồ

    danh_sách_lân_cận := các phần tử có khoảng_cách <= 500 m
    sắp xếp danh_sách_lân_cận theo khoảng cách tăng dần

    vẽ marker bằng danh_sách_bản_đồ
    hiển thị danh sách bên dưới bằng danh_sách_lân_cận
    cung cấp catalog POI hợp lệ cho phần nhận diện tự phát
```

Điểm tính và lọc: [tạo danh sách khoảng cách](../../voiceover-frontend/src/VoiceMapScreens.jsx:369). Danh sách được hiển thị tại [thẻ POI lân cận](../../voiceover-frontend/src/VoiceMapScreens.jsx:301), marker được tạo tại [lớp POI](../../voiceover-frontend/src/VoiceMapScreens.jsx:131).

### 6.3. Công thức khoảng cách

Code dùng Haversine tại [hàm khoảng cách](../../voiceover-frontend/src/autoNarration.js:6):

```text
đổi vĩ độ và chênh lệch tọa độ từ độ sang radian
a := sin²(chênh_vĩ_độ / 2)
     + cos(vĩ_độ_người_dùng) × cos(vĩ_độ_POI) × sin²(chênh_kinh_độ / 2)
d := 6.371.000 × 2 × atan2(sqrt(a), sqrt(max(0, 1 - a)))
```

`d` có đơn vị mét. Khoảng cách này phục vụ lân cận/kích hoạt; khoảng cách đi bộ theo đường phố là kết quả Directions thuộc tiến trình 2.0.

### 6.4. API lân cận khác với luồng UI đang dùng

Backend cũng có `GET /api/public/pois/nearby?lat=...&lng=...&radiusM=500`, tại [controller nearby](../../src/main/java/com/voiceover/backend/controllers/VoiceMapController.java:39) và [service nearby](../../src/main/java/com/voiceover/backend/services/PoiService.java:28). API này tự lọc khoảng cách, trả cờ trong vùng kích hoạt và tải trước.

Tuy nhiên, màn hình khách đang đọc catalog qua `GET /api/public/pois`, sau đó lọc 500 m ở frontend. Không trình bày API nearby như thể đó là request màn hình hiện tại đang gửi.

## 7. Thuật toán xử lý tiến trình 1.4

### 7.1. Điều kiện và tham số

| Điều kiện/tham số | Giá trị hiện tại | Ý nghĩa và nguồn |
|---|---|---|
| Vùng lân cận hiển thị | 500 m | Phù hợp F-01; [lọc danh sách](../../voiceover-frontend/src/VoiceMapScreens.jsx:370) |
| Vùng tải trước | 300 m | Theo F-01; chuẩn bị nội dung, chưa phải phát; [điều phối tải trước](../../voiceover-frontend/src/useTourNarration.js:28) |
| Bán kính kích hoạt | `radiusM` từng POI; nhánh nhận diện dự phòng 50 m | Theo vùng kích hoạt F-01; [đọc bán kính](../../voiceover-frontend/src/autoNarration.js:57) |
| Sai số cho tự phát | Không quá 30 m | Theo F-01; [kiểm tra độ chính xác](../../voiceover-frontend/src/autoNarration.js:21) |
| Thời gian trong vùng | Ít nhất 3 giây liên tục theo trạng thái đánh giá | Theo F-01; [hằng số thời gian](../../voiceover-frontend/src/autoNarration.js:2), [chọn ứng viên](../../voiceover-frontend/src/autoNarration.js:75) |
| Tuổi bản tin GPS | Không quá 5 giây và không nằm trong tương lai | Quy tắc kiểm tra dữ liệu mới trong code; [kiểm tra thời điểm](../../voiceover-frontend/src/autoNarration.js:25) |
| Ngưỡng ra khỏi vùng ghé | Lớn hơn `radiusM + 20 m` | Hysteresis theo F-01; [cập nhật lượt ghé](../../voiceover-frontend/src/autoNarration.js:59) |
| Chống tự phát lặp | 30 phút cho mỗi POI | Theo F-01; chỉ ghi sau khi thực sự phát; [cập nhật cooldown](../../voiceover-frontend/src/autoNarration.js:96) |
| Chu kỳ đánh giá | 250 ms | Lựa chọn triển khai; [bộ định thời](../../voiceover-frontend/src/useTourNarration.js:37) |
| Lùi thử sau lỗi phát | 30 giây | Lựa chọn triển khai; [trì hoãn ứng viên lỗi](../../voiceover-frontend/src/autoNarration.js:106) |

**Không nhầm hai khoảng 5 giây:** code giới hạn tuổi bản tin GPS ở 5 giây. Yêu cầu US-02 “bắt đầu phát trong 5 giây sau khi đủ điều kiện” là một yêu cầu thời gian phản hồi khác; chưa có đo thực tế để kết luận đáp ứng SLA này.

### 7.2. Trạng thái dùng trong thuật toán

- `visits`: trạng thái lượt ghé từng POI, gồm thời điểm vào vùng, đã tiêu thụ lượt ghé hay chưa và cờ ở trong vùng.
- `played`: thời điểm tự phát thật gần nhất theo POI; được khôi phục/lưu cooldown trên thiết bị.
- `reserved`: POI đang được giữ chỗ cho yêu cầu phát; ngăn chọn trùng trong lúc chờ nội dung.
- `deferred`: thời điểm được thử lại sau lỗi.
- Trạng thái bộ phát: dùng để tránh phát chồng khi có nội dung đang tải, bắt đầu, phát, tạm dừng hoặc đang chờ tương tác.

Khởi tạo trạng thái tại [constructor bộ nhận diện](../../voiceover-frontend/src/autoNarration.js:38); xác định bộ phát bận tại [kiểm tra trạng thái player](../../voiceover-frontend/src/narrationPlayer.js:24).

### 7.3. Cập nhật lượt ghé

Mỗi vòng đánh giá gọi [cập nhật nhận diện](../../voiceover-frontend/src/autoNarration.js:48):

```text
NẾU khoảng cách thời gian giữa hai bản tin GPS vượt 5 giây:
    xóa thời điểm bắt đầu ở trong vùng

cập nhật vị trí và danh mục POI hợp lệ
xóa trạng thái lượt ghé của POI không còn khả dụng

NẾU GPS không đủ chính xác hoặc đã cũ:
    xóa các mốc bắt đầu ở trong vùng; dừng cập nhật lượt ghé

CHO mỗi POI:
    d := khoảng cách Haversine
    r := bán kính kích hoạt của POI, dự phòng 50 m
    NẾU d > r + 20:
        reset lượt ghé, cho phép nhận diện lại ở lần ghé sau
    NGƯỢC LẠI NẾU d <= r:
        đánh dấu đang trong vùng
        nếu lượt ghé chưa dùng và chưa có mốc vào: lưu mốc hiện tại
    NGƯỢC LẠI:
        xóa mốc bắt đầu ở trong vùng
        giữ trạng thái lượt ghé để chống dao động ở biên
```

Vùng đệm 20 m không cho phép tính đủ 3 giây khi đang ở ngoài bán kính thật. Ra ngoài bán kính thật làm đứt thời gian chờ; ra xa hơn bán kính cộng 20 m mới reset đầy đủ lượt ghé.

### 7.4. Chọn POI và bàn giao cho tiến trình 3.0

1. Lọc ứng viên: GPS tốt; ở trong bán kính; đủ 3 giây; chưa dùng lượt ghé; hết cooldown và hết trì hoãn lỗi. Xem [danh sách ứng viên](../../voiceover-frontend/src/autoNarration.js:75).
2. Sắp xếp ứng viên theo khoảng cách tăng dần; nếu bằng nhau, ưu tiên mã POI nhỏ hơn. Xem [thứ tự ưu tiên](../../voiceover-frontend/src/autoNarration.js:84).
3. Nếu player bận hoặc đã giữ chỗ một POI, không chọn thêm. Nếu rảnh, giữ chỗ POI đầu tiên. Xem [đặt chỗ](../../voiceover-frontend/src/autoNarration.js:87).
4. Chỉ gửi yêu cầu tự phát khi trang không bị ẩn. Khi trang bị ẩn, reset thời gian chờ. Xem [điều phối theo trạng thái trang](../../voiceover-frontend/src/useTourNarration.js:24).
5. Bàn giao bằng mã POI, ngôn ngữ đang chọn và loại kích hoạt `AUTO_PLAY` tại [gọi bộ phát](../../voiceover-frontend/src/useTourNarration.js:34).
6. Bộ phát kiểm tra lại điều kiện sau khi tải guide và lúc bắt đầu phát. Nếu người dùng đã ra khỏi vùng hoặc GPS không còn hợp lệ thì hủy yêu cầu. Xem [kiểm tra trước phát](../../voiceover-frontend/src/narrationPlayer.js:94), [kiểm tra lúc bắt đầu](../../voiceover-frontend/src/narrationPlayer.js:62).
7. Chỉ khi audio/speech thực sự bắt đầu, callback mới cập nhật lượt ghé và cooldown. File audio dùng sự kiện `onplaying` tại [bắt đầu phát file](../../voiceover-frontend/src/narrationPlayer.js:103), nối về nhận diện tại [callback player](../../voiceover-frontend/src/useTourNarration.js:14).
8. Khi kết thúc/bỏ qua/lỗi, giải phóng giữ chỗ. Ứng viên khác được đánh giá lại theo vị trí hiện tại, không tự phát mù một hàng đợi cũ. Xem [callback kết thúc](../../voiceover-frontend/src/useTourNarration.js:15).

```text
MỖI 250 ms:
    cập nhật GPS, danh mục và trạng thái lượt ghé
    nếu GPS tốt và trang đang hiện: yêu cầu tải trước nội dung trong 300 m
    nếu trang bị ẩn: reset thời gian chờ và dừng vòng xử lý này
    nếu POI đang chờ phát không còn đủ điều kiện: hủy yêu cầu
    nếu bộ phát rảnh và chưa giữ chỗ:
        chọn POI đủ điều kiện gần nhất
        nếu có: giữ chỗ và chuyển yêu cầu AUTO_PLAY sang tiến trình 3.0

KHI tiến trình 3.0 báo bắt đầu phát thật:
    đánh dấu lượt ghé đã dùng
    nếu là tự phát: lưu thời điểm cooldown theo POI

KHI tiến trình 3.0 báo kết thúc/bỏ qua/lỗi:
    giải phóng giữ chỗ
    lỗi thì áp dụng thời gian trì hoãn trước lần thử tiếp
```

Tải trước do [bộ preload](../../voiceover-frontend/src/narrationApi.js:60) xử lý, tối đa 3 yêu cầu đang chờ. Tải trước không tạo lượt nghe. Tải trước thành công cũng không có nghĩa thiết bị đã cho phép autoplay.

## 8. Ghi nhận dữ liệu sử dụng vào D4

Đây là phần triển khai của luồng L15, không phải một tiến trình con mới được tự thêm vào sơ đồ.

1. Khi ứng dụng khách được gắn vào trang, gọi ghi nhận mở ứng dụng. Khi trang đang hiển thị, gửi một nhịp hoạt động ngay và tiếp tục mỗi 60 giây; khi quay lại tab cũng gửi nhịp. Nguồn: [vòng ghi nhận sử dụng](../../voiceover-frontend/src/VoiceMapScreens.jsx:342).
2. Tạo/đọc định danh khách ẩn danh tại [định danh phía trình duyệt](../../voiceover-frontend/src/narrationApi.js:6). Ghi nhận mở tại [sự kiện mở ứng dụng](../../voiceover-frontend/src/narrationApi.js:17), nhịp hoạt động tại [sự kiện hoạt động](../../voiceover-frontend/src/narrationApi.js:32).
3. Gửi `POST /api/public/playback-events` với loại sự kiện, định danh khách và định danh sự kiện. Với hai loại này, không gửi POI, Route hay ngôn ngữ của một lần nghe. Nguồn: [gửi sự kiện](../../voiceover-frontend/src/narrationApi.js:54).
4. Backend kiểm tra dữ liệu, chống giả sự kiện sử dụng thành sự kiện nghe tại [kiểm tra sự kiện ứng dụng](../../src/main/java/com/voiceover/backend/controllers/PlaybackEventController.java:30).
5. Chống trùng theo bộ ba định danh khách, định danh sự kiện và loại sự kiện; sau đó lưu bản ghi. Nguồn: [chống trùng và lưu](../../src/main/java/com/voiceover/backend/controllers/PlaybackEventController.java:50). Unique index hỗ trợ chống trùng sau khởi động lại nằm tại [migration chống trùng](../../src/main/resources/db/migration/V17__deduplicate_usage_events.sql:4).
6. Lỗi gửi sự kiện không chặn chức năng xem bản đồ, vì frontend chủ động bỏ qua lỗi telemetry tại [nơi gửi khi mở ứng dụng](../../voiceover-frontend/src/VoiceMapScreens.jsx:343).

Không có ghi nhận “mỗi lần gọi API POI thì tăng một người”. Sự kiện nghe `AUTO_PLAY`/`TAP_POI` do bộ phát ở tiến trình 3.0 gửi khi bắt đầu phát thật, xem [ghi nhận bắt đầu phát](../../voiceover-frontend/src/narrationPlayer.js:62).

## 9. Những chỗ sơ đồ mức 2 cần diễn giải khi đối chiếu source

| Chỗ trong sơ đồ | Kết quả đối chiếu | Cách trình bày/đề nghị làm rõ |
|---|---|---|
| 1.2 → 1.3: dữ liệu bản đồ nền | Tính POI lân cận không phụ thuộc tile bản đồ. Các nhánh tải map và tải catalog hoạt động độc lập. | Nếu giữ mũi tên để thể hiện kết hợp hiển thị, gọi 1.3 là “Xác định và hiển thị POI lân cận”. Không mô tả thuật toán đọc POI từ ảnh map. |
| 1.3 → 1.4: POI lân cận | Bộ nhận diện hiện nhận toàn bộ catalog hợp lệ và tính lại khoảng cách, không chỉ danh sách UI trong 500 m. | Nhãn sát source hơn: “POI đã xuất bản, tọa độ và bán kính kích hoạt”. |
| 1.3 → D4: sự kiện sử dụng | Sự kiện được tạo ở vòng đời ứng dụng, độc lập với thành công/thất bại khi tìm POI. | Giải thích đây là trách nhiệm ghi nhận sử dụng của giao diện khách, không tăng lượt nghe hay tăng người theo mỗi kết quả tìm kiếm. |
| 1.1 → 1.4: GPS | Code cần cả thời điểm bản tin để kiểm tra tuổi dữ liệu. | Trong từ điển dữ liệu phải có `timestamp`; có thể làm rõ nhãn thành “Vị trí, độ chính xác và thời điểm GPS”. |
| Trạng thái bộ phát quay lại 1.4 | Code có đọc trạng thái bận và callback bắt đầu/kết thúc/bỏ qua của player, nhưng sơ đồ chưa thể hiện luồng phản hồi này. | Nếu cần thể hiện đầy đủ điều khiển tự phát, bổ sung dữ liệu “Trạng thái phát/lượt phát” từ P3 về 1.4, đồng thời bổ sung P3 → P1 ở mức 1 để giữ cân bằng giữa hai mức. Đây là đề nghị chỉnh tài liệu, chưa thay sơ đồ của người dùng. |

Không dùng DFD để mô tả thứ tự chạy cứng giữa các nhánh tải GPS, tải bản đồ và tải catalog. Mũi tên mô tả dữ liệu trao đổi; thuật toán và ngoại lệ được trình bày trong sưu liệu này.

## 10. Ngoại lệ và giới hạn đã thấy trong source

| Trường hợp | Xử lý hiện tại | Code |
|---|---|---|
| Không hỗ trợ định vị/từ chối quyền/mất vị trí | Báo lỗi, vị trí rỗng; tự phát không đủ điều kiện. Tải catalog/map không bị buộc dừng theo GPS. | [Lỗi định vị](../../voiceover-frontend/src/useTourLocation.js:7), [kiểm tra điều kiện](../../voiceover-frontend/src/autoNarration.js:54) |
| GPS sai số trên 30 m hoặc cũ hơn 5 giây | Reset thời gian chờ, không tạo ứng viên tự phát. | [GPS tốt](../../voiceover-frontend/src/autoNarration.js:21), [reset chờ](../../voiceover-frontend/src/autoNarration.js:54) |
| Không có POI trong 500 m | Danh sách rỗng và hiện thông báo khi đã tải xong, không có lỗi. | [Thông báo không có POI](../../voiceover-frontend/src/VoiceMapScreens.jsx:298) |
| API catalog lỗi/phản hồi không đúng | Hiện lỗi và xóa catalog hiện tại. | [Nhánh lỗi tải catalog](../../voiceover-frontend/src/VoiceMapScreens.jsx:356) |
| POI bị ẩn/xóa | Bị loại ở lần làm mới catalog kế tiếp; bộ nhận diện xóa trạng thái cho POI không còn trong catalog. Không phải cập nhật tức thời qua socket. | [Làm mới catalog](../../voiceover-frontend/src/VoiceMapScreens.jsx:364), [loại lượt ghé cũ](../../voiceover-frontend/src/autoNarration.js:52) |
| Ra khỏi bán kính trong lúc chờ tải/phát | Kiểm tra lại và hủy nếu không còn đủ điều kiện. | [Hủy trước phát](../../voiceover-frontend/src/useTourNarration.js:31), [kiểm tra lúc bắt đầu](../../voiceover-frontend/src/narrationPlayer.js:62) |
| Trình duyệt chặn autoplay | Hiện trạng thái bị chặn, cung cấp thao tác phát của người dùng; không coi đặt chỗ/tải xong là đã phát thật. | [Lỗi autoplay](../../voiceover-frontend/src/narrationPlayer.js:108), [nút tiếp tục](../../voiceover-frontend/src/VoiceMapScreens.jsx:297) |
| Lỗi tải tile bản đồ | Chưa thấy nhánh thông báo và đổi nguồn tile riêng trong component. | [Cấu hình lớp tile](../../voiceover-frontend/src/VoiceMapScreens.jsx:72) |

## 11. Kiểm thử và giới hạn bằng chứng

Đã chạy ngày 09/11/2026, trong thư mục frontend:

```powershell
node --test src/autoNarration.test.js src/narrationPlayer.test.js
```

Kết quả: **22 test qua, 0 lỗi, 0 bỏ qua**; gồm 9 test nhận diện và 13 test bộ phát. Đây là unit test với dữ liệu và bộ phát giả lập, không phải kiểm thử GPS thực địa hoặc âm thanh thật.

| Nội dung có test | Vị trí test |
|---|---|
| Đủ 3 giây; chấp nhận sai số đúng 30 m, từ chối 31 m | [Test thời gian và sai số](../../voiceover-frontend/src/autoNarration.test.js:8) |
| Ra khỏi bán kính thật làm reset chờ, dù chưa ra khỏi vùng đệm | [Test biên vùng](../../voiceover-frontend/src/autoNarration.test.js:14) |
| Ưu tiên POI gần nhất, chờ khi player bận, kiểm tra lại vị trí | [Test ưu tiên/chờ](../../voiceover-frontend/src/autoNarration.test.js:18) |
| Chỉ đặt chỗ hoặc lỗi bắt đầu không tạo cooldown | [Test đặt chỗ](../../voiceover-frontend/src/autoNarration.test.js:25) |
| Cooldown theo POI và phục hồi trên thiết bị | [Test cooldown](../../voiceover-frontend/src/autoNarration.test.js:32) |
| GPS kém/cũ/mất làm đứt thời gian chờ | [Test GPS](../../voiceover-frontend/src/autoNarration.test.js:44) |
| Loại POI bị ẩn và không tự phát trùng sau lượt nghe thủ công trong cùng lần ghé | [Test POI ẩn](../../voiceover-frontend/src/autoNarration.test.js:50), [test lượt nghe thủ công](../../voiceover-frontend/src/autoNarration.test.js:54) |
| Kiểm tra lại GPS sau khi tải nội dung và khi bộ phát đang bắt đầu | [Test sau tải guide](../../voiceover-frontend/src/narrationPlayer.test.js:44), [test lúc bắt đầu](../../voiceover-frontend/src/narrationPlayer.test.js:49) |
| Chỉ sự kiện bắt đầu phát thật mới ghi nhận nghe/cooldown; file dùng `onplaying` | [Test speech](../../voiceover-frontend/src/narrationPlayer.test.js:13), [test file audio](../../voiceover-frontend/src/narrationPlayer.test.js:96) |

Trong lượt lập sưu liệu này chưa chạy trình duyệt, thử quyền GPS trên điện thoại, gọi dịch vụ bản đồ thật, truy vấn MySQL thật hoặc đo độ trễ tự phát. Các mô tả backend và giao diện tương ứng là kết quả đọc source, không phải khẳng định đã nghiệm thu runtime.

## 12. Thứ tự mở code khi trình bày

1. Mở [component ứng dụng khách](../../voiceover-frontend/src/VoiceMapScreens.jsx:340): chỉ ra cùng một vị trí được chuyển cho nhận diện, bản đồ và dẫn đường.
2. Mở [hook định vị](../../voiceover-frontend/src/useTourLocation.js:8): giải thích lấy tọa độ, sai số, thời điểm và lỗi quyền vị trí.
3. Mở [khởi tạo bản đồ](../../voiceover-frontend/src/VoiceMapScreens.jsx:71): chỉ ra nguồn bản đồ nền và lớp người dùng.
4. Mở [request catalog](../../voiceover-frontend/src/VoiceMapScreens.jsx:355), rồi [controller](../../src/main/java/com/voiceover/backend/controllers/VoiceMapController.java:77), [service](../../src/main/java/com/voiceover/backend/services/RouteSessionService.java:71) và [repository](../../src/main/java/com/voiceover/backend/repositories/PoiRepository.java:13): giải thích POI được lấy từ database qua backend.
5. Mở [lọc 500 m](../../voiceover-frontend/src/VoiceMapScreens.jsx:369) và [Haversine](../../voiceover-frontend/src/autoNarration.js:6): phân biệt khoảng cách lân cận với đường đi bộ.
6. Mở [cập nhật vùng ghé](../../voiceover-frontend/src/autoNarration.js:48), [chọn ứng viên](../../voiceover-frontend/src/autoNarration.js:75) và [gọi bộ phát](../../voiceover-frontend/src/useTourNarration.js:34): giải thích 30 m, 3 giây, bán kính, ưu tiên và chống phát chồng.
7. Mở [vòng ghi nhận sử dụng](../../voiceover-frontend/src/VoiceMapScreens.jsx:342) và [API sự kiện](../../src/main/java/com/voiceover/backend/controllers/PlaybackEventController.java:30): phân biệt người đang sử dụng với lượt nghe thật.
