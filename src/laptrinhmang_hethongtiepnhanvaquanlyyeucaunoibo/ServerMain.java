package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

/**
 * MÁY CHỦ TCP SERVER ĐA LUỒNG (MULTI-THREADED TCP SERVER)
 * =========================================================================
 * - Quản lý ServerSocket trên cổng cấu hình (mặc định 8888).
 * - Sử dụng Thread Pool xử lý đồng thời nhiều phiên Socket Client.
 * - Tích hợp bộ điều phối F06_NotificationBroadcaster để phát thông báo tới nhiều phiên.
 * - Điều hướng yêu cầu từ các client tới các bộ xử lý F01, F02, F03, F04, F05, F08.
 * =========================================================================
 */
public class ServerMain {
    public static final int DEFAULT_PORT = 8888;
    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private ExecutorService threadPool;
    private ScheduledExecutorService slaMonitor;
    private java.util.List<NetworkSecurityConfig.Cidr> allowedNetworks = java.util.List.of();
    private final F06_NotificationBroadcaster broadcaster = new F06_NotificationBroadcaster();

    // UI Components for Server Control Panel
    private JFrame serverFrame;
    private JTextArea logArea;
    private JLabel statusLabel;
    private JLabel clientsCountLabel;
    private JLabel roleCountsLabel;
    private JLabel commandMetricsLabel;
    private final ServerMetrics metrics = new ServerMetrics();
    private DefaultTableModel clientTableModel;

    public static void main(String[] args) {
        System.setProperty("file.encoding", "UTF-8");
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            ServerMain server = new ServerMain();
            server.createAndShowGUI();
            server.startServer(DEFAULT_PORT);
        });
    }

    public ServerMain() {
        // Khởi tạo DatabaseManager (MySQL / Datastore)
        DatabaseManager.getInstance();
    }

    public void createAndShowGUI() {
        serverFrame = new JFrame("WorkDesk Server - Hệ Thống Tiếp Nhận & Quản Lý Yêu Cầu");
        serverFrame.setSize(950, 650);
        serverFrame.setMinimumSize(new Dimension(800, 500));
        serverFrame.setLocationRelativeTo(null);
        serverFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        root.setBackground(new Color(245, 247, 250));

        // Thanh trạng thái phía trên
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(new Color(22, 43, 74));
        topPanel.setBorder(new EmptyBorder(12, 18, 12, 18));

        JLabel title = new JLabel("WORKDESK TCP SERVER CONTROLLER");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));

        JPanel stats = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        stats.setOpaque(false);
        statusLabel = new JLabel("TRẠNG THÁI: ĐANG DỪNG");
        statusLabel.setForeground(Color.YELLOW);
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

        clientsCountLabel = new JLabel("Số phiên Socket: 0");
        clientsCountLabel.setForeground(Color.WHITE);
        clientsCountLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        stats.add(statusLabel);
        stats.add(clientsCountLabel);

        topPanel.add(title, BorderLayout.WEST);
        topPanel.add(stats, BorderLayout.EAST);
        root.add(topPanel, BorderLayout.NORTH);

        // Nội dung chính: Bên trái là Bảng phiên kết nối (Sessions), Bên phải là Logs
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setResizeWeight(0.45);

        // Bảng kết nối Client
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setBorder(BorderFactory.createTitledBorder("Danh sách phiên kết nối Socket (F06 Multi-Socket)"));
        clientTableModel = new DefaultTableModel(new Object[]{"Mã phiên", "Tài khoản", "Vai trò", "IP:Port"}, 0);
        JTable clientTable = new JTable(clientTableModel);
        clientTable.setRowHeight(26);
        leftPanel.add(new JScrollPane(clientTable), BorderLayout.CENTER);

        // Nhật ký hoạt động
        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        rightPanel.setBorder(BorderFactory.createTitledBorder("Nhật ký gói tin Socket & Sự kiện hệ thống"));
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(new Color(25, 27, 31));
        logArea.setForeground(new Color(220, 225, 230));
        rightPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);

        split.setLeftComponent(leftPanel);
        split.setRightComponent(rightPanel);
        root.add(split, BorderLayout.CENTER);

        // Thanh công cụ phía dưới
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 5));
        JPanel metricsPanel = new JPanel(new GridLayout(2, 1, 2, 2));
        metricsPanel.setBorder(BorderFactory.createTitledBorder("Dashboard TCP"));
        roleCountsLabel = new JLabel("Client theo vai trò: Admin 0 | KTV 0 | Người gửi 0 | Khách 0");
        commandMetricsLabel = new JLabel("Lệnh: 0 | Lỗi: 0 | Phản hồi TB: 0.00 ms | Tối đa: 0.00 ms");
        metricsPanel.add(roleCountsLabel);
        metricsPanel.add(commandMetricsLabel);
        bottomPanel.add(metricsPanel, BorderLayout.CENTER);
        JPanel bottomActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        JButton btnClearLog = ClientMainGUI.createStyledButton("Xóa nhật ký", new Color(220, 53, 69), Color.WHITE, 12);
        btnClearLog.addActionListener(e -> logArea.setText(""));
        bottomActions.add(btnClearLog);
        bottomPanel.add(bottomActions, BorderLayout.EAST);
        root.add(bottomPanel, BorderLayout.SOUTH);

        new Timer(1000, e -> refreshMetricsUI()).start();
        serverFrame.setContentPane(root);
        serverFrame.setVisible(true);
    }

    public synchronized void startServer(int port) {
        if (isRunning) return;
        try {
            allowedNetworks = NetworkSecurityConfig.getAllowedNetworks();
            serverSocket = NetworkSecurityConfig.createServerSocket(port);
            isRunning = true;
            threadPool = Executors.newCachedThreadPool();
            slaMonitor = Executors.newSingleThreadScheduledExecutor();
            slaMonitor.scheduleAtFixedRate(this::checkSlaDeadlines, 0, 1, TimeUnit.MINUTES);

            log("Máy chủ TCP đã khởi động thành công trên cổng: " + port);
            log(NetworkSecurityConfig.isTlsEnabled()
                    ? "Bảo mật kết nối: TLS đã bật."
                    : "CẢNH BÁO: TLS đang tắt; lưu lượng mạng chưa được mã hóa.");
            log("Chế độ lưu trữ: " + (DatabaseManager.getInstance().isUsingMySQL() ? "MySQL Server (workdesk_db)" : "Local Datastore (Bộ nhớ tích hợp an toàn)"));
            log("Sẵn sàng lắng nghe kết nối từ các Client (F01-F11)...");
            log("CIDR được phép: " + System.getenv().getOrDefault("WORKDESK_ALLOWED_CIDRS", "127.0.0.0/8,::1/128 (mặc định localhost)"));

            if (statusLabel != null) {
                statusLabel.setText("TRẠNG THÁI: ĐANG CHẠY (PORT " + port + ")");
                statusLabel.setForeground(Color.GREEN);
            }

            // Luồng chấp nhận kết nối (Acceptor Thread)
            threadPool.execute(() -> {
                while (isRunning && !serverSocket.isClosed()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        if (!NetworkSecurityConfig.isAddressAllowed(
                                clientSocket.getInetAddress(), allowedNetworks)) {
                            log("[Từ chối kết nối] IP ngoài CIDR được phép: "
                                    + clientSocket.getInetAddress().getHostAddress());
                            clientSocket.close();
                            continue;
                        }
                        ClientHandler handler = new ClientHandler(clientSocket);
                        threadPool.execute(handler);
                    } catch (IOException e) {
                        if (isRunning) {
                            log("Lỗi tiếp nhận kết nối Socket: " + e.getMessage());
                        }
                    }
                }
            });

        } catch (IOException e) {
            log("Lỗi khởi động Server trên cổng " + port + ": " + e.getMessage());
            if (statusLabel != null) {
                statusLabel.setText("LỖI KHỞI ĐỘNG");
                statusLabel.setForeground(Color.RED);
            }
        }
    }

    public synchronized void stopServer() {
        isRunning = false;
        try {
            if (serverSocket != null) serverSocket.close();
            broadcaster.closeAllSessions();
            if (threadPool != null) threadPool.shutdownNow();
            if (slaMonitor != null) slaMonitor.shutdownNow();
            log("Máy chủ đã dừng.");
        } catch (IOException e) {
            log("Lỗi dừng máy chủ: " + e.getMessage());
        }
    }

    public int getActiveSessionCount() {
        return broadcaster.getActiveSessionCount();
    }

    public ServerMetrics getMetrics() {
        return metrics;
    }

    void checkSlaDeadlines() {
        DatabaseManager db = DatabaseManager.getInstance();
        LocalDateTime now = LocalDateTime.now();
        for (Models.Ticket ticket : db.getSlaCandidates()) {
            if (ticket.slaDueAt == null) continue;
            try {
                LocalDateTime dueAt = LocalDateTime.parse(ticket.slaDueAt);
                double remainingHours = BusinessHours.workingHoursBetween(now, dueAt);
                boolean overdue = !dueAt.isAfter(now);
                if (overdue && !ticket.slaOverdueSent && db.markSlaAlertSent(ticket.id, true)) {
                    sendSlaAlert(ticket, "SLA QUÁ HẠN");
                    db.recordAuditEvent("SLA_OVERDUE", ticket.id, "system",
                            "Yêu cầu quá hạn xử lý theo SLA.");
                } else if (!overdue && remainingHours <= 1.0 && !ticket.slaWarningSent
                        && db.markSlaAlertSent(ticket.id, false)) {
                    sendSlaAlert(ticket, "SLA SẮP ĐẾN HẠN");
                    db.recordAuditEvent("SLA_WARNING", ticket.id, "system",
                            "Yêu cầu còn tối đa 1 giờ làm việc trước hạn SLA.");
                }
            } catch (RuntimeException e) {
                log("[SLA] Không đọc được hạn xử lý của " + ticket.id + ": " + e.getMessage());
            }
        }
    }

    private void sendSlaAlert(Models.Ticket ticket, String title) {
        String message = title + ": yêu cầu [" + ticket.id + "] " + ticket.title;
        java.util.List<String> recipients = new java.util.ArrayList<>();
        recipients.add(ticket.owner);
        if (ticket.assignedTech != null && !"Chưa phân công".equalsIgnoreCase(ticket.assignedTech)) {
            recipients.add(ticket.assignedTech);
        }
        broadcaster.sendToUsers(recipients, "SLA_ALERT", ticket.id, message);
        broadcaster.sendToRole("admin", "SLA_ALERT", ticket.id, message);
        log("[SLA] " + message);
    }

    public void log(String message) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String entry = "[" + time + "] " + message;
        System.out.println(entry);
        if (logArea != null) {
            SwingUtilities.invokeLater(() -> {
                logArea.append(entry + "\n");
                logArea.setCaretPosition(logArea.getDocument().getLength());
            });
        }
    }

    private void updateSessionUI() {
        if (clientsCountLabel != null) {
            SwingUtilities.invokeLater(this::refreshMetricsUI);
        }
    }

    private void refreshMetricsUI() {
        java.util.Map<String, Integer> roles = broadcaster.getSessionCountsByRole();
        int admins = roles.getOrDefault("admin", 0);
        int technicians = roles.getOrDefault("tech", 0);
        int requesters = roles.getOrDefault("requester", 0);
        int guests = roles.getOrDefault("none", 0);
        if (clientsCountLabel != null) {
            clientsCountLabel.setText("Số phiên Socket: " + broadcaster.getActiveSessionCount());
        }
        if (roleCountsLabel != null) {
            roleCountsLabel.setText("Client theo vai trò: Admin " + admins + " | KTV " + technicians
                    + " | Người gửi " + requesters + " | Khách " + guests);
        }
        if (commandMetricsLabel != null) {
            commandMetricsLabel.setText(String.format(java.util.Locale.ROOT,
                    "Lệnh: %d | Lỗi: %d | Phản hồi TB: %.2f ms | Tối đa: %.2f ms",
                    metrics.getProcessedCommands(), metrics.getErrors(),
                    metrics.getAverageResponseMillis(), metrics.getMaxResponseMillis()));
        }
    }

    // =========================================================================
    // XỬ LÝ PHIÊN SOCKET CLIENT ĐA LUỒNG (CLIENT HANDLER)
    // =========================================================================
    public class ClientHandler implements Runnable, F06_NotificationBroadcaster.ClientSessionHolder {
        private final Socket socket;
        private final String sessionId;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private Models.User authenticatedUser;
        private boolean connected = true;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.sessionId = UUID.randomUUID().toString().substring(0, 8);
        }

        @Override
        public String getSessionId() { return sessionId; }

        @Override
        public String getUsername() { return authenticatedUser != null ? authenticatedUser.username : "Khách"; }

        @Override
        public String getRole() { return authenticatedUser != null ? authenticatedUser.role : "none"; }

        @Override
        public ObjectOutputStream getOutputStream() { return out; }

        @Override
        public boolean isConnected() { return connected && !socket.isClosed(); }

        @Override
        public void closeConnection() throws IOException {
            connected = false;
            socket.close();
        }

        @Override
        public void run() {
            String clientIp = socket.getRemoteSocketAddress().toString();
            log("[Kết nối mới] Phiên: " + sessionId + " từ " + clientIp);

            try {
                socket.setSoTimeout(30_000);
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());

                // Đăng ký phiên vào F06 Broadcaster
                broadcaster.registerSession(this);
                updateSessionUI();

                // Vòng lặp nhận và điều phối gói tin (Protocol Dispatcher)
                while (connected && !socket.isClosed()) {
                    Object packetObj = in.readObject();
                    if (!(packetObj instanceof Protocol.Packet packet)) {
                        metrics.recordError();
                        continue;
                    }

                    log("[Nhận Packet] Từ " + getUsername() + " [" + sessionId + "] -> Lệnh: " + packet.command);
                    long startedAt = System.nanoTime();
                    Protocol.Packet responsePacket = dispatchCommand(packet);
                    if (responsePacket != null) {
                        responsePacket.correlationId = packet.correlationId;
                    }
                    metrics.recordCommand(System.nanoTime() - startedAt,
                            responsePacket == null || Protocol.STATUS_ERROR.equals(responsePacket.status));
                    SwingUtilities.invokeLater(ServerMain.this::refreshMetricsUI);

                    if (responsePacket != null) {
                        synchronized (out) {
                            out.writeObject(responsePacket);
                            out.flush();
                            out.reset();
                        }
                    }
                }
            } catch (SocketTimeoutException e) {
                log("[Hết thời gian chờ] Đóng phiên không hoạt động " + sessionId + " từ " + clientIp);
            } catch (EOFException | SocketException e) {
                // Client ngắt kết nối bình thường
            } catch (Exception e) {
                metrics.recordError();
                SwingUtilities.invokeLater(ServerMain.this::refreshMetricsUI);
                log("[Lỗi phiên " + sessionId + "] " + e.getMessage());
            } finally {
                cleanup();
            }
        }

        /**
         * Điều phối gói tin tới đúng module F01, F02, F03, F04, F05, F08
         */
        private Protocol.Packet dispatchCommand(Protocol.Packet packet) {
            if (!Protocol.CMD_LOGIN.equals(packet.command) && authenticatedUser != null) {
                Models.User currentUser = DatabaseManager.getInstance().getUserByUsername(authenticatedUser.username);
                if (currentUser == null || "Đã khóa".equalsIgnoreCase(currentUser.status)) {
                    authenticatedUser = null;
                } else {
                    authenticatedUser = currentUser;
                }
            }
            if (!Protocol.CMD_LOGIN.equals(packet.command) && authenticatedUser == null) {
                return Protocol.Packet.error(packet.command, "Bạn cần đăng nhập bằng tài khoản đang hoạt động.");
            }

            switch (packet.command) {
                case Protocol.CMD_LOGIN: {
                    return handleLogin(packet);
                }

                case Protocol.CMD_CREATE_TICKET: {
                    // F02: Tạo yêu cầu hỗ trợ kỹ thuật
                    if (!isAllowedRole("requester")) return permissionDenied(packet);
                    String sourceIp = socket.getInetAddress().getHostAddress();
                    int scopeSeparator = sourceIp.indexOf('%');
                    if (scopeSeparator >= 0) sourceIp = sourceIp.substring(0, scopeSeparator);
                    Protocol.Packet resp = F02_CreateTicket.handleServerCreateTicket(
                            packet, authenticatedUser, broadcaster, sourceIp);
                    log("[F02] " + resp.message);
                    return resp;
                }

                case Protocol.CMD_GET_TICKETS: {
                    // F03: Xem danh sách yêu cầu
                    return F03_ViewTickets.handleServerGetTickets(packet, authenticatedUser);
                }

                case Protocol.CMD_GET_TICKET_DETAIL: {
                    // F03: Xem chi tiết yêu cầu
                    return F03_ViewTickets.handleServerGetTicketDetail(packet, authenticatedUser);
                }

                case Protocol.CMD_SEARCH_TICKETS: {
                    if (authenticatedUser == null) return Protocol.Packet.error(packet.command, "Bạn cần đăng nhập.");
                    return F07_SearchTickets.handle((F07_SearchTickets.SearchRequest) packet.data, authenticatedUser);
                }

                case Protocol.CMD_GET_STATISTICS: {
                    return F09_TicketStatistics.handle((F09_TicketStatistics.StatisticsRequest) packet.data, authenticatedUser);
                }

                case Protocol.CMD_GET_HISTORY: {
                    return F10_TicketHistoryReview.history((String) packet.data, authenticatedUser);
                }

                case Protocol.CMD_RATE_TICKET: {
                    return F10_TicketHistoryReview.review((F10_TicketHistoryReview.ReviewRequest) packet.data, authenticatedUser);
                }

                case Protocol.CMD_GET_TICKET_CHAT: {
                    return F11_TicketChat.getMessages(packet, authenticatedUser);
                }

                case Protocol.CMD_GET_UNREAD_CHAT_COUNTS: {
                    return F11_TicketChat.getUnreadCounts(authenticatedUser);
                }

                case Protocol.CMD_GET_CHAT_ATTACHMENT: {
                    return F11_TicketChat.getAttachment(packet, authenticatedUser);
                }

                case Protocol.CMD_SEND_TICKET_CHAT: {
                    return F11_TicketChat.sendMessage(packet, authenticatedUser, broadcaster);
                }

                case Protocol.CMD_ASSIGN_TECH: {
                    // F04: Phân công kỹ thuật viên
                    if (!isAllowedRole("admin")) return permissionDenied(packet);
                    Protocol.Packet resp = F04_AssignTechnician.handleServerAssign(packet, authenticatedUser, broadcaster);
                    log("[F04] " + resp.message);
                    return resp;
                }

                case Protocol.CMD_DOWNLOAD_FILE: {
                    // F04: Tải file đính kèm
                    return F04_AssignTechnician.handleServerDownloadFile(packet, authenticatedUser);
                }

                case Protocol.CMD_UPDATE_STATUS: {
                    // F05: Cập nhật trạng thái xử lý và tiến độ %
                    if (!isAllowedRole("admin", "tech")) return permissionDenied(packet);
                    Protocol.Packet resp = F05_UpdateProgress.handleServerUpdateProgress(packet, authenticatedUser, broadcaster);
                    log("[F05] " + resp.message);
                    return resp;
                }

                case Protocol.CMD_GET_USERS: {
                    // F08: Lấy danh sách tài khoản (Admin)
                    if (!isAllowedRole("admin")) return permissionDenied(packet);
                    return F08_UserManagement.handleServerGetUsers(packet, authenticatedUser);
                }

                case Protocol.CMD_ADD_USER: {
                    // F08: Thêm tài khoản mới
                    if (!isAllowedRole("admin")) return permissionDenied(packet);
                    return F08_UserManagement.handleServerAddUser(packet, this.authenticatedUser);
                }

                case Protocol.CMD_UPDATE_USER: {
                    // F08: Sửa thông tin tài khoản
                    if (!isAllowedRole("admin")) return permissionDenied(packet);
                    return F08_UserManagement.handleServerUpdateUser(packet, this.authenticatedUser);
                }

                case Protocol.CMD_LOCK_USER: {
                    // F08: Khóa / mở khóa tài khoản
                    if (!isAllowedRole("admin")) return permissionDenied(packet);
                    Protocol.Packet response =
                            F08_UserManagement.handleServerLockUser(packet, this.authenticatedUser);
                    if (Protocol.STATUS_SUCCESS.equals(response.status)
                            && packet.data instanceof F08_UserManagement.LockUserRequest request
                            && request.lock) {
                        String message = "Tài khoản '" + request.targetUsername
                                + "' đã bị khóa bởi quản trị viên. Phiên đăng nhập đã bị kết thúc.";
                        log("[F08] " + message);
                        broadcaster.terminateUserSessions(request.targetUsername, message);
                        updateSessionUI();
                    }
                    return response;
                }

                case Protocol.CMD_UPDATE_TICKET: {
                    if (!isAllowedRole("admin")) return permissionDenied(packet);
                    return AdminTicketManagement.update(packet, authenticatedUser, broadcaster);
                }

                case Protocol.CMD_DELETE_TICKET: {
                    if (!isAllowedRole("admin")) return permissionDenied(packet);
                    return AdminTicketManagement.softDelete(packet, authenticatedUser, broadcaster);
                }

                case Protocol.CMD_GET_AUDIT_LOG: {
                    if (!isAllowedRole("admin")) return permissionDenied(packet);
                    return Protocol.Packet.success(Protocol.CMD_GET_AUDIT_LOG,
                            "Đã tải nhật ký kiểm toán.", DatabaseManager.getInstance().getAuditEvents(500));
                }

                case Protocol.CMD_PING: {
                    return Protocol.Packet.success(Protocol.CMD_PING, "PONG", null);
                }

                default:
                    return Protocol.Packet.error(packet.command, "Lệnh không được hỗ trợ: " + packet.command);
            }
        }

        private Protocol.Packet handleLogin(Protocol.Packet packet) {
            Protocol.Packet response = F01_Authentication.handleServerLogin(packet);
            if (!Protocol.STATUS_SUCCESS.equals(response.status)) return response;

            F01_Authentication.LoginResponse login =
                    (F01_Authentication.LoginResponse) response.data;
            synchronized (ServerMain.this) {
                if (broadcaster.hasActiveSession(login.user.username, sessionId)) {
                    String ip = socket.getInetAddress().getHostAddress();
                    log("[CẢNH BÁO] Từ chối đăng nhập trùng tài khoản '" + login.user.username
                            + "' từ " + ip + "; tài khoản đang có phiên hoạt động.");
                    return Protocol.Packet.error(Protocol.CMD_LOGIN,
                            "Tài khoản này đang đăng nhập trên một máy khác. Hãy đăng xuất phiên hiện tại trước.");
                }

                authenticatedUser = login.user;
            }
            DatabaseManager.getInstance().recordAuditEvent("USER_LOGIN",
                    authenticatedUser.username, authenticatedUser.username, "Đăng nhập thành công.");
            log("[F01] Đăng nhập thành công: " + authenticatedUser.username
                    + " (" + authenticatedUser.role + ")");
            refreshTableSession(true);
            updateSessionUI();
            return response;
        }

        private boolean isAllowedRole(String... allowedRoles) {
            if (authenticatedUser == null) return false;
            for (String role : allowedRoles) {
                if (role.equalsIgnoreCase(authenticatedUser.role)) return true;
            }
            return false;
        }

        private Protocol.Packet permissionDenied(Protocol.Packet packet) {
            return Protocol.Packet.error(packet.command, "Tài khoản của bạn không có quyền thực hiện chức năng này.");
        }

        private void refreshTableSession(boolean isAddOrUpdate) {
            if (clientTableModel == null) return;
            SwingUtilities.invokeLater(() -> {
                // Xóa dòng cũ nếu có
                for (int i = 0; i < clientTableModel.getRowCount(); i++) {
                    if (sessionId.equals(clientTableModel.getValueAt(i, 0))) {
                        clientTableModel.removeRow(i);
                        break;
                    }
                }
                if (isAddOrUpdate && connected) {
                    clientTableModel.addRow(new Object[]{
                            sessionId,
                            getUsername(),
                            getRole(),
                            socket.getRemoteSocketAddress().toString()
                    });
                }
            });
        }

        private void cleanup() {
            connected = false;
            broadcaster.unregisterSession(this);
            refreshTableSession(false);
            updateSessionUI();
            log("[Ngắt kết nối] Phiên: " + sessionId + " (" + getUsername() + ")");
            try {
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException ignored) {}
        }
    }
}
