# LapTrinhMang - HeThongTiepNhanVaQuanLyYeuCauNoiBo
Mô hình Client/Server TCP Socket với các module chức năng F01-F11, dashboard giám sát TCP, một phiên đăng nhập cho mỗi tài khoản, thu hồi phiên ngay khi admin khóa tài khoản, phân công ticket riêng cho kỹ thuật viên, giới hạn ticket theo tài khoản, lưu IP nguồn và phòng ban, tự động giới hạn kết nối vào mạng nội bộ, heartbeat/tự kết nối lại, nhật ký kiểm toán, SLA giờ làm việc, phân quyền theo vai trò, quản trị yêu cầu và chat có đính kèm tệp.

`NetworkService` quản lý Socket client, heartbeat, tự kết nối lại và ghép cặp request/response; `ClientMainGUI` tập trung vào giao diện và callback. Tệp đính kèm của F02 được truyền tới và lưu trên máy chủ, tối đa 10 MB.
Xem chi tiết tài liệu và cách chạy trong [HUONG_DAN_SU_DUNG.md](HUONG_DAN_SU_DUNG.md).
