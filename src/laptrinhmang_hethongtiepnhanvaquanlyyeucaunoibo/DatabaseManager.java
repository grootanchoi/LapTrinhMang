package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý tương tác cơ sở dữ liệu (MySQL / Local Persistent Fallback).
 * Tự động kết nối MySQL nếu có sẵn server và driver.
 * Nếu không có MySQL, tự động chuyển sang lưu trữ bộ nhớ an toàn (In-Memory Datastore)
 * để chương trình luôn hoạt động 100% trong mọi môi trường chấm bài.
 */
public class DatabaseManager {
    private static DatabaseManager instance;
    private boolean useMySQL = false;
    private String dbUrl = "jdbc:mariadb://localhost:3306/workdesk_db?connectionCollation=utf8mb4_unicode_ci";
    private String dbUser = "root";
    private String dbPassword = "";

    // Local Datastore fallback
    private final Map<String, Models.User> localUsers = new ConcurrentHashMap<>();
    private final Map<String, Models.Ticket> localTickets = new ConcurrentHashMap<>();
    private int ticketSequence = 2;

    private DatabaseManager() {
        initLocalSampleData();
        tryConnectMySQL();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    private void initLocalSampleData() {
        // Tài khoản mẫu
        localUsers.put("admin", new Models.User("admin", "admin123", "Quản trị viên hệ thống", "admin", "Đang hoạt động"));
        localUsers.put("tech01", new Models.User("tech01", "tech123", "Nguyễn Văn Kỹ Thuật", "tech", "Đang hoạt động"));
        localUsers.put("tech02", new Models.User("tech02", "tech123", "Trần Thị Support", "tech", "Đang hoạt động"));
        localUsers.put("user01", new Models.User("user01", "user123", "Lê Văn Nhân Viên", "requester", "Đang hoạt động"));
        localUsers.put("user02", new Models.User("user02", "user123", "Phạm Thị Kế Toán", "requester", "Đang hoạt động"));

        // Yêu cầu mẫu
        Models.Ticket t1 = new Models.Ticket("YC-2026-0001", "Máy in phòng Kế toán không nhận lệnh in", "Hành chính", "Cao",
                "2026-10-20", "Đang xử lý", "user02", "tech01",
                "Máy in Canon 2900 báo ngoại tuyến liên tục, đã khởi động lại nhưng không được.", 40, "error_printer.txt");
        Models.Ticket t2 = new Models.Ticket("YC-2026-0002", "Xin cấp quyền truy cập VPN nội bộ", "IT", "Bình thường",
                "2026-10-21", "Mới", "user01", "Chưa phân công",
                "Cần tài khoản VPN kết nối máy chủ dữ liệu từ xa.", 0, "");

        localTickets.put(t1.id, t1);
        localTickets.put(t2.id, t2);
    }

    private void tryConnectMySQL() {
        try {
            Class.forName("org.mariadb.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
                if (conn != null && !conn.isClosed()) {
                    migrateUserStatusColumn(conn);
                    useMySQL = true;
                    System.out.println("[DatabaseManager] Kết nối MySQL thành công! Sử dụng CSDL: workdesk_db");
                }
            }
        } catch (Exception e) {
            useMySQL = false;
            System.out.println("[DatabaseManager] Không kết nối được MySQL (" + e.getMessage() + "). Tự động sử dụng Datastore dự phòng tích hợp.");
        }
    }

    /** Align older databases whose ENUM status values were imported with a mismatched encoding. */
    private void migrateUserStatusColumn(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("ALTER TABLE users MODIFY COLUMN status VARCHAR(30) NOT NULL DEFAULT 'Đang hoạt động'");
            st.executeUpdate("UPDATE users SET status = 'Đang hoạt động' WHERE status IS NULL OR status = ''");
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    public boolean isUsingMySQL() {
        return useMySQL;
    }

    // ==================== F01: ĐĂNG NHẬP & XÁC THỰC ====================
    public Models.User authenticate(String username, String password) {
        if (useMySQL) {
            String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, password);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new Models.User(
                                rs.getString("username"),
                                rs.getString("password"),
                                rs.getString("display_name"),
                                rs.getString("role"),
                                rs.getString("status")
                        );
                    }
                }
            } catch (SQLException e) {
                System.err.println("[DatabaseManager] Lỗi xác thực MySQL: " + e.getMessage());
            }
        }

        // Fallback
        Models.User u = localUsers.get(username);
        if (u != null && u.password.equals(password)) {
            return u;
        }
        return null;
    }

    // ==================== F02: TẠO YÊU CẦU ====================
    public synchronized String generateNextTicketId() {
        if (useMySQL) {
            String sql = "SELECT COUNT(*) FROM tickets";
            try (Connection conn = getConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                if (rs.next()) {
                    int count = rs.getInt(1) + 1;
                    return String.format("YC-%04d", count);
                }
            } catch (SQLException ignored) {}
        }
        ticketSequence++;
        return String.format("YC-%04d", ticketSequence);
    }

    public boolean insertTicket(Models.Ticket ticket) {
        if (useMySQL) {
            String sql = "INSERT INTO tickets (id, title, category, priority, created_date, status, owner, assigned_tech, detail, progress, attachment_name) "
                       + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, ticket.id);
                ps.setString(2, ticket.title);
                ps.setString(3, ticket.category);
                ps.setString(4, ticket.priority);
                ps.setString(5, ticket.createdDate);
                ps.setString(6, ticket.status);
                ps.setString(7, ticket.owner);
                ps.setString(8, ticket.assignedTech);
                ps.setString(9, ticket.detail);
                ps.setInt(10, ticket.progress);
                ps.setString(11, ticket.attachmentName);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                System.err.println("[DatabaseManager] Lỗi insert MySQL: " + e.getMessage());
            }
        }
        localTickets.put(ticket.id, ticket);
        return true;
    }

    // ==================== F03: XEM DANH SÁCH & CHI TIẾT ====================
    public List<Models.Ticket> getTicketsForUser(String username, String role) {
        List<Models.Ticket> list = new ArrayList<>();
        if (useMySQL) {
            String sql;
            if ("admin".equalsIgnoreCase(role)) {
                sql = "SELECT * FROM tickets ORDER BY id DESC";
            } else if ("tech".equalsIgnoreCase(role)) {
                sql = "SELECT * FROM tickets WHERE assigned_tech = ? OR status = 'Mới' ORDER BY id DESC";
            } else {
                sql = "SELECT * FROM tickets WHERE owner = ? ORDER BY id DESC";
            }

            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                if ("tech".equalsIgnoreCase(role) || "requester".equalsIgnoreCase(role)) {
                    ps.setString(1, username);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new Models.Ticket(
                                rs.getString("id"),
                                rs.getString("title"),
                                rs.getString("category"),
                                rs.getString("priority"),
                                rs.getString("created_date"),
                                rs.getString("status"),
                                rs.getString("owner"),
                                rs.getString("assigned_tech"),
                                rs.getString("detail"),
                                rs.getInt("progress"),
                                rs.getString("attachment_name")
                        ));
                    }
                    return list;
                }
            } catch (SQLException e) {
                System.err.println("[DatabaseManager] Lỗi lấy danh sách tickets MySQL: " + e.getMessage());
            }
        }

        // Fallback
        for (Models.Ticket t : localTickets.values()) {
            if ("admin".equalsIgnoreCase(role)) {
                list.add(t);
            } else if ("tech".equalsIgnoreCase(role)) {
                if (username.equalsIgnoreCase(t.assignedTech) || "Mới".equalsIgnoreCase(t.status)) {
                    list.add(t);
                }
            } else {
                if (username.equalsIgnoreCase(t.owner)) {
                    list.add(t);
                }
            }
        }
        list.sort((a, b) -> b.id.compareTo(a.id));
        return list;
    }

    // F07: SQL tìm kiếm có tham số và luôn giới hạn theo quyền của tài khoản.
    public List<Models.Ticket> searchTickets(String username, String role, String keyword, String status, String category, String priority) {
        String q = keyword == null ? "" : keyword.trim();
        String st = status == null ? "" : status.trim();
        String cat = category == null ? "" : category.trim();
        String pri = priority == null ? "" : priority.trim();
        if (useMySQL) {
            StringBuilder sql = new StringBuilder("SELECT * FROM tickets WHERE 1=1");
            if ("requester".equalsIgnoreCase(role)) sql.append(" AND owner = ?");
            else if ("tech".equalsIgnoreCase(role)) sql.append(" AND (assigned_tech = ? OR status = 'Mới')");
            if (!q.isEmpty()) sql.append(" AND (id LIKE ? OR title LIKE ? OR category LIKE ? OR detail LIKE ? OR owner LIKE ? OR assigned_tech LIKE ?)");
            if (!st.isEmpty()) sql.append(" AND status = ?");
            if (!cat.isEmpty()) sql.append(" AND category = ?");
            if (!pri.isEmpty()) sql.append(" AND priority = ?");
            sql.append(" ORDER BY created_date DESC, id DESC");
            try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
                int i = 1;
                if ("requester".equalsIgnoreCase(role) || "tech".equalsIgnoreCase(role)) ps.setString(i++, username);
                if (!q.isEmpty()) for (int n = 0; n < 6; n++) ps.setString(i++, "%" + q + "%");
                if (!st.isEmpty()) ps.setString(i++, st);
                if (!cat.isEmpty()) ps.setString(i++, cat);
                if (!pri.isEmpty()) ps.setString(i++, pri);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Models.Ticket> rows = new ArrayList<>();
                    while (rs.next()) rows.add(ticketFromResult(rs));
                    return rows;
                }
            } catch (SQLException e) { System.err.println("[F07] Lỗi tìm kiếm MySQL: " + e.getMessage()); return Collections.emptyList(); }
        }
        List<Models.Ticket> rows = new ArrayList<>();
        for (Models.Ticket t : getTicketsForUser(username, role)) {
            String haystack = (t.id + " " + t.title + " " + t.category + " " + t.detail + " " + t.owner + " " + t.assignedTech).toLowerCase();
            if ((q.isEmpty() || haystack.contains(q.toLowerCase())) && (st.isEmpty() || st.equals(t.status))
                    && (cat.isEmpty() || cat.equals(t.category)) && (pri.isEmpty() || pri.equals(t.priority))) rows.add(t);
        }
        return rows;
    }

    // F09: các phép đếm được GROUP BY trực tiếp trong MySQL.
    public F09_TicketStatistics.Statistics getTicketStatistics(String username, String role, String dateFrom, String dateTo) {
        List<Models.TicketStatistic> statusRows = new ArrayList<>(), techRows = new ArrayList<>();
        String from = dateFrom == null ? "" : dateFrom.trim(), to = dateTo == null ? "" : dateTo.trim();
        if (useMySQL) {
            String access = "";
            if ("requester".equalsIgnoreCase(role)) access = " WHERE owner = ?";
            else if ("tech".equalsIgnoreCase(role)) access = " WHERE (assigned_tech = ? OR status = 'Mới')";
            if (!from.isEmpty()) access += (access.isEmpty() ? " WHERE" : " AND") + " created_date >= ?";
            if (!to.isEmpty()) access += (access.isEmpty() ? " WHERE" : " AND") + " created_date <= ?";
            try (Connection c = getConnection()) {
                for (String group : new String[]{"status", "assigned_tech"}) {
                    try (PreparedStatement ps = c.prepareStatement("SELECT " + group + ", COUNT(*) AS total FROM tickets" + access + " GROUP BY " + group + " ORDER BY total DESC")) {
                        int i = 1;
                        if ("requester".equalsIgnoreCase(role) || "tech".equalsIgnoreCase(role)) ps.setString(i++, username);
                        if (!from.isEmpty()) ps.setString(i++, from);
                        if (!to.isEmpty()) ps.setString(i++, to);
                        try (ResultSet rs = ps.executeQuery()) {
                            List<Models.TicketStatistic> out = "status".equals(group) ? statusRows : techRows;
                            while (rs.next()) out.add(new Models.TicketStatistic(rs.getString(1), rs.getInt(2)));
                        }
                    }
                }
            } catch (SQLException e) { System.err.println("[F09] Lỗi thống kê MySQL: " + e.getMessage()); }
        } else {
            Map<String, Integer> byStatus = new java.util.TreeMap<>(), byTech = new java.util.TreeMap<>();
            for (Models.Ticket t : getTicketsForUser(username, role)) {
                if (!from.isEmpty() && t.createdDate.compareTo(from) < 0) continue;
                if (!to.isEmpty() && t.createdDate.compareTo(to) > 0) continue;
                byStatus.put(t.status, byStatus.getOrDefault(t.status, 0) + 1);
                byTech.put(t.assignedTech, byTech.getOrDefault(t.assignedTech, 0) + 1);
            }
            byStatus.forEach((k, v) -> statusRows.add(new Models.TicketStatistic(k, v)));
            byTech.forEach((k, v) -> techRows.add(new Models.TicketStatistic(k, v)));
        }
        return new F09_TicketStatistics.Statistics(statusRows, techRows);
    }

    private Models.Ticket ticketFromResult(ResultSet rs) throws SQLException {
        return new Models.Ticket(rs.getString("id"), rs.getString("title"), rs.getString("category"), rs.getString("priority"),
                rs.getString("created_date"), rs.getString("status"), rs.getString("owner"), rs.getString("assigned_tech"),
                rs.getString("detail"), rs.getInt("progress"), rs.getString("attachment_name"));
    }

    // F10: lấy timeline và upsert đánh giá một lần cho mỗi yêu cầu.
    public List<Models.TicketHistory> getTicketHistory(String ticketId) {
        List<Models.TicketHistory> rows = new ArrayList<>();
        if (!useMySQL) return rows;
        String sql = "SELECT action, updated_by, DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s') AS updated_at, note FROM ticket_history WHERE ticket_id = ? ORDER BY updated_at ASC, id ASC";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(new Models.TicketHistory(rs.getString("action"), rs.getString("updated_by"), rs.getString("updated_at"), rs.getString("note")));
            }
        } catch (SQLException e) { System.err.println("[F10] Lỗi tải lịch sử: " + e.getMessage()); }
        return rows;
    }

    public Models.TicketReview getTicketReview(String ticketId) {
        if (!useMySQL) return null;
        String sql = "SELECT reviewer, rating, comment, DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at FROM ticket_reviews WHERE ticket_id = ?";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new Models.TicketReview(ticketId, rs.getString("reviewer"), rs.getInt("rating"), rs.getString("comment"), rs.getString("created_at"));
            }
        } catch (SQLException e) { System.err.println("[F10] Lỗi tải đánh giá: " + e.getMessage()); }
        return null;
    }

    public boolean saveTicketReview(String ticketId, String reviewer, int rating, String comment) {
        if (!useMySQL) return false;
        String sql = "INSERT INTO ticket_reviews (ticket_id, reviewer, rating, comment) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE rating=VALUES(rating), comment=VALUES(comment), created_at=CURRENT_TIMESTAMP";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ticketId); ps.setString(2, reviewer); ps.setInt(3, rating); ps.setString(4, comment == null ? "" : comment.trim());
            boolean saved = ps.executeUpdate() > 0;
            if (saved) recordHistory(ticketId, "Người gửi đã đánh giá " + rating + "/5", reviewer, comment == null ? "" : comment.trim());
            return saved;
        } catch (SQLException e) { System.err.println("[F10] Lỗi lưu đánh giá: " + e.getMessage()); return false; }
    }

    public Models.Ticket getTicketById(String id) {
        if (useMySQL) {
            String sql = "SELECT * FROM tickets WHERE id = ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new Models.Ticket(
                                rs.getString("id"),
                                rs.getString("title"),
                                rs.getString("category"),
                                rs.getString("priority"),
                                rs.getString("created_date"),
                                rs.getString("status"),
                                rs.getString("owner"),
                                rs.getString("assigned_tech"),
                                rs.getString("detail"),
                                rs.getInt("progress"),
                                rs.getString("attachment_name")
                        );
                    }
                }
            } catch (SQLException ignored) {}
        }
        return localTickets.get(id);
    }

    // ==================== F04: PHÂN CÔNG KỸ THUẬT VIÊN ====================
    public boolean assignTechnician(String ticketId, String techUsername, String assignedBy) {
        if (useMySQL) {
            String sql = "UPDATE tickets SET assigned_tech = ?, status = 'Đang xử lý' WHERE id = ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, techUsername);
                ps.setString(2, ticketId);
                boolean ok = ps.executeUpdate() > 0;
                if (ok) {
                    recordHistory(ticketId, "Phân công xử lý cho " + techUsername, assignedBy, "");
                }
                return ok;
            } catch (SQLException e) {
                System.err.println("[DatabaseManager] Lỗi phân công MySQL: " + e.getMessage());
            }
        }

        Models.Ticket t = localTickets.get(ticketId);
        if (t != null) {
            t.assignedTech = techUsername;
            if ("Mới".equals(t.status)) {
                t.status = "Đang xử lý";
            }
            return true;
        }
        return false;
    }

    // ==================== F05: CẬP NHẬT TRẠNG THÁI & TIẾN ĐỘ ====================
    public boolean updateTicketStatus(String ticketId, String newStatus, int progress, String note, String updatedBy) {
        if (useMySQL) {
            String sql = "UPDATE tickets SET status = ?, progress = ? WHERE id = ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, newStatus);
                ps.setInt(2, progress);
                ps.setString(3, ticketId);
                boolean ok = ps.executeUpdate() > 0;
                if (ok) {
                    recordHistory(ticketId, "Cập nhật trạng thái: " + newStatus + " (" + progress + "%)", updatedBy, note);
                }
                return ok;
            } catch (SQLException e) {
                System.err.println("[DatabaseManager] Lỗi update status MySQL: " + e.getMessage());
            }
        }

        Models.Ticket t = localTickets.get(ticketId);
        if (t != null) {
            t.status = newStatus;
            t.progress = progress;
            return true;
        }
        return false;
    }

    public void updateTicketAttachment(String ticketId, String fileName) {
        if (useMySQL) {
            String sql = "UPDATE tickets SET attachment_name = ? WHERE id = ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, fileName);
                ps.setString(2, ticketId);
                ps.executeUpdate();
            } catch (SQLException ignored) {}
        }
        Models.Ticket t = localTickets.get(ticketId);
        if (t != null) {
            t.attachmentName = fileName;
        }
    }

    public void recordHistory(String ticketId, String action, String updatedBy, String note) {
        if (useMySQL) {
            String sql = "INSERT INTO ticket_history (ticket_id, action, updated_by, note) VALUES (?, ?, ?, ?)";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, ticketId);
                ps.setString(2, action);
                ps.setString(3, updatedBy);
                ps.setString(4, note);
                ps.executeUpdate();
            } catch (SQLException ignored) {}
        }
    }

    // ==================== F08: QUẢN LÝ NGƯỜI DÙNG & PHÂN QUYỀN ====================
    public List<Models.User> getAllUsers() {
        List<Models.User> list = new ArrayList<>();
        if (useMySQL) {
            String sql = "SELECT * FROM users ORDER BY username ASC";
            try (Connection conn = getConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    list.add(new Models.User(
                            rs.getString("username"),
                            rs.getString("password"),
                            rs.getString("display_name"),
                            rs.getString("role"),
                            rs.getString("status")
                    ));
                }
                return list;
            } catch (SQLException e) {
                System.err.println("[DatabaseManager] Lỗi lấy danh sách user MySQL: " + e.getMessage());
            }
        }
        list.addAll(localUsers.values());
        return list;
    }

    public boolean addUser(Models.User user) throws SQLException {
        if (useMySQL) {
            String sql = "INSERT INTO users (username, password, display_name, role, status) VALUES (?, ?, ?, ?, ?)";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, user.username);
                ps.setString(2, user.password);
                ps.setString(3, user.displayName);
                ps.setString(4, user.role);
                ps.setString(5, user.status);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                // Chỉ coi lỗi khóa UNIQUE/PRIMARY KEY là trùng username.
                // Các lỗi schema, quyền truy cập hoặc kết nối phải được trả về
                // đúng nguyên nhân thay vì báo nhầm là tài khoản đã tồn tại.
                if (e.getErrorCode() == 1062 || "23505".equals(e.getSQLState())) {
                    return false;
                }
                throw e;
            }
        }
        if (localUsers.containsKey(user.username)) return false;
        localUsers.put(user.username, user);
        return true;
    }

    public boolean updateUser(Models.User user) {
        if (useMySQL) {
            String sql = "UPDATE users SET role = ?, status = ?, display_name = ? WHERE username = ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, user.role);
                ps.setString(2, user.status);
                ps.setString(3, user.displayName);
                ps.setString(4, user.username);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                System.err.println("[DatabaseManager] Lỗi update user MySQL: " + e.getMessage());
                return false;
            }
        }
        Models.User u = localUsers.get(user.username);
        if (u != null) {
            u.role = user.role;
            u.status = user.status;
            u.displayName = user.displayName;
            return true;
        }
        return false;
    }

    public boolean setLockUser(String username, boolean lock) {
        String status = lock ? "Đã khóa" : "Đang hoạt động";
        if (useMySQL) {
            String sql = "UPDATE users SET status = ? WHERE username = ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, status);
                ps.setString(2, username);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                return false;
            }
        }
        Models.User u = localUsers.get(username);
        if (u != null) {
            u.status = status;
            return true;
        }
        return false;
    }
}
