# HỆ THỐNG TIẾP NHẬN VÀ QUẢN LÝ YÊU CẦU NỘI BỘ (WORKDESK)
## ĐỒ ÁN LẬP TRÌNH MẠNG MÁY TÍNH - MÔ HÌNH CLIENT/SERVER TCP SOCKET

Dự án được xây dựng và chuẩn hóa theo đúng yêu cầu phân tách từng file độc lập cho từng chức năng **F01-F11** (bao gồm các chức năng đã đăng ký và trao đổi hỗ trợ trực tuyến) dựa trên nền tảng TCP Socket, cơ sở dữ liệu MySQL và kiến trúc đa luồng (Multi-threading).

---

### 1. Cấu trúc các file chức năng riêng biệt

| Mã chức năng | Tên file mã nguồn | Vai trò & Xử lý chính | Đầu vào / Đầu ra |
|---|---|---|---|
| **F01** | `F01_Authentication.java` | Đăng nhập và xác thực tài khoản qua TCP Socket. Server xác thực MySQL, kiểm tra tài khoản, trả về vai trò (`admin`, `tech`, `requester`). | **IP, Port / Trạng thái kết nối** |
| **F02** | `F02_CreateTicket.java` | Kiểm tra dữ liệu hợp lệ, Server sinh mã yêu cầu (`YC-xxxx`), lưu thông tin vào MySQL và thông báo kết quả qua Socket. | **Sự kiện Socket / Thông báo** |
| **F03** | `F03_ViewTickets.java` | Server kiểm tra quyền truy cập, truy vấn MySQL theo tài khoản, trả danh sách / chi tiết. Hỗ trợ xuất danh sách ra file báo cáo lưu trên máy client. | **File / Kết quả lưu file** |
| **F04** | `F04_AssignTechnician.java` | Server kiểm tra quyền và trạng thái yêu cầu, phân công kỹ thuật viên. Xử lý truyền nhận tệp tin đính kèm qua Socket TCP stream. | **Tên file / File đã tải** |
| **F05** | `F05_UpdateProgress.java` | Server xác thực quyền KTV, cập nhật trạng thái mới (Đang xử lý, Hoàn thành, Đã hủy), cập nhật tiến độ (0% - 100%), ghi nhận số byte tệp nghiệm thu. | **Số byte / % Hoàn thành** |
| **F06** | `F06_NotificationBroadcaster.java` | Quản lý nhiều phiên Socket đồng thời. Tự động broadcast/push thông báo thời gian thực; dashboard server hiển thị phiên theo vai trò, số lệnh/lỗi và độ trễ phản hồi trung bình/cao nhất. | **Nhiều Socket / Dashboard TCP** |
| **F07** | `F07_SearchTickets.java` | Tìm kiếm theo từ khóa và lọc theo trạng thái, danh mục, mức ưu tiên. Server áp dụng điều kiện quyền truy cập và truy vấn SQL tham số hóa. | Từ khóa, bộ lọc / Danh sách yêu cầu phù hợp |
| **F08** | `F08_UserManagement.java` | Server xác thực quyền Admin, kiểm tra dữ liệu và thực hiện Thêm, Sửa vai trò hoặc Khóa tài khoản người dùng trong MySQL. | Dữ liệu tài khoản / Trạng thái CSDL |
| **F09** | `F09_TicketStatistics.java` | Thống kê số yêu cầu theo trạng thái và kỹ thuật viên bằng `COUNT` và `GROUP BY`; hỗ trợ lọc khoảng ngày và giới hạn theo quyền người dùng. | Vai trò, từ ngày, đến ngày / Bảng thống kê |
| **F10** | `F10_TicketHistoryReview.java` | Tra cứu lịch sử theo mã yêu cầu; người gửi có thể chấm điểm 1-5 và nhận xét khi yêu cầu hoàn thành. | Mã yêu cầu, điểm, nhận xét / Timeline và kết quả lưu |
| **F11** | `F11_TicketChat.java` | Trao đổi trực tuyến theo từng yêu cầu. Server xác thực người tham gia, lưu lịch sử/trạng thái đã đọc và đẩy tin nhắn qua TCP tới người gửi/kỹ thuật viên đang kết nối. | Mã yêu cầu, nội dung / Lịch sử, số chưa đọc, trạng thái đã đọc |

---

### 2. Các file kiến trúc hỗ trợ

- **`LapTrinhMang_HeThongTiepNhanVaQuanLyYeuCauNoiBo.java`**: Điểm khởi chạy chính (Launcher), cho phép chọn:
  - Khởi chạy cả Server và Client cùng lúc (Rất tiện lợi cho giảng viên / chấm điểm).
  - Hoặc khởi chạy độc lập từng máy chủ (Server) hoặc máy trạm (Client).
- **`ServerMain.java`**: Máy chủ TCP Server đa luồng (ThreadPool), có GUI Control Panel hiển thị danh sách các phiên Socket kết nối (F06) và Log sự kiện thời gian thực.
- **`ClientMainGUI.java`**: Giao diện Client tích hợp F01-F11: tìm kiếm và bộ lọc Socket (F07), thống kê (F09), lịch sử và đánh giá (F10), chat thời gian thực theo yêu cầu (F11).
- **`DatabaseManager.java`**: Quản lý kết nối MySQL (`workdesk_db`), tự động fallback sang datastore trong bộ nhớ khi không kết nối được MySQL. Mật khẩu mới được lưu dạng PBKDF2-HMAC-SHA256; mật khẩu cũ dạng văn bản được nâng cấp thành hash sau lần đăng nhập hợp lệ.
- **`FileTransferHelper.java`**: Module chia nhỏ luồng byte (chunking) và báo cáo tiến độ số byte / % truyền tải qua Socket.
- **`Protocol.java`**: Định nghĩa giao thức gói tin mạng tuần tự hóa (`Protocol.Packet`).
- **`schema.sql`**: Script tạo Database và các bảng `users`, `tickets`, `ticket_history`, `ticket_reviews`, `ticket_messages`, `ticket_message_reads` cùng dữ liệu mẫu đã băm mật khẩu.

---

### 3. Tài khoản thử nghiệm có sẵn

| Tên đăng nhập | Mật khẩu | Họ và tên | Vai trò (Role) | Chức năng có thể thao tác |
|---|---|---|---|---|
| **admin** | `admin123` | Quản trị viên hệ thống | `admin` | Quản lý người dùng và vai trò F08; xem mọi yêu cầu, thống kê, lịch sử; phân công, cập nhật; các chức năng còn lại |
| **tech01** | `tech123` | Nguyễn Văn Kỹ Thuật | `tech` | Tạo yêu cầu; xem yêu cầu mới và được phân công; phân công kỹ thuật viên; cập nhật yêu cầu được phân công; thống kê, lịch sử và tệp của yêu cầu được phép xem |
| **tech02** | `tech123` | Trần Thị Support | `tech` | Tạo yêu cầu; xem yêu cầu mới và được phân công; phân công kỹ thuật viên; cập nhật yêu cầu được phân công; thống kê, lịch sử và tệp của yêu cầu được phép xem |
| **user01** | `user123` | Lê Văn Nhân Viên | `requester` | Tạo yêu cầu; chỉ xem, xuất, xem lịch sử và tải tệp của yêu cầu do mình gửi; đánh giá yêu cầu đã hoàn thành của mình |
| **user02** | `user123` | Phạm Thị Kế Toán | `requester` | Tạo yêu cầu; chỉ xem, xuất, xem lịch sử và tải tệp của yêu cầu do mình gửi; đánh giá yêu cầu đã hoàn thành của mình |

Quản trị viên có thể gán vai trò `requester`, `tech` hoặc `admin` khi thêm tài khoản hoặc chọn **Sửa vai trò** trong F08. Máy chủ kiểm tra đăng nhập và quyền trên từng lệnh; tên người dùng/vai trò gửi kèm từ client không được dùng làm căn cứ phân quyền. Khi tài khoản bị khóa hoặc vai trò bị đổi, thay đổi có hiệu lực ở yêu cầu tiếp theo.

---

### 4. Cách chạy chương trình

### 4.1 Kịch bản demo điểm nhấn mạng (F11)
1. Khởi động Server và mở hai Client độc lập, đăng nhập lần lượt bằng **user02** và **tech01**.
2. Chọn yêu cầu mẫu **YC-2026-0001** ở cả hai Client; mở **Trao đổi trực tuyến (F11)**.
3. Gửi tin nhắn từ một Client và xác nhận tin xuất hiện tức thời ở Client còn lại mà không cần tải lại màn hình.
4. Đóng cửa sổ chat, mở lại để kiểm tra lịch sử được lưu; đăng nhập bằng **user01** để xác nhận không thể xem cuộc trao đổi của yêu cầu user02.
5. Nếu dùng MySQL, chạy `schema.sql` để tạo các bảng chat và read receipt; ứng dụng cũng tự tạo bảng read receipt khi khởi động nếu tài khoản DB có quyền DDL. Khi không có MySQL, tin nhắn và trạng thái đọc chỉ được giữ trong datastore bộ nhớ của phiên chạy hiện tại.

### 4.2 Dashboard giám sát TCP
- Dashboard nằm ở phần dưới cửa sổ Server: số client đang kết nối theo vai trò, tổng phiên, lệnh đã xử lý, lỗi lệnh, độ trễ trung bình và cao nhất.
- Bộ đếm được cập nhật sau mỗi lệnh TCP; đóng kết nối sẽ giảm bộ đếm phiên tương ứng.

### 4.3 Chạy bộ kiểm thử tự động
Mở Command Prompt tại thư mục dự án và chạy:
```bat
test.bat
```
Runner biên dịch mã nguồn và chạy kiểm thử tích hợp trên TCP thường lẫn TLS. Bộ test dùng datastore cô lập (`workdesk.testMode`), không ghi dữ liệu vào MySQL; JDK 21+ và JAR MariaDB trong `lib` phải sẵn có. Chứng chỉ TLS dùng cho test được tạo cục bộ dưới `build\test-tls`.

### 4.4 Bật TLS cho kết nối TCP
TLS tắt mặc định để giữ tương thích với demo cũ; khi bật, client kiểm tra chứng chỉ server và tên host. Tạo PKCS12 keystore cho máy chủ, rồi xuất chứng chỉ và nhập vào truststore của client (ví dụ máy chủ chạy tại `127.0.0.1`):
```bat
keytool -genkeypair -alias workdesk -keyalg RSA -keysize 2048 -validity 365 -storetype PKCS12 -keystore server.p12 -storepass changeit -keypass changeit -dname "CN=127.0.0.1" -ext "SAN=IP:127.0.0.1,DNS:localhost"
keytool -exportcert -alias workdesk -keystore server.p12 -storepass changeit -file server.cer
keytool -importcert -alias workdesk -file server.cer -storetype PKCS12 -keystore client-trust.p12 -storepass changeit -noprompt
```
Trước khi khởi chạy tiến trình Server, đặt:
```bat
set WORKDESK_TLS=true
set WORKDESK_KEYSTORE=C:\duong-dan\server.p12
set WORKDESK_KEYSTORE_PASSWORD=changeit
```
Trên mỗi tiến trình Client, đặt:
```bat
set WORKDESK_TLS=true
set WORKDESK_TRUSTSTORE=C:\duong-dan\client-trust.p12
set WORKDESK_TRUSTSTORE_PASSWORD=changeit
```
Thay `changeit` bằng mật khẩu riêng và bảo vệ keystore/truststore. Nếu kết nối tới máy chủ từ xa, chứng chỉ phải có SAN khớp địa chỉ/hostname mà client dùng để kết nối. Cả server và client phải cùng bật TLS và cấu hình chứng chỉ tương ứng.

### 4.5 Tài khoản mẫu và chuyển đổi dữ liệu cũ
Tài khoản được tạo bởi `schema.sql` dùng các mật khẩu ở bảng phía trên nhưng cột `password` lưu chuỗi PBKDF2, không lưu mật khẩu gốc. Khi cập nhật ứng dụng trên cơ sở dữ liệu cũ, mỗi tài khoản mật khẩu văn bản sẽ được chuyển sang hash khi người dùng đăng nhập hợp lệ lần đầu; không cần lưu hoặc gửi mật khẩu dạng văn bản trong SQL.

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
