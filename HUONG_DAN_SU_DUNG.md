# HỆ THỐNG TIẾP NHẬN VÀ QUẢN LÝ YÊU CẦU NỘI BỘ (WORKDESK)
## ĐỒ ÁN LẬP TRÌNH MẠNG MÁY TÍNH - MÔ HÌNH CLIENT/SERVER TCP SOCKET

Dự án được xây dựng và chuẩn hóa theo đúng yêu cầu phân tách từng file độc lập cho từng chức năng **F01-F11** (bao gồm các chức năng đã đăng ký và trao đổi hỗ trợ trực tuyến) dựa trên nền tảng TCP Socket, cơ sở dữ liệu MySQL và kiến trúc đa luồng (Multi-threading).

---

### 1. Cấu trúc các file chức năng riêng biệt

| Mã chức năng | Tên file mã nguồn | Vai trò & Xử lý chính | Đầu vào / Đầu ra |
|---|---|---|---|
| **F01** | `F01_Authentication.java` | Đăng nhập và xác thực tài khoản qua TCP Socket. Server xác thực MySQL, kiểm tra tài khoản, trả về vai trò (`admin`, `tech`, `requester`). | **IP, Port / Trạng thái kết nối** |
| **F02** | `F02_CreateTicket.java` | Kiểm tra dữ liệu hợp lệ, Server sinh mã yêu cầu (`YC-xxxx`), nhận nội dung tệp qua Socket, lưu trên máy chủ và thông báo kết quả. | **Gói Socket / Thông báo** |
| **F03** | `F03_ViewTickets.java` | Server kiểm tra quyền truy cập, truy vấn MySQL theo tài khoản, trả danh sách / chi tiết. Hỗ trợ xuất danh sách ra file báo cáo lưu trên máy client. | **File / Kết quả lưu file** |
| **F04** | `F04_AssignTechnician.java` | Server kiểm tra quyền và trạng thái yêu cầu, phân công kỹ thuật viên. Xử lý truyền nhận tệp tin đính kèm qua Socket TCP stream. | **Tên file / File đã tải** |
| **F05** | `F05_UpdateProgress.java` | Server xác thực quyền KTV, cập nhật trạng thái mới (Đang xử lý, Hoàn thành, Đã hủy), cập nhật tiến độ (0% - 100%), lưu tệp nghiệm thu với prefix mã ticket và ghi nhận số byte. | **Số byte / % Hoàn thành** |
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
- **`ClientMainGUI.java`**: Chịu trách nhiệm giao diện Swing và xử lý callback; các thao tác Socket được chuyển tới `NetworkService`.
- **`NetworkService.java`**: Quản lý Socket client, luồng đọc/gửi gói tin, ghép request/response bằng `correlationId`, heartbeat và tự kết nối lại.
- **`DatabaseManager.java`**: Quản lý kết nối MySQL (`workdesk_db`), tự động fallback sang datastore trong bộ nhớ khi không kết nối được MySQL. Mật khẩu mới được lưu dạng PBKDF2-HMAC-SHA256; mật khẩu cũ dạng văn bản được nâng cấp thành hash sau lần đăng nhập hợp lệ.
- **`FileTransferHelper.java`**: Module chia nhỏ luồng byte (chunking) và báo cáo tiến độ số byte / % truyền tải qua Socket.
- **`Protocol.java`**: Định nghĩa giao thức gói tin mạng tuần tự hóa (`Protocol.Packet`).
- **`schema.sql`**: Script tạo Database và các bảng `users`, `tickets`, `ticket_history`, `ticket_reviews`, `ticket_messages`, `ticket_message_reads` cùng dữ liệu mẫu đã băm mật khẩu.

---

### 3. Tài khoản thử nghiệm có sẵn

| Tên đăng nhập | Mật khẩu | Họ và tên | Vai trò (Role) | Chức năng có thể thao tác |
|---|---|---|---|---|
| **admin** | `admin123` | Quản trị viên hệ thống | `admin` | Quản lý tài khoản/vai trò F08; xem mọi yêu cầu và chat; phân công KTV; sửa/xóa mềm yêu cầu; xem nhật ký kiểm toán và SLA |
| **tech01** | `tech123` | Nguyễn Văn Kỹ Thuật | `tech` | Chỉ nhận/xem yêu cầu admin đã phân công cho mình; cập nhật tiến độ, xem lịch sử, tệp và chat của yêu cầu được phân công |
| **tech02** | `tech123` | Trần Thị Support | `tech` | Chỉ nhận/xem yêu cầu admin đã phân công cho mình; cập nhật tiến độ, xem lịch sử, tệp và chat của yêu cầu được phân công |
| **user01** | `user123` | Lê Văn Nhân Viên | `requester` | Tạo yêu cầu; chỉ xem, xuất, xem lịch sử và tải tệp yêu cầu do mình gửi; đánh giá yêu cầu đã hoàn thành; chat với KTV phụ trách |
| **user02** | `user123` | Phạm Thị Kế Toán | `requester` | Tạo yêu cầu; chỉ xem, xuất, xem lịch sử và tải tệp yêu cầu do mình gửi; đánh giá yêu cầu đã hoàn thành; chat với KTV phụ trách |

Quản trị viên là vai trò duy nhất được tạo tài khoản và gán vai trò `requester`, `tech` hoặc `admin` trong F08. Chỉ requester được tạo yêu cầu; chỉ admin được phân công kỹ thuật viên (F04); tech chỉ nhận/xem và cập nhật ticket đã phân công cho chính mình. Một tài khoản chỉ được có một phiên đăng nhập đang hoạt động; lần đăng nhập thứ hai bị từ chối và ghi cảnh báo ở log server. Khi admin khóa tài khoản, server gửi lệnh thu hồi phiên và đóng kết nối ngay; client trở về màn hình đăng nhập, không tự kết nối lại. Server kiểm tra đăng nhập và quyền trên từng lệnh; tên người dùng/vai trò gửi kèm từ client không được dùng làm căn cứ phân quyền.

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

### 4.3 Giới hạn ticket, phòng ban và mạng được phép
- Mỗi tài khoản requester được tạo tối đa **5 ticket trong 24 giờ gần nhất**. Ticket đã xóa mềm vẫn tính vào giới hạn. Server kiểm tra quota khi lưu, không dựa vào dữ liệu do client gửi.
- Admin gán phòng ban khi tạo/sửa tài khoản trong F08. Ticket lưu ảnh chụp phòng ban tại thời điểm gửi; requester chưa có phòng ban sẽ không thể tạo ticket cho đến khi admin cập nhật hồ sơ.
- IP nguồn được lấy từ socket phía server, lưu cùng ticket và hiển thị trong màn hình chi tiết cho admin. Nếu kết nối đi qua NAT/VPN, server ghi địa chỉ nguồn mà nó thực sự nhìn thấy (có thể là IP gateway).
- Server tự động cho phép địa chỉ IPv4 nội bộ (`10.x.x.x`, `172.16.x.x` đến `172.31.x.x`, `192.168.x.x`) và IPv6 ULA/loopback; không cần cấu hình CIDR. Các client trong mạng LAN/VPN riêng có thể kết nối nếu Windows Firewall cho phép cổng TCP của server (mặc định `8888`). Địa chỉ IP công cộng bị từ chối, vì vậy không mở cổng server trực tiếp ra Internet.
- Ứng dụng tự thêm các cột dữ liệu mới khi kết nối MySQL; tài khoản CSDL cần quyền `ALTER TABLE`. Với dữ liệu cũ, admin cần cập nhật phòng ban cho các requester hiện có.

### 4.4 Phiên đăng nhập và phân công kỹ thuật viên
- Không thể đăng nhập cùng một tài khoản requester/tech/admin ở hai client cùng lúc. Server từ chối phiên mới và ghi cảnh báo kèm IP kết nối trong log server; đăng xuất hoặc ngắt kết nối phiên cũ rồi mới đăng nhập lại.
- Khi admin khóa tài khoản đang trực tuyến, người dùng đó bị đăng xuất ngay lập tức. Mở khóa chỉ cho phép đăng nhập lại, không khôi phục phiên cũ.
- Tech chỉ thấy ticket có trường “Kỹ thuật viên phụ trách” là tài khoản của mình. Ticket mới chưa được admin phân công không xuất hiện trong danh sách, kết quả tìm kiếm, thống kê, chi tiết hoặc thông báo đến tech. Khi admin phân công, người được giao và người gửi nhận thông báo.

### 4.5 Chạy bộ kiểm thử tự động
Mở Command Prompt tại thư mục dự án và chạy:
```bat
test.bat
```
Runner biên dịch mã nguồn và chạy kiểm thử tích hợp trên TCP thường lẫn TLS. Bộ test dùng datastore cô lập (`workdesk.testMode`), không ghi dữ liệu vào MySQL; JDK 21+ và JAR MariaDB trong `lib` phải sẵn có. Chứng chỉ TLS dùng cho test được tạo cục bộ dưới `build\test-tls`.

### 4.6 Bật TLS cho kết nối TCP
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

### 4.7 Tài khoản mẫu và chuyển đổi dữ liệu cũ
Tài khoản được tạo bởi `schema.sql` dùng các mật khẩu ở bảng phía trên nhưng cột `password` lưu chuỗi PBKDF2, không lưu mật khẩu gốc. Khi cập nhật ứng dụng trên cơ sở dữ liệu cũ, mỗi tài khoản mật khẩu văn bản sẽ được chuyển sang hash khi người dùng đăng nhập hợp lệ lần đầu; không cần lưu hoặc gửi mật khẩu dạng văn bản trong SQL.

### 4.8 Heartbeat và tự kết nối lại
- Client gửi heartbeat TCP `PING` mỗi 15 giây; server phản hồi `PONG`. Server tự đóng phiên nếu không nhận được gói tin trong 30 giây. Nếu socket bị mất hoặc không có phản hồi, client tự đăng nhập/kết nối lại bằng thông tin chỉ được giữ tạm trong bộ nhớ của phiên chạy.
- Client thử lại theo khoảng 1, 2, 4, 8... giây, tối đa 30 giây giữa các lần thử. Khi kết nối lại thành công, client tải lại danh sách yêu cầu, số tin chưa đọc và lịch sử của các cửa sổ chat đang mở.
- Demo: mở client, dừng server trong chốc lát để thấy tiêu đề báo đang kết nối lại, sau đó khởi động server cùng IP/port và xác nhận client trở lại hoạt động.

### 4.9 Nhật ký kiểm toán, sửa/xóa yêu cầu và SLA
- Chỉ admin thấy **Quản lý tài khoản**, **Nhật ký kiểm toán**, **Sửa yêu cầu** và **Xóa yêu cầu**. Nhật ký hiển thị tối đa 500 sự kiện gần nhất như đăng nhập, tạo/sửa/xóa yêu cầu, phân công, cập nhật trạng thái, quản lý tài khoản, chat và cảnh báo SLA.
- Xóa yêu cầu là **xóa mềm**: yêu cầu biến khỏi danh sách làm việc nhưng giữ lịch sử kiểm toán và dữ liệu trò chuyện trong cơ sở dữ liệu. Admin có thể sửa tiêu đề, danh mục, mức ưu tiên và mô tả; đổi mức ưu tiên sẽ tính lại hạn SLA.
- SLA tính theo lịch thứ Hai–thứ Sáu, 08:00–17:00, chưa loại trừ ngày lễ. Hạn mặc định: Khẩn cấp 4 giờ làm việc; Cao 8; Bình thường 24; Thấp 48. Khi admin đổi mức ưu tiên, hạn được tính lại từ thời điểm tạo ticket theo mức mới. Server cảnh báo trước hạn tối đa 1 giờ làm việc và gửi cảnh báo khi quá hạn; cảnh báo một lần cho mỗi trạng thái/cấu hình SLA.
- Client không còn thanh tiến độ truyền tệp ở góc dưới bên phải. Tiến độ xử lý yêu cầu vẫn hiển thị trong bảng và chức năng F05.

### 4.10 Giao diện WorkDesk Client
- Dashboard dùng bố cục danh sách–chi tiết: chọn một yêu cầu để xem người gửi, phòng ban, hạn SLA, tiến độ và khung trao đổi ngay bên cạnh; nhấp đúp dòng để mở cửa sổ chi tiết riêng.
- Bảng yêu cầu dùng nhãn màu cho mức ưu tiên/trạng thái, thanh tiến độ nhỏ và cảnh báo màu cho SLA sắp đến hạn hoặc đã quá hạn. Thanh cuối cửa sổ hiển thị trạng thái kết nối, chế độ TLS/TCP, tài khoản/vai trò và độ trễ heartbeat.
- Tin nhắn của bạn và người khác được phân biệt bằng bubble; Enter gửi tin, Shift+Enter xuống dòng. Ảnh có thumbnail và có thể mở xem hoặc tải xuống. Thông báo mới xuất hiện dạng toast, không chặn thao tác.

### 4.11 Ảnh và tệp đính kèm trong F11
- Trong cửa sổ chat, chọn **Gửi ảnh/tệp** để gửi PNG/JPEG/GIF/WebP, PDF/TXT/DOCX/XLSX hoặc ZIP, tối đa 10 MB mỗi tệp.
- Server lưu tệp trong `server_storage` với tên lưu trữ ngẫu nhiên, lưu metadata/checksum SHA-256 trong MySQL và chỉ cho người có quyền xem cuộc trò chuyện tải xuống. Người nhận chọn nút **Tải tệp** trong lịch sử chat.
- Khi dùng MySQL, chạy lại `schema.sql` hoặc khởi động server với tài khoản DB có quyền tạo bảng để tạo các bảng phụ trợ mới. Bản fallback trong bộ nhớ giữ dữ liệu chat/audit trong thời gian server còn chạy; tệp chat vẫn được ghi vào `server_storage`.

### 4.12 Tệp đính kèm khi tạo yêu cầu F02
- Tệp được đọc trên máy client, gửi cùng yêu cầu tạo ticket qua Socket và lưu trong `server_storage` của máy chủ; không cần chia sẻ thư mục giữa client và server.
- Kích thước tệp F02 tối đa 10 MB. Nếu tệp không thể gửi hoặc máy chủ không lưu được, yêu cầu sẽ báo lỗi thay vì hiển thị thành công giả.
- Tệp nghiệm thu tải lên từ F05 cũng được lưu qua `FileTransferHelper` với prefix `<ticketId>_`; khi tải về `client_downloads`, client bỏ prefix để giữ tên tệp gốc.

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
