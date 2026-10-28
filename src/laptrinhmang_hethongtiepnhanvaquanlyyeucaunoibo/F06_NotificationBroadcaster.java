package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * CHỨC NĂNG F06: GỬI THÔNG BÁO KHI TRẠNG THÁI THAY ĐỔI
 * =========================================================================
 * - Xử lý chính: Khi trạng thái yêu cầu hoặc thông tin trong hệ thống thay đổi,
 *                Server tự động đẩy (Push/Broadcast) thông báo theo thời gian thực
 *                tới tất cả các Socket của các Client đang kết nối.
 * - Đầu vào / đầu ra: Nhiều Socket / Nhiều phiên kết nối (Multi-socket & Multi-session).
 * =========================================================================
 */
public class F06_NotificationBroadcaster {

    /**
     * Đại diện cho một phiên kết nối Socket Client đang hoạt động trên Server
     */
    public interface ClientSessionHolder {
        String getSessionId();
        String getUsername();
        String getRole();
        ObjectOutputStream getOutputStream();
        boolean isConnected();
        void closeConnection() throws IOException;
    }

    private final List<ClientSessionHolder> activeSessions = new CopyOnWriteArrayList<>();

    public void registerSession(ClientSessionHolder session) {
        activeSessions.add(session);
        System.out.println("[F06 Broadcaster] Đăng ký phiên kết nối mới: " + session.getSessionId()
                + " (Tài khoản: " + session.getUsername() + ", Số phiên hiện tại: " + activeSessions.size() + ")");
    }

    public void unregisterSession(ClientSessionHolder session) {
        activeSessions.remove(session);
        System.out.println("[F06 Broadcaster] Hủy phiên kết nối: " + session.getSessionId()
                + " (Còn lại: " + activeSessions.size() + " phiên)");
    }

    public void closeAllSessions() {
        for (ClientSessionHolder session : activeSessions) {
            try {
                session.closeConnection();
            } catch (IOException e) {
                System.err.println("[F06 Broadcaster] Không thể đóng phiên "
                        + session.getSessionId() + ": " + e.getMessage());
            }
        }
    }

    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    public boolean hasActiveSession(String username, String excludedSessionId) {
        return activeSessions.stream().anyMatch(session -> session.isConnected()
                && !session.getSessionId().equals(excludedSessionId)
                && username.equalsIgnoreCase(session.getUsername()));
    }

    public void terminateUserSessions(String username, String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss dd/MM"));
        Models.NotificationMessage notification =
                new Models.NotificationMessage("SESSION_REVOKED", "", message, timestamp);
        Protocol.Packet packet = new Protocol.Packet(
                Protocol.CMD_NOTIFICATION, Protocol.STATUS_SUCCESS, message, notification);
        for (ClientSessionHolder session : activeSessions) {
            if (!session.isConnected() || !username.equalsIgnoreCase(session.getUsername())) continue;
            try {
                ObjectOutputStream output = session.getOutputStream();
                synchronized (output) {
                    output.writeObject(packet);
                    output.flush();
                    output.reset();
                }
                session.closeConnection();
            } catch (IOException e) {
                System.err.println("[F06] Không thể thu hồi phiên " + session.getSessionId()
                        + " của " + username + ": " + e.getMessage());
                try {
                    session.closeConnection();
                } catch (IOException closeError) {
                    System.err.println("[F06] Không thể đóng phiên bị thu hồi "
                            + session.getSessionId() + ": " + closeError.getMessage());
                }
            }
        }
    }

    public java.util.Map<String, Integer> getSessionCountsByRole() {
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (ClientSessionHolder session : activeSessions) {
            if (session.isConnected()) {
                String role = session.getRole();
                counts.merge(role == null ? "none" : role.toLowerCase(), 1, Integer::sum);
            }
        }
        return counts;
    }

    /**
     * Phát sóng thông báo tới TẤT CẢ các Socket kết nối trong hệ thống (Broadcast)
     */
    public void broadcast(String eventType, String ticketId, String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss dd/MM"));
        Models.NotificationMessage notif = new Models.NotificationMessage(eventType, ticketId, message, timestamp);
        Protocol.Packet notifPacket = new Protocol.Packet(Protocol.CMD_NOTIFICATION, Protocol.STATUS_SUCCESS, message, notif);

        System.out.println("[F06 Broadcast] Bắn sự kiện '" + eventType + "' tới " + activeSessions.size() + " phiên Socket...");

        for (ClientSessionHolder session : activeSessions) {
            try {
                if (session.isConnected()) {
                    ObjectOutputStream out = session.getOutputStream();
                    synchronized (out) {
                        out.writeObject(notifPacket);
                        out.flush();
                        out.reset(); // Đảm bảo không bị cache object serialization
                    }
                }
            } catch (IOException e) {
                System.err.println("[F06 Broadcast] Lỗi gửi thông báo tới phiên " + session.getSessionId() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Gửi thông báo đích danh tới một người dùng cụ thể
     */
    public void sendToUser(String targetUsername, String eventType, String ticketId, String message) {
        sendToUsers(targetUsername == null ? List.of() : List.of(targetUsername), eventType, ticketId, message);
    }

    public void sendToUsers(List<String> targetUsernames, String eventType, String ticketId, String message) {
        sendToUsers(targetUsernames, eventType, ticketId, message, 0, null);
    }

    public void sendToUsers(List<String> targetUsernames, String eventType, String ticketId, String message,
                            long attachmentMessageId, String attachmentName) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss dd/MM"));
        Models.NotificationMessage notif = new Models.NotificationMessage(eventType, ticketId, message, timestamp,
                attachmentMessageId, attachmentName);
        Protocol.Packet notifPacket = new Protocol.Packet(Protocol.CMD_NOTIFICATION, Protocol.STATUS_SUCCESS, message, notif);

        for (ClientSessionHolder session : activeSessions) {
            boolean isTarget = targetUsernames.stream().anyMatch(username ->
                    username != null && username.equalsIgnoreCase(session.getUsername()));
            if (isTarget) {
                try {
                    if (session.isConnected()) {
                        ObjectOutputStream out = session.getOutputStream();
                        synchronized (out) {
                            out.writeObject(notifPacket);
                            out.flush();
                            out.reset();
                        }
                    }
                } catch (IOException e) {
                    System.err.println("[F06] Lỗi gửi thông báo tới phiên " + session.getSessionId() + ": " + e.getMessage());
                }
            }

        }
    }

    public void sendToRole(String role, String eventType, String ticketId, String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss dd/MM"));
        Models.NotificationMessage notif = new Models.NotificationMessage(eventType, ticketId, message, timestamp);
        Protocol.Packet packet = new Protocol.Packet(Protocol.CMD_NOTIFICATION, Protocol.STATUS_SUCCESS, message, notif);
        for (ClientSessionHolder session : activeSessions) {
            if (session.isConnected() && role.equalsIgnoreCase(session.getRole())) {
                try {
                    ObjectOutputStream output = session.getOutputStream();
                    synchronized (output) {
                        output.writeObject(packet);
                        output.flush();
                        output.reset();
                    }
                } catch (IOException e) {
                    System.err.println("[F06] Không gửi được thông báo tới vai trò " + role + " / phiên "
                            + session.getSessionId() + ": " + e.getMessage());
                }
            }
        }
    }
}
