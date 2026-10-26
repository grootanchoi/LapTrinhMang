package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * CHỨC NĂNG F02: TẠO YÊU CẦU HỖ TRỢ KỸ THUẬT
 * =========================================================================
 * - Xử lý chính: Kiểm tra tính hợp lệ dữ liệu đầu vào, Server tự động sinh mã
 *                yêu cầu (Format: YC-xxxx), lưu trữ vào CSDL (MySQL/Datastore)
 *                và thông báo kết quả cho người gửi qua sự kiện Socket.
 * - Đầu vào / đầu ra: Sự kiện Socket / Thông báo kết quả.
 * =========================================================================
 */
public class F02_CreateTicket {

    /**
     * DTO yêu cầu tạo ticket gửi qua Socket
     */
    public static class CreateTicketRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        public String title;
        public String category;
        public String priority;
        public String detail;
        public String owner;
        public String attachmentName;

        public CreateTicketRequest(String title, String category, String priority, String detail, String owner, String attachmentName) {
            this.title = title;
            this.category = category;
            this.priority = priority;
            this.detail = detail;
            this.owner = owner;
            this.attachmentName = attachmentName;
        }
    }

    // =========================================================================
    // XỬ LÝ PHÍA SERVER (Server-Side Logic)
    // =========================================================================
    public static Protocol.Packet handleServerCreateTicket(Protocol.Packet requestPacket, Models.User user,
                                                            F06_NotificationBroadcaster broadcaster) {
        if (user == null) {
            return Protocol.Packet.error(Protocol.CMD_CREATE_TICKET, "Bạn cần đăng nhập.");
        }
        if (!(requestPacket.data instanceof CreateTicketRequest req)) {
            return Protocol.Packet.error(Protocol.CMD_CREATE_TICKET, "Dữ liệu yêu cầu không đúng định dạng.");
        }

        // 1. Kiểm tra dữ liệu đầu vào (Validation)
        if (req.title == null || req.title.trim().isEmpty()) {
            return Protocol.Packet.error(Protocol.CMD_CREATE_TICKET, "Tiêu đề yêu cầu không được để trống.");
        }
        if (req.detail == null || req.detail.trim().isEmpty()) {
            return Protocol.Packet.error(Protocol.CMD_CREATE_TICKET, "Mô tả chi tiết yêu cầu không được để trống.");
        }
        DatabaseManager db = DatabaseManager.getInstance();

        // 2. Server tự động sinh mã yêu cầu theo chuẩn
        String newTicketId = db.generateNextTicketId();
        String today = LocalDate.now().toString();

        Models.Ticket ticket = new Models.Ticket(
                newTicketId,
                req.title.trim(),
                req.category != null ? req.category : "IT",
                req.priority != null ? req.priority : "Bình thường",
                today,
                "Mới",
                user.username,
                "Chưa phân công",
                req.detail.trim(),
                0,
                req.attachmentName != null ? req.attachmentName : ""
        );

        // 3. Lưu thông tin vào CSDL MySQL / Datastore
        boolean saved = db.insertTicket(ticket);
        if (!saved) {
            return Protocol.Packet.error(Protocol.CMD_CREATE_TICKET, "Không thể lưu yêu cầu vào hệ thống CSDL.");
        }

        // Ghi lại lịch sử khởi tạo
        db.recordHistory(newTicketId, "Khởi tạo yêu cầu", user.username, "Yêu cầu được gửi lên hệ thống");

        // 4. Phát sinh sự kiện Socket và thông báo tới các Client đang kết nối (F06)
        if (broadcaster != null) {
            String broadcastMsg = "Yêu cầu mới [" + newTicketId + "] '" + ticket.title + "' từ " + ticket.owner;
            broadcaster.broadcast("TICKET_CREATED", newTicketId, broadcastMsg);
        }

        // 5. Thông báo kết quả thành công cho người gửi
        return Protocol.Packet.success(
                Protocol.CMD_CREATE_TICKET,
                "Đã tạo yêu cầu hỗ trợ thành công với mã: " + newTicketId,
                ticket
        );
    }
}
