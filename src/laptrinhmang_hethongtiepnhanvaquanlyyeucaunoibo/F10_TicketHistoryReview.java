package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.io.Serializable;
import java.util.List;

/** Tra cứu lịch sử xử lý và lưu đánh giá của người gửi yêu cầu. */
public class F10_TicketHistoryReview {
    public static class ReviewRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        public String ticketId, comment;
        public int rating;
        public ReviewRequest(String ticketId, int rating, String comment) {
            this.ticketId = ticketId; this.rating = rating; this.comment = comment;
        }
    }

    public static Protocol.Packet history(String ticketId, Models.User user) {
        Models.Ticket ticket = DatabaseManager.getInstance().getTicketById(ticketId);
        if (ticket == null || !canView(ticket, user)) return Protocol.Packet.error(Protocol.CMD_GET_HISTORY, "Không tìm thấy yêu cầu hoặc bạn không có quyền xem lịch sử.");
        List<Models.TicketHistory> rows = DatabaseManager.getInstance().getTicketHistory(ticketId);
        Models.TicketReview review = DatabaseManager.getInstance().getTicketReview(ticketId);
        return Protocol.Packet.success(Protocol.CMD_GET_HISTORY, "Đã tải lịch sử xử lý.", new Object[]{rows, review});
    }

    public static Protocol.Packet review(ReviewRequest req, Models.User user) {
        if (req == null || user == null || req.ticketId == null || req.rating < 1 || req.rating > 5)
            return Protocol.Packet.error(Protocol.CMD_RATE_TICKET, "Điểm đánh giá phải từ 1 đến 5.");
        Models.Ticket ticket = DatabaseManager.getInstance().getTicketById(req.ticketId);
        if (ticket == null || !"requester".equalsIgnoreCase(user.role) || !user.username.equalsIgnoreCase(ticket.owner))
            return Protocol.Packet.error(Protocol.CMD_RATE_TICKET, "Chỉ người gửi yêu cầu mới được đánh giá yêu cầu của mình.");
        if (!"Hoàn thành".equalsIgnoreCase(ticket.status))
            return Protocol.Packet.error(Protocol.CMD_RATE_TICKET, "Chỉ có thể đánh giá yêu cầu đã hoàn thành.");
        boolean ok = DatabaseManager.getInstance().saveTicketReview(req.ticketId, user.username, req.rating, req.comment);
        if (ok) DatabaseManager.getInstance().recordAuditEvent("TICKET_REVIEWED", req.ticketId,
                user.username, "Đánh giá " + req.rating + "/5.");
        return ok ? Protocol.Packet.success(Protocol.CMD_RATE_TICKET, "Đã lưu đánh giá.", null)
                : Protocol.Packet.error(Protocol.CMD_RATE_TICKET, "Không thể lưu đánh giá vào cơ sở dữ liệu.");
    }

    private static boolean canView(Models.Ticket t, Models.User u) {
        return u != null && ("admin".equalsIgnoreCase(u.role)
                || ("requester".equalsIgnoreCase(u.role) && u.username.equalsIgnoreCase(t.owner))
                || ("tech".equalsIgnoreCase(u.role) && u.username.equalsIgnoreCase(t.assignedTech)));
    }
}
