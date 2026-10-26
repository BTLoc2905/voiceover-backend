**TRƯỜNG / KHOA: ........................................................**

**ĐỒ ÁN MÔN CÔNG NGHỆ PHẦN MỀM**

**TÀI LIỆU PHÂN TÍCH YÊU CẦU\
VÀ ĐẶC TẢ USE CASE**

**HỆ THỐNG THUYẾT MINH TỰ ĐỘNG ĐA NGÔN NGỮ\
(VoiceMap)**

Nhóm thực hiện: Nhóm 2

Giảng viên hướng dẫn: Nguyễn Quốc Huy

Thành viên: ........................................................

Phiên bản: 1.0

Ngày hoàn thiện: .... / .... / 2026


# **MỤC LỤC**


*⟦ Trong Word: nhấp phải vào Mục lục → Update Field → Update entire table sau khi dán xong. ⟧*
# **DANH MỤC TỪ VIẾT TẮT**

|Từ viết tắt|Giải thích|
| :- | :- |
|PRD|Product Requirements Document|
|UC|Use Case|
|US|User Story|
|POI|Point of Interest|
|GPS|Global Positioning System|
|TTS|Text-to-Speech|
|API|Application Programming Interface|
|FR|Functional Requirement|


# **CHƯƠNG 1. TỔNG QUAN DỰ ÁN**

## **1.1. Giới thiệu hệ thống VoiceMap**
**Mô tả hệ thống**

Hệ thống được thiết kế theo mô hình hai lớp, phục vụ đồng thời hai nhóm người dùng với hai mục tiêu khác nhau nhưng bổ trợ chặt chẽ cho nhau. Về phía du khách, ứng dụng di động tích hợp công nghệ định vị GPS để tự động nhận diện vị trí hiện tại của người dùng, từ đó xác định các điểm quan tâm (Points of Interest — POI) nằm trong bán kính lân cận và hiển thị trực quan trên bản đồ; không dừng lại ở việc định vị, hệ thống còn chủ động đề xuất những hành trình tham quan tối ưu, kết nối nhiều POI theo một lộ trình hợp lý, kèm theo chỉ dẫn di chuyển cụ thể giữa các điểm, biến việc khám phá một khu vực rộng lớn trở nên đơn giản như đi theo một câu chuyện được sắp đặt sẵn. Song song đó, về phía ban quản lý, một Cổng quản trị (Admin Portal) cho phép nhân viên vận hành khởi tạo session, cập nhật dữ liệu POI và có thể dùng AI gợi ý bản nháp mô tả gốc để soạn nhanh; cùng một Dashboard giám sát (Monitoring Dashboard) cho phép quản trị viên theo dõi tình trạng hoạt động và các chỉ số sử dụng theo thời gian thực

**Điểm nổi bật — Lõi công nghệ đa ngôn ngữ**

Giá trị cốt lõi làm nên sự khác biệt của hệ thống nằm ở khả năng tự động hóa hoàn toàn quy trình thuyết minh: mỗi khi du khách chạm vào một POI trên bản đồ, nội dung mô tả sẽ được dịch và chuyển đổi thành giọng nói ở hơn 15 ngôn ngữ khác nhau, phát trực tiếp cho người dùng nghe ngay tại chỗ. Toàn bộ dữ liệu — từ tọa độ, phạm vi hiển thị, hình ảnh minh họa đến nội dung thuyết minh đa ngôn ngữ — được lưu trữ tập trung trong hệ thống dữ liệu thống nhất, gồm cơ sở dữ liệu quan hệ (POI, bản dịch, lộ trình, session) và kho lưu file audio, tạo nền tảng vững chắc để hệ thống có thể mở rộng quy mô hoặc tích hợp thêm các tính năng trong tương lai.

**Ý nghĩa thực tiễn**

Với cách tiếp cận này, đề tài không chỉ giải quyết bài toán ngôn ngữ trong du lịch mà còn góp phần nâng cao trải nghiệm cá nhân hóa cho từng du khách, giảm tải cho đội ngũ hướng dẫn viên, đồng thời trao cho ban quản lý điểm đến một công cụ quản trị dữ liệu và giám sát vận hành hiệu quả, minh bạch. Đây là một hướng đi phù hợp với xu thế chuyển đổi số trong ngành du lịch, hứa hẹn mang lại giá trị thiết thực cả về mặt trải nghiệm người dùng lẫn hiệu quả quản lý.


## **1.2. Vấn đề cần giải quyết**
·        **Rào cản ngôn ngữ:** Rất nhiều du khách quốc tế gặp khó khăn khi tiếp cận thông tin văn hóa, lịch sử tại các điểm du lịch do thiếu hướng dẫn viên thông dịch ngôn ngữ mẹ đẻ của họ.

·        **Chi phí hướng dẫn viên cao:** Việc thuê hướng dẫn viên riêng tốn kém chi phí và khó sắp xếp linh hoạt theo lịch trình cá nhân.

·        **Trải nghiệm du lịch bị ngắt quãng:** Việc phải vừa đi vừa đọc tài liệu giấy hoặc tìm kiếm thông tin thủ công làm giảm trải nghiệm thực tế tại điểm tham quan.

·        **Thiếu công cụ quản lý dữ liệu du lịch:** Quản trị viên khó cập nhật thông tin POI nhanh chóng hoặc theo dõi tình trạng hệ thống và lượt tương tác của khách du lịch theo thời gian thực.


## **1.3. Mục tiêu hệ thống**
**3.1 Đối với người dùng (Du khách)**

·        Cung cấp ứng dụng bản đồ định vị chính xác và nhận diện POI lân cận.

·        Đơn giản hóa trải nghiệm tham quan nhờ tính năng phát âm thanh thuyết minh tự động bằng hơn 15 ngôn ngữ khi tương tác hoặc đến gần POI.

·        Tối ưu hóa thời gian di chuyển với các lộ trình tùy chỉnh linh hoạt.

**3.2 Đối với quản trị viên & Hệ thống**

·        Tự động hóa pipeline dịch thuật và chuyển đổi văn bản thành giọng nói (Text-to-Speech).

·        Quản lý dữ liệu POI, tọa độ, bán kính quét (proximity range) và phiên làm việc dễ dàng.

·        Giám sát hiệu năng và tình trạng hệ thống theo thời gian thực (Real-time monitoring).

·        Hỗ trợ nhân viên soạn nhanh mô tả POI bằng gợi ý từ AI, vẫn do nhân viên kiểm soát nội dung.


## **1.4. Đối tượng sử dụng**

|**Đối tượng**|**Bối cảnh & Nhu cầu chính**|
| :- | :- |
|Khách du lịch (End-Users)|Du khách trong và ngoài nước cần bản đồ dẫn đường, lộ trình tham quan và thuyết minh âm thanh đa ngôn ngữ.|
|Quản trị viên / Nhân viên vận hành (Admin)|Đăng nhập vào hệ thống quản trị để cập nhật dữ liệu POI, quản lý bản dịch/audio, lộ trình, session trên Admin Portal và theo dõi tình trạng hệ thống, số liệu sử dụng theo thời gian thực trên Monitoring Dashboard.|


## **1.5. Phạm vi hệ thống**
**In-Scope (Trong phạm vi triển khai)**

·        **Mobile App:** Ứng dụng định vị GPS, đề xuất tuyến đường, hiển thị POI, phát thuyết minh âm thanh đa ngôn ngữ (React Native).

·        **Data Pipeline & Backend:** Tự động dịch mô tả POI thành >15 ngôn ngữ và chuyển thành audio (Text-to-Speech); lưu trữ tọa độ, hình ảnh, bán kính gần.

·        **Dịch vụ AI (bên thứ ba):** gợi ý bản nháp mô tả gốc cho POI trong Admin Portal; nhân viên chỉnh sửa và lưu mới có hiệu lực.

·        **Admin Portal:** Tạo session, thêm/sửa/xóa thông tin POI (React.js).

·        **Monitoring Dashboard:** Giám sát trạng thái hệ thống và mức độ sử dụng theo thời gian thực.

·        **Hệ thống CI/CD:** Tự động hóa đóng gói và triển khai phần mềm; backend theo kiến trúc 3 lớp, chia module theo nghiệp vụ, đóng gói bằng Docker.


# **CHƯƠNG 2. PHÂN TÍCH YÊU CẦU HỆ THỐNG**
## **2.1. Các yêu cầu cốt lõi (Key Features)**

|**Yêu cầu đề bài (Key Features)**|**Thành phần tương ứng trong PRD**|
| :- | :- |
|1\. Ứng dụng dẫn đường dùng GPS, phát hiện vị trí hiện tại và làm nổi bật POI lân cận|F-01 Định vị GPS và POI lân cận|
|2\. Gợi ý lộ trình gồm một số POI (tùy chỉnh) và chỉ đường từ điểm này sang điểm khác|F-02 Gợi ý lộ trình và chỉ đường|
|3\. Data pipeline tự dịch mô tả POI sang 15+ ngôn ngữ và chuyển thành audio|F-03 Data pipeline dịch và tạo audio (TTS)|
|4\. Cơ sở dữ liệu lưu trữ dữ liệu sinh ra và thông tin POI (tọa độ, bán kính, ảnh...)|F-04 Cơ sở dữ liệu POI tích hợp|
|5\. Trang quản trị cho nhân viên tạo session và chỉnh sửa dữ liệu|F-05 Cổng quản trị nội dung (Admin Portal)|
|6\. Dashboard giám sát tình trạng hệ thống và mức sử dụng theo thời gian thực|F-06 Dashboard giám sát thời gian thực (Monitoring Dashboard)|
|7\. (Mở rộng) Hỗ trợ soạn mô tả POI bằng AI|F-05, chức năng AI gợi ý mô tả (Us-16)|



## **2.2. User Story và tiêu chí chấp nhận**

|Mã|User Story|Tiêu chí chấp nhận|Ưu tiên|
| :- | :- | :- | :- |
|Du Khách|** |** |** |
|Us-01|Với vai trò khách tham quan, tôi cần biết vị trí hiện tại của mình và các điểm tham quan (POI) nằm gần đó để chủ động khám phá khu vực xung quanh.|Bản đồ hiển thị đúng vị trí GPS của người dùng theo thời gian thực. Mọi POI trong bán kính 500m được đánh dấu nổi bật, kèm khoảng cách tính từ vị trí hiện tại.|Bắt buộc|
|Us-02|Với vai trò khách tham quan, tôi muốn nghe thuyết minh tự động khi tới gần một địa điểm, thay vì phải thao tác thủ công.|Khi người dùng vào vùng bán kính 50m quanh POI và duy trì vị trí đó tối thiểu 3 giây, hệ thống tự phát audio trong vòng 5 giây, đảm bảo đúng nội dung của POI tương ứng.|Bắt buộc|
|Us-03|Với vai trò khách tham quan, tôi muốn chạm trực tiếp vào một POI trên bản đồ để xem chi tiết và chủ động nghe thuyết minh bất kỳ lúc nào.|Chạm vào POI hiển thị thẻ thông tin (ảnh đại diện, mô tả ngắn, nút phát audio) trong vòng 2 giây. Audio bắt đầu phát trong vòng 1,5 giây sau khi nhấn nút nếu đã được tải trước, tối đa 3 giây nếu chưa tải trước.|Bắt buộc|
|Us-04|Với vai trò khách tham quan, tôi cần chọn được ngôn ngữ nghe từ danh sách hơn 15 ngôn ngữ để tiếp cận nội dung bằng ngôn ngữ mình thông thạo.|Việc đổi ngôn ngữ áp dụng đồng loạt cho toàn bộ POI. Với những POI chưa có bản audio ở ngôn ngữ được chọn, hệ thống thông báo cho người dùng và tự động phát bản audio ngôn ngữ mặc định thay thế.||

|Bắt buộc||
| :- | :- |

|||||
| :- | :- | :- | :- |
|Us-05|Với vai trò khách tham quan, tôi cần các thao tác điều khiển audio (dừng/phát, tua, đổi tốc độ, bỏ qua) để nghe thuyết minh theo tốc độ cá nhân.|Cung cấp đầy đủ nút phát/dừng, tua tiến/lùi 10 giây, điều chỉnh tốc độ trong khoảng 0,75x–1,5x và nút bỏ qua đoạn thuyết minh.|Bắt buộc|
|Us-06|Với vai trò khách tham quan, tôi cần được dẫn đường giữa các POI trong lộ trình để di chuyển đúng hướng mà không bị lạc.|Hệ thống hiển thị tuyến đường đi bộ, khoảng cách và thời gian ước tính đến điểm kế tiếp. Khi người dùng đến nơi, hệ thống tự động chuyển sang chặng tiếp theo trong lộ trình.|Bắt buộc|
|Us-07|Với vai trò khách tham quan, tôi mong muốn có thể chỉnh sửa lộ trình gợi ý (thêm/bớt/sắp xếp lại POI) để phù hợp với sở thích cá nhân.|Sau mỗi lần chỉnh sửa, hệ thống tự tính lại toàn bộ lộ trình và chỉ đường tương ứng, phản ánh đúng thay đổi vừa thực hiện.|Nên có|
|Us-08|Với vai trò khách tham quan, tôi muốn xem trước các lộ trình tham quan gồm nhiều POI được sắp xếp sẵn để dễ dàng lựa chọn hành trình phù hợp.|Mỗi lộ trình gợi ý hiển thị đầy đủ: số lượng điểm dừng, tổng quãng đường, thời gian di chuyển ước tính và danh sách POI theo đúng thứ tự tham quan.|Bắt buộc|
|Us-09|Với vai trò khách tham quan, tôi muốn sử dụng ứng dụng ngay lập tức mà không phải trải qua bước đăng ký tài khoản.|Toàn bộ hành trình từ lúc mở ứng dụng đến khi nghe được thuyết minh đầu tiên không yêu cầu bất kỳ bước đăng nhập hay tạo tài khoản nào.|Bắt buộc|
|Người quản lý|** |** |** |
|Us-10|Với vai trò quản trị viên, tôi cần thêm mới và chỉnh sửa thông tin POI (tên, tọa độ, bán kính lân cận, ảnh đại diện, mô tả gốc) để cập nhật nội dung trực tiếp mà không cần can thiệp vào mã nguồn.|Sau khi lưu, POI hiển thị ngay trong ứng dụng dành cho người dùng. Thao tác xóa POI bắt buộc phải qua bước xác nhận trước khi thực hiện.|Bắt buộc|
|Us-11|Với vai trò quản trị viên, tôi mong muốn hệ thống tự động dịch mô tả sang hơn 15 ngôn ngữ và tạo audio ngay khi lưu, để loại bỏ công đoạn dịch thuật và thu âm thủ công.|Ngay khi mô tả gốc được lưu, pipeline xử lý tự động được kích hoạt. Giao diện hiển thị trạng thái xử lý riêng cho từng ngôn ngữ, gồm các trạng thái: đang chờ, đang xử lý, hoàn tất, hoặc lỗi.|Bắt buộc|
|Us-12|Với vai trò quản trị viên, tôi cần nghe thử, hiệu chỉnh bản dịch và tạo lại audio khi phát hiện sai sót, để đảm bảo chất lượng nội dung trước khi công bố.|Khi bản dịch của một ngôn ngữ được chỉnh sửa, hệ thống chỉ tạo lại audio cho đúng ngôn ngữ đó, không ảnh hưởng đến các ngôn ngữ còn lại.|Nên có|
|Us-13|Với vai trò quản trị viên, tôi muốn khởi tạo các phiên tham quan (session), thiết lập các lộ trình (route) thuộc session và sắp xếp thứ tự POI trong từng lộ trình, để chuẩn bị sẵn chương trình tham quan cho du khách.|Nhân viên có thể tạo session, thiết lập các lộ trình (route) thuộc session và sắp xếp thứ tự POI trong từng lộ trình.|Bắt buộc|
|Us-14|Với vai trò quản trị viên hệ thống, tôi cần theo dõi tình trạng hoạt động của hệ thống theo thời gian thực để phát hiện và xử lý sự cố kịp thời.|Dashboard hiển thị trạng thái ứng dụng, pipeline xử lý, cơ sở dữ liệu và tỷ lệ lỗi hiện tại. Dữ liệu được cập nhật với độ trễ không quá 10 giây.|Bắt buộc|
|Us-15|Với vai trò quản trị viên hệ thống, tôi muốn quan sát số liệu sử dụng thực tế để nắm được POI, lộ trình và ngôn ngữ nào đang được du khách quan tâm nhiều nhất.|Dashboard hiển thị số người dùng đang hoạt động, số lượt nghe thuyết minh phân theo từng POI và ngôn ngữ, cùng thống kê các lộ trình được lựa chọn nhiều nhất.|Bắt buộc|
|Us-16|Với vai trò quản trị viên, tôi muốn nhờ AI gợi ý nội dung mô tả gốc từ tên và ghi chú của POI, để soạn nội dung thuyết minh nhanh hơn.|Nhân viên nhập tên POI (và ghi chú nếu có), bấm “Gợi ý bằng AI”, hệ thống trả bản nháp trong vòng 15 giây. Bản nháp chỉ được lưu khi nhân viên bấm Lưu. Nếu AI lỗi, nhân viên vẫn nhập tay được.|Nên có|



## **2.3. Yêu cầu chức năng (Functional Requirements)**

|**STT**|**Mã YC**|**Tên chức năng**|**Mô tả chi tiết**|
| :- | :- | :- | :- |
|1|FR-1|Định vị & Hiển thị POI|Xác định vị trí GPS hiện tại của người dùng và làm nổi bật các POI xung quanh dựa trên bán kính quét (Proximity range).|
|2|FR-2|Đề xuất & Tùy chỉnh Route|Gợi ý tuyến đường đi qua các POI; cho phép người dùng thêm/bớt POI và hiển thị chỉ đường chi tiết từng chặng.|
|3|FR-3|Pipeline Dịch & Âm thanh|Tự động dịch văn bản thuyết minh POI sang >15 ngôn ngữ và chuyển đổi thành file audio (Text-to-Speech).|
|4|FR-4|Quản lý Cơ sở dữ liệu POI|Lưu trữ tọa độ, thumbnail, bán kính quét, nội dung văn bản trong cơ sở dữ liệu; file audio đa ngôn ngữ lưu trong kho file, CSDL giữ đường dẫn.|
|5|FR-5|Admin Portal|Cung cấp giao diện web có đăng nhập cho nhân viên tạo session, lộ trình, thêm/sửa/ẩn/xóa POI và quản lý bản dịch/audio|
|6|FR-6|Monitoring Dashboard|Theo dõi trạng thái ứng dụng, cơ sở dữ liệu, pipeline; tỷ lệ lỗi và thời gian phản hồi (API latency); số người dùng và lượt nghe theo thời gian thực. Chỉ số CPU/Memory dự kiến|
|7|FR-7|AI gợi ý mô tả POI|Cho phép nhân viên yêu cầu AI sinh bản nháp mô tả gốc cho POI; nội dung được nhân viên xem lại và lưu trước khi dùng.|



## **2.4. Yêu cầu phi chức năng (Non-functional Requirements)**
`    `**Hiệu năng (Performance):** Độ trễ phát audio khi bấm vào POI dưới 1,5 giây (audio đã tải trước), tối đa 3 giây trong trường hợp còn lại; Gợi ý nội dung bằng AI phản hồi trong vòng 15 giây.

·        **Độ khả dụng (Availability):** Hệ thống duy trì thời gian hoạt động (Uptime) >= 99.5%.

·        **Khả năng mở rộng (Scalability):** Backend theo kiến trúc 3 lớp, chia module theo nghiệp vụ, kết hợp Docker và CI/CD để dễ nâng cấp và mở rộng.

·        **Độ chính xác GPS (Accuracy):** Mục tiêu sai số ≤ 5 m ngoài trời thoáng; hệ thống chỉ tự phát audio khi sai số ≤ 30 m.

·        **Giao diện (Usability):** Giao diện Mobile App và Web Admin thân thiện, tối ưu trải nghiệm người dùng (UX/UI).

·        **Độ tin cậy:** Lỗi của dịch vụ AI hoặc dịch vụ bên thứ ba không làm ngưng các chức năng còn lại của hệ thống.




# **CHƯƠNG 3. ĐẶC TẢ CHỨC NĂNG HỆ THỐNG**
## **3.1. F-01 – Định vị GPS và POI lân cận**
F-01 Định vị GPS và POI lân cận

|**User story**|Us-01, Us-02, Us-03, Us-04, Us-05, Us-09|** |** |** |
| :- | :- | :- | :- | :- |
|**Mô tả**|Tính năng theo dõi vị trí GPS liên tục của người dùng, tự động làm nổi bật các POI nằm trong tầm quan sát và kích hoạt phát thuyết minh khi người dùng tiến vào vùng ảnh hưởng của một POI hoặc chủ động chạm vào POI trên bản đồ.|** |** |** |
|**Luồng chính**|<p>1\. 	Người dùng khởi động ứng dụng, chọn ngôn ngữ mong muốn, nhấn "Bắt đầu" và đồng ý chia sẻ vị trí.</p><p>2\. 	Hệ thống hiển thị vị trí hiện tại trên bản đồ cùng các POI xung quanh; những POI nằm trong bán kính 500m được đánh dấu nổi bật kèm khoảng cách thực tế.</p><p>3\. 	Khi người dùng bước vào vùng lân cận của một POI, audio thuyết minh tự động phát; ngoài ra người dùng có thể chạm trực tiếp vào biểu tượng POI để mở thẻ thông tin và nghe theo yêu cầu.</p><p>4\. 	Khi audio phát, thẻ thông tin hiển thị kèm văn bản thuyết minh của ngôn ngữ đang chọn.</p><p>5\.     Trong lúc nghe, người dùng toàn quyền điều khiển audio: dừng, tua, đổi tốc độ hoặc bỏ qua.</p>|** |** |** |
|**Quy tắc nghiệp vụ**|<p>·        Mỗi POI được cấu hình một bán kính kích hoạt riêng (mặc định 50m), lưu trong cơ sở dữ liệu.</p><p>·        Audio chỉ tự phát khi đồng thời thỏa ba điều kiện: khoảng cách thực tế nằm trong bán kính kích hoạt, sai số định vị GPS không vượt quá 30m, và người dùng duy trì vị trí đó liên tục tối thiểu 3 giây. Người dùng chỉ được xem là đã rời khỏi vùng khi khoảng cách vượt quá bán kính kích hoạt cộng thêm 20m.</p><p>·        Khi nhiều vùng ảnh hưởng chồng lấn, hệ thống ưu tiên POI có khoảng cách gần nhất; nếu đang phát một audio khác, POI mới sẽ được xếp vào hàng đợi.</p><p>·        Một POI đã phát tự động sẽ không được kích hoạt lại trong vòng 30 phút kế tiếp.</p><p>·        Để giảm độ trễ, hệ thống tải trước audio của mọi POI nằm trong bán kính 300m tính từ vị trí hiện tại.</p>|** |** |** |
|**Xử lý nghiệp vụ**|<p>·        Người dùng từ chối cấp quyền vị trí: vẫn xem được bản đồ và nghe qua thao tác chạm, nhưng không có tính năng tự phát.</p><p>·        Tín hiệu GPS kém chính xác: hệ thống cảnh báo "Tín hiệu GPS yếu" và tạm ngưng tự phát.</p><p>·        Trình duyệt/thiết bị chặn phát audio tự động: hiển thị nút "Chạm để nghe" thay thế.</p><p>·        Ngôn ngữ đang chọn chưa có bản audio: tự động phát ngôn ngữ mặc định kèm thông báo cho người dùng biết.</p>|** |** |** |



## **3.2. F-02 – Gợi ý lộ trình và hỗ trợ dẫn đường**
F-02 — Lộ trình gợi ý và hỗ trợ dẫn đường

|**User story**|Us-06, Us-07, Us-08|** |** |
| :- | :- | :- | :- |
|**Mô tả**|Cung cấp các lộ trình tham quan dựng sẵn, mỗi lộ trình bao gồm nhiều POI theo một thứ tự nhất định, kèm chỉ dẫn đường đi bộ giữa các điểm dừng liên tiếp.|** |** |
|**Luồng chính**|<p>1\. 	Người dùng duyệt danh sách lộ trình gợi ý (đã hiển thị sẵn số điểm dừng, tổng quãng đường và thời gian dự kiến) và chọn một lộ trình.</p><p>2\. 	Nhấn "Bắt đầu lộ trình", hệ thống vẽ đường đi đến POI đầu tiên.</p><p>3\. 	Khi tới nơi, thuyết minh được phát theo cơ chế mô tả tại F-01.</p><p>4\. 	Ứng dụng tự động chuyển hướng dẫn sang chặng kế tiếp, hiển thị khoảng cách và thời gian còn lại.</p><p>5\.     Hành trình kết thúc khi người dùng đến POI cuối cùng trong lộ trình.</p>|** |** |
|**Quy tắc nghiệp vụ**|<p>·        Một lộ trình gồm từ 2 đến 10 POI có thứ tự cố định, được nhân viên thiết lập trước (xem F-05) và gắn với một session cụ thể.</p><p>·        Việc tính toán đường đi bộ dựa trên dịch vụ Directions API.</p><p>·        Người dùng có thể tự thêm, bớt hoặc sắp xếp lại POI trong lộ trình; hệ thống sẽ tính lại toàn bộ quãng đường và thời gian tương ứng.</p><p>·        Lộ trình chỉ được phép chứa các POI đã ở trạng thái xuất bản.</p>|** |** |
|**Xử lý nghiệp vụ**|<p>·        Không tìm được đường đi: hệ thống thông báo lỗi nhưng vẫn hiển thị vị trí của POI kế tiếp trên bản đồ.</p><p>·        Người dùng đi lệch tuyến đường đã vẽ: tính toán lại đường đi từ vị trí hiện tại .</p><p>·        Người dùng chủ động bỏ qua một điểm dừng: hệ thống chuyển thẳng sang POI kế tiếp trong danh sách.</p>|** |** |



## **3.3. F-03 – Pipeline dịch thuật và tạo audio**
F-03 — Pipeline tự động dịch thuật & tổng hợp giọng nói

|**User story**|Us-11, Us-12|** |** |
| :- | :- | :- | :- |
|**Mô tả**|Quy trình tự động chuyển đổi mô tả gốc của mỗi POI thành hơn 15 phiên bản ngôn ngữ, sau đó tổng hợp mỗi bản dịch thành file audio để phục vụ người dùng khi họ tương tác với POI đó. Trước khi dịch, hệ thống tự động kiểm tra và áp dụng danh sách từ ngữ bảo vệ (protected\_terms) để giữ nguyên các thuật ngữ, tên riêng theo quy định.|** |** |
|**Luồng chính**|<p>1\. 	Nhân viên nhập hoặc chỉnh sửa mô tả gốc (có thể dùng bản nháp do AI gợi ý, xem F-05) và lưu lại.</p><p>2\. 	Hệ thống khởi tạo một tác vụ xử lý riêng cho từng ngôn ngữ trong danh mục cấu hình.</p><p>3\. 	Với mỗi tác vụ: hệ thống đọc danh sách protected\_terms từ CSDL để đánh dấu giữ nguyên các từ không dịch, thực hiện dịch văn bản, sau đó chuyển bản dịch thành file audio thông qua TTS Service.</p><p>4\. 	Bản dịch được lưu vào cơ sở dữ liệu; file audio được lưu vào kho file, cơ sở dữ liệu chỉ lưu đường dẫn. Trạng thái xử lý được cập nhật tương ứng..</p><p>5\.     POI chính thức sẵn sàng phục vụ người dùng ở ngôn ngữ đó.</p>|** |** |
|**Quy tắc nghiệp vụ**|<p>·        Toàn bộ pipeline chạy nền, không làm gián đoạn thao tác của nhân viên.</p><p>·        Mỗi ngôn ngữ có một trong bốn trạng thái: đang chờ, đang xử lý, hoàn tất, hoặc lỗi.</p><p>·        Khi gặp lỗi, hệ thống tự động thử lại tối đa 3 lần trước khi báo lỗi lên dashboard giám sát.</p><p>·        Nếu mô tả gốc thay đổi, toàn bộ các ngôn ngữ sẽ được tạo lại; nếu chỉ chỉnh sửa thủ công một bản dịch, hệ thống chỉ tạo lại audio cho riêng ngôn ngữ đó.</p><p>·        Nội dung không thay đổi thì không dịch lại, giúp tiết kiệm chi phí gọi API.</p><p>·        Bổ sung ngôn ngữ mới chỉ cần cập nhật file cấu hình, không cần thay đổi mã nguồn.</p>|** |** |
|**Xử lý nghiệp vụ**|<p>·        Ngôn ngữ không được dịch vụ TTS hỗ trợ: đánh dấu "chỉ có bản văn bản" và chỉ hiển thị nội dung chữ cho người dùng.</p><p>·        Tên riêng bị dịch sai nghĩa: nhân viên chỉnh sửa thủ công hoặc bổ sung vào danh sách từ giữ nguyên không dịch.</p>|** |** |



## **3.4. F-04 – Mô hình dữ liệu**
F-04 — Mô hình dữ liệu (Data Model)

A. Cơ sở dữ liệu nghiệp vụ chính (voicemap\_db)

|**Thực thể**|**Trường dữ liệu chính**|**Ghi chú**|
| :- | :- | :- |
|POI|id, tên, tọa độ (lat, lng), bán kính lân cận (m), ảnh thumbnail, mô tả gốc, ngôn ngữ gốc, trạng thái (nháp / đã xuất bản)|Thông tin tĩnh mô tả địa điểm|
|Bản dịch|poi\_id, mã ngôn ngữ, nội dung văn bản, trạng thái, thời điểm cập nhật gần nhất|Được pipeline sinh ra tự động, cho phép chỉnh sửa thủ công|
|Audio|poi\_id, mã ngôn ngữ, đường dẫn file, thời lượng, trạng thái|Được sinh ra từ bước tổng hợp giọng nói|
|routes (Lộ trình)|id, session\_id (FK trỏ về sessions.id), name, poi\_sequence (danh sách POI ID theo thứ tự), status|1 Route chứa chuỗi POI theo thứ tự (poi\_sequence), thuộc về 1 Session|
|sessions (Phiên tham quan)|id, name, description, status (draft / published)|1 Session chứa nhiều Route; không gán POI trực tiếp vào Session|
|admin\_accounts (Tài khoản quản trị)|id, email, password\_hash, full\_name, status|Tài khoản dành cho Admin đăng nhập vào Admin Portal (F-05) và Monitoring Dashboard (F-06). Du khách không có tài khoản (Us-09)|
|protected\_terms (Từ ngữ bảo vệ)|id, term, note, created\_at|Danh sách thuật ngữ/tên riêng không dịch trong pipeline dịch thuật|
|Sự kiện sử dụng|loại sự kiện (mở app, tự phát, chạm POI, nghe hết audio…), poi\_id, ngôn ngữ, thời điểm|Phục vụ dashboard, không lưu vị trí hay dữ liệu cá nhân|


**\


**B. Kho dữ liệu Log & Giám sát (Log & Metrics Store — Monitoring Server)**

*(Tách khỏi voicemap\_db để tránh làm chậm CSDL chính và đảm bảo dữ liệu log/metric không bị ảnh hưởng khi POI/Route bị xóa)*

|**Thực thể**|**Trường dữ liệu chính**|**Ghi chú**|
| :- | :- | :- |
|**system\_logs**|id, component\_name (chuỗi: pipeline, database, auth, api), severity (INFO, WARNING, ERROR, CRITICAL), message, timestamp|Phục vụ công tác giám sát và xử lý sự cố (F-06, UC06). Không dùng FK vật lý sang voicemap\_db.|
|**system\_metrics**|id, metric\_type (error\_rate, api\_latency, cpu\_usage, memory\_usage), value, timestamp|Phục vụ vẽ biểu đồ giám sát thời gian thực trên Dashboard (Us-14).|







## **3.5. F-05 – Cổng quản trị nội dung (Admin Portal)**
F-05 — Cổng quản trị nội dung dành cho nhân viên (Admin Portal)

|**User story**|Us-10, Us-11, Us-12, Us-13, Us-16|** |** |
| :- | :- | :- | :- |
|**Mô tả**|Một trang web riêng biệt, dùng nội bộ, cho phép nhân viên tạo session tham quan và quản lý toàn bộ dữ liệu POI.|** |** |
|**Chức năng chính**|<p>1\. 	Đăng nhập hệ thống (UC-A01): Thực hiện xác thực tài khoản qua email và mật khẩu. Đây là tiền điều kiện bắt buộc trước khi thực hiện các chức năng quản trị. Thực hiện xác thực tài khoản qua email và mật khẩu để truy cập toàn bộ chức năng trên Admin Portal và Monitoring Dashboard.</p><p>2\. 	Quản lý POI: tạo mới, chỉnh sửa, ẩn hoặc xóa (yêu cầu xác nhận trước khi xóa); nhập tọa độ trực tiếp hoặc chọn vị trí trên bản đồ; cấu hình bán kính lân cận; tải lên ảnh thumbnail; nhập mô tả gốc; có thể bấm ‘Gợi ý bằng AI’ để lấy bản nháp mô tả gốc, chỉnh sửa rồi lưu.</p><p>3\. 	Theo dõi trạng thái xử lý pipeline theo từng ngôn ngữ; nghe thử audio; chỉnh sửa bản dịch; yêu cầu tạo lại khi cần.</p><p>4\. 	Khởi tạo và quản lý lộ trình (routes): tạo lộ trình gắn với một session, sắp xếp danh sách chuỗi POI theo thứ tự (poi\_sequence).</p><p>5\.     Khởi tạo và quản lý session (sessions): tạo phiên tham quan chứa một hoặc nhiều lộ trình (1 Session chứa nhiều Route), xuất bản hoặc chuyển trạng thái nháp khi cần.</p><p></p>|** |** |
|**Quy tắc nghiệp vụ**|<p>·        Chỉ nhân viên đã xác thực đăng nhập mới được truy cập trang quản trị.</p><p>·        POI ở trạng thái nháp sẽ không hiển thị với du khách.</p><p>·        Khi xóa một POI đang nằm trong bất kỳ lộ trình (Route) hoặc phiên tham quan (Session) nào ở trạng thái đã xuất bản, hệ thống phải hiển thị cảnh báo rủi ro đứt gãy lộ trình và chặn thao tác xóa (chỉ cho phép xóa sau khi xác nhận nếu POI không thuộc lộ trình/session đang xuất bản).</p><p>·        Nội dung do AI sinh ra chỉ là bản nháp, không tự lưu và không tự kích hoạt pipeline; pipeline chỉ chạy khi nhân viên lưu mô tả gốc.</p>|** |** |
|**Xử lý nghiệp vụ**|·        Dịch vụ AI lỗi hoặc quá thời gian: thông báo lỗi và cho nhân viên nhập mô tả thủ công.|** |** |



## **3.6. F-06 – Dashboard giám sát**
F-06 — Dashboard giám sát vận hành theo thời gian thực

|**User story**|Us-14, Us-15|** |** |** |
| :- | :- | :- | :- | :- |
|**Mô tả**|Trang tổng quan giúp quản trị viên hệ thống nắm bắt tình trạng vận hành hệ thống cũng như mức độ sử dụng thực tế của du khách, với dữ liệu được cập nhật liên tục.|** |** |** |
|**Nhóm chỉ số tình trạng hệ thống**|<p>·  Trạng thái tổng thể của ứng dụng, cơ sở dữ liệu và pipeline xử lý (phân loại: bình thường / cảnh báo / lỗi).</p><p>·  Số lượng tác vụ pipeline đang ở trạng thái chờ, đang xử lý, và bị lỗi.</p><p>·  Tỷ lệ lỗi hệ thống và thời gian phản hồi trung bình.</p><p>·  Danh sách các lỗi phát sinh gần nhất.</p>|** |** |** |
|**Nhóm chỉ số sử dụng thực tế**|<p>·        Số lượng người dùng đang hoạt động tại thời điểm hiện tại.</p><p>·        Số lượt nghe thuyết minh, phân tách theo từng POI và từng ngôn ngữ.</p><p>·        Tỷ lệ giữa lượt nghe do tự động phát và lượt nghe do người dùng chủ động chạm vào POI.</p><p>·        Danh sách các lộ trình được lựa chọn nhiều nhất.</p>|** |** |** |
|**Quy tắc nghiệp vụ**|<p>·        Toàn bộ số liệu trên dashboard được làm mới với độ trễ không quá 10 giây.</p><p>·        Trạng thái hệ thống thể hiện bằng ba màu quy ước: xanh, vàng, đỏ để dễ quan sát.</p><p>·        Tự động gửi cảnh báo khi tỷ lệ lỗi vượt ngưỡng cho phép triển khai ở v1.1.</p><p>·        Chỉ tài khoản có vai trò Admin đã đăng nhập mới được truy cập Dashboard.</p>|** |** |** |


# **CHƯƠNG 4. MÔ HÌNH USE CASE**
## **4.1. Danh mục Use Case**

|**Mã UC**|**Tên Use Case**|
| :- | :- |
|UC01|Định vị GPS và xem bản đồ POI lân cận|
|UC02|Nghe và điều khiển thuyết minh POI đa ngôn ngữ|
|UC03|Tùy chỉnh lộ trình cá nhân|
|UC04|Xem lộ trình và bắt đầu dẫn đường|
|UC05|Đăng nhập hệ thống|
|UC06|Quản lý điểm tham quan (POI)|
|UC07|Gợi ý mô tả POI bằng AI|
|UC08|Xử lý Pipeline dịch thuật và tạo Audio tự động|
|UC09|Kiểm duyệt và hiệu chỉnh bản dịch/Audio|
|UC10|Quản lý Session và lộ trình tham quan (Route)|
|UC11|Giám sát hệ thống và thống kê sử dụng thực tế |

**
## **4.2. Sơ đồ Use Case tổng quan (Use Case Diagram)**
![](Aspose.Words.a6265b4e-78e6-429f-8bea-ace273e81d90.001.jpeg)
**\

## **4.3. Đặc tả chi tiết Use Case**
### **4.3.1. UC01 – Định vị GPS và xem bản đồ POI lân cận**
**1. Use Case Number**

UC01

**2. Use Case Name**

Định vị GPS và xem bản đồ POI lân cận (GPS Positioning and Nearby POI Map).

**3. Actor(s)**

- **Primary Actor:** Du khách (User).
- **Supporting Systems:** GPS Location Service, Map Service, POI Database.

**4. Summary**

Use Case cho phép du khách xác định vị trí hiện tại của mình trên bản đồ và xem các điểm tham quan (POI) ở khu vực lân cận. Hệ thống sử dụng GPS để xác định vị trí người dùng, hiển thị các POI đã xuất bản trong bán kính 500 m và cung cấp khoảng cách từ vị trí hiện tại đến từng POI.

**5. Preconditions**

- Du khách đã mở ứng dụng VoiceMap.
- Du khách đã cấp quyền truy cập vị trí để sử dụng chức năng định vị GPS.
- Dữ liệu POI đã được lưu trong hệ thống.

**6. Basic Course of Events**

|**Actor Action**|**System Response**|
| :- | :- |
|1\. Du khách mở ứng dụng và chọn bắt đầu sử dụng.||
||2\. Hệ thống yêu cầu quyền truy cập vị trí nếu cần.|
|3\. Du khách đồng ý chia sẻ vị trí.||
||4\. Hệ thống xác định vị trí hiện tại thông qua GPS.|
|5\. Du khách xem bản đồ.||
||6\. Hệ thống hiển thị vị trí hiện tại và các POI đã xuất bản trong bán kính 500 m, kèm khoảng cách đến từng POI.|
|7\. Du khách tiếp tục di chuyển.||
||8\. Hệ thống cập nhật vị trí và các POI lân cận theo dữ liệu GPS mới.|

**7. Alternative Paths**

**A1. Du khách đã cấp quyền vị trí từ trước**

1. Du khách mở ứng dụng.
1. Hệ thống sử dụng quyền vị trí đã được cấp để xác định vị trí GPS.
1. Hệ thống hiển thị bản đồ và các POI lân cận.

**A2. Không tìm thấy POI lân cận**

1. Hệ thống không tìm thấy POI đã xuất bản trong bán kính 500 m.
1. Hệ thống vẫn hiển thị vị trí hiện tại và thông báo không có POI lân cận.

**8. Exception Paths**

**E1. Du khách từ chối quyền truy cập vị trí**

Hệ thống thông báo không thể xác định vị trí GPS tự động. Du khách vẫn có thể xem hoặc lựa chọn POI thủ công nếu dữ liệu bản đồ khả dụng.

**E2. GPS không khả dụng hoặc tín hiệu yếu**

Hệ thống thông báo không thể xác định chính xác vị trí hiện tại và yêu cầu du khách kiểm tra GPS.

**9. Postconditions**

- Vị trí hiện tại của du khách được hiển thị trên bản đồ nếu GPS hoạt động.
- Các POI đã xuất bản trong bán kính 500 m được hiển thị kèm khoảng cách tương ứng.
- Nếu GPS không khả dụng, hệ thống thông báo và không thực hiện được việc định vị tự động.

**10. Business Rules**

- Hệ thống tìm kiếm và làm nổi bật các POI trong bán kính 500 m tính từ vị trí hiện tại của du khách.
- Chỉ các POI đã xuất bản mới được hiển thị cho du khách.
- Khoảng cách đến POI được tính dựa trên vị trí hiện tại của du khách và tọa độ POI.
- Du khách không cần đăng nhập để sử dụng ứng dụng Mobile.

**11. Activity Diagram**

![](Aspose.Words.a6265b4e-78e6-429f-8bea-ace273e81d90.002.png)

**
### **4.3.2. UC02 – Nghe và điều khiển thuyết minh POI đa ngôn ngữ**
**1. Use Case Number**

UC02

**2. Use Case Name**

Nghe và điều khiển thuyết minh POI đa ngôn ngữ (Listen to and Control Multilingual POI Audio Guide).

**3. Actor(s)**

- **Primary Actor:** Du khách (User).
- **Supporting Systems:** GPS Location Service, Audio Player, POI Database.

**4. Summary**

Use Case cho phép du khách nghe thuyết minh tại các điểm tham quan (POI) bằng ngôn ngữ mong muốn. Hệ thống hỗ trợ tự động phát audio khi du khách đi vào vùng kích hoạt của POI hoặc phát theo yêu cầu khi du khách chủ động chọn POI trên bản đồ. Du khách có thể điều khiển audio và xem nội dung thuyết minh tương ứng.

**5. Preconditions**

- Du khách đã mở ứng dụng VoiceMap.
- POI đã được xuất bản và có nội dung thuyết minh.
- Để tự động phát audio, du khách cần cấp quyền truy cập vị trí.
- Audio hoặc nội dung văn bản của ngôn ngữ được chọn có sẵn trong hệ thống.

**6. Basic Course of Events**

|**Actor Action**|**System Response**|
| :- | :- |
|1\. Du khách chọn ngôn ngữ thuyết minh.||
||2\. Hệ thống ghi nhận ngôn ngữ được chọn.|
|3\. Du khách đi vào vùng kích hoạt của POI hoặc chọn POI trên bản đồ.||
||4\. Hệ thống xác định POI và kiểm tra nội dung thuyết minh tương ứng.|
|5\. Du khách bắt đầu nghe thuyết minh.||
||6\. Hệ thống phát audio và hiển thị văn bản thuyết minh theo ngôn ngữ đã chọn.|
|7\. Du khách thực hiện các thao tác điều khiển audio.||
||8\. Hệ thống xử lý thao tác phát, tạm dừng, tua, điều chỉnh tốc độ hoặc bỏ qua audio.|

**7. Alternative Paths**

**A1. Du khách chủ động chọn POI để nghe**

1. Du khách chọn POI trên bản đồ.
1. Hệ thống hiển thị thông tin POI và nút phát audio.
1. Du khách nhấn nút phát.
1. Hệ thống phát audio tương ứng với ngôn ngữ đã chọn.

**A2. Du khách thay đổi ngôn ngữ**

1. Du khách chọn ngôn ngữ khác.
1. Hệ thống tìm nội dung thuyết minh tương ứng.
1. Hệ thống phát audio và hiển thị văn bản theo ngôn ngữ mới nếu nội dung khả dụng.

**8. Exception Paths**

**E1. Du khách từ chối quyền truy cập vị trí**

Hệ thống không thể tự động phát audio theo GPS nhưng vẫn cho phép du khách chủ động chọn POI để nghe.

**E2. GPS không đủ chính xác**

Hệ thống thông báo tín hiệu GPS yếu và tạm dừng chức năng tự động phát.

**E3. Không có audio cho ngôn ngữ được chọn**

Hệ thống thông báo và phát audio bằng ngôn ngữ mặc định nếu có. Nếu ngôn ngữ chỉ hỗ trợ văn bản, hệ thống hiển thị nội dung chữ thay cho audio.

**E4. Thiết bị chặn phát audio tự động**

Hệ thống hiển thị nút “Chạm để nghe” để du khách chủ động phát audio.

**9. Postconditions**

- Du khách nghe được nội dung thuyết minh nếu audio khả dụng.
- Văn bản thuyết minh được hiển thị theo ngôn ngữ tương ứng.
- Hoạt động nghe được ghi nhận để phục vụ thống kê sử dụng hệ thống.

**10. Business Rules**

- Mỗi POI có bán kính kích hoạt riêng, mặc định là 50 m.
- Audio tự động phát khi du khách nằm trong bán kính kích hoạt, sai số GPS không vượt quá 30 m và duy trì trong vùng tối thiểu 3 giây.
- Hệ thống ưu tiên POI gần nhất khi nhiều POI đồng thời đủ điều kiện kích hoạt.
- POI đã tự động phát không được tự động kích hoạt lại trong vòng 30 phút.
- Du khách có thể phát, tạm dừng, tua tiến/lùi 10 giây, điều chỉnh tốc độ từ 0,75x đến 1,5x và bỏ qua audio.
- Hệ thống tải trước audio của các POI trong bán kính 300 m.
- Thời gian bắt đầu phát audio không vượt quá 1,5 giây đối với file đã tải trước và 3 giây đối với file cần tải mới.

**11. Activity Diagram**

![](Aspose.Words.a6265b4e-78e6-429f-8bea-ace273e81d90.003.png)


### **4.3.3. UC03 – Tùy chỉnh lộ trình cá nhân**
**1. Use Case Number**

UC03

**2. Use Case Name**

Tùy chỉnh lộ trình cá nhân (Customize Personal Route).

**3. Actor(s)**

- **Primary Actor:** Du khách (User).
- **Supporting Systems:** Map / Direction Service, Route Database, POI Database.

**4. Summary**

Use Case cho phép du khách tùy chỉnh lộ trình tham quan theo sở thích cá nhân bằng cách thêm, bớt hoặc thay đổi thứ tự các điểm tham quan (POI) trong lộ trình được gợi ý. Sau khi du khách thực hiện thay đổi, hệ thống tính toán lại quãng đường và thời gian dự kiến của lộ trình để du khách xem xét trước khi xác nhận.

**5. Preconditions**

- Du khách đã mở ứng dụng VoiceMap.
- Hệ thống có ít nhất một lộ trình tham quan được đề xuất.
- Các POI được lựa chọn đã được xuất bản và có dữ liệu vị trí hợp lệ.

**6. Basic Course of Events**

|**Actor Action**|**System Response**|
| :- | :- |
|1\. Du khách chọn một lộ trình tham quan được gợi ý.||
||2\. Hệ thống hiển thị danh sách POI trong lộ trình.|
|3\. Du khách chọn chức năng tùy chỉnh lộ trình.||
||4\. Hệ thống hiển thị danh sách POI và các thao tác tùy chỉnh.|
|5\. Du khách thêm, bớt hoặc sắp xếp lại thứ tự các POI.||
||6\. Hệ thống cập nhật danh sách POI theo lựa chọn của du khách.|
|7\. Du khách xem lộ trình sau khi chỉnh sửa.||
||8\. Hệ thống tính toán lại quãng đường và thời gian dự kiến.|
|9\. Du khách xác nhận lộ trình đã tùy chỉnh.||
||10\. Hệ thống lưu lộ trình tùy chỉnh để du khách sử dụng khi tham quan.|

**7. Alternative Paths**

**A1. Du khách chỉ thay đổi thứ tự POI**

1. Du khách chọn thay đổi thứ tự các POI trong lộ trình.
1. Hệ thống cập nhật thứ tự tham quan theo lựa chọn mới.
1. Hệ thống tính toán lại quãng đường và thời gian dự kiến.

**A2. Du khách thêm hoặc bớt POI**

1. Du khách chọn thêm POI từ danh sách hoặc loại bỏ POI khỏi lộ trình.
1. Hệ thống cập nhật danh sách POI.
1. Hệ thống tính toán lại quãng đường và thời gian dự kiến của lộ trình.

**8. Exception Paths**

**E1. Số lượng POI không hợp lệ**

- Hệ thống kiểm tra số lượng POI sau khi tùy chỉnh.
- Nếu lộ trình có ít hơn 2 hoặc nhiều hơn 10 POI, hệ thống thông báo không hợp lệ và yêu cầu du khách điều chỉnh lại.

**E2. Không tìm được đường đi phù hợp**

- Hệ thống thông báo không thể tính toán tuyến đường phù hợp.
- Du khách có thể thay đổi danh sách POI hoặc thứ tự tham quan để thử lại.

**E3. POI được chọn không khả dụng**

- Nếu POI không còn ở trạng thái đã xuất bản hoặc không có dữ liệu vị trí hợp lệ, hệ thống thông báo và không cho phép thêm POI đó vào lộ trình.

**9. Postconditions**

- Lộ trình được cập nhật theo lựa chọn của du khách nếu quá trình tùy chỉnh thành công.
- Quãng đường và thời gian dự kiến được tính toán lại.
- Lộ trình đã xác nhận sẵn sàng để du khách xem và bắt đầu dẫn đường thông qua UC04.

**10. Business Rules**

- Một lộ trình phải có từ 2 đến 10 POI.
- Du khách được phép thêm, bớt hoặc sắp xếp lại thứ tự các POI trong lộ trình.
- Chỉ POI đã xuất bản mới được đưa vào lộ trình.
- Sau khi lộ trình thay đổi, hệ thống phải tính toán lại quãng đường và thời gian dự kiến.
- Việc bắt đầu dẫn đường thuộc phạm vi UC04, không thuộc UC03.

**11. Activity Diagram**

![](Aspose.Words.a6265b4e-78e6-429f-8bea-ace273e81d90.004.png)
### **4.3.4. UC04 – Xem lộ trình và bắt đầu dẫn đường**
**1. Use Case Number**

UC04

**2. Use Case Name**

Xem lộ trình và bắt đầu dẫn đường (View Route and Start Navigation).

**3. Actor(s)**

- **Primary Actor:** Du khách (User).
- **Supporting Systems:** GPS Location Service, Map / Direction Service, Route Database.

**4. Summary**

Use Case cho phép du khách xem danh sách các lộ trình tham quan được đề xuất, lựa chọn lộ trình phù hợp và bắt đầu dẫn đường đến các điểm tham quan (POI). Hệ thống sử dụng vị trí GPS hiện tại để tính toán tuyến đường, hiển thị chỉ dẫn di chuyển và tự động chuyển sang POI tiếp theo khi du khách đến điểm tham quan hiện tại.

**5. Preconditions**

- Du khách đã mở ứng dụng VoiceMap.
- Có ít nhất một lộ trình tham quan khả dụng trong hệ thống.
- Các POI thuộc lộ trình đã được xuất bản và có dữ liệu vị trí.
- GPS đang hoạt động để xác định vị trí và hỗ trợ dẫn đường.

**6. Basic Course of Events**

|**Actor Action**|**System Response**|
| :- | :- |
|1\. Du khách mở danh sách lộ trình tham quan.||
||2\. Hệ thống hiển thị các lộ trình khả dụng, bao gồm danh sách POI, tổng quãng đường và thời gian dự kiến.|
|3\. Du khách chọn một lộ trình muốn tham quan.||
||4\. Hệ thống hiển thị thông tin chi tiết và thứ tự các POI trong lộ trình.|
|5\. Du khách nhấn nút “Bắt đầu dẫn đường”.||
||6\. Hệ thống xác định vị trí GPS hiện tại và tính toán tuyến đường đến POI đầu tiên.|
|7\. Du khách di chuyển theo chỉ dẫn trên bản đồ.||
||8\. Hệ thống cập nhật vị trí và hướng dẫn di chuyển theo thời gian thực.|
|9\. Du khách đến POI hiện tại.||
||10\. Hệ thống cập nhật trạng thái điểm tham quan và chuyển hướng dẫn sang POI tiếp theo.|
|11\. Du khách tiếp tục di chuyển đến các POI còn lại.||
||12\. Hệ thống kết thúc hành trình khi du khách đến POI cuối cùng trong lộ trình.|

**7. Alternative Paths**

**A1. Du khách xem trước lộ trình**

1. Du khách chọn một lộ trình trong danh sách.
1. Hệ thống hiển thị thứ tự POI, tổng quãng đường và thời gian dự kiến.
1. Du khách quyết định bắt đầu dẫn đường hoặc quay lại danh sách lộ trình.

**A2. Du khách bỏ qua một POI**

1. Trong quá trình dẫn đường, du khách chọn chức năng “Bỏ qua điểm này”.
1. Hệ thống chuyển sang POI tiếp theo trong lộ trình.
1. Hệ thống tính toán lại tuyến đường từ vị trí hiện tại đến POI tiếp theo.

**8. Exception Paths**

**E1. Không xác định được vị trí GPS**

Hệ thống thông báo không thể xác định vị trí hiện tại và yêu cầu du khách kiểm tra GPS trước khi bắt đầu dẫn đường.

**E2. Không tìm được tuyến đường phù hợp**

Hệ thống thông báo không tìm được tuyến đường phù hợp nhưng vẫn hiển thị vị trí của POI tiếp theo trên bản đồ.

**E3. Du khách đi lệch tuyến đường**

Hệ thống phát hiện du khách đi lệch khỏi tuyến đường đã chỉ dẫn và tự động tính toán lại tuyến đường từ vị trí hiện tại.

**9. Postconditions**

- Du khách được dẫn đường theo lộ trình đã chọn.
- Vị trí của du khách và trạng thái các POI trong hành trình được cập nhật.
- Hành trình kết thúc khi du khách đến POI cuối cùng trong lộ trình.

**10. Business Rules**

- Một lộ trình gồm từ 2 đến 10 POI theo thứ tự tham quan đã xác định.
- Lộ trình phải gắn với một Session cụ thể.
- Chỉ POI đã xuất bản mới được đưa vào lộ trình.
- Hệ thống ưu tiên dẫn đường đến POI tiếp theo chưa hoàn thành.
- Khi du khách đến POI cuối cùng, hệ thống kết thúc hành trình.
- Việc tùy chỉnh, thêm, bớt hoặc sắp xếp lại POI thuộc UC03.

**11. Activity Diagram**

![](Aspose.Words.a6265b4e-78e6-429f-8bea-ace273e81d90.005.png)
### **4.3.5. UC05 – Đăng nhập hệ thống**
**1. Use Case Number**

UC05

**2. Use Case Name**

Đăng nhập hệ thống (System Login).

**3. Actor(s)**

- **Primary Actor:** Quản trị viên (Admin)
- **Supporting Systems:** Authentication Service, Database.

**4. Summary**

Use Case cho phép Quản trị viên (Admin) đăng nhập vào hệ thống quản trị VoiceMap bằng email và mật khẩu. Sau khi xác thực thành công, hệ thống tạo phiên đăng nhập và chuyển Admin vào giao diện quản trị (Admin Portal và Monitoring Dashboard). 

**5. Preconditions**

- Người dùng đã được cấp tài khoản trong hệ thống.
- Hệ thống quản trị và dịch vụ xác thực đang hoạt động.

**6. Basic Course of Events**

|**Actor Action**|**System Response**|
| :- | :- |
|1\. Người dùng truy cập trang đăng nhập.||
||2\. Hệ thống hiển thị biểu mẫu đăng nhập gồm email và mật khẩu.|
|3\. Người dùng nhập email, mật khẩu và nhấn “Đăng nhập”.||
||4\. Hệ thống kiểm tra thông tin đăng nhập và đối chiếu với dữ liệu tài khoản.|
||5\. Hệ thống kiểm tra trạng thái tài khoản và xác thực kết quả đăng nhập.|
||6\. Nếu đăng nhập thành công, hệ thống tạo phiên đăng nhập và chuyển người dùng đến giao diện được phân quyền.|

**7. Alternative Paths**

**A1. Người dùng đăng xuất**

1. Người dùng nhấn nút “Đăng xuất”.
1. Hệ thống kết thúc phiên đăng nhập và chuyển về màn hình đăng nhập.

**A2. Người dùng quên mật khẩu**

1. Người dùng chọn chức năng “Quên mật khẩu”.
1. Hệ thống yêu cầu nhập email đã đăng ký.
1. Hệ thống gửi hướng dẫn đặt lại mật khẩu qua email nếu chức năng này được hỗ trợ.

**8. Exception Paths**

**E1. Email hoặc mật khẩu không chính xác**

Hệ thống thông báo thông tin đăng nhập không chính xác và yêu cầu người dùng nhập lại.

**E2. Tài khoản bị vô hiệu hóa**

Hệ thống thông báo tài khoản không hoạt động và từ chối đăng nhập.

**E3. Dịch vụ xác thực gặp lỗi**

Hệ thống thông báo dịch vụ tạm thời không khả dụng và yêu cầu người dùng thử lại sau.

**9. Postconditions**

- Đăng nhập thành công: Admin được truy cập hệ thống quản trị.
- Đăng nhập thất bại: Hệ thống từ chối truy cập.

**10. Business Rules**

- Chỉ Admin có tài khoản hợp lệ mới được đăng nhập hệ thống quản trị.
- Email và mật khẩu phải chính xác, tài khoản phải đang hoạt động.
- Mật khẩu được lưu dưới dạng băm (password\_hash), không lưu văn bản thuần.
- Sau khi đăng nhập thành công, Admin được truy cập cả Admin Portal và Monitoring Dashboard.
- Du khách sử dụng ứng dụng Mobile không cần đăng nhập.

**11. Activity Diagram**

![](Aspose.Words.a6265b4e-78e6-429f-8bea-ace273e81d90.006.png)


### **4.3.6. UC06 – Quản lý điểm tham quan (POI)**
**1. Use Case Number**

UC06

**2. Use Case Name**

Quản lý điểm tham quan (Manage Points of Interest – POI).

**3. Actor(s)**

- **Primary Actor:** Quản trị viên (Admin).
- **Supporting Systems:** Database, Map Service.

**4. Summary**

Use Case cho phép Admin quản lý thông tin các điểm tham quan (POI) trên hệ thống VoiceMap. Admin có thể tạo mới, chỉnh sửa, ẩn hoặc xóa POI, cập nhật tọa độ, bán kính lân cận, hình ảnh và mô tả gốc. POI sau khi được lưu có thể được quản lý theo trạng thái nháp hoặc đã xuất bản.

**5. Preconditions**

- Admin đã đăng nhập thành công vào Admin Portal thông qua UC05.
- Hệ thống và cơ sở dữ liệu đang hoạt động.

**6. Basic Course of Events**

|**Actor Action**|**System Response**|
| :- | :- |
|1\. Admin truy cập chức năng quản lý POI.||
||2\. Hệ thống hiển thị danh sách POI hiện có.|
|3\. Admin chọn tạo POI mới.||
||4\. Hệ thống hiển thị biểu mẫu nhập thông tin POI.|
|5\. Admin nhập tên, tọa độ, bán kính lân cận, hình ảnh và mô tả gốc.||
||6\. Hệ thống kiểm tra tính hợp lệ của thông tin.|
|7\. Admin nhấn “Lưu”.||
||8\. Hệ thống lưu POI vào cơ sở dữ liệu và cập nhật danh sách POI.|
|9\. Admin lựa chọn trạng thái phù hợp cho POI.||
||10\. Hệ thống cập nhật trạng thái nháp hoặc đã xuất bản theo lựa chọn hợp lệ.|

**7. Alternative Paths**

**A1. Chỉnh sửa POI**

1. Admin chọn một POI trong danh sách.
1. Hệ thống hiển thị thông tin hiện tại của POI.
1. Admin chỉnh sửa thông tin và nhấn “Lưu”.
1. Hệ thống kiểm tra và cập nhật thông tin POI.

**A2. Ẩn POI**

1. Admin chọn POI cần ẩn.
1. Admin chọn chức năng “Ẩn”.
1. Hệ thống cập nhật trạng thái hiển thị của POI.

**A3. Xóa POI**

1. Admin chọn POI cần xóa.
1. Hệ thống yêu cầu xác nhận thao tác xóa.
1. Admin xác nhận.
1. Hệ thống thực hiện xóa POI.

**8. Exception Paths**

**E1. Thông tin POI không hợp lệ**

Hệ thống thông báo các trường thông tin chưa hợp lệ và yêu cầu Admin chỉnh sửa trước khi lưu.

**E2. Không lưu được dữ liệu**

Hệ thống thông báo lỗi và yêu cầu Admin thử lưu lại sau khi hệ thống hoạt động bình thường.

**E3. Admin hủy thao tác xóa**

Hệ thống không xóa POI và quay lại màn hình quản lý.

**9. Postconditions**

- POI được tạo mới hoặc cập nhật thành công theo thông tin Admin nhập.
- Trạng thái POI được cập nhật theo thao tác quản lý.
- Nếu Admin xác nhận xóa, POI được xóa khỏi hệ thống theo quy tắc nghiệp vụ.

**10. Business Rules**

- Chỉ Admin đã đăng nhập mới được quản lý POI.
- Mỗi POI cần có thông tin vị trí, mô tả gốc và các dữ liệu cần thiết theo biểu mẫu.
- Tọa độ có thể được nhập trực tiếp hoặc lựa chọn trên bản đồ.
- Admin có thể tải ảnh thumbnail và cấu hình bán kính lân cận cho từng POI.
- POI có trạng thái nháp hoặc đã xuất bản.
- Hệ thống phải yêu cầu xác nhận trước khi xóa POI.
- Chức năng gợi ý mô tả POI bằng AI thuộc UC07; xử lý dịch thuật và tạo audio thuộc UC08.

**11. Activity Diagram**

![](Aspose.Words.a6265b4e-78e6-429f-8bea-ace273e81d90.007.png)


### **4.3.7. UC07 – Gợi ý mô tả POI bằng AI**
**1. Use Case Number**

UC07

**2. Use Case Name**

Gợi ý mô tả POI bằng AI (AI-Assisted POI Description).

**3. Actor(s)**

- **Primary Actor:** Quản trị viên (Admin).
- **Supporting Systems:** AI Service.

**4. Summary**

Use Case cho phép Admin sử dụng AI để tạo bản nháp mô tả cho điểm tham quan (POI) dựa trên tên POI và các ghi chú ngắn gọn. Admin có thể xem xét, chỉnh sửa bản nháp trước khi lưu vào hệ thống. Nội dung chỉ được sử dụng chính thức sau khi Admin xác nhận lưu.

**5. Preconditions**

- Admin đã đăng nhập thành công vào Admin Portal thông qua UC05.
- Admin đang ở màn hình tạo mới hoặc chỉnh sửa POI thuộc UC06.
- Admin đã nhập tên POI làm cơ sở để AI tạo mô tả.
- AI Service đang hoạt động.

**6. Basic Course of Events**

|**Actor Action**|**System Response**|
| :- | :- |
|1\. Admin nhập tên POI và các ghi chú (nếu có).||
||2\. Hệ thống tiếp nhận thông tin được cung cấp.|
|3\. Admin nhấn nút “Gợi ý bằng AI”.||
||4\. Hệ thống gửi thông tin đến AI Service để tạo mô tả.|
|5\. Admin chờ kết quả.||
||6\. AI Service trả về bản nháp mô tả trong vòng 15 giây và hệ thống hiển thị nội dung trên khung soạn thảo.|
|7\. Admin kiểm tra, chỉnh sửa bản nháp nếu cần và nhấn “Lưu”.||
||8\. Hệ thống lưu mô tả gốc vào cơ sở dữ liệu và có thể kích hoạt Pipeline dịch thuật, tạo audio theo UC08.|

**7. Alternative Paths**

**A1. Admin không sử dụng bản nháp AI**

1. Admin xóa nội dung bản nháp do AI tạo ra.
1. Admin tự nhập nội dung mô tả POI.
1. Admin nhấn “Lưu”.
1. Hệ thống lưu nội dung mô tả do Admin nhập.

**8. Exception Paths**

**E1. AI Service gặp lỗi hoặc phản hồi quá 15 giây**

- Hệ thống thông báo: “Không thể kết nối với dịch vụ AI lúc này. Vui lòng thử lại hoặc nhập thủ công.”
- Admin có thể thử lại hoặc tự nhập nội dung mô tả.

**E2. Admin thoát màn hình khi chưa lưu**

- Hệ thống hủy bản nháp AI chưa được lưu.
- Nội dung nháp không được tự động lưu vào cơ sở dữ liệu và Pipeline dịch thuật, tạo audio không được kích hoạt từ nội dung đó.

**9. Postconditions**

- Bản nháp mô tả POI được tạo và hiển thị để Admin xem xét.
- Nếu Admin xác nhận lưu, mô tả gốc được cập nhật vào cơ sở dữ liệu.
- Nếu Admin không lưu, bản nháp không được sử dụng làm nội dung chính thức.

**10. Business Rules**

- Chỉ Admin đã đăng nhập mới được sử dụng chức năng gợi ý mô tả POI bằng AI.
- Tên POI là thông tin đầu vào bắt buộc; ghi chú bổ sung là tùy chọn.
- AI chỉ tạo bản nháp, không tự động cập nhật mô tả chính thức vào cơ sở dữ liệu.
- Admin phải kiểm tra, chỉnh sửa nếu cần và xác nhận lưu trước khi nội dung có hiệu lực.
- AI Service cần trả về kết quả trong vòng 15 giây; nếu quá thời gian hoặc gặp lỗi, Admin có thể nhập mô tả thủ công.
- Pipeline dịch thuật và tạo audio chỉ được kích hoạt sau khi mô tả gốc được lưu theo quy trình của UC08.

**11. Activity Diagram**

![](Aspose.Words.a6265b4e-78e6-429f-8bea-ace273e81d90.008.png)


### **4.3.8. UC08 – Xử lý Pipeline dịch thuật và tạo Audio tự động**
**1. Use Case Number**

UC08

**2. Use Case Name**

Xử lý Pipeline Dịch thuật và Tạo Audio tự động (Automated Translation and Audio Generation Pipeline).

**3. Actor(s)**

- **Primary Actor:** Quản trị viên (Admin).
- **Supporting Systems:** Translation Service, Text-to-Speech (TTS) Service, Database, Audio Storage.

**4. Summary**

Use Case cho phép hệ thống tự động dịch mô tả gốc của POI sang hơn 15 ngôn ngữ và chuyển các bản dịch thành file audio bằng công nghệ Text-to-Speech. Khi Admin lưu mô tả gốc, hệ thống tự động khởi chạy Pipeline, xử lý nội dung theo từng ngôn ngữ, lưu kết quả và cập nhật trạng thái xử lý.

**5. Preconditions**

- Admin đã đăng nhập thành công vào Admin Portal thông qua UC05.
- POI đã tồn tại trong hệ thống và có mô tả gốc hợp lệ.
- Translation Service và TTS Service đang hoạt động.
- Danh sách ngôn ngữ và các thuật ngữ cần giữ nguyên khi dịch đã được cấu hình.

**6. Basic Course of Events**

|**Actor Action**|**System Response**|
| :- | :- |
|1\. Admin nhập hoặc chỉnh sửa mô tả gốc của POI và nhấn “Lưu” trong UC06.||
||2\. Hệ thống lưu mô tả gốc và tự động khởi chạy Pipeline dịch thuật, tạo audio.|
|3\. Admin chờ quá trình xử lý hoàn tất.||
||4\. Hệ thống đọc danh sách protected\_terms, tạo tác vụ xử lý riêng cho từng ngôn ngữ và bắt đầu dịch nội dung.|
||5\. Translation Service dịch mô tả sang các ngôn ngữ được cấu hình, đồng thời giữ nguyên các thuật ngữ cần bảo vệ.|
||6\. Hệ thống lưu các bản dịch vào cơ sở dữ liệu và gửi nội dung dịch đến TTS Service để tạo audio tương ứng.|
||7\. Hệ thống lưu file audio vào kho lưu trữ, cập nhật trạng thái xử lý và lưu đường dẫn file trong cơ sở dữ liệu.|
|8\. Admin xem trạng thái xử lý Pipeline.||
||9\. Hệ thống hiển thị trạng thái xử lý của từng ngôn ngữ.|

**7. Alternative Paths**

**A1. Xử lý nhiều ngôn ngữ đồng thời**

1. Hệ thống tạo các tác vụ riêng cho từng ngôn ngữ.
1. Các tác vụ được xử lý đồng thời.
1. Hệ thống cập nhật trạng thái và lưu kết quả riêng cho từng ngôn ngữ.

**A2. Mô tả gốc không thay đổi**

1. Admin lưu nội dung mô tả không có thay đổi.
1. Hệ thống kiểm tra và không thực hiện dịch lại nội dung đã được xử lý thành công nếu không cần thiết.

**8. Exception Paths**

**E1. Dịch thuật thất bại**

- Hệ thống tự động thử lại tác vụ dịch thuật tối đa 3 lần.
- Nếu vẫn thất bại, hệ thống cập nhật trạng thái lỗi và ghi nhận thông tin để hiển thị trên Dashboard giám sát.

**E2. Tạo audio thất bại**

- Hệ thống tự động thử lại tác vụ tạo audio tối đa 3 lần.
- Nếu vẫn thất bại, hệ thống cập nhật trạng thái lỗi và ghi nhận thông tin để Admin có thể kiểm tra, yêu cầu tạo lại audio thông qua UC09.

**E3. Mô tả gốc không hợp lệ**

Hệ thống từ chối khởi chạy Pipeline và thông báo để Admin kiểm tra, chỉnh sửa mô tả gốc.

**E4. Ngôn ngữ không được TTS hỗ trợ**

Hệ thống lưu bản dịch văn bản, đánh dấu ngôn ngữ ở trạng thái chỉ có bản văn bản và không tạo audio cho ngôn ngữ đó.

**9. Postconditions**

- Các bản dịch được tạo và lưu vào cơ sở dữ liệu theo từng ngôn ngữ.
- File audio được tạo và lưu vào kho lưu trữ nếu TTS xử lý thành công.
- Trạng thái Pipeline được cập nhật tương ứng với kết quả xử lý của từng ngôn ngữ.
- Nội dung đã xử lý thành công sẵn sàng phục vụ ứng dụng Mobile.

**10. Business Rules**

- Pipeline tự động khởi chạy khi mô tả gốc của POI được lưu hoặc cập nhật.
- Trước khi dịch, hệ thống phải áp dụng danh sách protected\_terms để giữ nguyên tên riêng, địa danh và thuật ngữ cần bảo vệ.
- Hệ thống hỗ trợ dịch sang hơn 15 ngôn ngữ theo danh mục được cấu hình.
- Pipeline chạy nền, không làm gián đoạn thao tác của Admin.
- Mỗi ngôn ngữ có một trong bốn trạng thái: Pending (đang chờ), Processing (đang xử lý), Completed (hoàn tất) hoặc Failed (thất bại).
- Khi gặp lỗi, hệ thống tự động thử lại tối đa 3 lần trước khi ghi nhận trạng thái thất bại.
- Nếu mô tả gốc thay đổi, hệ thống thực hiện lại Pipeline cho các ngôn ngữ tương ứng.
- Bản dịch được lưu trong cơ sở dữ liệu; file audio được lưu trong kho lưu trữ và cơ sở dữ liệu lưu đường dẫn đến file.
- Nếu chỉ chỉnh sửa thủ công một bản dịch, hệ thống chỉ cần tạo lại audio cho ngôn ngữ đó theo UC09.

**11. Activity Diagram**

![](Aspose.Words.a6265b4e-78e6-429f-8bea-ace273e81d90.009.png)


### **4.3.9. UC09 – Kiểm duyệt và hiệu chỉnh bản dịch/Audio**
**1. Use Case Number**

UC09

**2. Use Case Name**

Kiểm duyệt và hiệu chỉnh bản dịch/Audio (Review and Edit Translations/Audio).

**3. Actor(s)**

- **Primary Actor:** Quản trị viên (Admin).
- **Supporting Systems:** Database, Text-to-Speech (TTS) Service, Audio Storage.

**4. Summary**

Use Case cho phép Admin kiểm tra nội dung bản dịch và nghe thử audio được tạo tự động cho từng POI theo các ngôn ngữ. Admin có thể chỉnh sửa bản dịch hoặc yêu cầu tạo lại audio khi phát hiện nội dung chưa chính xác hoặc chất lượng chưa phù hợp. Sau khi lưu thay đổi, hệ thống cập nhật nội dung và trạng thái xử lý tương ứng.

**5. Preconditions**

- Admin đã đăng nhập thành công vào hệ thống thông qua UC05.
- POI đã có bản dịch hoặc audio được tạo từ Pipeline của UC08.
- Dữ liệu bản dịch và trạng thái audio đã được lưu trong hệ thống.

**6. Basic Course of Events**

|**Actor Action**|**System Response**|
| :- | :- |
|1\. Admin truy cập chức năng kiểm duyệt bản dịch/audio.||
||2\. Hệ thống hiển thị danh sách POI, các ngôn ngữ và trạng thái xử lý tương ứng.|
|3\. Admin chọn POI và ngôn ngữ cần kiểm tra.||
||4\. Hệ thống hiển thị nội dung bản dịch và audio tương ứng nếu có.|
|5\. Admin kiểm tra bản dịch và nghe thử audio.||
||6\. Hệ thống cung cấp nội dung văn bản và trình phát audio để Admin kiểm tra.|
|7\. Admin xác nhận nội dung phù hợp hoặc thực hiện chỉnh sửa khi cần.||
||8\. Hệ thống lưu bản dịch đã chỉnh sửa hoặc tiếp nhận yêu cầu tạo lại audio.|
|9\. Admin kiểm tra kết quả sau khi xử lý.||
||10\. Hệ thống cập nhật bản dịch, audio và trạng thái xử lý tương ứng.|

**7. Alternative Paths**

**A1. Admin chỉnh sửa bản dịch**

1. Admin chọn bản dịch cần chỉnh sửa.
1. Admin cập nhật nội dung văn bản và nhấn “Lưu”.
1. Hệ thống lưu bản dịch mới.
1. Hệ thống tạo lại audio cho riêng ngôn ngữ được chỉnh sửa thông qua TTS Service.

**A2. Admin yêu cầu tạo lại audio**

1. Admin chọn POI và ngôn ngữ cần tạo lại audio.
1. Admin nhấn chức năng “Tạo lại audio”.
1. Hệ thống gửi yêu cầu đến TTS Service.
1. Hệ thống lưu file audio mới và cập nhật trạng thái xử lý.

**8. Exception Paths**

**E1. Không có bản dịch hoặc audio**

Hệ thống thông báo nội dung chưa khả dụng và hiển thị trạng thái xử lý để Admin kiểm tra.

**E2. Tạo lại audio thất bại**

Hệ thống ghi nhận lỗi, cập nhật trạng thái audio và cho phép Admin yêu cầu tạo lại sau đó.

**E3. Không lưu được bản dịch đã chỉnh sửa**

Hệ thống thông báo lỗi và yêu cầu Admin thử lưu lại.

**9. Postconditions**

- Bản dịch được cập nhật nếu Admin thực hiện chỉnh sửa thành công.
- Audio được tạo lại nếu yêu cầu xử lý thành công.
- Trạng thái bản dịch và audio được cập nhật theo kết quả xử lý.

**10. Business Rules**

- Chỉ Admin đã đăng nhập mới được kiểm duyệt và hiệu chỉnh bản dịch/audio.
- Mỗi bản dịch phải gắn với một POI và một ngôn ngữ cụ thể.
- Admin có thể chỉnh sửa thủ công bản dịch do hệ thống tạo tự động.
- Khi bản dịch được chỉnh sửa, hệ thống chỉ tạo lại audio cho ngôn ngữ tương ứng, không dịch lại toàn bộ các ngôn ngữ.
- Khi yêu cầu tạo lại audio, hệ thống sử dụng bản dịch hiện tại của ngôn ngữ đó.
- Trạng thái xử lý bản dịch/audio phải được cập nhật theo kết quả thực tế.

**11. Activity Diagram**

Sơ đồ Activity Diagram mô tả quá trình Admin chọn POI và ngôn ngữ, kiểm tra bản dịch và nghe thử audio, sau đó quyết định lưu bản dịch đã chỉnh sửa hoặc yêu cầu tạo lại audio. Các nhánh ngoại lệ thể hiện trường hợp thiếu dữ liệu, lưu bản dịch thất bại hoặc tạo audio không thành công.

![](Aspose.Words.a6265b4e-78e6-429f-8bea-ace273e81d90.010.png)
### **4.3.10. UC10 – Quản lý Session và lộ trình tham quan (Route)**
**1. Use Case Number**

UC10

**2. Use Case Name**

Quản lý Session và Lộ trình tham quan (Manage Tour Sessions and Routes).

**3. Actor(s)**

- **Primary Actor:** Quản trị viên (Admin).
- **Supporting Systems:** Admin Portal, Database, POI Management Module.

**4. Summary**

Use Case cho phép Admin tạo mới, chỉnh sửa và quản lý các phiên tham quan (Session) và lộ trình tham quan (Route) trên hệ thống VoiceMap. Admin có thể thêm Route vào Session, sắp xếp thứ tự các POI trong từng Route và cập nhật trạng thái nháp hoặc đã xuất bản. Khi Session được xuất bản, các nội dung hợp lệ sẽ được hiển thị trên ứng dụng dành cho du khách.

**5. Preconditions**

- Admin đã đăng nhập thành công vào hệ thống thông qua UC05.
- Hệ thống và cơ sở dữ liệu đang hoạt động.
- Các POI cần sử dụng trong lộ trình đã tồn tại trong hệ thống.

**6. Basic Course of Events**

|**Actor Action**|**System Response**|
| :- | :- |
|1\. Admin truy cập chức năng quản lý Session và Route.||
||2\. Hệ thống hiển thị danh sách Session và Route hiện có.|
|3\. Admin chọn tạo mới hoặc chỉnh sửa Session.||
||4\. Hệ thống hiển thị biểu mẫu nhập thông tin Session.|
|5\. Admin nhập tên, mô tả và lựa chọn các Route thuộc Session.||
||6\. Hệ thống hiển thị thông tin Route và danh sách POI trong từng Route.|
|7\. Admin thêm POI và sắp xếp thứ tự tham quan trong Route.||
||8\. Hệ thống cập nhật danh sách POI theo thứ tự được chọn.|
|9\. Admin nhấn “Lưu”.||
||10\. Hệ thống kiểm tra tính hợp lệ của Session, Route và các POI trước khi lưu dữ liệu.|
|11\. Admin chọn trạng thái nháp hoặc xuất bản Session.||
||12\. Hệ thống cập nhật trạng thái và hiển thị thông báo kết quả. Nếu Session được xuất bản, hệ thống cung cấp nội dung hợp lệ cho ứng dụng du khách.|

**7. Alternative Paths**

**A1. Chỉnh sửa Session hoặc Route**

1. Admin chọn Session hoặc Route cần chỉnh sửa.
1. Hệ thống hiển thị thông tin hiện tại.
1. Admin cập nhật thông tin, danh sách Route hoặc thứ tự POI.
1. Hệ thống kiểm tra và lưu các thay đổi.

**A2. Sắp xếp lại thứ tự POI**

1. Admin chọn Route cần chỉnh sửa.
1. Admin thay đổi thứ tự các POI trong Route.
1. Hệ thống cập nhật thứ tự tham quan và lưu thay đổi.

**A3. Tạm đóng Session**

1. Admin chọn Session cần tạm đóng.
1. Admin thay đổi trạng thái Session.
1. Hệ thống cập nhật trạng thái và ngừng hiển thị Session trên ứng dụng dành cho du khách.

**8. Exception Paths**

**E1. Thiếu thông tin bắt buộc**

Hệ thống thông báo các thông tin còn thiếu và yêu cầu Admin bổ sung trước khi lưu.

**E2. POI chưa được xuất bản**

Hệ thống không cho phép thêm POI chưa được xuất bản vào lộ trình phục vụ du khách.

**E3. Lộ trình không hợp lệ**

Hệ thống thông báo lỗi khi dữ liệu Route hoặc thứ tự POI không hợp lệ và yêu cầu Admin chỉnh sửa.

**9. Postconditions**

- Session và Route được tạo mới hoặc cập nhật thành công.
- Danh sách POI và thứ tự tham quan trong từng Route được lưu lại.
- Trạng thái Session được cập nhật theo thao tác của Admin.
- Session đã xuất bản với các Route hợp lệ được hiển thị trên ứng dụng dành cho du khách.

**10. Business Rules**

- Chỉ Admin đã đăng nhập mới được quản lý Session và Route.
- Một Session có thể chứa nhiều Route; mỗi Route thuộc về một Session.
- Mỗi Route quản lý danh sách POI theo thứ tự trong trường poi\_sequence.
- POI được gán vào Route, không gán trực tiếp vào Session.
- Chỉ POI đã xuất bản mới được sử dụng trong lộ trình phục vụ du khách.
- Session có trạng thái nháp hoặc đã xuất bản.
- Session hoặc Route ở trạng thái nháp không được hiển thị cho du khách.

**11. Activity Diagram**

![](Aspose.Words.a6265b4e-78e6-429f-8bea-ace273e81d90.011.png)



### **4.3.11. UC11 – Giám sát hệ thống và thống kê sử dụng thực tế**
**1. Use Case Number**

UC11

**2. Use Case Name**

Giám sát hệ thống và thống kê sử dụng thực tế (System Monitoring and Usage Analytics).

**3. Actor(s)**

- **Primary Actor:** Quản trị viên (Admin).
- **Supporting Systems:** Monitoring Service, Logging System, Database.

**4. Summary**

Use Case cho phép Admin theo dõi tình trạng hoạt động của hệ thống VoiceMap và xem thống kê sử dụng của du khách thông qua Dashboard. Hệ thống hiển thị trạng thái ứng dụng, cơ sở dữ liệu, Pipeline dịch thuật và tạo audio, các lỗi gần nhất cùng những số liệu sử dụng như lượt nghe thuyết minh, POI phổ biến và ngôn ngữ được sử dụng nhiều nhất.

**5. Preconditions**

- Admin đã đăng nhập thành công vào hệ thống thông qua UC05.
- Monitoring Dashboard đang hoạt động.
- Hệ thống có dữ liệu giám sát và dữ liệu sử dụng để hiển thị.

**6. Basic Course of Events**

|**Actor Action**|**System Response**|
| :- | :- |
|1\. Admin truy cập Monitoring Dashboard.||
||2\. Hệ thống hiển thị giao diện Dashboard.|
|3\. Admin chọn mục giám sát hệ thống.||
||4\. Hệ thống hiển thị trạng thái ứng dụng, cơ sở dữ liệu, Pipeline và danh sách lỗi gần nhất.|
|5\. Admin chọn mục thống kê sử dụng.||
||6\. Hệ thống hiển thị số người dùng đang hoạt động, lượt nghe theo POI và ngôn ngữ, cùng các lộ trình phổ biến.|
|7\. Admin theo dõi các thông tin trên Dashboard.||
||8\. Hệ thống cập nhật dữ liệu để Admin theo dõi tình trạng vận hành và mức độ sử dụng thực tế.|

**7. Alternative Paths**

**A1. Admin chỉ xem trạng thái hệ thống**

1. Admin chọn mục “System Monitoring”.
1. Hệ thống hiển thị trạng thái ứng dụng, cơ sở dữ liệu, Pipeline, tỷ lệ lỗi, thời gian phản hồi và các lỗi gần nhất.

**A2. Admin chỉ xem thống kê sử dụng**

1. Admin chọn mục “Usage Analytics”.
1. Hệ thống hiển thị số người dùng đang hoạt động, lượt nghe theo POI và ngôn ngữ, tỷ lệ nghe tự động so với nghe chủ động và các lộ trình phổ biến.

**8. Exception Paths**

**E1. Không tải được dữ liệu giám sát**

Hệ thống thông báo dữ liệu giám sát tạm thời không khả dụng.

**E2. Không có dữ liệu thống kê**

Hệ thống thông báo chưa có dữ liệu sử dụng trong khoảng thời gian được chọn.

**E3. Monitoring Service gặp lỗi**

Hệ thống thông báo chức năng giám sát tạm thời không khả dụng.

**9. Postconditions**

- Admin xem được trạng thái hoạt động của các thành phần hệ thống.
- Các lỗi gần nhất được hiển thị để Admin theo dõi.
- Thống kê sử dụng thực tế được hiển thị dựa trên dữ liệu đã ghi nhận.

**10. Business Rules**

- Chỉ Admin đã đăng nhập mới được truy cập Monitoring Dashboard.
- Trạng thái ứng dụng, cơ sở dữ liệu và Pipeline được phân loại thành bình thường, cảnh báo hoặc lỗi.
- Dashboard sử dụng ba màu quy ước: xanh, vàng và đỏ để thể hiện trạng thái hệ thống.
- Các chỉ số giám sát gồm số tác vụ Pipeline đang chờ, đang xử lý hoặc bị lỗi, tỷ lệ lỗi hệ thống và thời gian phản hồi trung bình.
- Dữ liệu thống kê gồm số người dùng đang hoạt động, lượt nghe theo POI và ngôn ngữ, tỷ lệ nghe tự động so với nghe chủ động và các lộ trình phổ biến.
- Dữ liệu Dashboard được cập nhật với độ trễ không quá 10 giây.
- Chức năng tự động gửi cảnh báo khi tỷ lệ lỗi vượt ngưỡng thuộc phạm vi dự kiến của phiên bản 1.1.

**11. Activity Diagram**

![](Aspose.Words.a6265b4e-78e6-429f-8bea-ace273e81d90.012.png)

VoiceMap  |  Nhóm 2  |  Trang 1
