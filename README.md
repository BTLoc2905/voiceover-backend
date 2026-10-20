# VoiceMap — Backend

> Hệ thống ứng dụng **thuyết minh tự động đa ngôn ngữ** (Multilingual Auto-Tour Guide System).
> Backend phục vụ ứng dụng di động của du khách, Admin Portal và Monitoring Dashboard.

Đồ án môn **Công nghệ phần mềm** — Nhóm 2 — GVHD: Nguyễn Quốc Huy.

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F)
![MySQL](https://img.shields.io/badge/MySQL-8.4-4479A1)
![Docker](https://img.shields.io/badge/Docker-ready-2496ED)

> [!NOTE]
> **Trạng thái hiện tại:** repository đã có backend Spring Boot theo kiến trúc 3 lớp, schema cơ sở dữ liệu Flyway (V1–V6), API POI/lộ trình, pipeline đa ngôn ngữ và frontend demo.

---

## Mục lục

1. [Giới thiệu](#1-giới-thiệu)
​2. [Tính năng](#2-tính-năng)
3. [Kiến trúc hệ thống](#3-kiến-trúc-hệ-thống)
4. [Công nghệ sử dụng](#4-công-nghệ-sử-dụng)
5. [Cấu trúc thư mục](#5-cấu-trúc-thư-mục)
6. [Mô hình dữ liệu](#6-mô-hình-dữ-liệu)
7. [Bắt đầu nhanh](#7-bắt-đầu-nhanh)
8. [Cấu hình](#8-cấu-hình)
9. [Docker](#9-docker)
10. [CI/CD và triển khai](#10-cicd-và-triển-khai)
11. [Use case và quy tắc nghiệp vụ chính](#11-use-case-và-quy-tắc-nghiệp-vụ-chính)
12. [Tiến độ triển khai](#12-tiến-độ-triển-khai)
13. [Tài liệu liên quan](#13-tài-liệu-liên-quan)
14. [Nhóm thực hiện](#14-nhóm-thực-hiện)

---

## 1. Giới thiệu

VoiceMap được thiết kế theo mô hình **hai lớp người dùng**:

| Đối tượng | Giao diện | Mục tiêu |
|---|---|---|
| **Du khách** | Mobile App (React Native) | Định vị GPS, xem POI lân cận, nghe thuyết minh tự động bằng 15+ ngôn ngữ, đi theo lộ trình gợi ý. **Không cần đăng ký tài khoản.** |
| **Quản trị viên / Nhân viên** | Admin Portal (React.js) + Monitoring Dashboard | Quản lý POI, session, lộ trình; theo dõi pipeline dịch/TTS; giám sát hệ thống và số liệu sử dụng gần thời gian thực. |

**Giá trị cốt lõi:** khi nhân viên lưu mô tả gốc của một POI, hệ thống tự động **dịch sang hơn 15 ngôn ngữ** và **tổng hợp giọng nói (TTS)** cho từng ngôn ngữ; du khách chỉ cần chạm vào POI hoặc đi vào vùng kích hoạt là nghe được thuyết minh ngay tại chỗ.

Repository này là **backend** của hệ thống (REST API, pipeline dịch & TTS, lưu trữ dữ liệu, giám sát).

---

## 2. Tính năng

Theo PRD v1.0 (20/09/2026):

| Mã | Tính năng | Mô tả ngắn |
|---|---|---|
| F-01 / FR-1 | Định vị GPS & POI lân cận | Trả về POI đã xuất bản trong bán kính 500 m kèm khoảng cách; hỗ trợ tự phát audio khi vào vùng kích hoạt của POI. |
| F-02 / FR-2 | Lộ trình & chỉ đường | Lộ trình gợi ý (2–10 POI) thuộc một Session; người dùng thêm/bớt/sắp xếp lại; đường đi bộ qua Directions API. |
| F-03 / FR-3 | Pipeline dịch & TTS | Tác vụ nền theo từng ngôn ngữ: áp dụng `protected_term` → dịch → TTS → lưu audio; tự thử lại tối đa 3 lần. |
| F-04 / FR-4 | CSDL POI tích hợp | Lưu toạ độ, bán kính, thumbnail, mô tả, bản dịch; file audio lưu ở kho file, CSDL chỉ giữ đường dẫn. |
| F-05 / FR-5, FR-7 | Admin Portal | Đăng nhập; CRUD POI; xem trạng thái pipeline, nghe thử/sửa bản dịch; quản lý Session/Route; **AI gợi ý mô tả** (chỉ là bản nháp). |
| F-06 / FR-6 | Monitoring Dashboard | Trạng thái app/DB/pipeline (xanh–vàng–đỏ), tỷ lệ lỗi, độ trễ API, người dùng đang hoạt động, lượt nghe theo POI/ngôn ngữ, lộ trình phổ biến. |

---

## 3. Kiến trúc hệ thống

Backend theo **kiến trúc 3 lớp** (Controller → Service → Repository), đóng gói bằng Docker.

![Component diagram](docs/images/component-diagram.png)

### Các thành phần

| Thành phần | Vai trò |
|---|---|
| **Client** (Mobile App) | App Controller, GUI Map, GPS Tracker, Audio Player, Itinerary View, Language Selector. Gọi **REST API**. |
| **Admin Portal** | POI Content Editor (có AI gợi ý), Session Manager, Route Manager, Translation & Audio Review, Login. Gọi **Admin API**. |
| **Dashboard** | Usage Panel, Status Panel, Error List, Login. Gọi **Monitoring API**. |
| **SERVER** | Public API Controller, Admin API Controller, AuthService, Narration Service, Itinerary Service, Admin/Content Service và các Repository (POI, Translation/Audio, Itinerary/Session, Account). |
| **Translation & TTS Pipeline Worker** | Xử lý nền việc dịch và tạo audio; ghi bản dịch/audio về Repository và báo trạng thái/lỗi cho Monitoring. |
| **Monitoring Server** | Monitoring API Controller, Monitoring Service, Usage Event / System Log Repository; xác thực Admin thông qua AuthService. |
| **Database** | Relational Database (metadata), Audio File Storage (file `.mp3`), Log & Metrics Store. |
| **External Services** | AI Content API, Translation API, TTS API, Directions API. |

### Luồng pipeline dịch và tạo audio (F-03 / UC08)

```mermaid
flowchart TD
    A[Admin lưu mô tả gốc của POI] --> B{Mô tả hợp lệ<br/>và có thay đổi?}
    B -- Không hợp lệ --> X[Báo lỗi cho Admin<br/>không chạy pipeline]
    B -- Không đổi --> Y[Bỏ qua, không dịch lại]
    B -- Có --> C[Đọc danh sách protected_term]
    C --> D[Tạo pipeline_task cho từng ngôn ngữ<br/>status = PENDING]
    D --> E[Worker xử lý nền<br/>status = PROCESSING]
    E --> F[Translation API dịch<br/>giữ nguyên protected_term]
    F --> G[(Lưu translation)]
    G --> H{Ngôn ngữ hỗ trợ TTS?}
    H -- Không --> I[Chỉ có bản văn bản<br/>COMPLETED, không có audio]
    H -- Có --> J[TTS API tạo audio]
    J --> K[(Lưu file vào Audio Storage<br/>lưu file_path vào audio)]
    K --> L[COMPLETED]
    F -. lỗi, thử lại tối đa 3 lần .-> Z[FAILED + ghi system_log<br/>hiện trên Dashboard]
    J -. lỗi, thử lại tối đa 3 lần .-> Z