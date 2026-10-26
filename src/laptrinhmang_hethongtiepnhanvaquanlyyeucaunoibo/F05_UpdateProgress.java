package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.io.Serializable;

/**
 * CHỨC NĂNG F05: CẬP NHẬT TRẠNG THÁI XỬ LÝ VÀ TIẾN ĐỘ
 * =========================================================================
 * - Xử lý chính: Server xác thực quyền kỹ thuật viên / Quản trị viên, kiểm tra
 *                tính hợp lệ của trạng thái mới, cập nhật cơ sở dữ liệu MySQL,
 *                lưu vết lịch sử xử lý và thông báo kết quả.
 * - Đầu vào / đầu ra: Số byte / % hoàn thành (Theo dõi tiến độ xử lý và số byte truyền nhận).
 * =========================================================================
 */
public class F05_UpdateProgress {

    /**
     * DTO cập nhật trạng thái và tiến độ xử lý
     */
    public static class UpdateProgressRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        public String ticketId;
        public String newStatus; // "Đang xử lý", "Hoàn thành", "Đã hủy"
        public int progressPercent; // 0 - 100%
        public String note;
        public String updatedBy;
        public String resultFileName; // Tên file tài liệu kỹ thuật bổ sung
        public byte[] resultFileBytes; // Số byte truyền tệp nghiệm thu/kết quả

        public UpdateProgressRequest(String ticketId, String newStatus, int progressPercent,
                                     String note, String updatedBy, String resultFileName, byte[] resultFileBytes) {
            this.ticketId = ticketId;
            this.newStatus = newStatus;
            this.progressPercent = progressPercent;
            this.note = note;
            this.updatedBy = updatedBy;
            this.resultFileName = resultFileName;
            this.resultFileBytes = resultFileBytes;
        }
    }

    /**
     * DTO kết quả phản hồi cập nhật tiến độ
     */
    public static class UpdateProgressResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        public String ticketId;
        public String status;
        public int progressPercent;
        public long transferredBytes;
        public String message;

        public UpdateProgressResponse(String ticketId, String status, int progressPercent, long transferredBytes, String message) {
            this.ticketId = ticketId;
            this.status = status;
            this.progressPercent = progressPercent;
            this.transferredBytes = transferredBytes;
            this.message = message;
        }
    }

    // =========================================================================
    // XỬ LÝ PHÍA SERVER (Server-Side Logic)
    // =========================================================================
    public static Protocol.Packet handleServerUpdateProgress(Protocol.Packet requestPacket, Models.User user,
                                                              F06_NotificationBroadcaster broadcaster) {
        if (user == null || !("admin".equalsIgnoreCase(user.role) || "tech".equalsIgnoreCase(user.role))) {
            return Protocol.Packet.error(Protocol.CMD_UPDATE_STATUS, "Chỉ Kỹ thuật viên hoặc Quản trị viên mới được cập nhật tiến độ.");
        }
        if (!(requestPacket.data instanceof UpdateProgressRequest req)) {
            return Protocol.Packet.error(Protocol.CMD_UPDATE_STATUS, "Dữ liệu cập nhật tiến độ không đúng định dạng.");
        }

        // 1. Kiểm tra đầu vào % hoàn thành (0 - 100%)
        if (req.progressPercent < 0 || req.progressPercent > 100) {
            return Protocol.Packet.error(Protocol.CMD_UPDATE_STATUS, "Tiến độ phải nằm trong khoảng từ 0% đến 100%.");
        }

        DatabaseManager db = DatabaseManager.getInstance();
        Models.Ticket t = db.getTicketById(req.ticketId);
        if (t == null) {
            return Protocol.Packet.error(Protocol.CMD_UPDATE_STATUS, "Không tìm thấy yêu cầu: " + req.ticketId);
        }

        if ("tech".equalsIgnoreCase(user.role) && !user.username.equalsIgnoreCase(t.assignedTech)) {
            return Protocol.Packet.error(Protocol.CMD_UPDATE_STATUS, "Chỉ kỹ thuật viên được phân công mới được cập nhật yêu cầu này.");
        }
        if (req.resultFileBytes != null && req.resultFileBytes.length > 0
                && (req.resultFileName == null || req.resultFileName.trim().isEmpty()
                    || req.resultFileName.contains("/") || req.resultFileName.contains("\\")
                    || req.resultFileName.equals(".") || req.resultFileName.equals(".."))) {
            return Protocol.Packet.error(Protocol.CMD_UPDATE_STATUS, "Tên tệp kết quả không hợp lệ.");
        }

        long bytesReceived = 0;
        // 3. Xử lý tệp đính kèm kết quả nếu có (Ghi nhận số byte)
        if (req.resultFileBytes != null && req.resultFileBytes.length > 0 && req.resultFileName != null) {
            bytesReceived = req.resultFileBytes.length;
            try {
                java.io.File dest = new java.io.File(FileTransferHelper.SERVER_STORAGE_DIR, req.resultFileName);
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(dest)) {
                    fos.write(req.resultFileBytes);
                }
                db.updateTicketAttachment(req.ticketId, req.resultFileName);
            } catch (Exception e) {
                System.err.println("[F05] Lỗi lưu file đính kèm: " + e.getMessage());
            }
        }

        // 4. Tự động chuẩn hóa trạng thái nếu tiến độ đạt 100%
        String effectiveStatus = req.newStatus;
        if (req.progressPercent == 100 && !"Đã hủy".equalsIgnoreCase(effectiveStatus)) {
            effectiveStatus = "Hoàn thành";
        }

        // 5. Cập nhật vào CSDL MySQL / Datastore và lưu lịch sử
        boolean ok = db.updateTicketStatus(req.ticketId, effectiveStatus, req.progressPercent, req.note, user.username);
        if (!ok) {
            return Protocol.Packet.error(Protocol.CMD_UPDATE_STATUS, "Lỗi cập nhật CSDL MySQL.");
        }
        db.recordAuditEvent("TICKET_PROGRESS_UPDATED", req.ticketId, user.username,
                "Tiến độ " + req.progressPercent + "%; trạng thái " + effectiveStatus);

        // 6. Phát thông báo thay đổi trạng thái tới các Socket (F06)
        if (broadcaster != null) {
            String msg = "Yêu cầu [" + req.ticketId + "] cập nhật tiến độ: " + req.progressPercent + "% (" + effectiveStatus + ")";
            broadcaster.broadcast("STATUS_CHANGED", req.ticketId, msg);
        }

        UpdateProgressResponse resp = new UpdateProgressResponse(
                req.ticketId,
                effectiveStatus,
                req.progressPercent,
                bytesReceived,
                "Cập nhật trạng thái thành công (" + req.progressPercent + "% - " + effectiveStatus + ")"
        );

        return Protocol.Packet.success(Protocol.CMD_UPDATE_STATUS, resp.message, resp);
    }
}
