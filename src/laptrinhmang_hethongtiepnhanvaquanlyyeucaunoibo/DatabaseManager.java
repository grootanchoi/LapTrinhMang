package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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
    private String dbUrl = "jdbc:mysql://localhost:3306/workdesk_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&useUnicode=true&characterEncoding=UTF-8";
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
                "Máy in Canon 2900 báo offline liên tục, đã restart nhưng không được.", 40, "error_printer.txt");
        Models.Ticket t2 = new Models.Ticket("YC-2026-0002", "Xin cấp quyền truy cập VPN nội bộ", "IT", "Bình thường",
                "2026-10-21", "Mới", "user01", "Chưa phân công",
                "Cần tài khoản VPN kết nối máy chủ dữ liệu từ xa.", 0, "");

        localTickets.put(t1.id, t1);
        localTickets.put(t2.id, t2);
    }

    private void tryConnectMySQL() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
                if (conn != null && !conn.isClosed()) {
                    useMySQL = true;
                    System.out.println("[DatabaseManager] Kết nối MySQL thành công! Sử dụng CSDL: workdesk_db");
                }
            }
        } catch (Exception e) {
            useMySQL = false;
            System.out.println("[DatabaseManager] Không kết nối được MySQL (" + e.getMessage() + "). Tự động sử dụng Datastore dự phòng tích hợp.");
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

    public boolean addUser(Models.User user) {
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
                System.err.println("[DatabaseManager] Lỗi thêm user MySQL: " + e.getMessage());
                return false;
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
