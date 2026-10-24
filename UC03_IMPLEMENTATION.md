# UC03 Tùy chỉnh lộ trình cá nhân

## Phạm vi

Bám theo mục 4.3.3 và F-02 trong [PRD](./Nhom2_CNPM.docx): chọn lộ trình gợi ý, thêm/bớt/đổi thứ tự POI, xem lại đường đi bộ và số liệu mới, xác nhận rồi lưu lộ trình cá nhân. Lộ trình phải có 2–10 POI khác nhau, đã xuất bản và có tọa độ hợp lệ. Bắt đầu dẫn đường từng chặng thuộc UC04.

## Dữ liệu và API

- `GET /api/public/routes`: đọc route và session đã xuất bản; bỏ các route có POI không khả dụng hoặc không đủ 2–10 điểm. Không tạo route mẫu và không ghi database.
- `GET /api/public/pois`: danh mục POI đã xuất bản, có tọa độ hợp lệ để thêm vào route.
- `POST /api/public/routes/customize`: nhận mảng ID theo đúng thứ tự; kiểm tra lại trạng thái/tọa độ từ database, trả đường đi bộ GeoJSON, các chặng, tổng quãng đường và thời gian. Lỗi đầu vào trả 400, không tìm được đường trả 422, dịch vụ định tuyến lỗi trả 503.
- Chỉ khi bấm **Xác nhận lộ trình**, giao diện kiểm tra lại với server rồi lưu các ID theo thứ tự trong localStorage `voicemap-personal-route-v1`. Hủy chỉnh sửa giữ nguyên bản đã lưu. Khi mở lại, dữ liệu được đọc mới từ server và tính lại để tránh sử dụng POI đã ẩn/xóa.

UC03 cần ít nhất một Session và Route đã xuất bản với 2–10 POI hợp lệ, như điều kiện trước trong PRD. Nếu database chưa có dữ liệu này, giao diện hiển thị trạng thái trống; việc tạo Session/Route thuộc UC10. Không tự tạo dữ liệu trên database đang dùng.

## Định tuyến

Mặc định sử dụng máy chủ OSRM chạy profile đi bộ của FOSSGIS, thay cho máy chủ driving. Có thể cấu hình `WALKING_ROUTING_URL` và `WALKING_ROUTING_MIN_INTERVAL_MS`. Backend giới hạn mặc định 1 lần gọi mỗi 1.1 giây và cache kết quả 5 phút; giao diện gom các thao tác nhanh và bỏ kết quả request cũ. Lỗi Directions không được thay bằng đường chim bay hoặc thời gian giả.

Tài liệu: [OSRM Route service](https://project-osrm.org/docs/v5.24.0/api/#route-service), [chính sách máy chủ FOSSGIS](https://routing.openstreetmap.de/about.html).

## Kiểm tra

```powershell
# Tại thư mục gốc dự án: test dùng repository giả và Directions HTTP stub, không cần MySQL.
.\mvnw.cmd -o -B "-Dtest=RouteSessionServiceTest,WalkingDirectionsServiceTest" test

cd voiceover-frontend
node --test src/routePlan.test.js
npm.cmd run build
npm.cmd run lint
```

Kiểm tra giao diện: chọn một route đã xuất bản → tùy chỉnh → thêm/bớt/đổi thứ tự → đợi số liệu mới → xác nhận → tải lại trang. Thử bỏ xuống còn 1 điểm, thêm khi đủ 10 điểm, hủy chỉnh sửa, POI bị ẩn khi đang chỉnh sửa, Directions lỗi và thay đổi nhanh nhiều lần. Bản chỉnh sửa chưa xác nhận không được ghi đè bản đã lưu.

## Kết quả kiểm chứng ngày 23 tháng 10 năm 2026

- Backend: 9 test qua, không lỗi; biên dịch Java với `release 21`. Repository và Directions được giả lập, không kết nối MySQL.
- Frontend: 5 test logic qua; build thành công; lint thoát mã 0, còn 17 cảnh báo ở mã có sẵn, không có cảnh báo trong các module UC03 mới.
- Trình duyệt với API giả: thêm/bớt/đổi thứ tự, tính lại số liệu, xác nhận và khôi phục sau tải trang; giới hạn 2–10, loại POI nháp, hủy không ghi đè bản đã lưu, lỗi không có đường và thử lại, phản hồi cũ đến muộn, trạng thái không có route gợi ý đều đã kiểm tra. Giao diện rộng 390 px không tràn ngang.
- Kiểm tra runtime bổ sung ngày 23/10/2026: sau khi người dùng restart, đã tạo và xuất bản Session #1/Route #1 qua Admin trên MySQL thật, thứ tự [2,1]. Khách thấy Route và bắt đầu UC04 thành công, Directions trả 1.902 m/26 phút, GeoJSON 83 tọa độ. V12 đã được người dùng áp dụng trên `voicemap_db`; kiểm nháp 11 điểm lưu/đọc lại thành công, xuất bản bị chặn, bản nháp được trả về ban đầu. Restart sau khi tạo dữ liệu đã xác nhận bằng PID 20972 → 3936 và 5/5 kiểm tra qua; tên/trạng thái/thứ tự vẫn nguyên, khách vẫn bắt đầu Route được. Xem [Session & Routes](./SESSION_ROUTES_REQUIREMENTS.md). Chưa tuyên bố nghiệm thu lại toàn bộ thao tác tùy chỉnh UC03 trên DB trong lượt này.
