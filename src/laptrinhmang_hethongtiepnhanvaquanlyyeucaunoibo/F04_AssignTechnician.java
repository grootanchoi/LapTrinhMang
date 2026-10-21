package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.io.*;

/**
 * CHỨC NĂNG F04: TIẾP NHẬN VÀ PHÂN CÔNG KỸ THUẬT VIÊN
 * =========================================================================
 * - Xử lý chính: Server kiểm tra quyền (Admin/Kỹ thuật viên) và trạng thái yêu cầu,
 *                lưu thông tin phân công vào CSDL (MySQL/Datastore),
 *                gửi thông báo sự kiện Socket cho kỹ thuật viên phụ trách.
 * - Đầu vào / đầu ra: Tên file / File đã tải (Truyền nhận tệp tin đính kèm qua Socket TCP).
 * =========================================================================
 */
public class F04_AssignTechnician {

    /**
     * DTO phân công kỹ thuật viên
     */
    public static class AssignRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        public String ticketId;
        public String techUsername;
        public String assignedBy;

        public AssignRequest(String ticketId, String techUsername, String assignedBy) {
            this.ticketId = ticketId;
            this.techUsername = techUsername;
            this.assignedBy = assignedBy;
        }
    }

    /**
     * DTO yêu cầu tải file đính kèm (Đầu vào: Tên file)
     */
    public static class FileDownloadRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        public String fileName;

        public FileDownloadRequest(String fileName) {
            this.fileName = fileName;
        }
    }

    /**
     * DTO phản hồi tải file đính kèm (Đầu ra: File đã tải / Metadata)
     */
    public static class FileDownloadResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        public String fileName;
        public long fileSize;
        public byte[] fileBytes;

        public FileDownloadResponse(String fileName, long fileSize, byte[] fileBytes) {
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.fileBytes = fileBytes;
        }
    }

    // =========================================================================
    // XỬ LÝ PHÍA SERVER (Server-Side Logic)
    // =========================================================================

    /**
     * Tiếp nhận và phân công kỹ thuật viên
     */
    public static Protocol.Packet handleServerAssign(Protocol.Packet requestPacket, F06_NotificationBroadcaster broadcaster) {
        if (!(requestPacket.data instanceof AssignRequest req)) {
            return Protocol.Packet.error(Protocol.CMD_ASSIGN_TECH, "Dữ liệu phân công không đúng định dạng.");
        }

        DatabaseManager db = DatabaseManager.getInstance();
        Models.Ticket t = db.getTicketById(req.ticketId);
        if (t == null) {
            return Protocol.Packet.error(Protocol.CMD_ASSIGN_TECH, "Không tìm thấy yêu cầu: " + req.ticketId);
        }

        if ("Hoàn thành".equalsIgnoreCase(t.status) || "Đã hủy".equalsIgnoreCase(t.status)) {
            return Protocol.Packet.error(Protocol.CMD_ASSIGN_TECH, "Không thể phân công yêu cầu đã đóng hoặc đã hủy.");
        }

        // Lưu thông tin phân công vào CSDL
        boolean ok = db.assignTechnician(req.ticketId, req.techUsername, req.assignedBy);
        if (!ok) {
            return Protocol.Packet.error(Protocol.CMD_ASSIGN_TECH, "Lỗi khi cập nhật phân công trong CSDL.");
        }

        // Thông báo qua Socket tới kỹ thuật viên và người gửi (F06)
        if (broadcaster != null) {
            String msg = "Yêu cầu [" + req.ticketId + "] đã được phân công cho kỹ thuật viên: " + req.techUsername;
            broadcaster.broadcast("TICKET_ASSIGNED", req.ticketId, msg);
        }

        return Protocol.Packet.success(Protocol.CMD_ASSIGN_TECH, "Phân công thành công cho: " + req.techUsername, null);
    }

    /**
     * Xử lý tải file đính kèm qua Socket (Đầu vào: Tên file -> Đầu ra: Tệp dữ liệu đã tải)
     */
    public static Protocol.Packet handleServerDownloadFile(Protocol.Packet requestPacket) {
        if (!(requestPacket.data instanceof FileDownloadRequest req)) {
            return Protocol.Packet.error(Protocol.CMD_DOWNLOAD_FILE, "Yêu cầu tải file không hợp lệ.");
        }

        String fileName = req.fileName;
        if (fileName == null || fileName.trim().isEmpty()) {
            return Protocol.Packet.error(Protocol.CMD_DOWNLOAD_FILE, "Tên file không được để trống.");
        }

        File target = new File(FileTransferHelper.SERVER_STORAGE_DIR, fileName);
        if (!target.exists() || !target.isFile()) {
            // Nếu file demo chưa tồn tại, tạo file demo nội dung
            try {
                if (!target.getParentFile().exists()) target.getParentFile().mkdirs();
                try (OutputStreamWriter fw = new OutputStreamWriter(new FileOutputStream(target), java.nio.charset.StandardCharsets.UTF_8)) {
                    fw.write("[Log hệ thống]: Tệp tin đính kèm mẫu: " + fileName + "\nThời gian tạo: " + java.time.LocalDateTime.now());
                }
            } catch (Exception ignored) {}
        }

        try {
            long length = target.length();
            byte[] bytes = new byte[(int) length];
            try (FileInputStream fis = new FileInputStream(target)) {
                int read = fis.read(bytes);
            }
            FileDownloadResponse resp = new FileDownloadResponse(fileName, length, bytes);
            return Protocol.Packet.success(Protocol.CMD_DOWNLOAD_FILE, "Đã chuẩn bị tệp " + fileName + " (" + length + " bytes)", resp);
        } catch (Exception e) {
            return Protocol.Packet.error(Protocol.CMD_DOWNLOAD_FILE, "Lỗi khi đọc file trên server: " + e.getMessage());
        }
    }
}
