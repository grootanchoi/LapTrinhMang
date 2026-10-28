package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * CHỨC NĂNG F03: XEM DANH SÁCH VÀ CHI TIẾT YÊU CẦU
 * =========================================================================
 * - Xử lý chính: Server kiểm tra quyền truy cập, truy vấn CSDL theo tài khoản
 *                (Requester xem của mình, Tech xem việc phân công, Admin xem tất cả),
 *                trả danh sách hoặc chi tiết yêu cầu cho Client.
 * - Đầu vào / đầu ra: File / Kết quả lưu file (Xuất báo cáo danh sách yêu cầu ra file).
 * =========================================================================
 */
public class F03_ViewTickets {

    /**
     * DTO yêu cầu lấy danh sách vé từ Client
     */
    public static class ViewTicketsRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        public String username;
        public String role;

        public ViewTicketsRequest(String username, String role) {
            this.username = username;
            this.role = role;
        }
    }

    // =========================================================================
    // XỬ LÝ PHÍA SERVER (Server-Side Logic)
    // =========================================================================
    public static Protocol.Packet handleServerGetTickets(Protocol.Packet requestPacket, Models.User user) {
        if (user == null) {
            return Protocol.Packet.error(Protocol.CMD_GET_TICKETS, "Bạn cần đăng nhập.");
        }
        if (!(requestPacket.data instanceof ViewTicketsRequest)) {
            return Protocol.Packet.error(Protocol.CMD_GET_TICKETS, "Dữ liệu yêu cầu không hợp lệ.");
        }

        DatabaseManager db = DatabaseManager.getInstance();
        List<Models.Ticket> list = db.getTicketsForUser(user.username, user.role);

        return Protocol.Packet.success(
                Protocol.CMD_GET_TICKETS,
                "Lấy danh sách thành công (" + list.size() + " yêu cầu)",
                new ArrayList<>(list)
        );
    }

    public static Protocol.Packet handleServerGetTicketDetail(Protocol.Packet requestPacket, Models.User user) {
        if (user == null) {
            return Protocol.Packet.error(Protocol.CMD_GET_TICKET_DETAIL, "Bạn cần đăng nhập.");
        }
        if (!(requestPacket.data instanceof String ticketId)) {
            return Protocol.Packet.error(Protocol.CMD_GET_TICKET_DETAIL, "Mã yêu cầu không hợp lệ.");
        }

        DatabaseManager db = DatabaseManager.getInstance();
        Models.Ticket t = db.getTicketById(ticketId);
        if (t == null) {
            return Protocol.Packet.error(Protocol.CMD_GET_TICKET_DETAIL, "Không tìm thấy yêu cầu có mã: " + ticketId);
        }
        boolean allowed = "admin".equalsIgnoreCase(user.role)
                || ("requester".equalsIgnoreCase(user.role) && user.username.equalsIgnoreCase(t.owner))
                || ("tech".equalsIgnoreCase(user.role) && user.username.equalsIgnoreCase(t.assignedTech));
        if (!allowed) {
            return Protocol.Packet.error(Protocol.CMD_GET_TICKET_DETAIL, "Bạn không có quyền xem yêu cầu này.");
        }

        return Protocol.Packet.success(Protocol.CMD_GET_TICKET_DETAIL, "Tìm thấy chi tiết yêu cầu", t);
    }

    // =========================================================================
    // XỬ LÝ ĐẦU RA FILE: XUẤT VÀ LƯU FILE BÁO CÁO (File / Kết quả lưu file)
    // =========================================================================
    public static class FileExportResult {
        public boolean success;
        public String filePath;
        public long fileSize;
        public String message;

        public FileExportResult(boolean success, String filePath, long fileSize, String message) {
            this.success = success;
            this.filePath = filePath;
            this.fileSize = fileSize;
            this.message = message;
        }
    }

    /**
     * Xuất danh sách yêu cầu ra file trên đĩa (CSV hoặc TXT báo cáo)
     */
    public static FileExportResult exportTicketsToFile(List<Models.Ticket> tickets, File targetFile) {
        if (targetFile == null) {
            return new FileExportResult(false, null, 0, "Đường dẫn file lưu không hợp lệ.");
        }

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(targetFile), StandardCharsets.UTF_8))) {
            // Ghi UTF-8 BOM để Excel và Notepad hiển thị tiếng Việt không bị lỗi font
            writer.write('\ufeff');

            // Header báo cáo
            writer.write("====================================================================================================\r\n");
            writer.write("                     BÁO CÁO DANH SÁCH YÊU CẦU HỖ TRỢ KỸ THUẬT NỘI BỘ\r\n");
            writer.write("                     Ngày xuất: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\r\n");
            writer.write("====================================================================================================\r\n\r\n");

            // Cột dữ liệu
            writer.write(String.format("%-15s | %-30s | %-12s | %-12s | %-15s | %-15s | %-6s\r\n",
                    "Mã Yêu Cầu", "Tiêu Đề", "Danh Mục", "Mức Ưu Tiên", "Người Gửi", "Kỹ Thuật Viên", "Tiến Độ"));
            writer.write("----------------------------------------------------------------------------------------------------\r\n");

            for (Models.Ticket t : tickets) {
                String safeTitle = t.title.length() > 28 ? t.title.substring(0, 25) + "..." : t.title;
                writer.write(String.format("%-15s | %-30s | %-12s | %-12s | %-15s | %-15s | %4d%%\r\n",
                        t.id, safeTitle, t.category, t.priority, t.owner, t.assignedTech, t.progress));
            }

            writer.write("====================================================================================================\r\n");
            writer.write("Tổng số yêu cầu: " + tickets.size() + "\r\n");
            writer.flush();

            return new FileExportResult(true, targetFile.getAbsolutePath(), targetFile.length(),
                    "Đã lưu danh sách " + tickets.size() + " yêu cầu vào file thành công!");
        } catch (Exception e) {
            return new FileExportResult(false, targetFile.getAbsolutePath(), 0, "Lỗi khi lưu file: " + e.getMessage());
        }
    }
}
