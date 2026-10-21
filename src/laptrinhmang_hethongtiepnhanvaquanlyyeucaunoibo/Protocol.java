package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.io.Serializable;

/**
 * Giao thức mạng (Application Protocol over TCP Socket)
 * Định nghĩa gói tin Packet trao đổi giữa Client và Server.
 */
public class Protocol {
    // F01 - Đăng nhập & Xác thực
    public static final String CMD_LOGIN = "CMD_LOGIN";

    // F02 - Tạo yêu cầu
    public static final String CMD_CREATE_TICKET = "CMD_CREATE_TICKET";

    // F03 - Xem danh sách & Chi tiết & Lưu file
    public static final String CMD_GET_TICKETS = "CMD_GET_TICKETS";
    public static final String CMD_GET_TICKET_DETAIL = "CMD_GET_TICKET_DETAIL";
    public static final String CMD_EXPORT_TICKETS = "CMD_EXPORT_TICKETS";

    // F04 - Tiếp nhận & Phân công kỹ thuật viên, Tải file đính kèm
    public static final String CMD_ASSIGN_TECH = "CMD_ASSIGN_TECH";
    public static final String CMD_UPLOAD_FILE = "CMD_UPLOAD_FILE";
    public static final String CMD_DOWNLOAD_FILE = "CMD_DOWNLOAD_FILE";

    // F05 - Cập nhật trạng thái xử lý & Tiến độ %
    public static final String CMD_UPDATE_STATUS = "CMD_UPDATE_STATUS";

    // F06 - Gửi thông báo đa luồng (Real-time Broadcast Notification)
    public static final String CMD_NOTIFICATION = "CMD_NOTIFICATION";

    // F08 - Quản lý tài khoản và phân quyền
    public static final String CMD_GET_USERS = "CMD_GET_USERS";
    public static final String CMD_ADD_USER = "CMD_ADD_USER";
    public static final String CMD_UPDATE_USER = "CMD_UPDATE_USER";
    public static final String CMD_LOCK_USER = "CMD_LOCK_USER";

    // Trạng thái gói tin phản hồi
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_ERROR = "ERROR";

    /**
     * Gói tin mạng chuẩn được tuần tự hóa (Serialized Packet)
     */
    public static class Packet implements Serializable {
        private static final long serialVersionUID = 1L;

        public String command;
        public String status;
        public String message;
        public Object data;

        public Packet(String command) {
            this.command = command;
        }

        public Packet(String command, String status, String message, Object data) {
            this.command = command;
            this.status = status;
            this.message = message;
            this.data = data;
        }

        public static Packet success(String command, String message, Object data) {
            return new Packet(command, STATUS_SUCCESS, message, data);
        }

        public static Packet error(String command, String message) {
            return new Packet(command, STATUS_ERROR, message, null);
        }
    }
}
