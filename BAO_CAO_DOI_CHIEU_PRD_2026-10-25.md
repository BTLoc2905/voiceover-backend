# Báo cáo đối chiếu PRD VoiceMap — 25/10/2026

## Phạm vi và cách đánh giá

Nguồn yêu cầu chính: [Nhom2_CNPM.md](./Nhom2_CNPM.md), gồm Us-01–Us-16, FR-1–FR-7, F-01–F-06, UC01–UC11 và mục 2.4. Đã đối chiếu thêm [README](./README.md), [báo cáo cũ](./PRD_SOURCE_AUDIT.md), [bàn giao](./HANDOFF_TO_TEAM.md), [Session/Routes](./SESSION_ROUTES_REQUIREMENTS.md), [UC02 GPS](./UC02_GPS_IMPLEMENTATION.md), [UC03](./UC03_IMPLEMENTATION.md), [UC07](./UC07_IMPLEMENTATION.md), [UC09](./UC09_IMPLEMENTATION.md) và [README frontend](./voiceover-frontend/README.md).

Đánh giá dựa trên working tree hiện tại và đường gọi thực tế Controller → Service → Repository/provider → client. Tài liệu tiến độ là bằng chứng lịch sử, không thay thế code. Không gọi API ghi nghiệp vụ, không khởi động ứng dụng để chạy migration, không sửa code. Chỉ chạy test sẵn có và tạo báo cáo này.

Quy ước:

- **Đầy đủ:** đã có đủ xử lý cho phạm vi chức năng cụ thể nêu trong hàng; không suy rộng thành đã đạt SLA hoặc nghiệm thu mobile.
- **Một phần:** có xử lý thực nhưng thiếu nhánh, có sai lệch hoặc chưa nối đủ luồng.
- **Chưa triển khai:** không thấy xử lý tương ứng trong mã ứng dụng hiện tại.
- **Chưa đủ bằng chứng:** cần môi trường/provider/thiết bị hoặc đo lường thực tế để kết luận.

Kết quả kiểm chứng trong lượt khảo sát:

| Kiểm tra | Kết quả | Giới hạn |
|---|---|---|
| Backend: `mvnw.cmd -o -B test` | BUILD SUCCESS; 60 test, 0 failure/error/skipped | 14 lớp test; repository Mockito, MockMvc standalone và HTTP stub. Không kiểm schema/MySQL, transaction/proxy Spring đầy đủ hoặc provider thật. |
| Frontend: `node --test` cho autoNarration, narrationPlayer, routePlan, routeJourney | 29/29 qua | Test logic, media/GPS giả; không phải trình duyệt/điện thoại thật. |
| Build/lint frontend | Không chạy lại | Checkout khảo sát không có node_modules; không cài thêm dependency. Kết quả build cũ trong tài liệu chỉ là lịch sử. |
| Provider trong terminal khảo sát | Không có các biến GOOGLE_CLOUD_API_KEY, PIPER_EXECUTABLE, PIPER_MODEL, GEMINI_API_KEY; không có .env tại gốc | Không suy ra cấu hình của một tiến trình khác hoặc môi trường triển khai. Chưa gọi provider thật. |
| MySQL/runtime | Chưa nghiệm thu lại trong lượt này | Tài liệu Session/Routes ghi các lần kiểm DB/restart trước đây; không coi đó là kết quả thực hiện mới. |

## A. Kiến trúc và luồng hiện có

### Kết luận chính

Dự án đã có phần lớn khung và nhiều luồng nghiệp vụ thực, đặc biệt GPS/bộ phát, tùy chỉnh Route, Session/Routes và sửa riêng bản dịch. Tuy nhiên, **chưa đủ điều kiện kết luận luồng tạo POI bất kỳ → dịch → file audio → xuất bản → khách nghe đã hoàn chỉnh**. Các điểm chặn chính là tính nhất quán phiên bản khi đổi nguồn, xử lý thiếu provider như thành công text-only, fallback audio, side effect trên API đọc và schema chưa khớp nghiệp vụ.

Repository gồm backend **và** frontend web React/Vite. Không tìm thấy dự án React Native/native mobile. Có thể đánh giá code web và hợp đồng API; chưa xác minh được sản phẩm mobile theo phạm vi PRD.

### Thành phần

| Thành phần | Hiện trạng và bằng chứng |
|---|---|
| Backend | Java 21, Spring Boot 4.1.1, WebMVC, JPA, Flyway, MySQL; [pom.xml](./pom.xml). Monolith theo Controller → Service → Repository, chưa phải các server độc lập như sơ đồ định hướng. |
| Điểm vào frontend | [main.jsx](./voiceover-frontend/src/main.jsx) mount VoiceMapScreens hoặc AdminPortal theo pathname. App mẫu và VoiceMapDemoApp không được mount. |
| Dữ liệu | POI, language, translation, audio, pipeline_task, session, route, route_stop, admin_account, protected_term, usage_event, system_log, system_metric; [migration V1–V13](./src/main/resources/db/migration/). Có thêm bảng ai_description_draft ở V13. |
| Quan hệ | Translation unique theo POI/ngôn ngữ; audio unique theo translation và POI/ngôn ngữ; Route thuộc Session, thứ tự qua route_stop. Không gán POI trực tiếp Session. |
| Job | @Async và ThreadPoolTaskExecutor trong [AppConfig](./src/main/java/com/voiceover/backend/config/AppConfig.java#L39): core 5, max 20, queue 100 trong RAM. pipeline_task lưu trạng thái DB; chưa có broker/queue bền vững, lease hay scheduler quét job định kỳ. |
| Dịch | [PipelineService](./src/main/java/com/voiceover/backend/services/PipelineService.java#L265) gọi Google Cloud Translation nếu có key; nếu không dùng endpoint Google Translate không chính thức. Đây là HTTP thật, không phải hàm dịch giả. |
| TTS | [AudioGenerationService](./src/main/java/com/voiceover/backend/services/AudioGenerationService.java#L59) nối Google Cloud TTS hoặc Piper một ngôn ngữ cấu hình. Google chia văn bản theo byte và ghép PCM thành WAV; Piper chạy executable. |
| File | WAV có UUID mới, file .partial rồi move; DB giữ URL/thời lượng. [AppConfig](./src/main/java/com/voiceover/backend/config/AppConfig.java#L32) phục vụ /storage/audio/** và /audio/**. Ảnh local qua [ImageResourceConfiguration](./src/main/java/com/voiceover/backend/config/ImageResourceConfiguration.java). Chưa thấy object storage hoặc cấu hình volume triển khai. |
| AI mô tả | [AiDescriptionService](./src/main/java/com/voiceover/backend/services/AiDescriptionService.java#L47) thực sự gọi Gemini, lưu nháp/lỗi, có sửa/nhận/bỏ; không còn chỉ là template như báo cáo cũ mô tả. |
| Directions | [WalkingDirectionsService](./src/main/java/com/voiceover/backend/services/WalkingDirectionsService.java) gọi OSRM profile đi bộ, cache 5 phút, giới hạn tần suất, kiểm geometry/metrics. |
| Ảnh/OSM bổ sung | Wikimedia, OpenAI Images tùy key, SVG placeholder nếu thiếu key; POI/narration dựng sẵn. Các nhánh này không chứng minh pipeline cho POI bất kỳ. |
| Monitoring | Cùng datasource nghiệp vụ; schema system_metric có nhưng không thấy collector/entity/repository hay API đọc metric. Dashboard hiện là thống kê cơ bản. |
| Delivery | [Dockerfile](./Dockerfile) build JAR backend; [maven.yml](./.github/workflows/maven.yml) test/backend build và đẩy image GHCR. Chưa có bước triển khai server, frontend CI hoặc smoke test DB/provider/container/storage. |

Luồng chính hiện có:

`Admin lưu POI → commit POI → @Async xử lý lần lượt ngôn ngữ → dịch/lưu translation → TTS hoặc TEXT_ONLY → lưu audio/task → Admin xem/sửa qua UC09 → trạng thái POI cho phép public → GET guide → HTML Audio hoặc SpeechSynthesis.`

**Xuất bản hiện độc lập với hoàn tất pipeline và kiểm duyệt.** Không có bước approval bắt buộc trước khi chuyển PUBLISHED.

## B. Bảng đối chiếu yêu cầu

Các hàng “Đầy đủ” dưới đây chỉ nói phạm vi chức năng được nêu; tiền điều kiện xác thực Admin còn thiếu được đánh giá riêng ở UC05.

| Mã yêu cầu/UC | Yêu cầu | Trạng thái | Bằng chứng trong code | Phần còn thiếu/giới hạn |
|---|---|---|---|---|
| Us-01 / FR-1 / UC01 | GPS, POI 500 m, khoảng cách | Một phần | [useTourLocation](./voiceover-frontend/src/useTourLocation.js); [VoiceMapScreens](./voiceover-frontend/src/VoiceMapScreens.jsx#L287) lọc 500 m; GET /api/public/pois | API /pois/nearby không lọc bán kính và có ghi dữ liệu; chưa nghiệm thu thiết bị. |
| Us-02 / F-01 / UC02 | Tự phát, 50 m cấu hình, accuracy ≤30 m, dwell 3 s, +20 m, cooldown 30 phút, ưu tiên/hàng đợi | Đầy đủ về logic web | [AutoNarration](./voiceover-frontend/src/autoNarration.js), [useTourNarration](./voiceover-frontend/src/useTourNarration.js), 9 test autoNarration | Chưa đủ bằng chứng độ trễ ≤5 s/GPS thật và chính sách app nền. |
| Us-03 / UC02 | Chạm POI, xem nội dung và nghe | Một phần | [VoiceMapScreens](./voiceover-frontend/src/VoiceMapScreens.jsx#L319), [NarrationAudioScreen](./voiceover-frontend/src/NarrationAudioScreen.jsx), GET /pois/{id}/guide | SLA thẻ 2 s/audio 1,5–3 s chưa đo; phụ thuộc file hoặc giọng thiết bị. |
| Us-04 / UC02-E3 | Chọn >15 ngôn ngữ, fallback audio mặc định | Một phần | [PoiService.getPoiAudioGuide](./src/main/java/com/voiceover/backend/services/PoiService.java#L115); [danh sách khách](./voiceover-frontend/src/VoiceMapScreens.jsx#L225) | Fallback dựa vào thiếu translation, không dựa vào thiếu audio; UI hardcode 17 mã, DB seed 16, lệch zh-TW. |
| Us-05 / UC02 | Pause, tua ±10 s, tốc độ 0,75–1,5x, bỏ qua | Một phần | [NarrationPlayer](./voiceover-frontend/src/narrationPlayer.js), [màn nghe](./voiceover-frontend/src/NarrationAudioScreen.jsx) | File audio có điều khiển theo thời gian media; SpeechSynthesis tua/thời lượng chỉ ước tính. |
| F-01 / UC02 | Preload trong 300 m, không chồng tiếng | Đầy đủ về logic web | [GuidePreloader](./voiceover-frontend/src/narrationApi.js), [bộ phát chung](./voiceover-frontend/src/useTourNarration.js) | Tối đa 3 request đồng thời; hiệu quả cache và SLA trên mạng thật chưa đo. |
| Us-06 / FR-2 / UC04 | Chặng đi bộ, tự tới điểm tiếp, lệch tuyến tính lại | Một phần | [RouteJourney](./voiceover-frontend/src/RouteJourney.jsx), [JourneyArrivalTracker/OffRouteTracker](./voiceover-frontend/src/routeJourney.js), [walkingLeg](./src/main/java/com/voiceover/backend/services/RouteSessionService.java#L234) | Đã có logic tự chuyển, không phải chỉ thao tác tay. Điểm chồng vùng yêu cầu xác nhận; chưa nghiệm thu thực địa. Không có chỉ dẫn rẽ từng bước: OSRM steps=false. |
| Us-07 / UC03 | Thêm/bớt/đổi thứ tự, tính lại và xác nhận | Đầy đủ cho bản cá nhân trên web | [RoutePlanner](./voiceover-frontend/src/RoutePlanner.jsx), [routePlan](./voiceover-frontend/src/routePlan.js), [validateAndCalculateCustomRoute](./src/main/java/com/voiceover/backend/services/RouteSessionService.java#L26) | Lưu localStorage, không đồng bộ nhiều thiết bị; kéo thả/offline đã được tài liệu để sau. |
| Us-08 / UC04 | Mỗi gợi ý có số điểm, tổng đường/thời gian, thứ tự | Một phần | [getSuggestedRoutes](./src/main/java/com/voiceover/backend/services/RouteSessionService.java#L76), [RoutePlanner](./voiceover-frontend/src/RoutePlanner.jsx) | API danh sách không có tổng đường/thời gian; UI chỉ tính preview Route đang chọn, chưa hiển thị đủ cho từng gợi ý trước chọn. |
| Us-09 | Du khách không đăng nhập | Đầy đủ | [main.jsx](./voiceover-frontend/src/main.jsx), các endpoint /api/public trong [VoiceMapController](./src/main/java/com/voiceover/backend/controllers/VoiceMapController.java#L37) | Mobile native chưa có trong repo. |
| Us-10 / FR-5 / UC06 | Tạo/sửa, tọa độ/map, bán kính, mô tả/ngôn ngữ gốc, ảnh, ẩn/xóa | Một phần | [PoiManagement](./voiceover-frontend/src/AdminPortal.jsx#L132); [saveOrUpdatePoi/validatePoi](./src/main/java/com/voiceover/backend/services/PoiService.java#L157); [PoiThumbnailService](./src/main/java/com/voiceover/backend/services/PoiThumbnailService.java) | HIDDEN lệch CHECK DB; xóa vướng translation/audio; proxy ảnh thiếu; public catalog refresh 30 s không phải “ngay”. |
| F-05 / UC06 | Xác nhận xóa, bảo vệ Route đang phục vụ | Một phần | [remove](./voiceover-frontend/src/AdminPortal.jsx#L211), [deletePoiSafely](./src/main/java/com/voiceover/backend/services/PoiService.java#L215), [RouteStopRepository](./src/main/java/com/voiceover/backend/repositories/RouteStopRepository.java) | Có confirm UI và lỗi nêu Route; AND Route+Session theo điều chỉnh B05, khác chữ “hoặc” PRD. Xóa dữ liệu con chưa xử lý. |
| FR-4 / F-04 | DB lưu metadata/text, file audio lưu riêng | Một phần | [migration](./src/main/resources/db/migration/), [AudioGenerationService](./src/main/java/com/voiceover/backend/services/AudioGenerationService.java#L59) | Có cấu trúc và code ghi file thật; schema trạng thái lệch; chưa chứng minh fresh migration/storage bền vững. |
| Us-11 / FR-3 / UC08 | Lưu nguồn tự chạy pipeline và task từng ngôn ngữ | Một phần | [afterCommit](./src/main/java/com/voiceover/backend/services/PoiService.java#L172), [triggerFullPipelineForPoi](./src/main/java/com/voiceover/backend/services/PipelineService.java#L57), [reserveTranslation](./src/main/java/com/voiceover/backend/services/TranslationReviewService.java#L108) | Task được tạo dần ở PROCESSING, không tạo đủ PENDING lúc lưu; từng ngôn ngữ trong một POI chạy tuần tự. |
| F-03 / UC08 | Dịch đúng ngôn ngữ nguồn, cùng ngôn ngữ giữ nguyên | Một phần | [translateRealApiWithProtectedTerms](./src/main/java/com/voiceover/backend/services/PipelineService.java#L265) | Gọi provider dùng đúng sourceLanguageCode; hash chỉ chứa văn bản nên đổi mã nguồn mà giữ text có thể bị bỏ qua. |
| F-03 / UC08 | Áp dụng protected_terms | Một phần | [ProtectedTermRepository](./src/main/java/com/voiceover/backend/repositories/ProtectedTermRepository.java), [mask/restore token](./src/main/java/com/voiceover/backend/services/PipelineService.java#L272) | Đã nối DB → token → provider → restore. Thiếu quản lý terms qua UI/API, xử lý overlap/collision và xác minh token được bảo toàn; test pipeline đang dùng danh sách terms rỗng. |
| F-03 / UC08 | Không đổi nguồn thì không dịch lại | Một phần | [reserveTranslation](./src/main/java/com/voiceover/backend/services/TranslationReviewService.java#L108), [translationReady](./src/main/java/com/voiceover/backend/services/PipelineService.java#L136) | Có reuse và TTS_ONLY khi file thiếu/hỏng; fingerprint không chứa source language hay phiên bản terms. |
| UC08-E1/E2 | Retry, lỗi riêng từng ngôn ngữ | Một phần | [ProviderRetry](./src/main/java/com/voiceover/backend/services/ProviderRetry.java), [translateWithRetry](./src/main/java/com/voiceover/backend/services/PipelineService.java#L232), [generateAudioWithRetry](./src/main/java/com/voiceover/backend/services/PipelineService.java#L194) | Có timeout/backoff, tối đa 3 lần gọi, phân loại transient; không có retry dịch riêng từ UI, recovery còn thiếu và lỗi chưa lên Dashboard đúng yêu cầu. |
| UC08-E4 | Ngôn ngữ không hỗ trợ TTS: chỉ văn bản | Đầy đủ về nhánh xử lý | [PipelineService](./src/main/java/com/voiceover/backend/services/PipelineService.java#L158), [ReviewAudioWorker](./src/main/java/com/voiceover/backend/services/ReviewAudioWorker.java), [NarrationPlayer](./voiceover-frontend/src/narrationPlayer.js) | Không tạo file, vẫn giữ chữ và không cố đọc khi browserTtsAllowed=false. Không được gộp nhánh thiếu cấu hình provider vào tiêu chí này. |
| UC08 / FR-3 | TTS tạo file, lưu đường dẫn/thời lượng, khách nghe file đó | Một phần | [AudioGenerationService.generate](./src/main/java/com/voiceover/backend/services/AudioGenerationService.java#L59), [AppConfig](./src/main/java/com/voiceover/backend/config/AppConfig.java#L32), [narrationApi](./voiceover-frontend/src/narrationApi.js) | Đã nối code và test WAV HTTP stub; provider thật, chất lượng/đủ nội dung, HTTP Range và file sau restart chưa xác minh. |
| Us-12 / UC09 | Nghe thử, sửa đúng một bản dịch, tạo lại audio | Đầy đủ về lõi UI/API/worker | [TranslationReview](./voiceover-frontend/src/TranslationReview.jsx), [TranslationReviewController](./src/main/java/com/voiceover/backend/controllers/TranslationReviewController.java), [submit](./src/main/java/com/voiceover/backend/services/TranslationReviewService.java#L76) | PUT/POST trả 202, giữ bản dịch khi TTS lỗi, regenerate dùng text đã lưu; nghiệm thu ghi MySQL/provider thật còn thiếu. |
| UC08 + UC09 | Ngăn tác vụ cũ ghi kết quả cho nội dung mới | Một phần | [UC09.complete](./src/main/java/com/voiceover/backend/services/TranslationReviewService.java#L153) so revision/latest; [UC08](./src/main/java/com/voiceover/backend/services/PipelineService.java#L144) ghi trực tiếp | UC09 có bảo vệ; UC08 thiếu kiểm phiên bản lúc ghi translation/audio và có thể bỏ mất lần xử lý nguồn mới. |
| Us-13 / FR-5 / UC10 | Session nhiều Route, thứ tự, draft/publish/tạm đóng | Đầy đủ trong phạm vi B01–B10 đã ghi nhận | [SessionRoutes](./voiceover-frontend/src/SessionRoutes.jsx), [SessionRoutesController](./src/main/java/com/voiceover/backend/controllers/SessionRoutesController.java), [RouteSessionService](./src/main/java/com/voiceover/backend/services/RouteSessionService.java#L155) | Có lock/transaction, draft dở, publish 2–10 điểm, lọc cả hai cấp; xác thực Admin vẫn thiếu ở UC05. Lịch sử DB/restart trong tài liệu, chưa chạy lại lượt này. |
| UC05 / FR-5 | Xác thực email/password và trạng thái tài khoản | Một phần | [adminLogin](./src/main/java/com/voiceover/backend/controllers/VoiceMapController.java#L88), BCrypt ở [AppConfig](./src/main/java/com/voiceover/backend/config/AppConfig.java#L51) | Đã kiểm DB/mật khẩu băm, nhưng chỉ trả profile. |
| UC05 / F-05/F-06 | Phiên đăng nhập, bảo vệ Admin/Dashboard, logout server | Chưa triển khai | [AdminPortal](./voiceover-frontend/src/AdminPortal.jsx#L294) chỉ dùng localStorage; [pom.xml](./pom.xml) chỉ có security-crypto; controller không kiểm phiên | Cần session/token, authorization tại server, expiry/revocation và logout. Không coi localStorage là xác thực. |
| Us-16 / FR-7 / UC07 | Gợi ý AI từ tên/ghi chú, ≤15 s, không tự lưu nháp | Một phần | [AiDescriptionService](./src/main/java/com/voiceover/backend/services/AiDescriptionService.java#L47), [UI AI](./voiceover-frontend/src/AdminPortal.jsx#L225), [V13](./src/main/resources/db/migration/V13__ai_description_drafts.sql) | Có Gemini thật; timeout mặc định 30 s, cần POI đã lưu, tự persist draft; chưa có bằng chứng sinh thành công. |
| Us-14 / FR-6 / UC11 | Health app/DB/pipeline, task counts, lỗi/latency | Chưa triển khai phần monitoring nghiệp vụ | [getMonitoringDashboard](./src/main/java/com/voiceover/backend/controllers/VoiceMapController.java#L151) chỉ trả số đếm; [header tĩnh](./voiceover-frontend/src/AdminPortal.jsx#L315) | Thiếu health thật, error rate, latency, task counts, recent errors, ba màu theo dữ liệu. |
| Us-15 / UC11 | Active users, lượt nghe POI/ngôn ngữ, auto/tap, Route phổ biến | Một phần | [UsageEventRepository](./src/main/java/com/voiceover/backend/repositories/UsageEventRepository.java), [PlaybackEventController](./src/main/java/com/voiceover/backend/controllers/PlaybackEventController.java) | Có lượt nghe theo POI, distinct client mọi thời gian. Thiếu active window/heartbeat, group ngôn ngữ, tỷ lệ, Route/App events và thống kê Route. |
| F-06 / UC11 | Log/metric tách kho nghiệp vụ | Chưa triển khai | [V3](./src/main/resources/db/migration/V3__monitoring_and_terms.sql), [application.properties](./src/main/resources/application.properties) | Các bảng cùng DB; chưa có Monitoring Server/store tách biệt; system_metric mới là schema. |
| Mục 2.4 | Audio/AI/Dashboard SLA, GPS ≤5 m, uptime ≥99,5% | Chưa đủ bằng chứng | Có timeout/polling/test, không có báo cáo đo nghiệm thu hiện tại | Poll 10 s chưa bảo đảm dữ liệu tới màn ≤10 s; cần đo từ sự kiện tới hiển thị, mạng/thiết bị và uptime thực tế. |
| Mục 1.5/2.4 | Docker, CI/CD, kiến trúc mở rộng | Một phần | [Dockerfile](./Dockerfile), [maven.yml](./.github/workflows/maven.yml) | Có build/test/publish image; thiếu deploy/smoke/storage/front CI; chưa có chứng cứ khả dụng/multi-instance. |

## C. Luồng tạo/cập nhật POI hiện tại

| Bước | Thực tế code | Đánh giá |
|---|---|---|
| 1. Admin nhập POI | UI có tên, map/tọa độ, radius, source language từ API, trạng thái, mô tả và upload PNG/JPEG; [PoiManagement](./voiceover-frontend/src/AdminPortal.jsx#L132) | Có hoạt động ở mức code. Quyền Admin mới chặn UI. |
| 2. Lưu mô tả gốc | POST /api/admin/pois → [saveOrUpdatePoi](./src/main/java/com/voiceover/backend/services/PoiService.java#L157); lock POI cũ, validate, strip, save | Có transaction. Chưa có revision cho edit POI giữa hai Admin; request cũ có thể ghi đè nguồn mới. |
| 3. Ảnh | Sau lưu POI, UI POST multipart tới /pois/{id}/thumbnail; [store](./src/main/java/com/voiceover/backend/services/PoiThumbnailService.java#L29) ghi file | Tách hai request: ảnh lỗi thì POI/pipeline có thể đã lưu/chạy. Proxy frontend chưa có /images. |
| 4. Khởi phát pipeline | Callback sau commit gọi @Async; [PoiService](./src/main/java/com/voiceover/backend/services/PoiService.java#L172) | Nối đúng sau commit, nhưng chưa ghi job/outbox cùng transaction nguồn; crash hoặc queue đầy có thể lưu POI mà mất yêu cầu xử lý. |
| 5. Tạo task | [triggerFullPipelineForPoi](./src/main/java/com/voiceover/backend/services/PipelineService.java#L57) lấy ngôn ngữ active và terms, lặp; reserve task từng ngôn ngữ | Chỉ một task ngôn ngữ hiện tại được tạo rồi xử lý; các ngôn ngữ sau có thể vẫn MISSING, không hiện đủ PENDING. Chạy tuần tự trong một POI. |
| 6. Dịch/lưu | Mask terms → Google dịch → restore → save translation COMPLETED; nguồn=đích trả nguyên văn | Có provider thật và lưu text. Fingerprint/ghi kết quả chưa an toàn khi đổi nguồn giữa chừng. |
| 7. TTS | Unsupported → TEXT_ONLY; có provider → generate WAV; thiếu provider → TEXT_ONLY + task COMPLETED | Nhánh có provider đã nối. **Nhánh thiếu provider che mất việc chưa tạo file**, khác lỗi TTS trong UC09. |
| 8. Lưu file/URL | [generate](./src/main/java/com/voiceover/backend/services/AudioGenerationService.java#L59) trả URL UUID/thời lượng sau tạo file; pipeline save audio | Có kiểm WAV và tên mới. Ghi file và DB không nguyên tử; thất bại/superseded có thể để file mồ côi; chưa có cleanup. |
| 9. Theo dõi | GET /admin/pois/{id}/translations trả task/text/audio/provider; [TranslationReview](./voiceover-frontend/src/TranslationReview.jsx#L88) poll 3 s | Có trạng thái từng ngôn ngữ tại UC09. Form POI chỉ báo đã kích hoạt; Dashboard chưa tổng hợp task/lỗi. |
| 10. Kiểm duyệt/sửa | PUT /translations/{code} hoặc POST /translations/{code}/audio, revision, 202, event after commit, worker TTS | Có trọn đường gọi cho từng ngôn ngữ. Không có approval state, cũng chưa thấy PRD bắt buộc thêm state này. |
| 11. Xuất bản | Chọn PUBLISHED ngay trong form → cùng API save POI | Không đợi pipeline/review/audio mặc định. Public catalog có thể thấy điểm trước khi có narration đầy đủ. |
| 12. Khách nghe | GET /public/pois → GET /pois/{id}/guide → [NarrationPlayer](./voiceover-frontend/src/narrationPlayer.js) | File COMPLETED có URL thì phát; không có file thì SpeechSynthesis nếu cho phép. Thiếu fallback audio và kiểm độ mới, nên chưa bảo đảm nghe đúng bản mới nhất. |

### Các nhánh thay đổi và lỗi

- **Nguồn không đổi:** có reuse translation và chỉ tạo TTS_ONLY nếu audio chưa dùng được; kiểm file thật nằm trong storage ở reserveTranslation. Đây là phần đã có, không cần viết lại toàn bộ.
- **Đổi mô tả gốc:** hash khác thì bản dịch tự động được xử lý lại. Nhưng bản manualEdit COMPLETED khác hash bị fail task và giữ nguyên, khác quy tắc PRD “toàn bộ ngôn ngữ được tạo lại”. Guide vẫn có thể trả bản manual cũ vì không so sourceHash.
- **Đổi riêng sourceLanguageCode:** vẫn kích hoạt pipeline, nhưng hash văn bản không đổi có thể khiến bỏ qua bản dịch/audio đã có.
- **Sửa riêng bản dịch:** UC09 lưu đúng ngôn ngữ, manualEdit=true, đặt audio PENDING và bỏ tham chiếu file cũ; phát task TTS_ONLY sau commit. Ngôn ngữ khác không bị cập nhật.
- **Chỉ tạo lại audio:** dùng nội dung translation đã lưu và revision client, không nhận nội dung nháp từ request regenerate. TTS lỗi giữ translation, audio/task FAILED để yêu cầu lại.
- **Retry:** lỗi mạng/IO, timeout, 408/429/5xx được retry có backoff; 4xx cấu hình/dữ liệu không retry vô ích. Code tối đa **3 lần gọi tổng cộng**. Chưa có retry translation riêng từ màn Admin; thường phải lưu lại POI.
- **Restart:** ReviewAudioListener đánh dấu TTS_ONLY dở là FAILED; TRANSLATE_TTS PROCESSING được requeue. Chưa phục hồi toàn bộ ý định xử lý một POI, chưa quản lý ownership đa instance.
- **Text-only:** với tts_supported=false là nhánh hợp lệ. Với tts_supported=true nhưng thiếu key/model, UC08 vẫn báo COMPLETED/TEXT_ONLY, còn UC09 báo FAILED; cùng một nguyên nhân nhưng trạng thái khác nhau.

## D. Các sai lệch và điểm cần làm rõ

### D1. Lỗi/tồn tại xác định được từ code

**1. Kết quả UC08 cũ có thể trở thành kết quả đang phục vụ.**

Ví dụ: task tiếng Anh đang dịch nguồn A; Admin lưu nguồn B. [assertSourceEditable](./src/main/java/com/voiceover/backend/services/TranslationReviewService.java#L135) chỉ chặn TTS_ONLY nên cho phép đổi khi TRANSLATE_TTS chạy. Lần trigger B thấy task tiếng Anh còn active nên [reserveTranslation](./src/main/java/com/voiceover/backend/services/TranslationReviewService.java#L108) trả null. Worker A sau đó lưu translation/audio trực tiếp, không so nguồn hiện tại hoặc latest task trước commit. Không có cơ chế tự xếp lại tiếng Anh cho B. Kết quả có thể trộn các ngôn ngữ từ A/B.

UC09 có so revision/latest khi complete; **không được suy rộng cơ chế đó sang toàn bộ UC08**. Ngoài ra POI source chưa có optimistic revision để ngăn hai Admin ghi đè nhau.

**2. Hash nguồn thiếu ngữ cảnh và bảo toàn manualEdit chưa khớp PRD.**

[PipelineService](./src/main/java/com/voiceover/backend/services/PipelineService.java#L63) chỉ hash descriptionSource; đổi source language/terms không invalidate kết quả. [Nhánh manualEdit](./src/main/java/com/voiceover/backend/services/PipelineService.java#L131) giữ bản cũ và fail khi nguồn đổi, trong khi F-03 yêu cầu tạo lại toàn bộ. Cần chọn rõ chính sách bản chỉnh tay và đánh dấu stale, không để bản cũ vẫn trông như nội dung hiện hành.

**3. API đọc nearby sửa nguồn do Admin quản lý.**

[getNearbyPublishedPois](./src/main/java/com/voiceover/backend/services/PoiService.java#L31) không so distance với maxRadiusMeters. Nó gọi [refreshFallbackNarration](./src/main/java/com/voiceover/backend/services/PoiService.java#L98), ghi đè mô tả của bốn tên POI bằng hằng số, sửa ảnh và kích hoạt pipeline. Khi ít điểm, nó có thể insert POI mẫu PUBLISHED. Hàm fallback bỏ qua lat/lng/radius đầu vào. Nếu tìm lại POI mẫu đã tồn tại ở DRAFT/HIDDEN, nhánh bổ sung không kiểm lại published trước khi thêm vào kết quả. UI mới dùng catalog khác nên lỗi có thể bị che trong demo.

**4. Fallback guide kiểm thiếu bản dịch thay vì thiếu audio.**

[getPoiAudioGuide](./src/main/java/com/voiceover/backend/services/PoiService.java#L115) giữ ngôn ngữ yêu cầu khi translation đã COMPLETED, dù audio FAILED/MISSING/TEXT_ONLY; không thử audio ngôn ngữ mặc định. Không kiểm sourceHash hiện tại hoặc file còn tồn tại trong lúc phục vụ. Nếu cả bản dịch mặc định cũng thiếu và POI không có built-in, code lấy descriptionSource nhưng activeLanguage vẫn có thể là ngôn ngữ mặc định khác ngôn ngữ nguồn. Có nguy cơ đọc sai giọng/gắn sai nhãn.

**5. Thiếu provider được báo thành công; task không phải hàng đợi bền vững.**

[UC08 provider UNAVAILABLE](./src/main/java/com/voiceover/backend/services/PipelineService.java#L174) xóa URL, đặt TEXT_ONLY, task COMPLETED và không lưu lỗi. [UC09 worker](./src/main/java/com/voiceover/backend/services/ReviewAudioWorker.java) lại fail. Ngoài ra callback async không có outbox; task ngôn ngữ chưa được reserve sẽ mất khi crash, chỉ task hiện tại có dấu vết recovery. Queue trong RAM không bảo đảm tiếp nhận bền vững. Recovery chưa có lease/owner, một instance khởi động có thể xử lý task của instance khác.

**6. Schema có lỗi chức năng mà unit test mock không bắt được.**

- [V1](./src/main/resources/db/migration/V1__core_schema.sql#L48) chỉ cho POI Draft/Published/Archived; service/UI dùng DRAFT/PUBLISHED/HIDDEN. Không có migration sau đó đổi CHECK này. Với schema theo migration, HIDDEN bị từ chối; không chỉ là khác chữ hoa/thường.
- [deletePoiSafely](./src/main/java/com/voiceover/backend/services/PoiService.java#L215) chỉ delete POI, không xóa audio/translation. FK translation→POI, audio→translation/POI không cascade trong [V1](./src/main/resources/db/migration/V1__core_schema.sql#L67) và [V5](./src/main/resources/db/migration/V5__audio_lookup_columns.sql). Vì vậy POI có kết quả pipeline có thể không xóa được dù không thuộc Route.
- [application.properties](./src/main/resources/application.properties#L11) dùng ddl-auto=update cùng Flyway; chưa có test DB sạch/nâng cấp để chứng minh schema tái lập. Hibernate update không phải bằng chứng các CHECK/FK đã được chỉnh đúng nghiệp vụ.

**7. Kiểm duyệt không phải bước phê duyệt xuất bản.**

[saveOrUpdatePoi](./src/main/java/com/voiceover/backend/services/PoiService.java#L157) validate dữ liệu nhưng không kiểm audio mặc định, hash kết quả hoặc trạng thái review. [poiCoverage](./src/main/java/com/voiceover/backend/services/RouteSessionService.java#L203) chỉ đọc metadata; không dùng làm gate xuất bản. Đây là hiện trạng cần biết, nhưng PRD chưa quy định rõ bắt buộc audio mặc định hay cờ approved. B08 trong tài liệu Session/Routes còn ghi thiếu audio không ngăn xuất bản.

**8. Admin có kiểm mật khẩu nhưng chưa được bảo vệ bởi server.**

[adminLogin](./src/main/java/com/voiceover/backend/controllers/VoiceMapController.java#L88) không tạo session/token. [AdminPortal](./voiceover-frontend/src/AdminPortal.jsx#L294) đọc profile localStorage; logout chỉ xóa profile. Không thấy filter/security chain/interceptor kiểm tài khoản cho các endpoint Admin. Có thể gọi API quản trị mà không qua login. Tài liệu ghi phần này từng được hoãn; đó không phải bằng chứng đã đáp ứng UC05.

**9. UC07 đã gọi AI thật nhưng chưa khớp trải nghiệm PRD.**

[generate](./src/main/java/com/voiceover/backend/services/AiDescriptionService.java#L47) đòi POI đã tồn tại và UI chỉ bật nút sau lưu. Trong khi PRD cho nhập tên ở màn tạo mới để nhờ AI soạn mô tả, form hiện lại bắt có mô tả để lưu POI trước. Nháp được save tự động vào DB; rời màn không hủy nháp như UC07-E2. Timeout mặc định 30 s thay vì 15 s. Prompt luôn tiếng Việt nhưng accept không chỉnh sourceLanguageCode. Có source_snapshot nhưng accept không kiểm snapshot còn khớp POI, nên nháp từ nguồn cũ vẫn có thể áp dụng lên nguồn mới. Cần nghiệm thu thêm sự phối hợp với lock khi UC09 đang tạo audio.

**10. Danh mục ngôn ngữ và storage chưa đồng nhất toàn tuyến.**

[V4](./src/main/resources/db/migration/V4__seed_languages.sql) có 16 active, mặc định vi, 14 cờ TTS; ms/hi chỉ chữ. [UI khách](./voiceover-frontend/src/VoiceMapScreens.jsx#L225) viết cứng 17 mã, có zh-TW không được seed; ngôn ngữ inactive vẫn có thể xuất hiện. Thêm DB language chưa đủ đáp ứng UI và mapping voice/provider. [vite.config](./voiceover-frontend/vite.config.js) proxy /api, /audio, /storage nhưng thiếu /images, trong khi UI dùng URL ảnh tương đối. Chưa có manifest/volume storage hay dọn file cũ/mồ côi trong Docker flow.

**11. Dashboard chưa phải monitoring PRD.**

[getMonitoringDashboard](./src/main/java/com/voiceover/backend/controllers/VoiceMapController.java#L151) chỉ trả poiCount, activePoiCount, userCount, poiUsage. [countDistinctUsers](./src/main/java/com/voiceover/backend/repositories/UsageEventRepository.java#L9) không có cửa sổ thời gian. APP_OPEN/ROUTE_SELECTED có trong schema nhưng không có producer đang nối; POST playback chỉ nhận AUTO_PLAY/TAP_POI/AUDIO_COMPLETED. Header System operational là chuỗi tĩnh. Lỗi UC08 có ghi system_log, lỗi UC09 chỉ thể hiện task, nhưng Dashboard không đọc các lỗi đó.

Chống trùng playback hiện là cache RAM 30 phút/2.048 khóa; playbackId không được lưu thành khóa unique DB. Restart/đa instance có thể đếm lại; gửi telemetry lỗi không có hàng đợi retry bền vững. Đây là giới hạn số liệu, không phải lý do để dừng phát thuyết minh.

### D2. Code dựng sẵn, TODO và dịch vụ chưa kiểm chứng

| Vị trí | Bản chất | Ảnh hưởng kết luận |
|---|---|---|
| [OpenStreetMapPoiService](./src/main/java/com/voiceover/backend/services/OpenStreetMapPoiService.java#L128) | Nội dung/thuyết minh địa danh được viết sẵn; nhiều ngôn ngữ dùng câu giới thiệu khuôn mẫu | Nghe được POI mẫu không chứng minh mô tả do Admin nhập đã được dịch. |
| [createSaigonUniversityFallback](./src/main/java/com/voiceover/backend/services/OpenStreetMapPoiService.java#L329) | Trả bốn POI cố định, bỏ qua vị trí/bán kính | Không phải khám phá POI lân cận thật. findNearbyPois còn return fallback trước các nhánh Overpass/Photon vì danh sách này luôn không rỗng; đường gọi nearby hiện dùng thẳng fallback. |
| [RealContentService](./src/main/java/com/voiceover/backend/services/RealContentService.java#L34) | Có OpenAI Images thật khi có key; thiếu key sinh SVG placeholder | Endpoint tên with-real-image không bảo đảm có ảnh thật; service này không thay UC07 sinh mô tả. |
| [AdminPortal](./voiceover-frontend/src/AdminPortal.jsx#L315), [HomeController](./src/main/java/com/voiceover/backend/controllers/HomeController.java) | Nhãn operational/UP tĩnh | Không phải health DB/pipeline. |
| [V3 system_metric](./src/main/resources/db/migration/V3__monitoring_and_terms.sql#L35) | Mới có schema, chưa nối collector/API/UI | Không đánh dấu monitoring hoàn thành. |
| [App mẫu](./voiceover-frontend/src/App.jsx), [VoiceMapDemoApp](./voiceover-frontend/src/VoiceMapDemoApp.jsx) | Không mount từ main.jsx | Không lấy tính năng/mock trong đây làm bằng chứng ứng dụng hiện hành. |
| [MapArtwork](./voiceover-frontend/src/VoiceMapScreens.jsx#L166) | Còn nhánh driving/đường thẳng cũ nếu routeGeometry undefined | RoutePlanner hiện truyền geometry hoặc null nên nhánh này không chứng minh UC03/UC04 đang dùng driving. Nên dọn để tránh tái sử dụng sai. |

Không thấy TODO/FIXME hay hàm nghiệp vụ rỗng nổi bật trong mã production đã rà soát; các khoảng trống chính nằm ở nhánh fallback, xử lý chưa có, hoặc khung schema chưa được sử dụng. Google Translation/TTS, Piper, Gemini, Directions đều có đường gọi thật trong code; **thiếu cấu hình/chưa nghiệm thu provider không đồng nghĩa chưa tích hợp code**.

### D3. Mâu thuẫn hoặc điểm chưa rõ của yêu cầu/tài liệu

| Vấn đề | Nguồn | Cần chốt |
|---|---|---|
| Dịch lúc khách chạm hay sinh trước? | Tổng quan 1.1 mô tả chạm POI thì dịch/TTS; F-03/UC08 yêu cầu chạy lúc Admin lưu | Lấy pipeline sinh trước làm luồng chính để đạt SLA; GET guide không tạo tác vụ. |
| “Lưu là hiển thị ngay” và POI nháp | Us-10 so với F-05/UC06 | Phân biệt lưu nháp, xuất bản, và thời gian cập nhật catalog khách. |
| Kiểm duyệt → xuất bản; audio mặc định | UC09 chỉ yêu cầu xem/sửa/tạo lại; chưa có approved flag hoặc điều kiện audio; Session/Routes B08 không chặn thiếu audio | Có cần gate mặc định có audio thật không? Nếu text-only hợp lệ thì điều kiện nào? Nếu cần đây là quyết định bổ sung, không gán thành nguyên văn PRD. |
| Nguồn đổi và bản dịch sửa tay | F-03 yêu cầu tạo lại toàn bộ; code/tài liệu bàn giao muốn bảo toàn manual | Chọn ghi đè có xác nhận, giữ bản sửa với trạng thái stale, hoặc tạo version mới chờ đối chiếu. |
| Retry tối đa 3 lần | PRD không rõ 3 lần tổng hay 1 lần đầu + 3 retry | Code hiện 3 lần gọi tổng; cần thống nhất cách hiển thị retryCount. |
| >15 ngôn ngữ và text-only | FR-3/F-03 so với UC08-E4; V4 có 16 tổng, gồm nguồn, chỉ 14 cờ TTS | Đếm tổng phiên bản hay 16 ngôn ngữ đích? Có bắt buộc audio cho mọi ngôn ngữ không hỗ trợ TTS không? Không coi 16 bản ghi là 16 file. |
| Fallback và text-only | Us-04 yêu cầu thiếu audio thì phát mặc định; UC02-E3 cho chỉ hiển thị chữ | Đặt thứ tự ưu tiên rõ giữa file chọn, file mặc định, chỉ chữ và browser speech. |
| Xóa POI: Route hoặc Session published | F-05 dùng “hoặc”; Session/Routes B05 đã đổi sang cả Route và Session | Ghi rõ đây là điều chỉnh được tài liệu ghi nhận. Draft reference còn bị chặn xóa để bảo toàn FK. |
| Route 2–10 so với lưu draft dở | F-02 so với B02 và V12 | Đã có quyết định: chỉ kiểm 2–10 khi xuất bản; không báo draft 11 điểm là lỗi PRD hiện hành. |
| Monitoring real-time, active user | F-06/UC11 chưa định nghĩa window/threshold/error denominator | Chốt active window, heartbeat, error rate, latency window, thời điểm tính độ trễ ≤10 s. |
| AI nháp và ngôn ngữ | UC07-E2 cấm tự lưu DB; code V13 cố ý lưu lịch sử; PRD có source language nhưng không nêu AI chỉ tiếng Việt | Chốt cho phép lưu nháp lịch sử hay không; AI dùng ngôn ngữ nguồn nào; không mặc nhiên coi V13 là đáp ứng E2. |

Tài liệu hiện trạng cũng cần cập nhật: README còn checklist skeleton, V1–V4/V12, ddl-auto=validate và DB_* khác cấu hình thật. PRD_SOURCE_AUDIT còn nói UC07 là endpoint mẫu, trong khi code đã dùng Gemini và V13. HANDOFF có kết quả lịch sử lỗi model/build, nhưng khảo sát hiện tại đã build/test backend thành công. Không dùng các mô tả cũ đó để kết luận hiện trạng.

## E. Backlog theo ưu tiên

**P0** là việc chặn tính đúng/độ tin cậy của luồng nội dung cần hoàn thành trước khi tuyên bố end-to-end. **P1** hoàn thiện các UC bắt buộc và phát hành. **P2** tối ưu/mở rộng hoặc phạm vi đã được hoãn.

| ID | Ưu tiên / công việc | Lý do | Phụ thuộc | Tiêu chí hoàn thành |
|---|---|---|---|---|
| T01 | P0 — Phiên bản nguồn và commit kết quả có điều kiện cho UC08/UC09 | Tránh bản cũ/trộn ngôn ngữ khi đổi nguồn | Chốt manualEdit; fingerprint text + source language + terms version | Lưu A rồi B khi worker A đang chạy: A không cập nhật nội dung đang phục vụ; mọi ngôn ngữ cần xử lý B có task; hai Admin dùng revision cũ bị 409. |
| T02 | P0 — Ghi ý định xử lý bền vững cùng nguồn, dispatch/recovery thống nhất | Không mất job sau lưu/queue đầy/restart | T01; dùng DB job/outbox cũng được, chưa bắt buộc thêm broker | Ngay sau commit thấy đủ PENDING cho ngôn ngữ cần làm; crash trước dispatch vẫn được lấy lại; không trùng claim; có chính sách giới hạn/retry và superseded. |
| T03 | P0 — Thống nhất TTS unsupported/unavailable/failed, nghiệm thu provider thật | Không báo hoàn tất audio khi chưa tạo file | Provider hợp lệ và storage; T01 | Unsupported → TEXT_ONLY; thiếu cấu hình/lỗi provider thể hiện rõ; 1 POI mới không trùng tên built-in tạo được file vi/en, HTTP URL phát đủ đầu/giữa/cuối, duration đúng và còn sau restart. |
| T04 | P0 — Guide chọn nội dung/file đúng version, fallback audio | Khách có thể nghe bản cũ hoặc không được fallback | T01, T03; chốt thứ tự fallback | Translation có nhưng audio chọn lỗi vẫn trả file mặc định hợp lệ + thông báo; không gắn nhãn sai ngôn ngữ nguồn; file mất không trả URL thành công giả; GET không ghi dữ liệu. |
| T05 | P0 — Bỏ mutation/seed khỏi nearby, lọc radius | GET hiện có thể ghi đè nguồn Admin và lộ POI không public | Không phụ thuộc provider | Ca 499/501 m đúng; DRAFT/HIDDEN không lộ; GET không đổi mô tả/ảnh, không insert, không tạo pipeline. Tách seed/import thành thao tác rõ ràng. |
| T06 | P0 — Migration khớp trạng thái và vòng đời xóa | Ẩn/xóa POI có thể thất bại trên DB thật | DB test riêng; quyết định xóa dữ liệu/file | Migration mới không sửa lịch sử; DB sạch/nâng cấp cho phép HIDDEN; xóa POI có audio/translation khi không còn Route reference được, guard Route vẫn đúng; không mất dữ liệu ngoài phạm vi. |
| T07 | P1 — Quy tắc sẵn sàng xuất bản và manual review | Tránh hiểu published đồng nghĩa audio/review hoàn tất | Quyết định tại D3; T01/T04 | Có bảng điều kiện publish được duyệt; API thực thi đúng; nếu cần default audio thì kiểm file/version thật; nếu cho text-only thì UI nói rõ. Không tự thêm approval bắt buộc khi chưa chốt. |
| T08 | P1 — Quản lý protected_terms và catalog/voice config | Tên riêng và thêm ngôn ngữ chưa trọn luồng | T01 fingerprint; catalog chuẩn | CRUD terms, test overlap/token biến dạng/collision, bảo toàn thuật ngữ; UI khách đọc API active; thêm/tắt ngôn ngữ không sửa JSX; voice/provider hỗ trợ được kiểm. |
| T09 | P1 — UC11 monitoring và usage đủ yêu cầu | Thiếu hầu hết Us-14/15 | T02/T03 task/lỗi thật; định nghĩa metrics | Health DB/app/pipeline, pending/processing/failed, error/latency/recent logs; active window, POI/ngôn ngữ, auto/tap, Route phổ biến; số liệu kịch bản biết trước đúng và đo độ trễ ≤10 s. |
| T10 | P1 trước phát hành — Session xác thực Admin/API | Tiền điều kiện bắt buộc UC05/06/09/10/11 chưa có | Giữ scope đã hoãn; không chặn thử lõi nội dung trong môi trường riêng | API Admin từ chối request chưa đăng nhập; account/role/expiry được kiểm server; logout vô hiệu phiên; public vẫn dùng không cần tài khoản. |
| T11 | P1 — UC07 đúng tạo mới/nháp/ngôn ngữ/15 s | AI hiện không dùng được theo trải nghiệm tạo mới PRD | Chốt persistence nháp và provider hợp lệ | Nhập tên chưa lưu POI vẫn lấy nháp; timeout 15 s; bỏ/rời không đổi nguồn/task; accept kiểm revision và ngôn ngữ; có một lần gọi provider thật thành công; lỗi vẫn nhập tay. |
| T12 | P1 — Tích hợp UI/storage/deployment và nghiệm thu | Test mock không bắt proxy ảnh, FK, flyway, provider, GPS | T01–T06; config test/demo tái lập | Proxy/base URL ảnh đúng; DB sạch và nâng cấp qua; Docker có storage bền vững; CI kiểm backend/frontend và smoke có DB; seed dữ liệu riêng; đo audio/GPS/Route/SLA trên thiết bị thật. |
| T13 | P1 — Hoàn thiện gợi ý Route và UI lỗi GPS | Us-08 cần metrics từng Route; accuracy yếu chưa có cảnh báo rõ | Giữ lõi UC03/UC10; Directions | Mỗi gợi ý có stopCount/total/time trước chọn; GPS yếu có cảnh báo nhưng vẫn nghe tay; lỗi Directions không vẽ số liệu/đường giả. |
| T14 | P2 — Vận hành mở rộng và phần hoãn | Scale, chi phí và chất lượng bảo trì | Luồng nội dung ổn định | Lease đa instance, idempotency telemetry DB, cleanup file mồ côi/version cũ, cache có version, cấu hình ngưỡng/voice; theo scope mới làm offline, kéo thả, cảnh báo v1.1, email reset. |
| T15 | P2 — Tài liệu và dữ liệu bàn giao | Máy khác chưa tái hiện từ Git | Migration/seed/file của T12 | README không còn kết luận skeleton sai; thống nhất biến SPRING_DATASOURCE_*, .env, V13, WAV; bộ dữ liệu/ảnh/audio demo có thể khôi phục và không chứa secret. |

Thứ tự đề xuất: **T01 → T02 → T03 → T04**, xử lý **T05/T06** trong cùng đợt ổn định dữ liệu, rồi T07–T13 theo phụ thuộc. T10 phải xong trước đưa API Admin ra môi trường dùng chung; tài liệu từng hoãn phần quyền không làm mất yêu cầu này, nhưng cũng không cần dừng nghiệm thu lõi POI để xây lại toàn bộ auth ngay đầu tiên.

## F. Công việc cụ thể nên bắt đầu ngay

**Bắt đầu bằng một lát cắt UC08 có kiểm soát phiên bản, rồi nghiệm thu file TTS thật cho một POI hoàn toàn mới.** Không cần làm lại màn Session/Routes, UC03 hoặc bộ phát đã có.

1. **Chốt hợp đồng dữ liệu của một lần xử lý.** Mỗi source revision gắn text, sourceLanguageCode, phiên bản terms; mỗi task gắn POI/ngôn ngữ/revision. Chốt manualEdit khi đổi nguồn; publish gate là quyết định riêng. Điểm bắt đầu: [PoiService](./src/main/java/com/voiceover/backend/services/PoiService.java#L157), [TranslationReviewService](./src/main/java/com/voiceover/backend/services/TranslationReviewService.java#L108), [PipelineTask](./src/main/java/com/voiceover/backend/entities/PipelineTask.java).
2. **Sửa đường lập lịch/ghi kết quả trước khi mở rộng số ngôn ngữ.** Lưu source + đủ task PENDING trong một transaction; worker nhận task ID và snapshot; ghi translation/audio có điều kiện revision còn hiện hành. Nếu nguồn mới đến khi task cũ chạy, giữ yêu cầu mới để xử lý tiếp, không skip rồi quên. Bắt đầu tại [PipelineService](./src/main/java/com/voiceover/backend/services/PipelineService.java#L57) và [ReviewAudioListener](./src/main/java/com/voiceover/backend/services/ReviewAudioListener.java).
3. **Chạy tích hợp với DB kiểm thử riêng và provider thật cho vi/en.** Dùng POI tên mới để không đi qua built-in; nguồn dài đủ kiểm đầu/giữa/cuối, có một protected term. Chọn Google Cloud để có cả hai giọng; Piper hiện chỉ cấu hình một ngôn ngữ nên không tự chứng minh audio đa ngôn ngữ. Dùng lại [AudioGenerationService](./src/main/java/com/voiceover/backend/services/AudioGenerationService.java), không thay bằng SpeechSynthesis để đánh dấu file TTS hoàn tất.
4. **Nối nghiệm thu Admin → khách.** Quan sát task tại UC09; GET file trả audio hợp lệ; publish theo quy tắc đã chốt; GET guide trả đúng text/file/version; khách nghe file đó. Sửa riêng en → chỉ URL en đổi; sửa nguồn → các ngôn ngữ tự động có revision mới; file cũ không được ghi đè kết quả mới.
5. **Kiểm các ca hỏng bắt buộc.** Provider 503 rồi hồi phục, 4xx dừng sớm, một ngôn ngữ lỗi không cản ngôn ngữ khác, unsupported chỉ chữ, thiếu key không báo có file; lưu A/B nhanh; restart giữa task; file bị mất; fallback mặc định; GET không tạo/sửa POI. Thực hiện trên dữ liệu test, không chỉnh các POI/Route demo.

**Tiêu chí đóng lát cắt:** có bằng chứng POI mới → source DB → task từng ngôn ngữ → translation DB → WAV thật/URL → trạng thái đúng → nội dung Admin đã sửa → khách nghe đúng phiên bản → restart vẫn dùng được; đồng thời tác vụ A hoàn tất muộn không thể làm mất kết quả B. Sau đó mở rộng toàn bộ catalog và đo SLA, thay vì đánh dấu xong chỉ vì có 16 translation hoặc nghe được giọng trình duyệt.
