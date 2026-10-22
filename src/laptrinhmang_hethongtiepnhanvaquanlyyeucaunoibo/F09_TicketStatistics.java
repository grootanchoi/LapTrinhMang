package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.util.List;

/** Tổng hợp yêu cầu theo trạng thái và kỹ thuật viên phụ trách. */
public class F09_TicketStatistics {
    public static class StatisticsRequest implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        public String dateFrom, dateTo;
        public StatisticsRequest(String dateFrom, String dateTo) { this.dateFrom = dateFrom; this.dateTo = dateTo; }
    }
    public static class Statistics implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        public List<Models.TicketStatistic> byStatus, byTechnician;
        public Statistics(List<Models.TicketStatistic> byStatus, List<Models.TicketStatistic> byTechnician) {
            this.byStatus = byStatus; this.byTechnician = byTechnician;
        }
    }

    public static Protocol.Packet handle(StatisticsRequest request, Models.User user) {
        if (user == null) return Protocol.Packet.error(Protocol.CMD_GET_STATISTICS, "Bạn cần đăng nhập.");
        if (request == null) request = new StatisticsRequest("", "");
        Statistics stats = DatabaseManager.getInstance().getTicketStatistics(user.username, user.role, request.dateFrom, request.dateTo);
        return Protocol.Packet.success(Protocol.CMD_GET_STATISTICS, "Đã tổng hợp thống kê yêu cầu.", stats);
    }
}
