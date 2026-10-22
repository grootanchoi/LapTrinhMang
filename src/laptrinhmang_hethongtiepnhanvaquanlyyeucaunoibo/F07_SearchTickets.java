package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.io.Serializable;
import java.util.List;

/** Tìm kiếm và lọc yêu cầu trên server bằng truy vấn SQL có điều kiện. */
public class F07_SearchTickets {
    public static class SearchRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        public String keyword, status, category, priority;
        public SearchRequest(String keyword, String status, String category, String priority) {
            this.keyword = keyword; this.status = status; this.category = category; this.priority = priority;
        }
    }

    public static Protocol.Packet handle(SearchRequest req, Models.User user) {
        if (req == null || user == null) return Protocol.Packet.error(Protocol.CMD_SEARCH_TICKETS, "Yêu cầu tìm kiếm không hợp lệ.");
        List<Models.Ticket> result = DatabaseManager.getInstance().searchTickets(
                user.username, user.role, req.keyword, req.status, req.category, req.priority);
        return Protocol.Packet.success(Protocol.CMD_SEARCH_TICKETS, "Tìm thấy " + result.size() + " yêu cầu.", result);
    }
}
