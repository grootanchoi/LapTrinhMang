package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private final F06_NotificationBroadcaster broadcaster = new F06_NotificationBroadcaster();

    // UI Components for Server Control Panel
    private JFrame serverFrame;
    private JTextArea logArea;
    private JLabel statusLabel;
    private JLabel clientsCountLabel;
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
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        JButton btnClearLog = ClientMainGUI.createStyledButton("Xóa nhật ký", new Color(220, 53, 69), Color.WHITE, 12);
        btnClearLog.addActionListener(e -> logArea.setText(""));
        bottomPanel.add(btnClearLog);
        root.add(bottomPanel, BorderLayout.SOUTH);

        serverFrame.setContentPane(root);
        serverFrame.setVisible(true);
    }

    public synchronized void startServer(int port) {
        if (isRunning) return;
        try {
            serverSocket = new ServerSocket(port);
            isRunning = true;
            threadPool = Executors.newCachedThreadPool();

            log("Máy chủ TCP đã khởi động thành công trên cổng: " + port);
            log("Chế độ lưu trữ: " + (DatabaseManager.getInstance().isUsingMySQL() ? "MySQL Server (workdesk_db)" : "Local Datastore (Bộ nhớ tích hợp an toàn)"));
            log("Sẵn sàng lắng nghe kết nối từ các Client (F01, F02, F03, F04, F05, F06, F08)...");

            if (statusLabel != null) {
                statusLabel.setText("TRẠNG THÁI: ĐANG CHẠY (PORT " + port + ")");
                statusLabel.setForeground(Color.GREEN);
            }

            // Luồng chấp nhận kết nối (Acceptor Thread)
            threadPool.execute(() -> {
                while (isRunning && !serverSocket.isClosed()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
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
            if (threadPool != null) threadPool.shutdownNow();
            log("Máy chủ đã dừng.");
        } catch (IOException e) {
            log("Lỗi dừng máy chủ: " + e.getMessage());
        }
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
            SwingUtilities.invokeLater(() -> clientsCountLabel.setText("Số phiên Socket: " + broadcaster.getActiveSessionCount()));
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
        public void run() {
            String clientIp = socket.getRemoteSocketAddress().toString();
            log("[Kết nối mới] Phiên: " + sessionId + " từ " + clientIp);

            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());

                // Đăng ký phiên vào F06 Broadcaster
                broadcaster.registerSession(this);
                updateSessionUI();

                // Vòng lặp nhận và điều phối gói tin (Protocol Dispatcher)
                while (connected && !socket.isClosed()) {
                    Object packetObj = in.readObject();
                    if (!(packetObj instanceof Protocol.Packet packet)) continue;

                    log("[Nhận Packet] Từ " + getUsername() + " [" + sessionId + "] -> Lệnh: " + packet.command);
                    Protocol.Packet responsePacket = dispatchCommand(packet);

                    if (responsePacket != null) {
                        synchronized (out) {
                            out.writeObject(responsePacket);
                            out.flush();
                            out.reset();
                        }
                    }
                }
            } catch (EOFException | SocketException e) {
                // Client ngắt kết nối bình thường
            } catch (Exception e) {
                log("[Lỗi phiên " + sessionId + "] " + e.getMessage());
            } finally {
                cleanup();
            }
        }

        /**
         * Điều phối gói tin tới đúng module F01, F02, F03, F04, F05, F08
         */
        private Protocol.Packet dispatchCommand(Protocol.Packet packet) {
            switch (packet.command) {
                case Protocol.CMD_LOGIN: {
                    // F01: Đăng nhập & Xác thực
                    Protocol.Packet resp = F01_Authentication.handleServerLogin(packet);
                    if (Protocol.STATUS_SUCCESS.equals(resp.status)) {
                        F01_Authentication.LoginResponse lr = (F01_Authentication.LoginResponse) resp.data;
                        this.authenticatedUser = lr.user;
                        log("[F01] Đăng nhập thành công: " + authenticatedUser.username + " (" + authenticatedUser.role + ")");
                        refreshTableSession(true);
                    }
                    return resp;
                }

                case Protocol.CMD_CREATE_TICKET: {
                    // F02: Tạo yêu cầu hỗ trợ kỹ thuật
                    Protocol.Packet resp = F02_CreateTicket.handleServerCreateTicket(packet, broadcaster);
                    log("[F02] " + resp.message);
                    return resp;
                }

                case Protocol.CMD_GET_TICKETS: {
                    // F03: Xem danh sách yêu cầu
                    return F03_ViewTickets.handleServerGetTickets(packet);
                }

                case Protocol.CMD_GET_TICKET_DETAIL: {
                    // F03: Xem chi tiết yêu cầu
                    return F03_ViewTickets.handleServerGetTicketDetail(packet);
                }

                case Protocol.CMD_ASSIGN_TECH: {
                    // F04: Phân công kỹ thuật viên
                    Protocol.Packet resp = F04_AssignTechnician.handleServerAssign(packet, broadcaster);
                    log("[F04] " + resp.message);
                    return resp;
                }

                case Protocol.CMD_DOWNLOAD_FILE: {
                    // F04: Tải file đính kèm
                    return F04_AssignTechnician.handleServerDownloadFile(packet);
                }

                case Protocol.CMD_UPDATE_STATUS: {
                    // F05: Cập nhật trạng thái xử lý và tiến độ %
                    Protocol.Packet resp = F05_UpdateProgress.handleServerUpdateProgress(packet, broadcaster);
                    log("[F05] " + resp.message);
                    return resp;
                }

                case Protocol.CMD_GET_USERS: {
                    // F08: Lấy danh sách tài khoản (Admin)
                    return F08_UserManagement.handleServerGetUsers(packet);
                }

                case Protocol.CMD_ADD_USER: {
                    // F08: Thêm tài khoản mới
                    return F08_UserManagement.handleServerAddUser(packet, this.authenticatedUser);
                }

                case Protocol.CMD_UPDATE_USER: {
                    // F08: Sửa thông tin tài khoản
                    return F08_UserManagement.handleServerUpdateUser(packet, this.authenticatedUser);
                }

                case Protocol.CMD_LOCK_USER: {
                    // F08: Khóa / mở khóa tài khoản
                    return F08_UserManagement.handleServerLockUser(packet, this.authenticatedUser);
                }

                default:
                    return Protocol.Packet.error(packet.command, "Lệnh không được hỗ trợ: " + packet.command);
            }
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
