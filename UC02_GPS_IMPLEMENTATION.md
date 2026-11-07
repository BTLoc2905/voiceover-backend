# UC02 — Nghe thuyết minh và tự phát theo GPS

Cập nhật ngày 23/10/2026. Nguồn yêu cầu: [Nhom2_CNPM.docx](./Nhom2_CNPM.docx), F-01, UC02 và các yêu cầu điều khiển audio. Đây là phần bổ sung vào giao diện khách đang được mount từ [main.jsx](./voiceover-frontend/src/main.jsx), sau phạm vi [Session & Routes](./SESSION_ROUTES_REQUIREMENTS.md).

**Chức năng nghe khi bấm POI/Phát đã có và người dùng đã xác nhận nghe được.** Điểm thiếu được xử lý trong lượt này là nối GPS liên tục với bộ phát thật dùng chung trên các màn hình; không coi việc đã nghe thủ công là chưa có audio.

## 1. Yêu cầu gốc và phần triển khai

| Yêu cầu trong PRD | Xử lý hiện tại |
|---|---|
| POI đã xuất bản, vùng mặc định 50 m hoặc bán kính cấu hình | Lấy catalog public; lọc trạng thái/tọa độ; dùng `radiusM`, mặc định 50 m; bản đồ vẽ đúng bán kính |
| GPS có sai số không quá 30 m, ở trong vùng liên tục ít nhất 3 giây | Một `watchPosition` ở gốc ứng dụng; sai số/vị trí cũ hoặc ra khỏi bán kính thật làm tính lại thời gian đứng trong vùng |
| Hysteresis: chỉ tính đã rời khi vượt bán kính +20 m | Giữ trạng thái đã nghe trong vành biên; không phát lại khi GPS dao động sát mép |
| Nhiều vùng chồng nhau: điểm gần nhất ưu tiên, điểm khác chờ | Chọn theo khoảng cách, ID làm thứ tự phụ; chỉ một bài được tải/phát cho lượt tự động; kiểm lại điều kiện khi đến lượt |
| Cùng POI không tự phát lại trong 30 phút | Ghi mốc khi có `onplaying`/`onstart`, lưu theo ID POI trong localStorage; muốn phát lại tự động còn phải rời vùng và vào lại |
| Tải trước audio của các điểm trong 300 m | Tải trước các điểm lân cận, tối đa 3 request đồng thời; không gọi play và không ghi lượt nghe từ GET |
| Tạm dừng/tiếp tục, tua ±10 giây, tốc độ 0,75–1,5×, bỏ qua | Dùng một bộ phát xuyên màn hình. File audio tua bằng thời gian media thật; giọng trình duyệt chỉ có thể tua theo đoạn ước tính, giao diện ghi rõ giới hạn này |
| Ngôn ngữ không hỗ trợ TTS vẫn xem văn bản | API trả `browserTtsAllowed`; không cố đọc khi cờ là false và không có file; vẫn hiển thị nội dung/fallback |

Các ngưỡng 30 m, 3 giây, +20 m, 30 phút và 300 m là yêu cầu gốc, không phải quy tắc mới do triển khai tự đặt.

## 2. Lựa chọn kỹ thuật bổ sung

- Theo điều chỉnh giao diện tiếp theo của người dùng, vào thẳng bản đồ: đã bỏ thanh bật/tắt, hộp chào và điều khiển nổi phía trên. GPS dùng hộp thoại cấp quyền vị trí của trình duyệt; cơ chế tự phát sẵn sàng khi nhận được vị trí hợp lệ. Nếu trình duyệt chặn audio/giọng đọc, chỉ hiện nút **Phát thuyết minh** trong danh sách bên dưới khi cần thao tác; chưa thực sự phát thì chưa tính đã nghe.
- Vị trí quá 5 giây được coi là cũ; GPS cập nhật sau một khoảng mất tín hiệu quá 5 giây phải tính lại dwell. Khi tab ẩn, không bắt đầu bài tự động mới; bài đã phát vẫn có thể tiếp tục theo chính sách trình duyệt. Trở lại tab phải tính lại 3 giây.
- Tạm dừng, mở nội dung hoặc chọn thủ công giữ quyền sử dụng bộ phát; hàng đợi tự động không chen vào. Nghe thủ công trong vùng ngăn phát tự động trùng ngay lần ghé đó, nhưng không tạo cooldown tự động 30 phút.
- Hủy request cũ khi chọn bài khác; kiểm lại GPS cả sau tải guide lẫn khi media bắt đầu. Lỗi tải được lùi thử lại 30 giây để tránh gọi liên tục.
- Catalog làm mới mỗi 30 giây khi tab hiện, và khi trở lại tab. Mỗi lần chuẩn bị phát vẫn GET guide mới; backend từ chối guide của POI nháp/ẩn. Cache tải trước được dọn khi ra khỏi vùng 300 m/đổi ngôn ngữ và làm mới sau 60 giây.
- Theo phản hồi tiếp theo của người dùng về POI đã xuất bản không hiện: bản đồ khám phá hiển thị toàn bộ catalog public hợp lệ, tự căn khung ban đầu và có nút **Xem tất cả POI**; danh sách **Điểm tham quan gần bạn** vẫn lọc 500 m theo PRD. Đây là bổ sung hiển thị bản đồ, không nới bán kính tự phát hoặc tải trước. Điểm trùng tọa độ được gom thành marker số lượng với lựa chọn nghe từng điểm trong popup; không tự sửa tọa độ trong DB. Popup đang mở được giữ khi GPS/catalog cập nhật.
- Cooldown giữ tối đa 128 POI gần đây trên thiết bị/trình duyệt; không đồng bộ giữa thiết bị. Nếu storage bị chặn, dùng bộ nhớ của phiên.
- Giữ cách nghe hiện có: ưu tiên URL file do API cung cấp; nếu không có file thì dùng giọng trình duyệt khi được hỗ trợ. Chất lượng/giọng ngôn ngữ phụ thuộc thiết bị, không tuyên bố đã có MP3 cho mọi POI/ngôn ngữ.

## 3. Backend và dữ liệu lượt nghe

Không thêm bảng hoặc migration mới trong lượt UC02 này.

- GET `/api/public/pois/{id}/guide` trả nội dung, ngôn ngữ thực tế, file và cờ TTS. Các tham số `triggerType`/`clientId` cũ vẫn được chấp nhận để tương thích nhưng GET không ghi `usage_event`.
- POST `/api/public/playback-events` nhận `poiId`, `languageCode`, `eventType`, `clientId`, `playbackId`. Chỉ chấp nhận `AUTO_PLAY`, `TAP_POI`, `AUDIO_COMPLETED`, POI đã xuất bản và ngôn ngữ hoạt động.
- Frontend gửi start khi media thực sự phát, completion khi kết thúc toàn bài; pause/resume/đổi tốc độ không tạo thêm start. Bấm nghe lại sau khi kết thúc tạo playback ID mới và là lượt nghe thủ công.
- Sự kiện hợp lệ được lưu MySQL qua repository. Chống gửi lặp theo client/playback/type bằng cache trong một tiến trình, TTL 30 phút, tối đa 2.048 khóa; **không phải** ràng buộc duy nhất trong DB và không bảo đảm chống trùng qua nhiều instance/restart.
- Lỗi telemetry không làm dừng thuyết minh; chưa có hàng đợi gửi lại bền vững khi mất mạng. Vì vậy số liệu sử dụng không được cam kết ghi đủ mọi lượt trong điều kiện mất mạng.
- Vite thêm proxy `/storage` đến backend cho file audio tương đối.

## 4. Mã chính

- [autoNarration.js](./voiceover-frontend/src/autoNarration.js): điều kiện GPS, dwell, vùng đệm, thứ tự và cooldown.
- [useTourLocation.js](./voiceover-frontend/src/useTourLocation.js), [useTourNarration.js](./voiceover-frontend/src/useTourNarration.js): GPS liên tục và vòng chọn bài ở gốc ứng dụng.
- [narrationPlayer.js](./voiceover-frontend/src/narrationPlayer.js): bộ phát dùng chung, audio/giọng trình duyệt và sự kiện nghe.
- [narrationApi.js](./voiceover-frontend/src/narrationApi.js): guide, tải trước và gửi sự kiện.
- [VoiceMapScreens.jsx](./voiceover-frontend/src/VoiceMapScreens.jsx), [NarrationAudioScreen.jsx](./voiceover-frontend/src/NarrationAudioScreen.jsx), [AutoNarration.css](./voiceover-frontend/src/AutoNarration.css): giao diện đang dùng thật; không còn component thanh bật/tắt hoặc hộp chào.
- [PlaybackEventController.java](./src/main/java/com/voiceover/backend/controllers/PlaybackEventController.java), [PoiService.java](./src/main/java/com/voiceover/backend/services/PoiService.java): lưu sự kiện và kiểm tra POI public.

## 5. Kiểm chứng đã thực hiện

### Tự động / build

- Frontend: 9 test GPS, 10 test bộ phát, 5 test route hiện có đã qua (24 trường hợp, chạy chung trước sửa replay rồi chạy lại riêng toàn bộ 10 test bộ phát sau sửa). Gồm biên sai số 30/31 m, mất GPS, dwell, hysteresis, hàng đợi, cooldown qua storage, async trả muộn, browser chặn phát, audio thật qua đối tượng media giả, pause/rate, replay và text-only.
- Production build frontend thành công sau sửa cuối. Lint: không lỗi; còn 5 cảnh báo cũ trong file demo không được mount.
- Backend: lượt chạy toàn bộ có 27 test qua, sau đó thêm kiểm tra guide nháp/ẩn và chạy lại riêng `PoiRouteProtectionTest`: 3/3 qua. Tổng 28 trường hợp hiện tại ở 6 lớp test nghiệp vụ đã có kết quả qua; không tính báo cáo cũ của lớp test đã xóa.

### Giao diện trình duyệt có mô phỏng

Thực hiện trên origin riêng `127.0.0.1:5174`, không đổi GPS/cooldown/storage của tab người dùng ở `localhost`. Mô phỏng GPS, SpeechSynthesis và API; đây không phải lần nghe thực trên điện thoại.

1. Chỉ hiển thị hai điểm PUBLISHED, không hiện điểm nháp; tải trước hai guide không gửi lượt nghe.
2. Sau bật tự phát, mốc 1,7 giây vẫn chưa đọc/ghi start; sau đủ dwell chọn điểm gần nhất trước.
3. Pause không làm phát điểm thứ hai. Resume không tạo lượt start thứ hai; kết thúc toàn bài gửi completion, sau đó mới phát điểm hàng đợi.
4. Hai mốc cooldown còn sau reload; bật lại và chờ hơn 4 giây không tự đọc lại hai điểm.
5. GPS ±31 m không phát sau hơn 4 giây. Đổi về ±30 m phải chờ dwell mới; ở 1,5 giây chưa phát, sau đó phát điểm gần nhất.
6. Giả lập lỗi `not-allowed` hiển thị lời nhắc; không gửi lượt nghe. Bấm lại khi được phép gửi đúng một `TAP_POI`; không có giọng thứ hai chen vào.
7. Kiểm DOM ở viewport rộng 390/1.280 px: không tràn ngang; còn nút phát và nhãn thời gian/tua ước tính.

Điều chỉnh giao diện sau phản hồi của người dùng: bỏ hẳn thanh bật/tắt và hộp chào; vào thẳng bản đồ, tự phát sẵn sàng sau quyền vị trí. Production build và lint đã qua (5 cảnh báo demo cũ). Kiểm lại DOM: viewport 390×844 có màn hình cao 844 px, bản đồ cao 621 px bắt đầu ở y=0; viewport 1.280×844 có màn hình cao 820 px, bản đồ cao 572 px. Cả hai không còn hộp chào/công tắc và không tràn ngang. Các bước bật công tắc trong bằng chứng mô phỏng ở trên thuộc giao diện trước điều chỉnh này.

Kiểm lại lỗi không thấy POI với API thật: catalog có 6 POI PUBLISHED, danh sách gần vị trí đang thử có 0 điểm trong 500 m. Sau sửa, bản đồ có 5 marker đại diện đủ 6 POI (Đại học Sài Gòn và Chợ An Đông cùng tọa độ nên chung marker số 2). Nút **Xem tất cả POI** đưa toàn bộ marker vào khung ở cả viewport 390 và 1.280 px. Popup cụm hiển thị hai lựa chọn riêng; chọn Chợ An Đông mở đúng nội dung của điểm đó. Không chỉnh tọa độ/trạng thái POI trong DB. Build sau sửa cuối thành công.

### Tích hợp backend/MySQL sau restart: đã qua

Người dùng xác nhận restart; backend cổng 8080 đổi từ PID 3936 sang **38392**. API mới đã được nạp: POST body rỗng trả 400 `INVALID_INPUT` thay vì 404. GET guide của POI #1 trả 200, `activeLanguage=vi`, `browserTtsAllowed=true`, `audioStatus=TEXT_ONLY`.

Kiểm tra API thật với client kiểm thử `uc02-qa-f246d8cf-22ad-4f3d-a56f-790a5dd4368f`, playback `c90eeccf-f50d-4bf8-aef8-785bc4f91d56`:

| Thao tác | Kết quả API | Lượt nghe POI #1 đọc mới từ Dashboard |
|---|---|---|
| Trước kiểm tra | GET Dashboard 200 | 3 |
| GET guide PRELOAD ba lần | 200, không ghi lượt | 3 |
| POST AUTO_PLAY | 200, `recorded=true` | 4 |
| Gửi lại cùng AUTO_PLAY/client/playback | 200, `recorded=false` | 4 |
| POST AUDIO_COMPLETED | 200, `recorded=true` | 4 |
| POST loại sự kiện PRELOAD | 400 `INVALID_INPUT` | Không ghi |

Đây là **hai bản ghi sự kiện kiểm thử lưu thật** (một start và một completion), không phải khẳng định người dùng đã nghe audio trên điện thoại trong lượt kiểm này. Dashboard đọc lại qua repository xác nhận start được lưu và completion không cộng vào tổng lượt bắt đầu nghe. Chưa kiểm chống trùng qua nhiều instance/restart vì cơ chế này chỉ có cache trong tiến trình.

Script `-CheckPersisted -SessionId 1 -RouteId 1 -ExpectedPoiIds 2,1` chạy mã thoát 0, **5/5 qua**: Session và Route còn PUBLISHED, thứ tự [2,1] giữ nguyên, Route hiện cho khách và start trả geometry đi bộ LineString. Catalog vẫn 6 POI; không chỉnh dữ liệu Session/Route/POI trong lượt kiểm UC02.

## 6. Cách thử thực tế sau khi backend đã restart

1. Giữ frontend cổng 5174, tải lại trang khách, cấp quyền vị trí.
2. Đến một POI đã xuất bản, chờ GPS hiển thị sai số không quá 30 m, đứng trong vòng kích hoạt ít nhất 3 giây; không cần bật công tắc trong ứng dụng.
3. Nếu trình duyệt chặn, bấm **Phát thuyết minh** trong phần danh sách POI. Chạm vào POI để mở màn nghe đầy đủ, thử pause/resume, đổi tốc độ và bỏ qua.
4. Ra hẳn ngoài vòng bán kính +20 m rồi vào lại: cùng điểm chưa đủ 30 phút sẽ không tự phát lại. Tải lại trang vẫn giữ cooldown.

**Còn phải nghiệm thu ngoài thực địa:** sai số trên thiết bị thật, giọng đọc sẵn có, quyền/vòng đời tab, độ trễ 5 giây và SLA tải audio 1,5/3 giây trong mạng thực. Tua ±10 giây chính xác với giọng trình duyệt chưa thể đáp ứng như file audio. UC04 tự chuyển chặng khi đến nơi, offline đầy đủ và phân quyền nằm ngoài thay đổi UC02 này.
