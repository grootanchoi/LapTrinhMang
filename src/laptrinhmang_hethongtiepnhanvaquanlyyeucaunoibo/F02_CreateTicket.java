package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
    private static final int MAX_TICKETS_PER_24_HOURS = 5;

    /**
     * DTO yêu cầu tạo ticket gửi qua Socket
     */
    public static class CreateTicketRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        public String title;
        public String category;
        public String device;
        public String priority;
        public String detail;
        public String owner;
        public String attachmentName;
        public byte[] attachmentBytes;

        public CreateTicketRequest(String title, String category, String priority, String detail, String owner, String attachmentName) {
            this(title, category, priority, detail, owner, attachmentName, null, "");
        }

        public CreateTicketRequest(String title, String category, String priority, String detail,
                                   String owner, String attachmentName, byte[] attachmentBytes) {
            this(title, category, priority, detail, owner, attachmentName, attachmentBytes, "");
        }

        public CreateTicketRequest(String title, String category, String priority, String detail,
                                   String owner, String attachmentName, byte[] attachmentBytes, String device) {
            this.title = title;
            this.category = category;
            this.device = device;
            this.priority = priority;
            this.detail = detail;
            this.owner = owner;
            this.attachmentName = attachmentName;
            this.attachmentBytes = attachmentBytes;
        }
    }

    // =========================================================================
    // XỬ LÝ PHÍA SERVER (Server-Side Logic)
    // =========================================================================
    public static Protocol.Packet handleServerCreateTicket(Protocol.Packet requestPacket, Models.User user,
                                                            F06_NotificationBroadcaster broadcaster,
                                                            String sourceIp) {
        if (user == null) {
            return Protocol.Packet.error(Protocol.CMD_CREATE_TICKET, "Bạn cần đăng nhập.");
        }
        if (!"requester".equalsIgnoreCase(user.role)) {
            return Protocol.Packet.error(Protocol.CMD_CREATE_TICKET, "Chỉ người gửi yêu cầu mới được tạo yêu cầu hỗ trợ.");
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
        if (req.device != null && req.device.trim().length() > 100) {
            return Protocol.Packet.error(Protocol.CMD_CREATE_TICKET, "Tên thiết bị không được vượt quá 100 ký tự.");
        }
        if (user.department == null || user.department.isBlank()) {
            return Protocol.Packet.error(Protocol.CMD_CREATE_TICKET,
                    "Tài khoản chưa được gán phòng ban. Vui lòng liên hệ quản trị viên.");
        }
        DatabaseManager db = DatabaseManager.getInstance();

        // 2. Server tự động sinh mã yêu cầu theo chuẩn
        String newTicketId = db.generateNextTicketId();
        String attachmentName = req.attachmentName == null ? "" : req.attachmentName.trim();
        byte[] attachmentBytes = req.attachmentBytes;
        boolean hasAttachmentName = !attachmentName.isEmpty();
        boolean hasAttachmentBytes = attachmentBytes != null;
        if (hasAttachmentName != hasAttachmentBytes) {
            return Protocol.Packet.error(Protocol.CMD_CREATE_TICKET,
                    "Tên và dữ liệu tệp đính kèm phải được gửi cùng nhau.");
        }
        if (attachmentBytes != null && (attachmentBytes.length == 0
                || attachmentBytes.length > FileTransferHelper.MAX_ATTACHMENT_SIZE)) {
            return Protocol.Packet.error(Protocol.CMD_CREATE_TICKET,
                    "Tệp đính kèm phải lớn hơn 0 byte và không vượt quá 10 MB.");
        }

        String storedAttachmentName = "";
        if (attachmentBytes != null) {
            try {
                storedAttachmentName = FileTransferHelper.storeTicketAttachment(
                        newTicketId, attachmentName, attachmentBytes);
            } catch (java.io.IOException e) {
                return Protocol.Packet.error(Protocol.CMD_CREATE_TICKET,
                        "Không thể lưu tệp đính kèm trên máy chủ: " + e.getMessage());
            }
        }
        LocalDateTime createdAt = LocalDateTime.now();
        String today = createdAt.toLocalDate().toString();

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
                storedAttachmentName
        );
        ticket.createdAt = createdAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        ticket.device = req.device == null ? "" : req.device.trim();
        ticket.ownerDepartment = user.department.trim();
        ticket.sourceIp = sourceIp;

        // Check the rolling quota and insert atomically on the server.
        DatabaseManager.TicketCreateResult result = db.insertRequesterTicketWithinLimit(
                ticket, MAX_TICKETS_PER_24_HOURS);
        if (result == DatabaseManager.TicketCreateResult.QUOTA_EXCEEDED) {
            deleteStoredAttachment(storedAttachmentName);
            return Protocol.Packet.error(Protocol.CMD_CREATE_TICKET,
                    "Bạn đã đạt giới hạn " + MAX_TICKETS_PER_24_HOURS + " yêu cầu trong 24 giờ gần nhất.");
        }
        if (result != DatabaseManager.TicketCreateResult.CREATED) {
            deleteStoredAttachment(storedAttachmentName);
            return Protocol.Packet.error(Protocol.CMD_CREATE_TICKET, "Không thể lưu yêu cầu vào hệ thống CSDL.");
        }

        // Ghi lại lịch sử khởi tạo
        db.recordHistory(newTicketId, "Khởi tạo yêu cầu", user.username, "Yêu cầu được gửi lên hệ thống");
        db.recordAuditEvent("TICKET_CREATED", newTicketId, user.username,
                "Tạo yêu cầu mới: " + ticket.title + " (ưu tiên " + ticket.priority
                + ", phòng ban " + ticket.ownerDepartment + ", IP " + ticket.sourceIp + ")");

        // 4. Phát sinh sự kiện Socket và thông báo tới các Client đang kết nối (F06)
        if (broadcaster != null) {
            String broadcastMsg = "Yêu cầu mới [" + newTicketId + "] '" + ticket.title + "' từ " + ticket.owner;
            broadcaster.sendToRole("admin", "TICKET_CREATED", newTicketId, broadcastMsg);
        }

        // 5. Thông báo kết quả thành công cho người gửi
        return Protocol.Packet.success(
                Protocol.CMD_CREATE_TICKET,
                "Đã tạo yêu cầu hỗ trợ thành công với mã: " + newTicketId,
                ticket
        );
    }

    private static void deleteStoredAttachment(String fileName) {
        if (fileName == null || fileName.isBlank()) return;
        try {
            FileTransferHelper.deleteStoredFile(fileName);
        } catch (java.io.IOException e) {
            System.err.println("[F02] Không thể dọn tệp đính kèm sau khi tạo ticket thất bại: "
                    + e.getMessage());
        }
    }
}
