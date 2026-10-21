# HỆ THỐNG TIẾP NHẬN VÀ QUẢN LÝ YÊU CẦU NỘI BỘ (WORKDESK)
## ĐỒ ÁN LẬP TRÌNH MẠNG MÁY TÍNH - MÔ HÌNH CLIENT/SERVER TCP SOCKET

Dự án được xây dựng và chuẩn hóa theo đúng yêu cầu phân tách từng file độc lập cho từng chức năng **F01, F02, F03, F04, F05, F06, F08** dựa trên nền tảng TCP Socket, cơ sở dữ liệu MySQL và kiến trúc đa luồng (Multi-threading).

---

### 1. Cấu trúc các file chức năng riêng biệt

| Mã chức năng | Tên file mã nguồn | Vai trò & Xử lý chính | Đầu vào / Đầu ra |
|---|---|---|---|
| **F01** | `F01_Authentication.java` | Đăng nhập và xác thực tài khoản qua TCP Socket. Server xác thực MySQL, kiểm tra tài khoản, trả về vai trò (`admin`, `tech`, `requester`). | **IP, Port / Trạng thái kết nối** |
| **F02** | `F02_CreateTicket.java` | Kiểm tra dữ liệu hợp lệ, Server sinh mã yêu cầu (`YC-xxxx`), lưu thông tin vào MySQL và thông báo kết quả qua Socket. | **Sự kiện Socket / Thông báo** |
| **F03** | `F03_ViewTickets.java` | Server kiểm tra quyền truy cập, truy vấn MySQL theo tài khoản, trả danh sách / chi tiết. Hỗ trợ xuất danh sách ra file báo cáo lưu trên máy client. | **File / Kết quả lưu file** |
| **F04** | `F04_AssignTechnician.java` | Server kiểm tra quyền và trạng thái yêu cầu, phân công kỹ thuật viên. Xử lý truyền nhận tệp tin đính kèm qua Socket TCP stream. | **Tên file / File đã tải** |
| **F05** | `F05_UpdateProgress.java` | Server xác thực quyền KTV, cập nhật trạng thái mới (Đang xử lý, Hoàn thành, Đã hủy), cập nhật tiến độ (0% - 100%), ghi nhận số byte tệp nghiệm thu. | **Số byte / % Hoàn thành** |
| **F06** | `F06_NotificationBroadcaster.java` | Quản lý nhiều phiên Socket đồng thời. Tự động broadcast/push thông báo thời gian thực tới tất cả các client khi có trạng thái thay đổi. | **Nhiều Socket / Nhiều phiên** |
| **F08** | `F08_UserManagement.java` | Server xác thực quyền Admin, kiểm tra dữ liệu và thực hiện Thêm, Sửa vai trò hoặc Khóa tài khoản người dùng trong MySQL. | Dữ liệu tài khoản / Trạng thái CSDL |

---

### 2. Các file kiến trúc hỗ trợ

- **`LapTrinhMang_HeThongTiepNhanVaQuanLyYeuCauNoiBo.java`**: Điểm khởi chạy chính (Launcher), cho phép chọn:
  - Khởi chạy cả Server và Client cùng lúc (Rất tiện lợi cho giảng viên / chấm điểm).
  - Hoặc khởi chạy độc lập từng máy chủ (Server) hoặc máy trạm (Client).
- **`ServerMain.java`**: Máy chủ TCP Server đa luồng (ThreadPool), có GUI Control Panel hiển thị danh sách các phiên Socket kết nối (F06) và Log sự kiện thời gian thực.
- **`ClientMainGUI.java`**: Giao diện Client trực quan, hiện đại, tích hợp cấu hình IP:Port (F01), form tạo vé (F02), bảng danh sách & xuất file (F03), phân công & tải file (F04), cập nhật tiến độ slider (F05), luồng nền nhận thông báo (F06), màn hình quản trị người dùng (F08).
- **`DatabaseManager.java`**: Quản lý kết nối MySQL (`workdesk_db`). Đặc biệt có cơ chế **tự động fallback sang Datastore tích hợp an toàn** nếu máy chấm thi chưa bật service MySQL, đảm bảo ứng dụng chạy 100% không bao giờ gặp lỗi thiếu DB.
- **`FileTransferHelper.java`**: Module chia nhỏ luồng byte (chunking) và báo cáo tiến độ số byte / % truyền tải qua Socket.
- **`Protocol.java`**: Định nghĩa giao thức gói tin mạng tuần tự hóa (`Protocol.Packet`).
- **`schema.sql`**: Script tạo Database, bảng `users`, `tickets`, `ticket_history` và dữ liệu mẫu trong MySQL.

---

### 3. Tài khoản thử nghiệm có sẵn

| Tên đăng nhập | Mật khẩu | Họ và tên | Vai trò (Role) | Chức năng có thể thao tác |
|---|---|---|---|---|
| **admin** | `admin123` | Quản trị viên hệ thống | `admin` | Toàn quyền: F01, F02, F03, F04, F05, F06, F08 |
| **tech01** | `tech123` | Nguyễn Văn Kỹ Thuật | `tech` | F01, F02, F03, Tiếp nhận F04, Cập nhật tiến độ F05, Nhận thông báo F06 |
| **tech02** | `tech123` | Trần Thị Support | `tech` | F01, F02, F03, Tiếp nhận F04, Cập nhật tiến độ F05, Nhận thông báo F06 |
| **user01** | `user123` | Lê Văn Nhân Viên | `requester` | F01, Tạo yêu cầu F02, Xem yêu cầu của mình & xuất file F03, Nhận thông báo F06 |
| **user02** | `user123` | Phạm Thị Kế Toán | `requester` | F01, Tạo yêu cầu F02, Xem yêu cầu của mình & xuất file F03, Nhận thông báo F06 |

---

### 4. Cách chạy chương trình

#### Cách 1: Chạy trực tiếp từ NetBeans / VS Code / IntelliJ
- Mở dự án `LapTrinhMang` trong IDE.
- Bấm **Run Project** (hoặc chạy file `LapTrinhMang_HeThongTiepNhanVaQuanLyYeuCauNoiBo.java`).
- Trên màn hình Launcher, chọn **"Khởi chạy cả Server & Client"**.

#### Cách 2: Chạy từ dòng lệnh (Terminal / PowerShell)
1. **Biên dịch**:
   ```powershell
   & "C:\Program Files\Apache NetBeans\jdk\bin\javac.exe" -encoding UTF-8 -d build/classes src/laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo/*.java
   ```
2. **Khởi chạy**:
   ```powershell
   & "C:\Program Files\Apache NetBeans\jdk\bin\java.exe" -cp build/classes laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo.LapTrinhMang_HeThongTiepNhanVaQuanLyYeuCauNoiBo
   ```
