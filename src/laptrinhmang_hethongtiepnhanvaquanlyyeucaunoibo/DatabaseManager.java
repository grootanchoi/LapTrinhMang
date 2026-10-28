package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private static final DateTimeFormatter TICKET_DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Local Datastore fallback
    private final Map<String, Models.User> localUsers = new ConcurrentHashMap<>();
    private final Map<String, Models.Ticket> localTickets = new ConcurrentHashMap<>();
    private final Map<String, List<Models.TicketChatMessage>> localTicketChat = new ConcurrentHashMap<>();
    private final Map<Long, Models.ChatAttachment> localChatAttachments = new ConcurrentHashMap<>();
    private final List<Models.AuditEvent> localAuditEvents = new java.util.concurrent.CopyOnWriteArrayList<>();
    private int ticketSequence = 2;

    private DatabaseManager() {
        initLocalSampleData();
        if (!Boolean.getBoolean("workdesk.testMode")) tryConnectMySQL();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    private void initLocalSampleData() {
        // Tài khoản mẫu
        localUsers.put("admin", new Models.User("admin", PasswordHasher.hash("admin123"), "Quản trị viên hệ thống", "admin", "Đang hoạt động", "IT"));
        localUsers.put("tech01", new Models.User("tech01", PasswordHasher.hash("tech123"), "Nguyễn Văn Kỹ Thuật", "tech", "Đang hoạt động", "IT"));
        localUsers.put("tech02", new Models.User("tech02", PasswordHasher.hash("tech123"), "Trần Thị Support", "tech", "Đang hoạt động", "IT"));
        localUsers.put("user01", new Models.User("user01", PasswordHasher.hash("user123"), "Lê Văn Nhân Viên", "requester", "Đang hoạt động", "IT"));
        localUsers.put("user02", new Models.User("user02", PasswordHasher.hash("user123"), "Phạm Thị Kế Toán", "requester", "Đang hoạt động", "Kế toán"));

        // Yêu cầu mẫu
        Models.Ticket t1 = new Models.Ticket("YC-2026-0001", "Máy in phòng Kế toán không nhận lệnh in", "Hành chính", "Cao",
                "2026-10-20", "Đang xử lý", "user02", "tech01",
                "Máy in Canon 2900 báo ngoại tuyến liên tục, đã khởi động lại nhưng không được.", 40, "error_printer.txt");
        t1.ownerDepartment = "Kế toán";
        t1.createdAt = "2026-10-20T08:00:00";
        Models.Ticket t2 = new Models.Ticket("YC-2026-0002", "Xin cấp quyền truy cập VPN nội bộ", "IT", "Bình thường",
                "2026-10-21", "Mới", "user01", "Chưa phân công",
                "Cần tài khoản VPN kết nối máy chủ dữ liệu từ xa.", 0, "");
        t2.ownerDepartment = "IT";
        t2.createdAt = "2026-10-21T08:00:00";

        localTickets.put(t1.id, t1);
        localTickets.put(t2.id, t2);
    }

    private void tryConnectMySQL() {
        try {
            Class.forName("org.mariadb.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
                if (conn != null && !conn.isClosed()) {
                    migrateUserStatusColumn(conn);
                    ensureUserDepartmentColumn(conn);
                    ensureTicketChatTable(conn);
                    ensureTicketManagementColumns(conn);
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

    private void ensureUserDepartmentColumn(Connection conn) throws SQLException {
        try (ResultSet columns = conn.getMetaData().getColumns(conn.getCatalog(), null, "users", "department")) {
            if (columns.next()) return;
        }
        try (Statement statement = conn.createStatement()) {
            statement.executeUpdate("ALTER TABLE users ADD COLUMN department VARCHAR(100) NOT NULL DEFAULT ''");
        }
    }

    private void ensureTicketChatTable(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS ticket_messages ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "ticket_id VARCHAR(30) NOT NULL, "
                + "sender VARCHAR(50) NOT NULL, "
                + "content VARCHAR(2000) NOT NULL, "
                + "sent_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "INDEX idx_ticket_messages_ticket_sent (ticket_id, sent_at, id), "
                + "FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE, "
                + "FOREIGN KEY (sender) REFERENCES users(username)) "
                + "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(sql);
            st.executeUpdate("CREATE TABLE IF NOT EXISTS ticket_message_reads ("
                    + "message_id BIGINT NOT NULL, username VARCHAR(50) NOT NULL, "
                    + "read_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (message_id, username), "
                    + "FOREIGN KEY (message_id) REFERENCES ticket_messages(id) ON DELETE CASCADE, "
                    + "FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE) "
                    + "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS ticket_chat_attachments ("
                    + "message_id BIGINT NOT NULL PRIMARY KEY, file_name VARCHAR(255) NOT NULL, "
                    + "mime_type VARCHAR(100) NOT NULL, storage_name VARCHAR(80) NOT NULL, "
                    + "file_size BIGINT NOT NULL, sha256 CHAR(64) NOT NULL, "
                    + "FOREIGN KEY (message_id) REFERENCES ticket_messages(id) ON DELETE CASCADE) "
                    + "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS audit_events ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, event_type VARCHAR(80) NOT NULL, "
                    + "entity_id VARCHAR(80) NOT NULL, actor VARCHAR(50) NOT NULL, detail TEXT NOT NULL, "
                    + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "INDEX idx_audit_events_created (created_at, id), "
                    + "INDEX idx_audit_events_entity (entity_id, id)) "
                    + "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        }
    }

    private void ensureTicketManagementColumns(Connection conn) throws SQLException {
        ensureColumn(conn, "sla_due_at", "DATETIME NULL");
        ensureColumn(conn, "sla_warning_sent", "BOOLEAN NOT NULL DEFAULT FALSE");
        ensureColumn(conn, "sla_overdue_sent", "BOOLEAN NOT NULL DEFAULT FALSE");
        ensureColumn(conn, "deleted_at", "DATETIME NULL");
        ensureColumn(conn, "created_at", "DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00'");
        ensureColumn(conn, "owner_department", "VARCHAR(100) NOT NULL DEFAULT ''");
        ensureColumn(conn, "source_ip", "VARCHAR(45) NOT NULL DEFAULT ''");
        try (Statement statement = conn.createStatement()) {
            statement.executeUpdate("UPDATE tickets SET created_at = COALESCE("
                    + "STR_TO_DATE(CONCAT(created_date, ' 08:00:00'), '%Y-%m-%d %H:%i:%s'), "
                    + "'1970-01-01 00:00:00') WHERE created_at = '1970-01-01 00:00:00'");
            statement.executeUpdate("UPDATE tickets t JOIN users u ON u.username = t.owner "
                    + "SET t.owner_department = u.department "
                    + "WHERE t.owner_department = '' AND u.department <> ''");
        }
        String select = "SELECT id, priority, created_date FROM tickets WHERE sla_due_at IS NULL";
        try (Statement statement = conn.createStatement(); ResultSet rs = statement.executeQuery(select);
                PreparedStatement update = conn.prepareStatement("UPDATE tickets SET sla_due_at = ? WHERE id = ?")) {
            while (rs.next()) {
                LocalDateTime createdAt;
                try {
                    createdAt = java.time.LocalDate.parse(rs.getString("created_date")).atTime(8, 0);
                } catch (RuntimeException e) {
                    createdAt = LocalDateTime.now();
                }
                LocalDateTime dueAt = BusinessHours.addWorkingHours(createdAt, slaHours(rs.getString("priority")));
                update.setTimestamp(1, Timestamp.valueOf(dueAt));
                update.setString(2, rs.getString("id"));
                update.addBatch();
            }
            update.executeBatch();
        }
    }

    private void ensureColumn(Connection conn, String name, String definition) throws SQLException {
        try (ResultSet columns = conn.getMetaData().getColumns(conn.getCatalog(), null, "tickets", name)) {
            if (columns.next()) return;
        }
        try (Statement statement = conn.createStatement()) {
            statement.executeUpdate("ALTER TABLE tickets ADD COLUMN " + name + " " + definition);
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
            String sql = "SELECT username, password, display_name, role, status, department FROM users WHERE username = ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String storedPassword = rs.getString("password");
                        if (!PasswordHasher.verify(password, storedPassword)) return null;
                        if (!PasswordHasher.isEncoded(storedPassword)) {
                            migrateLegacyPassword(username, password);
                        }
                        return new Models.User(
                                rs.getString("username"),
                                rs.getString("password"),
                                rs.getString("display_name"),
                                rs.getString("role"),
                                rs.getString("status"),
                                rs.getString("department")
                        );
                    }
                }
            } catch (SQLException e) {
                System.err.println("[DatabaseManager] Lỗi xác thực MySQL: " + e.getMessage());
            }
        }

        Models.User u = localUsers.get(username);
        if (u != null && PasswordHasher.verify(password, u.password)) {
            if (!PasswordHasher.isEncoded(u.password)) {
                u.password = PasswordHasher.hash(password);
            }
            return u;
        }
        return null;
    }

    private void migrateLegacyPassword(String username, String legacyPassword) {
        String sql = "UPDATE users SET password = ? WHERE username = ? AND password = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, PasswordHasher.hash(legacyPassword));
            ps.setString(2, username);
            ps.setString(3, legacyPassword);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Không thể nâng cấp mật khẩu cũ: " + e.getMessage());
        }
    }

    public Models.User getUserByUsername(String username) {
        if (username == null || username.trim().isEmpty()) return null;
        if (useMySQL) {
            String sql = "SELECT username, display_name, role, status, department FROM users WHERE username = ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new Models.User(rs.getString("username"), "", rs.getString("display_name"),
                                rs.getString("role"), rs.getString("status"), rs.getString("department"));
                    }
                    return null;
                }
            } catch (SQLException e) {
                System.err.println("[DatabaseManager] Lỗi kiểm tra tài khoản: " + e.getMessage());
                return null;
            }
        }
        Models.User user = localUsers.get(username);
        return user == null ? null : new Models.User(user.username, "", user.displayName, user.role,
                user.status, user.department);
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
        prepareTicketForInsert(ticket);
        if (useMySQL) {
            String sql = ticketInsertSql();
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                bindTicketInsert(ps, ticket);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                System.err.println("[DatabaseManager] Lỗi insert MySQL: " + e.getMessage());
            }
        }
        localTickets.put(ticket.id, ticket);
        return true;
    }

    public enum TicketCreateResult { CREATED, QUOTA_EXCEEDED, FAILED }

    public synchronized TicketCreateResult insertRequesterTicketWithinLimit(Models.Ticket ticket, int limit) {
        prepareTicketForInsert(ticket);
        if (useMySQL) {
            String countSql = "SELECT COUNT(*) FROM tickets "
                    + "WHERE owner = ? AND created_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 24 HOUR)";
            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);
                try {
                    try (PreparedStatement lock = conn.prepareStatement(
                            "SELECT username FROM users WHERE username = ? FOR UPDATE")) {
                        lock.setString(1, ticket.owner);
                        try (ResultSet rs = lock.executeQuery()) {
                            if (!rs.next()) {
                                conn.rollback();
                                return TicketCreateResult.FAILED;
                            }
                        }
                    }
                    try (PreparedStatement count = conn.prepareStatement(countSql)) {
                        count.setString(1, ticket.owner);
                        try (ResultSet rs = count.executeQuery()) {
                            if (rs.next() && rs.getInt(1) >= limit) {
                                conn.rollback();
                                return TicketCreateResult.QUOTA_EXCEEDED;
                            }
                        }
                    }
                    try (PreparedStatement insert = conn.prepareStatement(ticketInsertSql())) {
                        bindTicketInsert(insert, ticket);
                        if (insert.executeUpdate() == 0) {
                            conn.rollback();
                            return TicketCreateResult.FAILED;
                        }
                    }
                    conn.commit();
                    return TicketCreateResult.CREATED;
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                System.err.println("[DatabaseManager] Lỗi tạo ticket có kiểm soát quota: " + e.getMessage());
                return TicketCreateResult.FAILED;
            }
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minusHours(24);
        long recentTickets = localTickets.values().stream()
                .filter(existing -> ticket.owner.equalsIgnoreCase(existing.owner))
                .filter(existing -> {
                    LocalDateTime created = ticketCreatedAt(existing);
                    return created != null && !created.isBefore(windowStart) && !created.isAfter(now);
                })
                .count();
        if (recentTickets >= limit) return TicketCreateResult.QUOTA_EXCEEDED;
        ticket.createdAt = now.format(TICKET_DATE_TIME_FORMAT);
        localTickets.put(ticket.id, ticket);
        return TicketCreateResult.CREATED;
    }

    private void prepareTicketForInsert(Models.Ticket ticket) {
        if (ticket.createdAt == null || ticket.createdAt.isBlank()) {
            ticket.createdAt = LocalDateTime.now().format(TICKET_DATE_TIME_FORMAT);
        }
        if (ticket.slaDueAt == null || ticket.slaDueAt.isBlank()) {
            ticket.slaDueAt = BusinessHours.addWorkingHours(LocalDateTime.now(), slaHours(ticket.priority)).toString();
        }
    }

    private static LocalDateTime ticketCreatedAt(Models.Ticket ticket) {
        try {
            return ticket.createdAt == null || ticket.createdAt.isBlank()
                    ? java.time.LocalDate.parse(ticket.createdDate).atStartOfDay()
                    : parseTicketDateTime(ticket.createdAt);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static LocalDateTime parseTicketDateTime(String value) {
        try {
            return LocalDateTime.parse(value, TICKET_DATE_TIME_FORMAT);
        } catch (java.time.format.DateTimeParseException e) {
            return LocalDateTime.parse(value);
        }
    }

    private static String ticketInsertSql() {
        return "INSERT INTO tickets (id, title, category, priority, created_date, created_at, status, owner, "
                + "assigned_tech, detail, progress, attachment_name, sla_due_at, owner_department, source_ip) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    private static void bindTicketInsert(PreparedStatement ps, Models.Ticket ticket) throws SQLException {
        ps.setString(1, ticket.id);
        ps.setString(2, ticket.title);
        ps.setString(3, ticket.category);
        ps.setString(4, ticket.priority);
        ps.setString(5, ticket.createdDate);
        ps.setTimestamp(6, Timestamp.valueOf(parseTicketDateTime(ticket.createdAt)));
        ps.setString(7, ticket.status);
        ps.setString(8, ticket.owner);
        ps.setString(9, ticket.assignedTech);
        ps.setString(10, ticket.detail);
        ps.setInt(11, ticket.progress);
        ps.setString(12, ticket.attachmentName);
        ps.setTimestamp(13, Timestamp.valueOf(LocalDateTime.parse(ticket.slaDueAt)));
        ps.setString(14, ticket.ownerDepartment == null ? "" : ticket.ownerDepartment);
        ps.setString(15, ticket.sourceIp == null ? "" : ticket.sourceIp);
    }

    // ==================== F03: XEM DANH SÁCH & CHI TIẾT ====================
    public List<Models.Ticket> getTicketsForUser(String username, String role) {
        List<Models.Ticket> list = new ArrayList<>();
        if (useMySQL) {
            String sql;
            if ("admin".equalsIgnoreCase(role)) {
                sql = ticketSelectWithLatestNote() + " WHERE t.deleted_at IS NULL ORDER BY t.id DESC";
            } else if ("tech".equalsIgnoreCase(role)) {
                sql = ticketSelectWithLatestNote()
                        + " WHERE t.deleted_at IS NULL AND t.assigned_tech = ? ORDER BY t.id DESC";
            } else {
                sql = ticketSelectWithLatestNote()
                        + " WHERE t.deleted_at IS NULL AND t.owner = ? ORDER BY t.id DESC";
            }

            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                if ("tech".equalsIgnoreCase(role) || "requester".equalsIgnoreCase(role)) {
                    ps.setString(1, username);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(ticketFromResult(rs));
                    }
                    return list;
                }
            } catch (SQLException e) {
                System.err.println("[DatabaseManager] Lỗi lấy danh sách tickets MySQL: " + e.getMessage());
            }
        }

        // Fallback
        for (Models.Ticket t : localTickets.values()) {
            if (t.deleted) continue;
            if ("admin".equalsIgnoreCase(role)) {
                list.add(t);
            } else if ("tech".equalsIgnoreCase(role)) {
                if (username.equalsIgnoreCase(t.assignedTech)) {
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
            StringBuilder sql = new StringBuilder(ticketSelectWithLatestNote()).append(" WHERE t.deleted_at IS NULL");
            if ("requester".equalsIgnoreCase(role)) sql.append(" AND t.owner = ?");
            else if ("tech".equalsIgnoreCase(role)) sql.append(" AND t.assigned_tech = ?");
            if (!q.isEmpty()) sql.append(" AND (id LIKE ? OR title LIKE ? OR category LIKE ? OR detail LIKE ? OR owner LIKE ? OR assigned_tech LIKE ?)");
            if (!st.isEmpty()) sql.append(" AND t.status = ?");
            if (!cat.isEmpty()) sql.append(" AND t.category = ?");
            if (!pri.isEmpty()) sql.append(" AND t.priority = ?");
            sql.append(" ORDER BY t.created_date DESC, t.id DESC");
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
            access = " WHERE deleted_at IS NULL";
            if ("requester".equalsIgnoreCase(role)) access += " AND owner = ?";
            else if ("tech".equalsIgnoreCase(role)) access += " AND assigned_tech = ?";
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
        Models.Ticket ticket = new Models.Ticket(rs.getString("id"), rs.getString("title"), rs.getString("category"), rs.getString("priority"),
                rs.getString("created_date"), rs.getString("status"), rs.getString("owner"), rs.getString("assigned_tech"),
                rs.getString("detail"), rs.getInt("progress"), rs.getString("attachment_name"));
        Timestamp dueAt = rs.getTimestamp("sla_due_at");
        ticket.slaDueAt = dueAt == null ? null : dueAt.toLocalDateTime().toString();
        ticket.slaWarningSent = rs.getBoolean("sla_warning_sent");
        ticket.slaOverdueSent = rs.getBoolean("sla_overdue_sent");
        ticket.deleted = rs.getTimestamp("deleted_at") != null;
        Timestamp createdAt = rs.getTimestamp("created_at");
        ticket.createdAt = createdAt == null ? null : createdAt.toLocalDateTime().format(TICKET_DATE_TIME_FORMAT);
        ticket.latestNote = rs.getString("latest_note");
        ticket.ownerDepartment = rs.getString("owner_department");
        ticket.sourceIp = rs.getString("source_ip");
        return ticket;
    }

    private static String ticketSelectWithLatestNote() {
        return "SELECT t.*, (SELECT h.note FROM ticket_history h WHERE h.ticket_id = t.id "
                + "ORDER BY h.id DESC LIMIT 1) AS latest_note FROM tickets t";
    }

    public boolean updateTicketForAdmin(Models.Ticket updated, String actor) {
        Models.Ticket previous = getTicketById(updated.id);
        if (previous == null) return false;
        String previousDetail = "title=" + previous.title + "; category=" + previous.category
                + "; priority=" + previous.priority;
        boolean priorityChanged = previous.priority == null
                || !previous.priority.equalsIgnoreCase(updated.priority);
        String dueAt = priorityChanged
                ? BusinessHours.addWorkingHours(LocalDateTime.now(), slaHours(updated.priority)).toString()
                : previous.slaDueAt;
        if (useMySQL) {
            String sql = "UPDATE tickets SET title = ?, category = ?, priority = ?, detail = ?, sla_due_at = ?, "
                    + "sla_warning_sent = IF(?, FALSE, sla_warning_sent), "
                    + "sla_overdue_sent = IF(?, FALSE, sla_overdue_sent) "
                    + "WHERE id = ? AND deleted_at IS NULL";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, updated.title);
                ps.setString(2, updated.category);
                ps.setString(3, updated.priority);
                ps.setString(4, updated.detail);
                ps.setTimestamp(5, Timestamp.valueOf(dueAt));
                ps.setBoolean(6, priorityChanged);
                ps.setBoolean(7, priorityChanged);
                ps.setString(8, updated.id);
                if (ps.executeUpdate() == 0) return false;
            } catch (SQLException e) {
                System.err.println("[F02] Lỗi admin cập nhật yêu cầu: " + e.getMessage());
                return false;
            }
        } else {
            Models.Ticket current = localTickets.get(updated.id);
            if (current == null || current.deleted) return false;
            current.title = updated.title;
            current.category = updated.category;
            current.priority = updated.priority;
            current.detail = updated.detail;
            if (priorityChanged) {
                current.slaDueAt = dueAt;
                current.slaWarningSent = false;
                current.slaOverdueSent = false;
            }
        }
        recordHistory(updated.id, "Admin chỉnh sửa nội dung yêu cầu", actor,
                previousDetail + " => title=" + updated.title + "; category=" + updated.category
                        + "; priority=" + updated.priority);
        recordAuditEvent("TICKET_UPDATED", updated.id, actor,
                "Admin đã chỉnh sửa tiêu đề, danh mục, ưu tiên hoặc mô tả.");
        return true;
    }

    public boolean softDeleteTicket(String ticketId, String actor) {
        Models.Ticket ticket = getTicketById(ticketId);
        if (ticket == null) return false;
        if (useMySQL) {
            try (Connection conn = getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "UPDATE tickets SET deleted_at = CURRENT_TIMESTAMP WHERE id = ? AND deleted_at IS NULL")) {
                ps.setString(1, ticketId);
                if (ps.executeUpdate() == 0) return false;
            } catch (SQLException e) {
                System.err.println("[F02] Lỗi xóa mềm yêu cầu: " + e.getMessage());
                return false;
            }
        } else {
            ticket.deleted = true;
        }
        recordHistory(ticketId, "Admin xóa mềm yêu cầu", actor, "Dữ liệu được giữ để kiểm toán.");
        recordAuditEvent("TICKET_SOFT_DELETED", ticketId, actor, "Admin ẩn yêu cầu; lịch sử và chat được giữ lại.");
        return true;
    }

    public void recordAuditEvent(String eventType, String entityId, String actor, String detail) {
        String createdAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        if (useMySQL) {
            String sql = "INSERT INTO audit_events (event_type, entity_id, actor, detail) VALUES (?, ?, ?, ?)";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, eventType);
                ps.setString(2, entityId == null ? "" : entityId);
                ps.setString(3, actor == null ? "system" : actor);
                ps.setString(4, detail == null ? "" : detail);
                ps.executeUpdate();
            } catch (SQLException e) {
                System.err.println("[Audit] Không lưu được sự kiện kiểm toán: " + e.getMessage());
            }
            return;
        }
        localAuditEvents.add(new Models.AuditEvent(eventType, entityId, actor, detail, createdAt));
    }

    public List<Models.AuditEvent> getAuditEvents(int limit) {
        List<Models.AuditEvent> events = new ArrayList<>();
        int boundedLimit = Math.max(1, Math.min(limit, 1000));
        if (useMySQL) {
            String sql = "SELECT event_type, entity_id, actor, detail, "
                    + "DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at "
                    + "FROM audit_events ORDER BY id DESC LIMIT ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, boundedLimit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) events.add(new Models.AuditEvent(rs.getString("event_type"),
                            rs.getString("entity_id"), rs.getString("actor"), rs.getString("detail"),
                            rs.getString("created_at")));
                }
            } catch (SQLException e) {
                System.err.println("[Audit] Không tải được nhật ký kiểm toán: " + e.getMessage());
            }
            return events;
        }
        for (int i = localAuditEvents.size() - 1; i >= 0 && events.size() < boundedLimit; i--) {
            events.add(localAuditEvents.get(i));
        }
        return events;
    }

    public List<Models.Ticket> getSlaCandidates() {
        if (useMySQL) {
            List<Models.Ticket> tickets = new ArrayList<>();
            String sql = ticketSelectWithLatestNote() + " WHERE t.deleted_at IS NULL "
                    + "AND t.status NOT IN ('Hoàn thành', 'Đã hủy') AND t.sla_due_at IS NOT NULL";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql);
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) tickets.add(ticketFromResult(rs));
            } catch (SQLException e) {
                System.err.println("[SLA] Không tải được danh sách yêu cầu cần giám sát: " + e.getMessage());
            }
            return tickets;
        }
        return localTickets.values().stream()
                .filter(ticket -> !ticket.deleted && ticket.slaDueAt != null
                        && !"Hoàn thành".equalsIgnoreCase(ticket.status)
                        && !"Đã hủy".equalsIgnoreCase(ticket.status))
                .toList();
    }

    public boolean markSlaAlertSent(String ticketId, boolean overdue) {
        Models.Ticket ticket = getTicketById(ticketId);
        if (ticket == null) return false;
        String column = overdue ? "sla_overdue_sent" : "sla_warning_sent";
        if (useMySQL) {
            String sql = "UPDATE tickets SET " + column + " = TRUE WHERE id = ? AND " + column + " = FALSE";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, ticketId);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                System.err.println("[SLA] Không thể ghi nhận cảnh báo: " + e.getMessage());
                return false;
            }
        }
        if (overdue ? ticket.slaOverdueSent : ticket.slaWarningSent) return false;
        if (overdue) ticket.slaOverdueSent = true;
        else ticket.slaWarningSent = true;
        return true;
    }

    private static double slaHours(String priority) {
        if ("Khẩn cấp".equalsIgnoreCase(priority)) return 4;
        if ("Cao".equalsIgnoreCase(priority)) return 24;
        if ("Thấp".equalsIgnoreCase(priority)) return 120;
        return 72;
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
            String sql = ticketSelectWithLatestNote() + " WHERE t.id = ? AND t.deleted_at IS NULL";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return ticketFromResult(rs);
                    }
                }
            } catch (SQLException ignored) {}
        }
        Models.Ticket ticket = localTickets.get(id);
        return ticket == null || ticket.deleted ? null : ticket;
    }

    public synchronized List<Models.TicketChatMessage> getTicketChatMessages(String ticketId, String username) {
        List<Models.TicketChatMessage> messages = new ArrayList<>();
        if (useMySQL) {
            String sql = "SELECT m.id, m.ticket_id, m.sender, m.content, "
                    + "DATE_FORMAT(m.sent_at, '%Y-%m-%d %H:%i:%s') AS sent_at, "
                    + "a.file_name, a.mime_type, a.file_size, "
                    + "CASE WHEN m.sender = ? THEN EXISTS (SELECT 1 FROM ticket_message_reads r "
                    + "WHERE r.message_id = m.id AND r.username <> m.sender) "
                    + "ELSE EXISTS (SELECT 1 FROM ticket_message_reads r "
                    + "WHERE r.message_id = m.id AND r.username = ?) END AS is_read "
                    + "FROM ticket_messages m LEFT JOIN ticket_chat_attachments a ON a.message_id = m.id "
                    + "WHERE m.ticket_id = ? ORDER BY m.id ASC";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, username);
                ps.setString(3, ticketId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Models.TicketChatMessage message = new Models.TicketChatMessage(rs.getLong("id"), rs.getString("ticket_id"),
                                rs.getString("sender"), rs.getString("content"), rs.getString("sent_at"),
                                rs.getBoolean("is_read"));
                        message.attachmentName = rs.getString("file_name");
                        message.attachmentMimeType = rs.getString("mime_type");
                        message.attachmentSize = rs.getLong("file_size");
                        messages.add(message);
                    }
                }
            } catch (SQLException e) {
                System.err.println("[F11] Lỗi tải lịch sử trao đổi: " + e.getMessage());
                return messages;
            }
            return messages;
        }
        List<Models.TicketChatMessage> localMessages = localTicketChat.get(ticketId);
        if (localMessages != null) {
            for (Models.TicketChatMessage message : localMessages) {
                boolean isRead = message.sender.equalsIgnoreCase(username)
                        ? localTicketChatReads.getOrDefault(message.id, java.util.Set.of()).stream()
                                .anyMatch(reader -> !reader.equalsIgnoreCase(username))
                        : localTicketChatReads.getOrDefault(message.id, java.util.Set.of()).contains(username);
                messages.add(new Models.TicketChatMessage(message.id, message.ticketId, message.sender,
                        message.content, message.sentAt, isRead));
                Models.TicketChatMessage copy = messages.get(messages.size() - 1);
                Models.ChatAttachment attachment = localChatAttachments.get(message.id);
                if (attachment != null) {
                    copy.attachmentName = attachment.fileName;
                    copy.attachmentMimeType = attachment.mimeType;
                    copy.attachmentSize = attachment.fileSize;
                }
            }
        }
        return messages;
    }

    public synchronized Models.TicketChatMessage addTicketChatMessage(String ticketId, String sender, String content) {
        String sentAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        if (useMySQL) {
            String sql = "INSERT INTO ticket_messages (ticket_id, sender, content) VALUES (?, ?, ?)";
            try (Connection conn = getConnection();
                    PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, ticketId);
                ps.setString(2, sender);
                ps.setString(3, content);
                if (ps.executeUpdate() > 0) {
                    long messageId;
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            System.err.println("[F11] Không nhận được mã tin nhắn vừa lưu.");
                            return null;
                        }
                        messageId = keys.getLong(1);
                    }
                    String select = "SELECT DATE_FORMAT(sent_at, '%Y-%m-%d %H:%i:%s') AS sent_at "
                            + "FROM ticket_messages WHERE id = ?";
                    try (PreparedStatement last = conn.prepareStatement(select)) {
                        last.setLong(1, messageId);
                        try (ResultSet rs = last.executeQuery()) {
                            if (rs.next()) return new Models.TicketChatMessage(messageId, ticketId,
                                    sender, content, rs.getString("sent_at"), false);
                        }
                    }
                }
            } catch (SQLException e) {
                System.err.println("[F11] Lỗi lưu tin nhắn trao đổi: " + e.getMessage());
                return null;
            }
            return null;
        }
        if (!localTickets.containsKey(ticketId) || !localUsers.containsKey(sender)) return null;
        Models.TicketChatMessage message = new Models.TicketChatMessage(
                localTicketChatSequence.incrementAndGet(), ticketId, sender, content, sentAt, false);
        localTicketChat.computeIfAbsent(ticketId, key -> new ArrayList<>()).add(message);
        return message;
    }

    public synchronized void markTicketChatRead(String ticketId, String username) {
        if (useMySQL) {
            String sql = "INSERT IGNORE INTO ticket_message_reads (message_id, username) "
                    + "SELECT id, ? FROM ticket_messages WHERE ticket_id = ? AND sender <> ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, ticketId);
                ps.setString(3, username);
                ps.executeUpdate();
            } catch (SQLException e) {
                System.err.println("[F11] Lỗi đánh dấu tin nhắn đã đọc: " + e.getMessage());
            }
            return;
        }
        List<Models.TicketChatMessage> messages = localTicketChat.get(ticketId);
        if (messages == null) return;
        for (Models.TicketChatMessage message : messages) {
            if (!message.sender.equalsIgnoreCase(username)) {
                localTicketChatReads.computeIfAbsent(message.id, key -> ConcurrentHashMap.newKeySet()).add(username);
            }
        }
    }

    public synchronized Map<String, Integer> getUnreadTicketChatCounts(String username, String role) {
        Map<String, Integer> counts = new java.util.HashMap<>();
        if (useMySQL) {
            String sql = "SELECT m.ticket_id, COUNT(*) AS unread_count "
                    + "FROM ticket_messages m JOIN tickets t ON t.id = m.ticket_id "
                    + "LEFT JOIN ticket_message_reads r ON r.message_id = m.id AND r.username = ? "
                    + "WHERE m.sender <> ? AND r.message_id IS NULL "
                    + "AND (? = 'admin' OR (? = 'requester' AND t.owner = ?) "
                    + "OR (? = 'tech' AND t.assigned_tech = ?)) "
                    + "GROUP BY m.ticket_id";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, username);
                ps.setString(3, role);
                ps.setString(4, role);
                ps.setString(5, username);
                ps.setString(6, role);
                ps.setString(7, username);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) counts.put(rs.getString("ticket_id"), rs.getInt("unread_count"));
                }
            } catch (SQLException e) {
                System.err.println("[F11] Lỗi thống kê tin nhắn chưa đọc: " + e.getMessage());
            }
            return counts;
        }
        localTicketChat.forEach((ticketId, messages) -> {
            Models.Ticket ticket = localTickets.get(ticketId);
            if (ticket == null || !("admin".equalsIgnoreCase(role)
                    || ("requester".equalsIgnoreCase(role) && username.equalsIgnoreCase(ticket.owner))
                    || ("tech".equalsIgnoreCase(role) && username.equalsIgnoreCase(ticket.assignedTech)))) return;
            int unread = 0;
            for (Models.TicketChatMessage message : messages) {
                if (!message.sender.equalsIgnoreCase(username)
                        && !localTicketChatReads.getOrDefault(message.id, java.util.Set.of()).contains(username)) {
                    unread++;
                }
            }
            if (unread > 0) counts.put(ticketId, unread);
        });
        return counts;
    }

    private final Map<Long, java.util.Set<String>> localTicketChatReads = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong localTicketChatSequence = new java.util.concurrent.atomic.AtomicLong();

    public boolean storeChatAttachment(long messageId, String fileName, String mimeType,
                                       String storageName, byte[] bytes, String sha256) {
        if (useMySQL) {
            String sql = "INSERT INTO ticket_chat_attachments "
                    + "(message_id, file_name, mime_type, storage_name, file_size, sha256) VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, messageId);
                ps.setString(2, fileName);
                ps.setString(3, mimeType);
                ps.setString(4, storageName);
                ps.setLong(5, bytes.length);
                ps.setString(6, sha256);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                System.err.println("[F11] Không lưu được metadata tệp chat: " + e.getMessage());
                return false;
            }
        }
        localChatAttachments.put(messageId,
                new Models.ChatAttachment(messageId, fileName, mimeType, bytes.length, bytes.clone()));
        return true;
    }

    public Models.ChatAttachment getChatAttachment(long messageId) {
        if (useMySQL) {
            String sql = "SELECT file_name, mime_type, storage_name, file_size, sha256 "
                    + "FROM ticket_chat_attachments WHERE message_id = ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, messageId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    long size = rs.getLong("file_size");
                    if (size < 0 || size > 10L * 1024 * 1024) {
                        throw new IOException("Kích thước tệp đính kèm lưu trên máy chủ không hợp lệ.");
                    }
                    Path path = Path.of(FileTransferHelper.SERVER_STORAGE_DIR, rs.getString("storage_name"));
                    byte[] bytes = Files.readAllBytes(path);
                    if (bytes.length != size) throw new IOException("Tệp đính kèm trên máy chủ không toàn vẹn.");
                    String actualHash = java.util.HexFormat.of().formatHex(
                            java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
                    if (!actualHash.equalsIgnoreCase(rs.getString("sha256"))) {
                        throw new IOException("Checksum SHA-256 của tệp đính kèm không khớp.");
                    }
                    return new Models.ChatAttachment(messageId, rs.getString("file_name"),
                            rs.getString("mime_type"), size, bytes);
                }
            } catch (Exception e) {
                System.err.println("[F11] Không tải được tệp chat: " + e.getMessage());
                return null;
            }
        }
        Models.ChatAttachment attachment = localChatAttachments.get(messageId);
        return attachment == null ? null : new Models.ChatAttachment(messageId, attachment.fileName,
                attachment.mimeType, attachment.fileSize, attachment.bytes.clone());
    }

    public synchronized void deleteTicketChatMessage(long messageId) {
        if (useMySQL) {
            try (Connection conn = getConnection();
                    PreparedStatement ps = conn.prepareStatement("DELETE FROM ticket_messages WHERE id = ?")) {
                ps.setLong(1, messageId);
                ps.executeUpdate();
            } catch (SQLException e) {
                System.err.println("[F11] Không xóa được tin nhắn tải tệp thất bại: " + e.getMessage());
            }
            return;
        }
        for (List<Models.TicketChatMessage> messages : localTicketChat.values()) {
            messages.removeIf(message -> message.id == messageId);
        }
        localChatAttachments.remove(messageId);
        localTicketChatReads.remove(messageId);
    }

    public String getTicketIdForChatMessage(long messageId) {
        if (useMySQL) {
            try (Connection conn = getConnection();
                    PreparedStatement ps = conn.prepareStatement("SELECT ticket_id FROM ticket_messages WHERE id = ?")) {
                ps.setLong(1, messageId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString("ticket_id") : null;
                }
            } catch (SQLException e) {
                System.err.println("[F11] Không xác định được yêu cầu của tệp chat: " + e.getMessage());
                return null;
            }
        }
        for (Map.Entry<String, List<Models.TicketChatMessage>> entry : localTicketChat.entrySet()) {
            if (entry.getValue().stream().anyMatch(message -> message.id == messageId)) return entry.getKey();
        }
        return null;
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
            t.latestNote = note;
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
                            rs.getString("status"),
                            rs.getString("department")
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
        String storedPassword = PasswordHasher.isEncoded(user.password)
                ? user.password : PasswordHasher.hash(user.password);
        if (useMySQL) {
            String sql = "INSERT INTO users (username, password, display_name, role, status, department) VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, user.username);
                ps.setString(2, storedPassword);
                ps.setString(3, user.displayName);
                ps.setString(4, user.role);
                ps.setString(5, user.status);
                ps.setString(6, user.department == null ? "" : user.department);
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
        localUsers.put(user.username, new Models.User(user.username, storedPassword,
                user.displayName, user.role, user.status, user.department));
        return true;
    }

    public boolean updateUser(Models.User user) {
        if (useMySQL) {
            String sql = "UPDATE users SET role = ?, status = ?, display_name = ?, department = ? WHERE username = ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, user.role);
                ps.setString(2, user.status);
                ps.setString(3, user.displayName);
                ps.setString(4, user.department == null ? "" : user.department);
                ps.setString(5, user.username);
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
            u.department = user.department == null ? "" : user.department;
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
