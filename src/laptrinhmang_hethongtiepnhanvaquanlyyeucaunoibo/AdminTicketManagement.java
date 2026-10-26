package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.io.Serializable;

/** Admin-only editing and soft deletion for support tickets. */
public final class AdminTicketManagement {
    private AdminTicketManagement() {}

    public static class UpdateRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        public String ticketId, title, category, priority, detail;

        public UpdateRequest(String ticketId, String title, String category, String priority, String detail) {
            this.ticketId = ticketId;
            this.title = title;
            this.category = category;
            this.priority = priority;
            this.detail = detail;
        }
    }

    public static class DeleteRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        public String ticketId;

        public DeleteRequest(String ticketId) {
            this.ticketId = ticketId;
        }
    }

    public static Protocol.Packet update(Protocol.Packet packet, Models.User user,
                                         F06_NotificationBroadcaster broadcaster) {
        if (!isAdmin(user)) return Protocol.Packet.error(Protocol.CMD_UPDATE_TICKET, "Chỉ admin mới được sửa yêu cầu.");
        if (!(packet.data instanceof UpdateRequest request) || isBlank(request.ticketId)
                || isBlank(request.title) || isBlank(request.category) || isBlank(request.priority)
                || isBlank(request.detail) || request.title.length() > 255
                || request.category.length() > 50 || request.detail.length() > 20_000
                || !java.util.Set.of("Thấp", "Bình thường", "Cao", "Khẩn cấp")
                        .contains(request.priority)) {
            return Protocol.Packet.error(Protocol.CMD_UPDATE_TICKET, "Thông tin yêu cầu cập nhật không hợp lệ.");
        }
        Models.Ticket updated = new Models.Ticket(request.ticketId, request.title.trim(), request.category.trim(),
                request.priority.trim(), "", "", "", "", request.detail.trim(), 0, "");
        if (!DatabaseManager.getInstance().updateTicketForAdmin(updated, user.username)) {
            return Protocol.Packet.error(Protocol.CMD_UPDATE_TICKET, "Không thể cập nhật yêu cầu.");
        }
        if (broadcaster != null) {
            broadcaster.broadcast("TICKET_UPDATED", request.ticketId,
                    "Admin đã cập nhật thông tin yêu cầu [" + request.ticketId + "].");
        }
        return Protocol.Packet.success(Protocol.CMD_UPDATE_TICKET, "Đã cập nhật yêu cầu.", null);
    }

    public static Protocol.Packet softDelete(Protocol.Packet packet, Models.User user,
                                             F06_NotificationBroadcaster broadcaster) {
        if (!isAdmin(user)) return Protocol.Packet.error(Protocol.CMD_DELETE_TICKET, "Chỉ admin mới được xóa yêu cầu.");
        if (!(packet.data instanceof DeleteRequest request) || isBlank(request.ticketId)) {
            return Protocol.Packet.error(Protocol.CMD_DELETE_TICKET, "Mã yêu cầu cần xóa không hợp lệ.");
        }
        if (!DatabaseManager.getInstance().softDeleteTicket(request.ticketId, user.username)) {
            return Protocol.Packet.error(Protocol.CMD_DELETE_TICKET, "Không tìm thấy yêu cầu hoặc không thể xóa.");
        }
        if (broadcaster != null) {
            broadcaster.broadcast("TICKET_DELETED", request.ticketId,
                    "Admin đã xóa yêu cầu [" + request.ticketId + "].");
        }
        return Protocol.Packet.success(Protocol.CMD_DELETE_TICKET, "Đã xóa yêu cầu (xóa mềm; lịch sử vẫn được giữ).", null);
    }

    private static boolean isAdmin(Models.User user) {
        return user != null && "admin".equalsIgnoreCase(user.role);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
