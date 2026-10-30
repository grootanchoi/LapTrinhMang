package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * GIAO DIỆN CLIENT ĐỒ HỌA SWING (CLIENT WORKSPACE)
 * Tích hợp toàn diện các chức năng F01, F02, F03, F04, F05, F06, F08.
 */
public class ClientMainGUI {
    // Màu sắc giao diện chuyên nghiệp
    private static final Color NAVY = new Color(20, 35, 60);
    private static final Color BLUE = new Color(30, 110, 220);
    private static final Color LIGHT_BG = new Color(244, 247, 252);
    private static final Color CARD_BG = Color.WHITE;
    private static final Color TEXT_DARK = new Color(33, 43, 54);
    private static final Color GREEN = new Color(34, 160, 88);
    private static final Color AMBER = new Color(217, 119, 6);
    private static final Color BORDER = new Color(226, 232, 240);

    private JFrame mainFrame;
    private NetworkService networkService;
    private String serverHost = "127.0.0.1";
    private int serverPort = 8888;
    private Models.User currentUser;
    private final java.util.Map<String, JPanel> openTicketChats = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, List<PendingChatEvent>> pendingTicketChatMessages = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<String> loadingTicketChats = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Map<String, Integer> unreadChatCounts = new java.util.concurrent.ConcurrentHashMap<>();

    // Bảng dữ liệu và cache
    private DefaultTableModel ticketTableModel;
    private JTable ticketTable;
    private List<Models.Ticket> cachedTickets = new ArrayList<>();
    private JLabel statusConnectionLabel;
    private JLabel footerConnectionDot;
    private JLabel footerConnectionText;
    private JLabel footerSecurityBadge;
    private JLabel footerIdentity;
    private JLabel footerLatency;
    private JLabel toastMessage;
    private JPanel toastContainer;
    private Timer networkStatusTimer;
    private Timer toastTimer;
    private JLabel inspectorId;
    private JTextArea inspectorTitle;
    private JLabel inspectorOwner;
    private JLabel inspectorDepartment;
    private JLabel inspectorDevice;
    private JLabel inspectorSourceIp;
    private JLabel inspectorCreatedAt;
    private JLabel inspectorSla;
    private JLabel inspectorLatestNote;
    private JProgressBar inspectorProgress;
    private JPanel inspectorChatHost;
    private String inspectorChatTicketId;
    private final java.util.Map<String, JPanel> inspectorTicketChats =
            new java.util.concurrent.ConcurrentHashMap<>();
    private boolean statusDotVisible = true;

    private static final class PendingChatEvent {
        private final String message;
        private final long attachmentId;
        private final String attachmentName;

        private PendingChatEvent(String message, long attachmentId, String attachmentName) {
            this.message = message;
            this.attachmentId = attachmentId;
            this.attachmentName = attachmentName;
        }
    }

    public static void main(String[] args) {
        System.setProperty("file.encoding", "UTF-8");
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new ClientMainGUI().showLoginWindow();
        });
    }

    // =========================================================================
    // F01: ĐĂNG NHẬP VÀ XÁC THỰC TÀI KHOẢN (IP, Port / Trạng thái kết nối)
    // =========================================================================
    public void showLoginWindow() {
        if (mainFrame != null) mainFrame.dispose();
        mainFrame = new JFrame("WorkDesk - Đăng nhập hệ thống (F01)");
        mainFrame.setSize(500, 620);
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(LIGHT_BG);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CARD_BG);
        card.setBorder(new EmptyBorder(30, 35, 30, 35));
        card.setPreferredSize(new Dimension(420, 530));

        JLabel brand = new JLabel("WORKDESK CONNECT");
        brand.setFont(new Font("Segoe UI", Font.BOLD, 13));
        brand.setForeground(BLUE);

        JLabel title = new JLabel("Đăng nhập hệ thống");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(NAVY);

        JLabel sub = new JLabel("Hỗ trợ kỹ thuật & Quản lý yêu cầu nội bộ");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(Color.GRAY);

        // Cấu hình IP và Port (Đầu vào F01)
        JPanel netPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        netPanel.setOpaque(false);
        JTextField txtHost = new JTextField("127.0.0.1");
        JTextField txtPort = new JTextField("8888");
        JPanel hostBox = new JPanel(new BorderLayout(0, 4)); hostBox.setOpaque(false);
        hostBox.add(new JLabel("Máy chủ IP:"), BorderLayout.NORTH); hostBox.add(txtHost, BorderLayout.CENTER);
        JPanel portBox = new JPanel(new BorderLayout(0, 4)); portBox.setOpaque(false);
        portBox.add(new JLabel("Cổng Port:"), BorderLayout.NORTH); portBox.add(txtPort, BorderLayout.CENTER);
        netPanel.add(hostBox); netPanel.add(portBox);

        JTextField txtUser = new JTextField("admin");
        JPasswordField txtPass = new JPasswordField("admin123");

        statusConnectionLabel = new JLabel("Trạng thái: Chưa kết nối");
        statusConnectionLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        statusConnectionLabel.setForeground(Color.DARK_GRAY);

        JButton btnLogin = createStyledButton("Đăng nhập Socket TCP", new Color(13, 110, 253), Color.WHITE, 14);
        btnLogin.setPreferredSize(new Dimension(350, 42));
        btnLogin.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

        // Thêm gợi ý tài khoản mẫu
        JLabel note = new JLabel("<html><font color='#666666'>Tài khoản mẫu: <b>admin</b> (MK: admin123)<br>Kỹ thuật viên: <b>tech01</b> (MK: tech123)<br>Nhân viên: <b>user01</b> (MK: user123)</font></html>");
        note.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        card.add(brand);
        card.add(Box.createVerticalStrut(5));
        card.add(title);
        card.add(Box.createVerticalStrut(3));
        card.add(sub);
        card.add(Box.createVerticalStrut(18));
        card.add(netPanel);
        card.add(Box.createVerticalStrut(12));
        card.add(new JLabel("Tên đăng nhập:"));
        card.add(Box.createVerticalStrut(4));
        card.add(txtUser);
        card.add(Box.createVerticalStrut(10));
        card.add(new JLabel("Mật khẩu:"));
        card.add(Box.createVerticalStrut(4));
        card.add(txtPass);
        card.add(Box.createVerticalStrut(8));
        card.add(statusConnectionLabel);
        card.add(Box.createVerticalStrut(15));
        card.add(btnLogin);
        card.add(Box.createVerticalStrut(15));
        card.add(note);

        btnLogin.addActionListener(e -> {
            String host = txtHost.getText().trim();
            int port;
            try {
                port = Integer.parseInt(txtPort.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(mainFrame, "Cổng Port phải là số nguyên hợp lệ.");
                return;
            }
            String username = txtUser.getText().trim();
            String password = new String(txtPass.getPassword());
            serverHost = host;
            serverPort = port;

            statusConnectionLabel.setText("Đang kết nối đến " + host + ":" + port + "...");
            statusConnectionLabel.setForeground(BLUE);

            // Thực hiện F01 qua TCP Socket
            F01_Authentication.ConnectionResult res = F01_Authentication.performLogin(host, port, username, password);
            if (res.isConnected) {
                this.currentUser = res.loggedInUser;
                this.networkService = new NetworkService(this::handleNetworkPacket,
                        this::handleNetworkDisconnect, this::handleNetworkReconnected);
                this.networkService.setReconnectCredentials(host, port, username, password);
                try {
                    this.networkService.connect(res.socket, res.in, res.out);
                } catch (IOException ex) {
                    this.networkService.close();
                    this.networkService = null;
                    this.currentUser = null;
                    JOptionPane.showMessageDialog(mainFrame, ex.getMessage(), "Lỗi kết nối",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
                statusConnectionLabel.setText("Kết nối thành công!");
                statusConnectionLabel.setForeground(GREEN);

                // Hiển thị giao diện điều khiển chính
                showDashboard();
            } else {
                statusConnectionLabel.setText("Thất bại: " + res.statusMessage);
                statusConnectionLabel.setForeground(Color.RED);
                JOptionPane.showMessageDialog(mainFrame, res.statusMessage, "Lỗi đăng nhập", JOptionPane.WARNING_MESSAGE);
            }
        });

        txtPass.addActionListener(e -> btnLogin.doClick());

        root.add(card);
        mainFrame.setContentPane(root);
        mainFrame.setVisible(true);
    }

    private void handleNetworkPacket(Protocol.Packet packet) {
        if (!(packet.data instanceof Models.NotificationMessage notification)) return;
        if (!"SESSION_REVOKED".equals(notification.eventType)) {
            SwingUtilities.invokeLater(() -> {
                if ("TICKET_CHAT_MESSAGE".equals(notification.eventType)) {
                    appendTicketChatMessage(notification.ticketId, notification.message,
                            notification.attachmentMessageId, notification.attachmentName);
                    if (openTicketChats.containsKey(notification.ticketId)
                            || inspectorTicketChats.containsKey(notification.ticketId)) {
                        markChatRead(notification.ticketId);
                    } else {
                        refreshChatUnreadCounts();
                    }
                }
                if ("TICKET_CREATED".equals(notification.eventType)
                        || "STATUS_CHANGED".equals(notification.eventType)
                        || "TICKET_ASSIGNED".equals(notification.eventType)
                        || "TICKET_UPDATED".equals(notification.eventType)) {
                    requestTicketList();
                }
                if (!"TICKET_CHAT_MESSAGE".equals(notification.eventType)
                        || !openTicketChats.containsKey(notification.ticketId)) {
                    showToastNotification("THÔNG BÁO HỆ THỐNG (" + notification.timestamp + ")\n"
                            + notification.message);
                }
            });
            return;
        }

        SwingUtilities.invokeLater(() -> {
            logout();
            JOptionPane.showMessageDialog(mainFrame, notification.message,
                    "Phiên đăng nhập đã kết thúc", JOptionPane.WARNING_MESSAGE);
        });
    }

    private void handleNetworkDisconnect(Exception cause) {
        SwingUtilities.invokeLater(() -> {
            if (mainFrame != null) {
                mainFrame.setTitle("WorkDesk | Đang mất kết nối - tự động kết nối lại...");
                refreshNetworkStatusBar();
            }
        });
        System.err.println("[Client] Mất kết nối tới Server: " + cause.getMessage());
    }

    private void handleNetworkReconnected(Models.User user) {
        SwingUtilities.invokeLater(() -> {
            currentUser = user;
            if (mainFrame != null) {
                mainFrame.setTitle("WorkDesk | Đã kết nối lại - [" + currentUser.getRoleName() + "]");
            }
            refreshNetworkStatusBar();
            requestTicketList();
            refreshChatUnreadCounts();
            reloadOpenChatHistories();
        });
        System.out.println("[Client] Đã kết nối lại tới Server.");
    }

    private void reloadOpenChatHistories() {
        for (String ticketId : openTicketChats.keySet()) {
            new Thread(() -> {
                try {
                    Protocol.Packet response = sendAndReceiveSync(new Protocol.Packet(
                            Protocol.CMD_GET_TICKET_CHAT, "", "",
                            new F11_TicketChat.ChatRequest(ticketId, "")));
                    if (!Protocol.STATUS_SUCCESS.equals(response.status)
                            || !(response.data instanceof List<?> rows)) {
                        throw new IOException(response.message);
                    }
                    List<Models.TicketChatMessage> messages = rows.stream()
                            .filter(Models.TicketChatMessage.class::isInstance)
                            .map(Models.TicketChatMessage.class::cast).toList();
                    SwingUtilities.invokeLater(() -> {
                        JPanel transcript = openTicketChats.get(ticketId);
                        if (transcript == null) return;
                        transcript.removeAll();
                        for (Models.TicketChatMessage message : messages) {
                            renderChatMessage(ticketId, transcript, message);
                        }
                        transcript.revalidate();
                        transcript.repaint();
                    });
                } catch (Exception e) {
                    System.err.println("[F11] Không thể đồng bộ lại lịch sử " + ticketId + ": " + e.getMessage());
                }
            }, "f11-reload-after-reconnect").start();
        }
    }

    private void showToastNotification(String message) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> showToastNotification(message));
            return;
        }
        if (toastMessage == null || toastContainer == null) return;
        toastMessage.setText("<html><b>WorkDesk</b>&nbsp;&nbsp;" + escapeHtml(message).replace("\n", "<br>") + "</html>");
        toastMessage.setForeground(TEXT_DARK);
        toastContainer.setBackground(new Color(239, 246, 255));
        toastContainer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(147, 197, 253)),
                new EmptyBorder(10, 14, 10, 14)));
        toastContainer.setVisible(true);
        toastContainer.revalidate();
        if (toastTimer != null) toastTimer.stop();
        toastTimer = new Timer(120, null);
        final int[] fadeStep = {0};
        toastTimer.addActionListener(event -> {
            fadeStep[0]++;
            if (fadeStep[0] < 6) {
                int component = 239 + (fadeStep[0] * 16 / 6);
                toastContainer.setBackground(new Color(component, component, component));
            } else {
                toastTimer.stop();
                toastContainer.setVisible(false);
            }
        });
        toastTimer.setInitialDelay(3600);
        toastTimer.start();
    }

    private JPanel createNetworkFooter() {
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setBackground(Color.WHITE);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
                new EmptyBorder(7, 18, 7, 18)));

        JPanel connection = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        connection.setOpaque(false);
        footerConnectionDot = new JLabel("●");
        footerConnectionDot.setFont(new Font("Segoe UI", Font.BOLD, 13));
        footerConnectionText = new JLabel("Đang kiểm tra kết nối...");
        footerConnectionText.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        connection.add(footerConnectionDot);
        connection.add(footerConnectionText);

        footerSecurityBadge = new JLabel();
        footerSecurityBadge.setOpaque(true);
        footerSecurityBadge.setBorder(new EmptyBorder(3, 8, 3, 8));
        footerSecurityBadge.setFont(new Font("Segoe UI", Font.BOLD, 10));
        footerSecurityBadge.setForeground(new Color(21, 101, 69));
        footerSecurityBadge.setBackground(new Color(220, 252, 231));

        footerIdentity = new JLabel();
        footerIdentity.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        footerIdentity.setForeground(TEXT_DARK);
        footerLatency = new JLabel("Heartbeat: chờ đo");
        footerLatency.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        footerLatency.setForeground(new Color(71, 85, 105));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        right.setOpaque(false);
        right.add(footerSecurityBadge);
        right.add(footerIdentity);
        right.add(footerLatency);
        footer.add(connection, BorderLayout.WEST);
        footer.add(right, BorderLayout.EAST);

        toastMessage = new JLabel();
        toastMessage.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        toastContainer = new JPanel(new BorderLayout());
        toastContainer.setOpaque(true);
        toastContainer.setVisible(false);
        toastContainer.add(toastMessage, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(0, 4));
        bottom.setOpaque(false);
        JPanel toastRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 18, 0));
        toastRow.setOpaque(false);
        toastRow.add(toastContainer);
        bottom.add(toastRow, BorderLayout.CENTER);
        bottom.add(footer, BorderLayout.SOUTH);

        if (networkStatusTimer != null) networkStatusTimer.stop();
        networkStatusTimer = new Timer(500, event -> {
            statusDotVisible = !statusDotVisible;
            refreshNetworkStatusBar();
        });
        networkStatusTimer.start();
        refreshNetworkStatusBar();
        return bottom;
    }

    private void refreshNetworkStatusBar() {
        if (footerConnectionDot == null || footerConnectionText == null) return;
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::refreshNetworkStatusBar);
            return;
        }
        boolean connected = networkService != null && networkService.isConnected();
        boolean reconnecting = networkService != null && networkService.isReconnecting();
        if (connected) {
            footerConnectionDot.setForeground(GREEN);
            footerConnectionText.setText("Đã kết nối  " + serverHost + ":" + serverPort);
        } else if (reconnecting) {
            footerConnectionDot.setForeground(statusDotVisible ? AMBER : new Color(255, 237, 213));
            footerConnectionText.setText("Đang kết nối lại...");
        } else {
            footerConnectionDot.setForeground(new Color(220, 53, 69));
            footerConnectionText.setText("Mất kết nối tới " + serverHost + ":" + serverPort);
        }
        footerSecurityBadge.setText(networkService == null
                ? (NetworkSecurityConfig.isTlsEnabled() ? "TLS" : "Plaintext TCP")
                : networkService.getSecurityLabel());
        footerIdentity.setText(currentUser == null ? "" : currentUser.username + "  ·  "
                + currentUser.getRoleName());
        long rtt = networkService == null ? -1 : networkService.getLastHeartbeatRttMillis();
        footerLatency.setText(rtt < 0 ? "Heartbeat: mỗi 15 giây"
                : "Heartbeat RTT: " + rtt + " ms");
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    // =========================================================================
    // GIAO DIỆN CHÍNH (DASHBOARD) - HIỂN THỊ DANH SÁCH & ĐIỀU KHIỂN CÁC CHỨC NĂNG
    // =========================================================================
    public void showDashboard() {
        if (mainFrame != null) mainFrame.dispose();
        boolean isAdmin = "admin".equalsIgnoreCase(currentUser.role);
        boolean isRequester = "requester".equalsIgnoreCase(currentUser.role);
        boolean canManageTickets = "admin".equalsIgnoreCase(currentUser.role)
                || "tech".equalsIgnoreCase(currentUser.role);
        boolean canUpdateProgress = "tech".equalsIgnoreCase(currentUser.role);
        boolean canAssignTechnician = isAdmin;
        mainFrame = new JFrame("WorkDesk | Bàn làm việc - [" + currentUser.displayName + " / " + currentUser.getRoleName() + "]");
        mainFrame.setSize(1180, 720);
        mainFrame.setMinimumSize(new Dimension(900, 600));
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(LIGHT_BG);

        // Header trên cùng
        JPanel header = new JPanel(new GridLayout(2, 1, 0, 8));
        header.setBackground(NAVY);
        header.setBorder(new EmptyBorder(14, 24, 14, 24));

        JLabel lblLogo = new JLabel("WORKDESK  ·  HỆ THỐNG TIẾP NHẬN & QUẢN LÝ YÊU CẦU NỘI BỘ");
        lblLogo.setForeground(Color.WHITE);
        lblLogo.setFont(new Font("Segoe UI", Font.BOLD, 15));

        JPanel userPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        userPanel.setOpaque(false);
        JLabel lblUser = new JLabel(currentUser.displayName + " (" + currentUser.getRoleName() + ")");
        lblUser.setForeground(new Color(220, 235, 255));
        lblUser.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JButton btnUsers = createStyledButton("Quản lý tài khoản", new Color(25, 135, 84), Color.WHITE, 12);
        btnUsers.setVisible(isAdmin);
        btnUsers.addActionListener(e -> showUserManagementWindow());

        JButton btnAudit = createStyledButton("Nhật ký kiểm toán", new Color(111, 66, 193), Color.WHITE, 12);
        btnAudit.setVisible(isAdmin);
        btnAudit.addActionListener(e -> showAuditLogWindow());

        JButton btnLogout = createStyledButton("Đăng xuất", new Color(220, 53, 69), Color.WHITE, 12);
        btnLogout.addActionListener(e -> logout());

        userPanel.add(lblUser);
        userPanel.add(btnUsers);
        userPanel.add(btnAudit);
        userPanel.add(btnLogout);

        header.add(lblLogo);
        header.add(userPanel);
        root.add(header, BorderLayout.NORTH);

        // Body Content
        JPanel body = new JPanel(new BorderLayout(0, 15));
        body.setBackground(LIGHT_BG);
        body.setBorder(new EmptyBorder(20, 25, 20, 25));

        // Action Toolbar
        JPanel toolbar = new JPanel(new GridLayout(2, 1, 0, 6));
        toolbar.setOpaque(false);

        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftActions.setOpaque(false);

        JButton btnNewTicket = createStyledButton("+  Tạo yêu cầu mới", new Color(13, 110, 253), Color.WHITE, 13);
        btnNewTicket.setVisible(isRequester);
        btnNewTicket.addActionListener(e -> showCreateTicketDialog());

        JButton btnExportFile = createStyledButton("Xuất danh sách ra File", Color.WHITE, new Color(20, 35, 60), 13);
        btnExportFile.addActionListener(e -> exportTicketListToFile());

        JButton btnRefresh = createStyledButton("Làm mới danh sách", Color.WHITE, new Color(20, 35, 60), 13);
        btnRefresh.addActionListener(e -> requestTicketList());
        JButton btnStats = createStyledButton("Thống kê", new Color(111, 66, 193), Color.WHITE, 12);
        btnStats.addActionListener(e -> showStatisticsDialog());

        leftActions.add(btnNewTicket);
        leftActions.add(btnExportFile);
        leftActions.add(btnRefresh);
        leftActions.add(btnStats);

        // Ô tìm kiếm nhanh
        JTextField txtSearch = new JTextField();
        txtSearch.setPreferredSize(new Dimension(230, 34));
        txtSearch.setToolTipText("Tìm kiếm theo mã, tiêu đề, danh mục, trạng thái...");
        JPanel searchBox = new JPanel(new BorderLayout(5, 0));
        searchBox.setOpaque(false);
        JComboBox<String> cbStatus = new JComboBox<>(new String[]{"Tất cả trạng thái", "Mới", "Đang xử lý", "Hoàn thành", "Đã hủy"});
        JComboBox<String> cbCategory = new JComboBox<>(new String[]{"Tất cả danh mục", "IT - Phần cứng", "Mạng & Internet", "Phần mềm & Cấp quyền", "Nhân sự", "Hành chính", "Tài chính"});
        JComboBox<String> cbPriority = new JComboBox<>(new String[]{"Tất cả ưu tiên", "Thấp", "Bình thường", "Cao", "Khẩn cấp"});
        JButton btnSearch = createStyledButton("Tìm / Lọc", BLUE, Color.WHITE, 12);
        JPanel filterControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0)); filterControls.setOpaque(false);
        filterControls.add(new JLabel("Tìm kiếm:")); filterControls.add(txtSearch); filterControls.add(cbStatus); filterControls.add(cbCategory); filterControls.add(cbPriority); filterControls.add(btnSearch);
        searchBox.add(filterControls, BorderLayout.CENTER);
        btnSearch.addActionListener(e -> searchTickets(txtSearch.getText(), (String) cbStatus.getSelectedItem(), (String) cbCategory.getSelectedItem(), (String) cbPriority.getSelectedItem()));
        txtSearch.addActionListener(e -> btnSearch.doClick());

        toolbar.add(leftActions);
        toolbar.add(searchBox);
        body.add(toolbar, BorderLayout.NORTH);

        // Bảng danh sách Ticket (F03)
        JPanel tableCard = new JPanel(new BorderLayout(0, 8));
        tableCard.setBackground(CARD_BG);
        tableCard.setBorder(new EmptyBorder(15, 15, 15, 15));
        tableCard.setMinimumSize(new Dimension(350, 200));

        ticketTableModel = new DefaultTableModel(
                new Object[]{"Mã YC", "Tiêu đề", "Danh mục", "Mức ưu tiên", "Người gửi", "Kỹ thuật viên", "Tiến độ", "Trạng thái", "Hạn SLA", "File đính kèm"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };

        JTable table = new JTable(ticketTableModel);
        ticketTable = table;
        table.setRowHeight(36);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        table.setFillsViewportHeight(true);
        table.setShowVerticalLines(false);
        table.setGridColor(new Color(241, 245, 249));
        table.getTableHeader().setPreferredSize(new Dimension(0, 38));
        table.getTableHeader().setBackground(new Color(248, 250, 252));
        table.getTableHeader().setForeground(new Color(71, 85, 105));
        table.getColumnModel().getColumn(3).setCellRenderer(new PillRenderer("priority"));
        table.getColumnModel().getColumn(6).setCellRenderer(new MiniProgressRenderer());
        table.getColumnModel().getColumn(7).setCellRenderer(new PillRenderer("status"));
        table.getColumnModel().getColumn(8).setCellRenderer(new SlaCellRenderer());
        table.getColumnModel().getColumn(0).setPreferredWidth(115);
        table.getColumnModel().getColumn(1).setPreferredWidth(220);
        table.getColumnModel().getColumn(3).setPreferredWidth(110);
        table.getColumnModel().getColumn(6).setPreferredWidth(100);
        table.getColumnModel().getColumn(7).setPreferredWidth(115);
        table.getColumnModel().getColumn(8).setPreferredWidth(135);
        table.getColumnModel().getColumn(9).setPreferredWidth(140);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                refreshTicketInspector(getSelectedTicket());
            }
        });

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (table.rowAtPoint(e.getPoint()) == -1) {
                    table.clearSelection();
                    return;
                }
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) {
                    int row = table.getSelectedRow();
                    String ticketId = (String) table.getValueAt(row, 0);
                    showTicketDetailDialog(ticketId);
                }
            }
        });

        tableCard.add(new JScrollPane(table), BorderLayout.CENTER);

        // Thanh thao tác dưới bảng: Phân công (F04), Cập nhật tiến độ (F05), Tải file (F04)
        JPanel bottomBar = new JPanel(new BorderLayout(10, 5));
        bottomBar.setOpaque(false);

        JPanel actionButtons = new JPanel(new GridLayout(2, 1, 0, 4));
        actionButtons.setOpaque(false);
        JPanel primaryActions = new JPanel(new GridLayout(1, 0, 6, 0));
        primaryActions.setOpaque(false);
        JPanel secondaryActions = new JPanel(new GridLayout(1, 0, 6, 0));
        secondaryActions.setOpaque(false);

        JButton btnAssign = createStyledButton("Phân công", new Color(13, 110, 253), Color.WHITE, 12);
        btnAssign.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(mainFrame, "Vui lòng chọn một yêu cầu cần phân công."); return; }
            showAssignDialog((String) table.getValueAt(row, 0));
        });

        JButton btnUpdateStatus = createStyledButton("Tiến độ", new Color(25, 135, 84), Color.WHITE, 12);
        btnUpdateStatus.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(mainFrame, "Vui lòng chọn một yêu cầu cần cập nhật."); return; }
            showUpdateStatusDialog((String) table.getValueAt(row, 0));
        });

        JButton btnDownloadAttach = createStyledButton("Tải tệp", new Color(75, 85, 99), Color.WHITE, 12);
        btnDownloadAttach.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(mainFrame, "Vui lòng chọn một yêu cầu có file đính kèm."); return; }
            String fileName = (String) table.getValueAt(row, 9);
            if (fileName == null || fileName.trim().isEmpty()) {
                JOptionPane.showMessageDialog(mainFrame, "Yêu cầu này không có file đính kèm.");
                return;
            }
            String ticketId = (String) table.getValueAt(row, 0);
            downloadAttachmentFile(ticketId, fileName);
        });

        JButton btnChat = createStyledButton("Trao đổi", new Color(111, 66, 193), Color.WHITE, 12);
        chatActionButton = btnChat;
        btnChat.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(mainFrame, "Vui lòng chọn yêu cầu cần trao đổi.");
                return;
            }
            String ticketId = (String) table.getValueAt(row, 0);
            Models.Ticket ticket = cachedTickets.stream()
                    .filter(item -> item.id.equals(ticketId))
                    .findFirst().orElse(null);
            if (ticket != null) showTicketChatDialog(ticket);
        });

        JButton btnHistory = createStyledButton("Lịch sử & Đánh giá",
                new Color(111, 66, 193), Color.WHITE, 12);
        btnHistory.addActionListener(e -> {
            Models.Ticket ticket = getSelectedTicket();
            if (ticket == null) {
                showToastNotification("Vui lòng chọn một yêu cầu để xem lịch sử.");
            } else {
                showHistoryReviewDialog(ticket);
            }
        });

        JButton btnEditTicket = createStyledButton("Sửa", Color.WHITE, new Color(20, 35, 60), 12);
        btnEditTicket.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(mainFrame, "Vui lòng chọn yêu cầu cần sửa."); return; }
            String ticketId = (String) table.getValueAt(row, 0);
            cachedTickets.stream().filter(item -> item.id.equals(ticketId)).findFirst()
                    .ifPresent(this::showEditTicketDialog);
        });

        JButton btnDeleteTicket = createStyledButton("Xóa", new Color(220, 53, 69), Color.WHITE, 12);
        btnDeleteTicket.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(mainFrame, "Vui lòng chọn yêu cầu cần xóa."); return; }
            deleteTicket((String) table.getValueAt(row, 0));
        });

        if (canAssignTechnician) primaryActions.add(btnAssign);
        if (canUpdateProgress) primaryActions.add(btnUpdateStatus);
        primaryActions.add(btnDownloadAttach);
        secondaryActions.add(btnChat);
        secondaryActions.add(btnHistory);
        if (isAdmin) {
            secondaryActions.add(btnEditTicket);
            secondaryActions.add(btnDeleteTicket);
        }
        actionButtons.add(primaryActions);
        actionButtons.add(secondaryActions);

        bottomBar.add(actionButtons, BorderLayout.CENTER);
        tableCard.add(bottomBar, BorderLayout.SOUTH);

        JPanel inspector = createTicketInspector(isAdmin, canManageTickets, canAssignTechnician,
                canUpdateProgress);
        inspector.setMinimumSize(new Dimension(300, 200));
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableCard, inspector);
        splitPane.setResizeWeight(0.60);
        splitPane.setDividerLocation(0.60);
        splitPane.setBorder(null);
        splitPane.setContinuousLayout(true);
        body.add(splitPane, BorderLayout.CENTER);
        root.add(body, BorderLayout.CENTER);
        root.add(createNetworkFooter(), BorderLayout.SOUTH);

        mainFrame.setContentPane(root);
        mainFrame.setVisible(true);

        // Lấy danh sách lần đầu
        requestTicketList();
        refreshChatUnreadCounts();
    }

    private JPanel createTicketInspector(boolean isAdmin, boolean canManageTickets,
                                         boolean canAssignTechnician, boolean canUpdateProgress) {
        JPanel inspector = new JPanel(new BorderLayout(0, 10));
        inspector.setBackground(Color.WHITE);
        inspector.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(16, 16, 14, 16)));

        JPanel details = new JPanel();
        details.setOpaque(false);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setBorder(new EmptyBorder(0, 0, 0, 0));
        inspectorId = new JLabel("Chọn một yêu cầu");
        inspectorId.setFont(new Font("Segoe UI", Font.BOLD, 12));
        inspectorId.setForeground(BLUE);
        inspectorId.setAlignmentX(Component.LEFT_ALIGNMENT);
        inspectorTitle = new JTextArea("Chi tiết yêu cầu");
        inspectorTitle.setEditable(false);
        inspectorTitle.setLineWrap(true);
        inspectorTitle.setWrapStyleWord(true);
        inspectorTitle.setOpaque(false);
        inspectorTitle.setBorder(null);
        inspectorTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        inspectorTitle.setForeground(NAVY);
        inspectorTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        details.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent event) {
                updateInspectorTitleHeight(details);
            }
        });
        inspectorOwner = detailValueLabel("Người gửi: —");
        inspectorDepartment = detailValueLabel("Phòng ban: —");
        inspectorCreatedAt = detailValueLabel("Thời gian gửi: —");
        inspectorSla = detailValueLabel("Hạn SLA: —");
        inspectorLatestNote = detailValueLabel("Ghi chú mới nhất: —");
        inspectorDevice = canManageTickets ? detailValueLabel("Thiết bị: —") : null;
        if (inspectorDevice != null) inspectorDevice.setOpaque(false);
        JPanel ownerDepartmentRow = new JPanel(new GridLayout(1, 2, 8, 0));
        ownerDepartmentRow.setOpaque(false);
        ownerDepartmentRow.add(inspectorOwner);
        ownerDepartmentRow.add(inspectorDepartment);
        JPanel adminRow = null;
        if (isAdmin) {
            inspectorSourceIp = detailValueLabel("IP máy gửi: —");
            adminRow = new JPanel(new GridLayout(1, 1));
            adminRow.setOpaque(false);
            adminRow.add(inspectorSourceIp);
        } else {
            inspectorSourceIp = null;
        }
        JPanel createdSlaRow = new JPanel(new GridLayout(1, 2, 8, 0));
        createdSlaRow.setOpaque(false);
        createdSlaRow.add(inspectorCreatedAt);
        createdSlaRow.add(inspectorSla);
        inspectorProgress = new JProgressBar(0, 100);
        inspectorProgress.setStringPainted(true);
        inspectorProgress.setPreferredSize(new Dimension(180, 14));
        inspectorProgress.setMinimumSize(new Dimension(1, 14));
        inspectorProgress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));
        inspectorProgress.setForeground(BLUE);
        inspectorProgress.setBackground(new Color(226, 232, 240));
        inspectorProgress.setAlignmentX(Component.LEFT_ALIGNMENT);

        details.add(inspectorId);
        details.add(Box.createVerticalStrut(3));
        details.add(inspectorTitle);
        details.add(Box.createVerticalStrut(4));
        details.add(ownerDepartmentRow);
        if (adminRow != null) {
            details.add(Box.createVerticalStrut(3));
            details.add(adminRow);
        }
        details.add(Box.createVerticalStrut(3));
        details.add(createdSlaRow);
        if (inspectorDevice != null) {
            details.add(Box.createVerticalStrut(3));
            details.add(inspectorDevice);
        }
        details.add(Box.createVerticalStrut(3));
        details.add(inspectorLatestNote);
        details.add(Box.createVerticalStrut(4));
        details.add(inspectorProgress);
        details.add(Box.createVerticalStrut(5));

        JPanel quickActions = new JPanel(new GridLayout(1, 0, 4, 0));
        quickActions.setOpaque(false);
        quickActions.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton assign = createStyledButton("Phân công", new Color(13, 110, 253), Color.WHITE, 11);
        compactInspectorButton(assign);
        assign.setVisible(canAssignTechnician);
        assign.addActionListener(event -> {
            Models.Ticket selected = getSelectedTicket();
            if (selected != null) showAssignDialog(selected.id);
        });
        JButton update = createStyledButton("Tiến độ", new Color(25, 135, 84), Color.WHITE, 11);
        compactInspectorButton(update);
        update.addActionListener(event -> {
            Models.Ticket selected = getSelectedTicket();
            if (selected != null) showUpdateStatusDialog(selected.id);
        });
        JButton download = createStyledButton("Tải tệp", new Color(75, 85, 99), Color.WHITE, 11);
        compactInspectorButton(download);
        download.addActionListener(event -> {
            Models.Ticket selected = getSelectedTicket();
            if (selected == null || selected.attachmentName == null
                    || selected.attachmentName.isBlank()) {
                showToastNotification("Yêu cầu này chưa có tệp đính kèm.");
            } else {
                downloadAttachmentFile(selected.id, selected.attachmentName);
            }
        });
        JButton detail = createStyledButton("Chi tiết", Color.WHITE, NAVY, 11);
        compactInspectorButton(detail);
        detail.addActionListener(event -> {
            Models.Ticket selected = getSelectedTicket();
            if (selected != null) showTicketDetailDialog(selected.id);
        });
        JButton history = createStyledButton("Lịch sử",
                new Color(111, 66, 193), Color.WHITE, 11);
        compactInspectorButton(history);
        history.addActionListener(event -> {
            Models.Ticket selected = getSelectedTicket();
            if (selected != null) {
                showHistoryReviewDialog(selected);
            } else {
                showToastNotification("Vui lòng chọn một yêu cầu để xem lịch sử.");
            }
        });
        quickActions.add(assign);
        if (canUpdateProgress) quickActions.add(update);
        quickActions.add(download);
        quickActions.add(history);
        quickActions.add(detail);
        details.add(quickActions);
        inspector.add(details, BorderLayout.NORTH);

        inspectorChatHost = new JPanel(new BorderLayout());
        inspectorChatHost.setBackground(Color.WHITE);
        JLabel empty = new JLabel("Chọn một yêu cầu để xem trao đổi trực tuyến.", SwingConstants.CENTER);
        empty.setForeground(new Color(100, 116, 139));
        inspectorChatHost.add(empty, BorderLayout.CENTER);
        JPanel chatSection = new JPanel(new BorderLayout(0, 6));
        chatSection.setOpaque(false);
        JLabel chatHeading = new JLabel("Trao đổi trực tuyến");
        chatHeading.setFont(new Font("Segoe UI", Font.BOLD, 12));
        chatHeading.setForeground(new Color(71, 85, 105));
        chatSection.add(chatHeading, BorderLayout.NORTH);
        chatSection.add(inspectorChatHost, BorderLayout.CENTER);
        inspector.add(chatSection, BorderLayout.CENTER);
        inspector.setPreferredSize(new Dimension(420, 400));
        inspectorId.setText("Chi tiết yêu cầu");
        inspectorTitle.setText("Chọn một yêu cầu để xem chi tiết");
        return inspector;
    }

    private JLabel detailValueLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        label.setForeground(new Color(71, 85, 105));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private void compactInspectorButton(JButton button) {
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 10f));
        button.setMargin(new Insets(3, 6, 3, 6));
        button.setPreferredSize(null);
    }

    private Models.Ticket getSelectedTicket() {
        if (ticketTable == null || ticketTable.getSelectedRow() < 0) return null;
        int modelRow = ticketTable.convertRowIndexToModel(ticketTable.getSelectedRow());
        if (modelRow < 0 || modelRow >= cachedTickets.size()) return null;
        String ticketId = String.valueOf(ticketTableModel.getValueAt(modelRow, 0));
        return cachedTickets.stream().filter(ticket -> ticket.id.equals(ticketId))
                .findFirst().orElse(null);
    }

    private void refreshTicketInspector(Models.Ticket ticket) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> refreshTicketInspector(ticket));
            return;
        }
        if (inspectorChatHost == null) return;
        if (ticket == null) {
            if (inspectorChatTicketId != null) {
                inspectorTicketChats.remove(inspectorChatTicketId);
            }
            inspectorChatTicketId = null;
            inspectorId.setText("Chi tiết yêu cầu");
            inspectorTitle.setText("Chọn một yêu cầu để xem chi tiết");
            updateInspectorTitleHeight((JPanel) inspectorTitle.getParent());
            inspectorOwner.setText("Người gửi: —");
            inspectorDepartment.setText("Phòng ban: —");
            if (inspectorDevice != null) inspectorDevice.setText("Thiết bị: —");
            if (inspectorSourceIp != null) {
                inspectorSourceIp.setText("IP máy gửi: —");
            }
            inspectorCreatedAt.setText("Thời gian gửi: —");
            inspectorSla.setText("Hạn SLA: —");
            inspectorLatestNote.setText("Ghi chú mới nhất: —");
            inspectorProgress.setValue(0);
            inspectorProgress.setString("—");
            inspectorChatHost.removeAll();
            inspectorChatHost.add(new JLabel("Chọn một yêu cầu để xem trao đổi trực tuyến.",
                    SwingConstants.CENTER), BorderLayout.CENTER);
            inspectorChatHost.revalidate();
            inspectorChatHost.repaint();
            return;
        }
        inspectorId.setText(ticket.id + "  ·  " + nullSafe(ticket.status));
        inspectorTitle.setText(nullSafe(ticket.title));
        updateInspectorTitleHeight((JPanel) inspectorTitle.getParent());
        inspectorOwner.setText("Người gửi: " + nullSafe(ticket.owner));
        inspectorDepartment.setText("Phòng ban: " + nullSafe(ticket.ownerDepartment));
        if (inspectorDevice != null) {
            String device = ticket.device == null || ticket.device.isBlank() ? "Chưa ghi nhận" : ticket.device;
            inspectorDevice.setText("Thiết bị: " + device);
        }
        if (inspectorSourceIp != null) {
            String ipDisplay = (ticket.sourceIp == null || ticket.sourceIp.isBlank())
                    ? "Chưa ghi nhận" : ticket.sourceIp;
            inspectorSourceIp.setText("IP máy gửi: " + ipDisplay);
        }
        inspectorCreatedAt.setText("Gửi: " + formatCompactCreatedAt(ticket.createdAt, ticket.createdDate));
        inspectorSla.setText("SLA: " + formatSlaDeadline(
                ticket.slaDueAt, ticket.status, ticket.progress));
        inspectorLatestNote.setText("<html><body style='width:270px'>Ghi chú: "
                + escapeHtml(nullSafe(ticket.latestNote)) + "</body></html>");
        inspectorProgress.setValue(Math.max(0, Math.min(100, ticket.progress)));
        inspectorProgress.setString(ticket.progress + "% hoàn thành");
        inspectorProgress.setForeground(ticket.progress >= 100 ? GREEN : BLUE);

        if (ticket.id.equals(inspectorChatTicketId)
                && inspectorTicketChats.containsKey(ticket.id)) return;
        if (inspectorChatTicketId != null) {
            inspectorTicketChats.remove(inspectorChatTicketId);
        }
        inspectorChatTicketId = ticket.id;
        inspectorChatHost.removeAll();
        JPanel chatPanel = createTicketChatPanel(ticket, mainFrame, true);
        inspectorTicketChats.put(ticket.id, (JPanel) chatPanel.getClientProperty("transcript"));
        inspectorChatHost.add(chatPanel, BorderLayout.CENTER);
        inspectorChatHost.revalidate();
        inspectorChatHost.repaint();
    }

    private void updateInspectorTitleHeight(JPanel details) {
        if (inspectorTitle == null || details == null || details.getWidth() <= 0) return;
        int width = Math.max(1, details.getWidth()
                - details.getInsets().left - details.getInsets().right);
        java.awt.FontMetrics metrics = inspectorTitle.getFontMetrics(inspectorTitle.getFont());
        int lines = 0;
        for (String paragraph : inspectorTitle.getText().split("\\R", -1)) {
            if (paragraph.isEmpty()) {
                lines++;
                continue;
            }
            int lineWidth = 0;
            for (String word : paragraph.split("\\s+")) {
                int wordWidth = metrics.stringWidth(word);
                if (lineWidth > 0 && lineWidth + metrics.stringWidth(" ") + wordWidth > width) {
                    lines++;
                    lineWidth = 0;
                }
                if (wordWidth > width) {
                    int charWidth = 0;
                    for (int index = 0; index < word.length(); index++) {
                        int nextWidth = metrics.charWidth(word.charAt(index));
                        if (charWidth > 0 && charWidth + nextWidth > width) {
                            lines++;
                            charWidth = 0;
                        }
                        charWidth += nextWidth;
                    }
                    lineWidth = charWidth;
                } else {
                    lineWidth += (lineWidth == 0 ? 0 : metrics.stringWidth(" ")) + wordWidth;
                }
            }
            lines++;
        }
        int height = Math.max(1, lines) * metrics.getHeight()
                + inspectorTitle.getInsets().top + inspectorTitle.getInsets().bottom;
        inspectorTitle.setPreferredSize(new Dimension(width, height));
        details.revalidate();
    }

    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static final class PillRenderer extends DefaultTableCellRenderer {
        private final String type;

        private PillRenderer(String type) {
            this.type = type;
            setHorizontalAlignment(SwingConstants.CENTER);
            setBorder(new EmptyBorder(0, 4, 0, 4));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean selected, boolean focus,
                                                       int row, int column) {
            String text = value == null ? "" : value.toString();
            super.getTableCellRendererComponent(table, text, selected, focus, row, column);
            setOpaque(false);
            if (selected) {
                setForeground(Color.WHITE);
                setBackground(table.getSelectionBackground());
                return this;
            }
            Color background;
            Color foreground;
            if ("priority".equals(type)) {
                switch (text.toLowerCase(java.util.Locale.ROOT)) {
                    case "khẩn cấp" -> { background = new Color(254, 226, 226); foreground = new Color(185, 28, 28); }
                    case "cao" -> { background = new Color(255, 237, 213); foreground = new Color(194, 65, 12); }
                    case "thấp" -> { background = new Color(241, 245, 249); foreground = new Color(71, 85, 105); }
                    default -> { background = new Color(219, 234, 254); foreground = new Color(29, 78, 216); }
                }
            } else {
                switch (text.toLowerCase(java.util.Locale.ROOT)) {
                    case "mới" -> { background = new Color(219, 234, 254); foreground = new Color(29, 78, 216); }
                    case "đang xử lý" -> { background = new Color(254, 243, 199); foreground = new Color(146, 64, 14); }
                    case "hoàn thành" -> { background = new Color(220, 252, 231); foreground = new Color(21, 128, 61); }
                    default -> { background = new Color(241, 245, 249); foreground = new Color(71, 85, 105); }
                }
            }
            setBackground(background);
            setForeground(foreground);
            setFont(table.getFont().deriveFont(Font.BOLD, 11f));
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (isOpaque()) {
                super.paintComponent(graphics);
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(getBackground());
            g.fillRoundRect(5, 6, getWidth() - 10, getHeight() - 12, 16, 16);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class MiniProgressRenderer extends JProgressBar
            implements javax.swing.table.TableCellRenderer {
        private MiniProgressRenderer() {
            super(0, 100);
            setStringPainted(true);
            setBorderPainted(false);
            setPreferredSize(new Dimension(76, 12));
            setFont(new Font("Segoe UI", Font.BOLD, 10));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean selected, boolean focus,
                                                       int row, int column) {
            int progress = value instanceof Number number ? number.intValue() : 0;
            setValue(Math.max(0, Math.min(100, progress)));
            setString(progress + "%");
            setForeground(progress >= 100 ? GREEN : new Color(59, 130, 246));
            setBackground(selected ? table.getSelectionBackground() : new Color(226, 232, 240));
            setBorder(selected ? BorderFactory.createLineBorder(table.getSelectionBackground())
                    : BorderFactory.createEmptyBorder(10, 8, 10, 8));
            return this;
        }
    }

    private static final class SlaCellRenderer extends DefaultTableCellRenderer {
        private SlaCellRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean selected, boolean focus,
                                                       int row, int column) {
            String raw = value == null ? "" : value.toString();
            String status = String.valueOf(table.getValueAt(row, 7));
            Object progressValue = table.getValueAt(row, 6);
            int progress = progressValue instanceof Number number ? number.intValue() : 0;
            boolean completed = "Hoàn thành".equalsIgnoreCase(status) || progress >= 100;
            boolean cancelled = "Đã hủy".equalsIgnoreCase(status);
            String text = "Chưa có hạn";
            Color color = new Color(71, 85, 105);
            if (cancelled) {
                text = "Đã hủy";
                color = new Color(100, 116, 139);
            }
            try {
                if (!cancelled) {
                    java.time.LocalDateTime due = java.time.LocalDateTime.parse(raw);
                    double hours = BusinessHours.workingHoursBetween(java.time.LocalDateTime.now(), due);
                    boolean overdue = due.isBefore(java.time.LocalDateTime.now());
                    String date = due.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"));
                    if (completed) {
                        text = "[Đã xong] " + date;
                        color = new Color(21, 128, 61);
                    } else if (overdue && ("Mới".equalsIgnoreCase(status)
                            || "Đang xử lý".equalsIgnoreCase(status))) {
                        text = "[QUÁ HẠN] " + date;
                        color = new Color(185, 28, 28);
                    } else {
                        text = date;
                        if (hours <= 1.0) color = AMBER;
                    }
                }
            } catch (java.time.format.DateTimeParseException ignored) {
                if (!cancelled && !raw.isBlank()) text = raw;
            }
            super.getTableCellRendererComponent(table, text, selected, focus, row, column);
            setForeground(selected ? table.getSelectionForeground() : color);
            setFont(table.getFont().deriveFont(color.equals(new Color(185, 28, 28))
                    || color.equals(AMBER) ? Font.BOLD : Font.PLAIN));
            return this;
        }
    }

    // =========================================================================
    // F03: GỬI LỆNH LẤY DANH SÁCH YÊU CẦU QUA SOCKET
    // =========================================================================
    public void requestTicketList() {
        new Thread(() -> {
            try {
                F03_ViewTickets.ViewTicketsRequest req = new F03_ViewTickets.ViewTicketsRequest(currentUser.username, currentUser.role);
                Protocol.Packet packet = new Protocol.Packet(Protocol.CMD_GET_TICKETS, "", "", req);
                Protocol.Packet response = sendAndReceiveSync(packet);
                if (Protocol.STATUS_SUCCESS.equals(response.status) && response.data instanceof List<?> rows) {
                    List<Models.Ticket> tickets = rows.stream()
                            .filter(Models.Ticket.class::isInstance)
                            .map(Models.Ticket.class::cast).toList();
                    SwingUtilities.invokeLater(() -> displayTickets(tickets));
                } else if (!Protocol.STATUS_SUCCESS.equals(response.status)) {
                    System.err.println("Lỗi tải danh sách yêu cầu: " + response.message);
                }
            } catch (Exception e) {
                System.err.println("Lỗi gửi yêu cầu danh sách: " + e.getMessage());
            }
        }).start();
    }

    public Protocol.Packet sendAndReceiveSync(Protocol.Packet sendPacket) throws Exception {
        if (networkService == null) throw new IOException("Chưa kết nối tới máy chủ.");
        return networkService.request(sendPacket);
    }

    private void sendCommandAsync(Protocol.Packet request, java.awt.Component dialog,
                                 String errorTitle,
                                 java.util.function.Consumer<Protocol.Packet> onSuccess) {
        new Thread(() -> {
            try {
                Protocol.Packet response = sendAndReceiveSync(request);
                if (!Protocol.STATUS_SUCCESS.equals(response.status)) {
                    throw new IOException(response.message);
                }
                SwingUtilities.invokeLater(() -> onSuccess.accept(response));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(dialog,
                        e.getMessage(), errorTitle, JOptionPane.ERROR_MESSAGE));
            }
        }, "workdesk-command-" + request.command).start();
    }

    private void searchTickets(String keyword, String statusLabel, String categoryLabel, String priorityLabel) {
        String status = statusLabel.startsWith("Tất cả") ? "" : statusLabel;
        String category = categoryLabel.startsWith("Tất cả") ? "" : categoryLabel;
        String priority = priorityLabel.startsWith("Tất cả") ? "" : priorityLabel;
        new Thread(() -> {
            try {
                Protocol.Packet response = sendAndReceiveSync(new Protocol.Packet(Protocol.CMD_SEARCH_TICKETS, "", "",
                        new F07_SearchTickets.SearchRequest(keyword, status, category, priority)));
                if (!Protocol.STATUS_SUCCESS.equals(response.status)) throw new IOException(response.message);
                @SuppressWarnings("unchecked") List<Models.Ticket> rows = (List<Models.Ticket>) response.data;
                SwingUtilities.invokeLater(() -> displayTickets(rows));
            } catch (Exception ex) { SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(mainFrame, ex.getMessage(), "F07 - Tìm kiếm", JOptionPane.ERROR_MESSAGE)); }
        }, "f07-search").start();
    }

    private void displayTickets(List<Models.Ticket> rows) {
        String previouslySelectedId = getSelectedTicket() == null ? null : getSelectedTicket().id;
        cachedTickets = new ArrayList<>(rows);
        if (ticketTableModel == null) return;
        ticketTableModel.setRowCount(0);
        for (Models.Ticket t : rows) ticketTableModel.addRow(new Object[]{t.id, t.title, t.category, t.priority,
                t.owner, t.assignedTech, t.progress, t.status, t.slaDueAt, t.attachmentName});
        int selectedModelRow = -1;
        if (previouslySelectedId != null) {
            for (int row = 0; row < ticketTableModel.getRowCount(); row++) {
                if (previouslySelectedId.equals(ticketTableModel.getValueAt(row, 0))) {
                    selectedModelRow = row;
                    break;
                }
            }
        }
        if (ticketTable != null && selectedModelRow >= 0) {
            int viewRow = ticketTable.convertRowIndexToView(selectedModelRow);
            ticketTable.setRowSelectionInterval(viewRow, viewRow);
        } else {
            if (ticketTable != null) ticketTable.clearSelection();
            refreshTicketInspector(null);
        }
    }

    private String formatCreatedAt(String value, String fallbackDate) {
        if (value == null || value.isBlank()) value = fallbackDate;
        if (value == null || value.isBlank()) return "—";
        try {
            java.time.LocalDateTime created;
            try {
                created = java.time.LocalDateTime.parse(value,
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (java.time.format.DateTimeParseException e) {
                created = java.time.LocalDateTime.parse(value);
            }
            return created.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        } catch (java.time.format.DateTimeParseException e) {
            try {
                return java.time.LocalDate.parse(value).atStartOfDay()
                        .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
            } catch (java.time.format.DateTimeParseException ignored) {
                return value;
            }
        }
    }

    private String formatCompactCreatedAt(String value, String fallbackDate) {
        if (value == null || value.isBlank()) value = fallbackDate;
        if (value == null || value.isBlank()) return "—";
        try {
            java.time.LocalDateTime created;
            try {
                created = java.time.LocalDateTime.parse(value,
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (java.time.format.DateTimeParseException e) {
                created = java.time.LocalDateTime.parse(value);
            }
            return created.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"));
        } catch (java.time.format.DateTimeParseException e) {
            try {
                return java.time.LocalDate.parse(value)
                        .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"));
            } catch (java.time.format.DateTimeParseException ignored) {
                return value;
            }
        }
    }

    private String formatSlaDeadline(String value, String status, int progress) {
        if ("Đã hủy".equalsIgnoreCase(status)) return "Đã hủy";
        if (value == null || value.isBlank()) return "Chưa có hạn";
        try {
            java.time.LocalDateTime due = java.time.LocalDateTime.parse(value);
            boolean terminal = "Hoàn thành".equalsIgnoreCase(status) || progress >= 100;
            String prefix = !terminal && due.isBefore(java.time.LocalDateTime.now())
                    ? "Quá hạn: " : terminal ? "[Đã xong] " : "";
            return prefix + due.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"));
        } catch (java.time.format.DateTimeParseException e) {
            return value;
        }
    }

    private void showEditTicketDialog(Models.Ticket ticket) {
        if (currentUser == null || !"admin".equalsIgnoreCase(currentUser.role)) return;
        JTextField title = new JTextField(ticket.title);
        JTextField category = new JTextField(ticket.category);
        JComboBox<String> priority = new JComboBox<>(new String[]{"Thấp", "Bình thường", "Cao", "Khẩn cấp"});
        priority.setSelectedItem(ticket.priority);
        JTextArea detail = new JTextArea(ticket.detail, 5, 24);
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);
        JScrollPane detailScroll = new JScrollPane(detail);
        detailScroll.setPreferredSize(new Dimension(450, 140));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(8, 8, 8, 8));
        form.setPreferredSize(new Dimension(480, 390));
        addEditTicketField(form, 0, "Tiêu đề:", title, 0);
        addEditTicketField(form, 2, "Danh mục:", category, 0);
        addEditTicketField(form, 4, "Ưu tiên:", priority, 0);
        addEditTicketField(form, 6, "Mô tả:", detailScroll, 1);
        if (JOptionPane.showConfirmDialog(mainFrame, form, "Admin sửa yêu cầu " + ticket.id,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        if (title.getText().isBlank() || category.getText().isBlank() || detail.getText().isBlank()) {
            JOptionPane.showMessageDialog(mainFrame, "Tiêu đề, danh mục và mô tả không được để trống.",
                    "Dữ liệu chưa hợp lệ", JOptionPane.WARNING_MESSAGE);
            return;
        }
        AdminTicketManagement.UpdateRequest request = new AdminTicketManagement.UpdateRequest(
                ticket.id, title.getText(), category.getText(), (String) priority.getSelectedItem(),
                detail.getText());
        new Thread(() -> {
            try {
                Protocol.Packet response = sendAndReceiveSync(new Protocol.Packet(
                        Protocol.CMD_UPDATE_TICKET, "", "", request));
                if (!Protocol.STATUS_SUCCESS.equals(response.status)) throw new IOException(response.message);
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(mainFrame, response.message);
                    requestTicketList();
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(mainFrame, e.getMessage(),
                        "Không thể sửa yêu cầu", JOptionPane.ERROR_MESSAGE));
            }
        }, "admin-edit-ticket").start();
    }

    private void addEditTicketField(JPanel form, int row, String label, JComponent field, double weighty) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridwidth = 1;
        constraints.weightx = 1;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(5, 0, 3, 0);
        constraints.gridy = row;
        form.add(new JLabel(label), constraints);

        constraints.gridy = row + 1;
        constraints.weighty = weighty;
        constraints.fill = weighty > 0 ? GridBagConstraints.BOTH : GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 8, 0);
        form.add(field, constraints);
    }

    private void deleteTicket(String ticketId) {
        if (!"admin".equalsIgnoreCase(currentUser.role)) return;
        int choice = JOptionPane.showConfirmDialog(mainFrame,
                "Xóa yêu cầu " + ticketId + " khỏi danh sách?\nHệ thống sẽ ẩn yêu cầu nhưng giữ lịch sử kiểm toán và trò chuyện.",
                "Xác nhận xóa mềm", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;
        new Thread(() -> {
            try {
                Protocol.Packet response = sendAndReceiveSync(new Protocol.Packet(Protocol.CMD_DELETE_TICKET, "", "",
                        new AdminTicketManagement.DeleteRequest(ticketId)));
                if (!Protocol.STATUS_SUCCESS.equals(response.status)) throw new IOException(response.message);
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(mainFrame, response.message);
                    requestTicketList();
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(mainFrame, e.getMessage(),
                        "Không thể xóa yêu cầu", JOptionPane.ERROR_MESSAGE));
            }
        }, "admin-delete-ticket").start();
    }

    private void showAuditLogWindow() {
        if (!"admin".equalsIgnoreCase(currentUser.role)) return;
        new Thread(() -> {
            try {
                Protocol.Packet response = sendAndReceiveSync(new Protocol.Packet(
                        Protocol.CMD_GET_AUDIT_LOG, "", "", null));
                if (!Protocol.STATUS_SUCCESS.equals(response.status)) throw new IOException(response.message);
                if (!(response.data instanceof List<?> rows)) throw new IOException("Dữ liệu audit không hợp lệ.");
                StringBuilder text = new StringBuilder();
                for (Object row : rows) {
                    if (row instanceof Models.AuditEvent event) {
                        text.append(event.createdAt).append(" | ").append(event.actor).append(" | ")
                                .append(event.eventType).append(" | ").append(event.entityId).append("\n")
                                .append(event.detail).append("\n\n");
                    }
                }
                SwingUtilities.invokeLater(() -> {
                    JTextArea log = new JTextArea(text.length() == 0 ? "Chưa có nhật ký." : text.toString());
                    log.setEditable(false);
                    log.setLineWrap(true);
                    log.setWrapStyleWord(true);
                    JScrollPane pane = new JScrollPane(log);
                    pane.setPreferredSize(new Dimension(760, 500));
                    JOptionPane.showMessageDialog(mainFrame, pane, "Nhật ký kiểm toán (tối đa 500 sự kiện)",
                            JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(mainFrame, e.getMessage(),
                        "Không thể tải nhật ký", JOptionPane.ERROR_MESSAGE));
            }
        }, "admin-audit-log").start();
    }

    private void showStatisticsDialog() {
        JTextField dateFrom = new JTextField(10), dateTo = new JTextField(10);
        JPanel range = new JPanel(new GridLayout(2, 2, 6, 6));
        range.add(new JLabel("Từ ngày (YYYY-MM-DD, để trống = không giới hạn):")); range.add(dateFrom);
        range.add(new JLabel("Đến ngày (YYYY-MM-DD, để trống = không giới hạn):")); range.add(dateTo);
        if (JOptionPane.showConfirmDialog(mainFrame, range, "F09 - Chọn khoảng thời gian", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        String from = dateFrom.getText().trim(), to = dateTo.getText().trim();
        try {
            if (!from.isEmpty()) java.time.LocalDate.parse(from);
            if (!to.isEmpty()) java.time.LocalDate.parse(to);
            if (!from.isEmpty() && !to.isEmpty() && from.compareTo(to) > 0) throw new IllegalArgumentException("Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.");
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(mainFrame, "Ngày phải có định dạng YYYY-MM-DD và là ngày hợp lệ.", "F09 - Khoảng ngày không hợp lệ", JOptionPane.WARNING_MESSAGE);
            return;
        }
        new Thread(() -> {
            try {
                Protocol.Packet response = sendAndReceiveSync(new Protocol.Packet(Protocol.CMD_GET_STATISTICS, "", "",
                        new F09_TicketStatistics.StatisticsRequest(from, to)));
                if (!Protocol.STATUS_SUCCESS.equals(response.status)) throw new IOException(response.message);
                F09_TicketStatistics.Statistics stats = (F09_TicketStatistics.Statistics) response.data;
                SwingUtilities.invokeLater(() -> {
                    String period = from.isEmpty() && to.isEmpty() ? "Toàn bộ thời gian" : (from.isEmpty() ? "Đến " + to : to.isEmpty() ? "Từ " + from : from + " đến " + to);
                    StringBuilder text = new StringBuilder("Khoảng thời gian: ").append(period).append("\n\nTHỐNG KÊ THEO TRẠNG THÁI\n");
                    for (Models.TicketStatistic s : stats.byStatus) text.append("• ").append(s.groupName).append(": ").append(s.count).append(" yêu cầu\n");
                    text.append("\nTHỐNG KÊ THEO KỸ THUẬT VIÊN\n");
                    for (Models.TicketStatistic s : stats.byTechnician) text.append("• ").append(s.groupName).append(": ").append(s.count).append(" yêu cầu\n");
                    JOptionPane.showMessageDialog(mainFrame, text.toString(), "F09 - Thống kê yêu cầu", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception ex) { SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(mainFrame, ex.getMessage(), "F09 - Thống kê", JOptionPane.ERROR_MESSAGE)); }
        }, "f09-statistics").start();
    }

    // =========================================================================
    // F02: HỘP THOẠI TẠO YÊU CẦU MỚI (Sự kiện Socket / Thông báo)
    // =========================================================================
    private void showCreateTicketDialog() {
        JDialog dialog = new JDialog(mainFrame, "Tạo yêu cầu hỗ trợ kỹ thuật (F02)", true);
        dialog.setSize(520, 530);
        dialog.setLocationRelativeTo(mainFrame);

        JPanel p = new JPanel(new GridLayout(0, 1, 6, 6));
        p.setBorder(new EmptyBorder(18, 22, 18, 22));

        JTextField txtTitle = new JTextField();
        JComboBox<String> cbCategory = new JComboBox<>(new String[]{"IT - Phần cứng", "Mạng & Internet", "Phần mềm & Cấp quyền", "Nhân sự", "Hành chính", "Tài chính"});
        JComboBox<String> cbPriority = new JComboBox<>(new String[]{"Bình thường", "Thấp", "Cao", "Khẩn cấp"});
        JComboBox<String> cbDevice = new JComboBox<>(new String[]{
            "Máy tính để bàn (PC)",
            "Máy tính xách tay (Laptop)",
            "Máy in / Máy quét (Printer/Scanner)",
            "Thiết bị mạng (Wifi / Router / Switch)",
            "Điện thoại bàn / IP Phone",
            "Thiết bị phòng họp / Máy chiếu",
            "Thiết bị khác"
        });
        JLabel lblSlaPreview = new JLabel();
        Runnable updateSlaPreview = () -> {
            String workingHours = switch ((String) cbPriority.getSelectedItem()) {
                case "Khẩn cấp" -> "4";
                case "Cao" -> "8";
                case "Thấp" -> "48";
                default -> "24";
            };
            lblSlaPreview.setText("Hạn xử lý SLA: " + workingHours + " giờ làm việc");
        };
        cbPriority.addActionListener(event -> updateSlaPreview.run());
        updateSlaPreview.run();
        JTextArea txtDetail = new JTextArea(4, 20);
        txtDetail.setLineWrap(true);
        txtDetail.setWrapStyleWord(true);

        JLabel lblAttach = new JLabel("Chưa đính kèm tệp");
        JButton btnAttach = createStyledButton("Chọn tệp đính kèm...", Color.WHITE, new Color(20, 35, 60), 12);
        final File[] chosenFile = new File[1];

        btnAttach.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (fc.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                chosenFile[0] = fc.getSelectedFile();
                lblAttach.setText(chosenFile[0].getName() + " (" + chosenFile[0].length() + " bytes)");
            }
        });

        p.add(new JLabel("Tiêu đề yêu cầu: *"));
        p.add(txtTitle);
        p.add(new JLabel("Danh mục hỗ trợ:"));
        p.add(cbCategory);
        p.add(new JLabel("Mức độ ưu tiên:"));
        p.add(cbPriority);
        p.add(lblSlaPreview);
        p.add(new JLabel("Thiết bị gặp sự cố:"));
        p.add(cbDevice);
        p.add(new JLabel("Mô tả chi tiết sự cố: *"));
        p.add(new JScrollPane(txtDetail));

        JPanel attachPanel = new JPanel(new BorderLayout(8, 0));
        attachPanel.add(btnAttach, BorderLayout.WEST);
        attachPanel.add(lblAttach, BorderLayout.CENTER);
        p.add(attachPanel);

        JButton btnSubmit = createStyledButton("Gửi yêu cầu qua Socket", new Color(13, 110, 253), Color.WHITE, 13);
        p.add(btnSubmit);

        btnSubmit.addActionListener(e -> {
            String title = txtTitle.getText().trim();
            String detail = txtDetail.getText().trim();
            if (title.isEmpty() || detail.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Vui lòng nhập đầy đủ tiêu đề và mô tả sự cố.");
                return;
            }

            String fileName = (chosenFile[0] != null) ? chosenFile[0].getName() : "";
            byte[] attachmentBytes = null;
            if (chosenFile[0] != null) {
                try {
                    if (chosenFile[0].length() <= 0
                            || chosenFile[0].length() > FileTransferHelper.MAX_ATTACHMENT_SIZE) {
                        JOptionPane.showMessageDialog(dialog,
                                "Tệp phải lớn hơn 0 byte và không vượt quá 10 MB.",
                                "Kích thước tệp không hợp lệ", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    attachmentBytes = java.nio.file.Files.readAllBytes(chosenFile[0].toPath());
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(dialog, "Không thể đọc tệp đính kèm: " + ex.getMessage(),
                            "Lỗi tệp", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            F02_CreateTicket.CreateTicketRequest req = new F02_CreateTicket.CreateTicketRequest(
                    title, (String) cbCategory.getSelectedItem(), (String) cbPriority.getSelectedItem(),
                    detail, currentUser.username, fileName, attachmentBytes,
                    (String) cbDevice.getSelectedItem()
            );

            btnSubmit.setEnabled(false);
            new Thread(() -> {
                try {
                Protocol.Packet packet = new Protocol.Packet(Protocol.CMD_CREATE_TICKET, "", "", req);
                    Protocol.Packet response = sendAndReceiveSync(packet);
                    if (!Protocol.STATUS_SUCCESS.equals(response.status)) {
                        throw new IOException(response.message);
                    }
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(dialog, response.message,
                                "Thành công", JOptionPane.INFORMATION_MESSAGE);
                        dialog.dispose();
                        refreshTicketsDirectly();
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(dialog,
                            "Lỗi gửi yêu cầu: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE));
                } finally {
                    SwingUtilities.invokeLater(() -> btnSubmit.setEnabled(true));
                }
            }, "f02-create-ticket").start();
        });

        dialog.setContentPane(p);
        dialog.setVisible(true);
    }

    // =========================================================================
    // F03: XEM CHI TIẾT YÊU CẦU & XUẤT RA FILE (File / Kết quả lưu file)
    // =========================================================================
    private void showTicketDetailDialog(String ticketId) {
        Models.Ticket t = cachedTickets.stream().filter(x -> x.id.equals(ticketId)).findFirst().orElse(null);
        if (t == null) return;

        JDialog d = new JDialog(mainFrame, "Chi tiết yêu cầu: " + t.id, true);
        d.setSize(520, 500);
        d.setLocationRelativeTo(mainFrame);

        JPanel p = new JPanel(new GridLayout(0, 1, 5, 5));
        p.setBorder(new EmptyBorder(20, 24, 20, 24));
        p.setBackground(Color.WHITE);

        p.add(new JLabel("<html><b>Mã yêu cầu:</b> " + t.id + "</html>"));
        p.add(new JLabel("<html><b>Tiêu đề:</b> " + t.title + "</html>"));
        p.add(new JLabel("<html><b>Danh mục:</b> " + t.category + " | <b>Ưu tiên:</b> " + t.priority + "</html>"));
        p.add(new JLabel("<html><b>Người tạo:</b> " + t.owner + " | <b>Thời gian gửi:</b> "
                + formatCreatedAt(t.createdAt, t.createdDate) + "</html>"));
        p.add(new JLabel("<html><b>Phòng ban:</b> "
                + (t.ownerDepartment == null || t.ownerDepartment.isBlank() ? "Chưa cập nhật" : t.ownerDepartment)
                + "</html>"));
        if ("admin".equalsIgnoreCase(currentUser.role) || "tech".equalsIgnoreCase(currentUser.role)) {
            String device = t.device == null || t.device.isBlank() ? "Chưa ghi nhận" : t.device;
            p.add(new JLabel("<html><b>Thiết bị:</b> " + escapeHtml(device) + "</html>"));
        }
        if ("admin".equalsIgnoreCase(currentUser.role)) {
            p.add(new JLabel("<html><b>IP máy gửi:</b> "
                    + (t.sourceIp == null || t.sourceIp.isBlank() ? "Chưa ghi nhận" : t.sourceIp) + "</html>"));
        }
        p.add(new JLabel("<html><b>Kỹ thuật viên phụ trách:</b> " + t.assignedTech + "</html>"));
        p.add(new JLabel("<html><b>Trạng thái:</b> " + t.status + " | <b>Tiến độ:</b> " + t.progress + "%</html>"));
        p.add(new JLabel("<html><b>Tệp đính kèm:</b> " + (t.attachmentName.isEmpty() ? "Không có" : t.attachmentName) + "</html>"));

        JTextArea ta = new JTextArea(t.detail);
        ta.setEditable(false);
        ta.setLineWrap(true);
        p.add(new JLabel("Nội dung chi tiết:"));
        p.add(new JScrollPane(ta));

        JButton btnClose = createStyledButton("Đóng", new Color(108, 117, 125), Color.WHITE, 12);
        btnClose.addActionListener(e -> d.dispose());
        p.add(btnClose);

        d.setContentPane(p);
        d.setVisible(true);
    }

    private void showHistoryReviewDialog(Models.Ticket ticket) {
        new Thread(() -> {
            try {
                Protocol.Packet response = sendAndReceiveSync(new Protocol.Packet(Protocol.CMD_GET_HISTORY, "", "", ticket.id));
                if (!Protocol.STATUS_SUCCESS.equals(response.status)) throw new IOException(response.message);
                Object[] payload = (Object[]) response.data;
                @SuppressWarnings("unchecked") List<Models.TicketHistory> history = (List<Models.TicketHistory>) payload[0];
                Models.TicketReview review = (Models.TicketReview) payload[1];
                SwingUtilities.invokeLater(() -> {
                    JDialog dialog = new JDialog(mainFrame, "F10 - Lịch sử và đánh giá " + ticket.id, true);
                    dialog.setSize(610, 460); dialog.setLocationRelativeTo(mainFrame);
                    JPanel root = new JPanel(new BorderLayout(8, 8)); root.setBorder(new EmptyBorder(14, 16, 14, 16));
                    JTextArea timeline = new JTextArea(); timeline.setEditable(false); timeline.setLineWrap(true); timeline.setWrapStyleWord(true);
                    StringBuilder text = new StringBuilder();
                    for (Models.TicketHistory row : history) text.append(row.updatedAt).append(" | ").append(row.updatedBy).append("\n")
                            .append(row.action).append(row.note == null || row.note.isEmpty() ? "" : "\nGhi chú: " + row.note).append("\n\n");
                    if (history.isEmpty()) text.append("Chưa có lịch sử xử lý.");
                    if (review != null) text.append("\nĐánh giá hiện tại: ").append(review.rating).append("/5 - ").append(review.comment == null ? "" : review.comment);
                    timeline.setText(text.toString()); root.add(new JScrollPane(timeline), BorderLayout.CENTER);
                    JPanel footer = new JPanel(new BorderLayout(0, 8));
                    boolean canRate = "requester".equalsIgnoreCase(currentUser.role) && currentUser.username.equalsIgnoreCase(ticket.owner) && "Hoàn thành".equalsIgnoreCase(ticket.status);
                    if (canRate) {
                        int[] selectedRating = {review == null ? 5 : review.rating};
                        JButton[] stars = new JButton[5];
                        JPanel reviewInput = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
                        reviewInput.add(new JLabel("Đánh giá KTV:"));
                        for (int index = 0; index < stars.length; index++) {
                            int ratingValue = index + 1;
                            JButton star = new JButton();
                            star.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 24));
                            star.setBorderPainted(false);
                            star.setContentAreaFilled(false);
                            star.setFocusPainted(false);
                            stars[index] = star;
                            star.addActionListener(e -> {
                                selectedRating[0] = ratingValue;
                                for (int starIndex = 0; starIndex < stars.length; starIndex++) {
                                    stars[starIndex].setText(starIndex < ratingValue ? "★" : "☆");
                                    stars[starIndex].setForeground(starIndex < ratingValue
                                            ? new Color(217, 119, 6) : new Color(148, 163, 184));
                                }
                            });
                            reviewInput.add(star);
                        }
                        for (int index = 0; index < stars.length; index++) {
                            stars[index].setText(index < selectedRating[0] ? "★" : "☆");
                            stars[index].setForeground(index < selectedRating[0]
                                    ? new Color(217, 119, 6) : new Color(148, 163, 184));
                        }
                        JTextField comment = new JTextField(review == null || review.comment == null
                                ? "" : review.comment, 18);
                        comment.setToolTipText("Nhận xét về kết quả hỗ trợ");
                        JButton save = createStyledButton("Lưu đánh giá", BLUE, Color.WHITE, 12);
                        save.addActionListener(e -> saveTicketReview(ticket.id, selectedRating[0],
                                comment.getText(), dialog));
                        reviewInput.add(comment);
                        footer.add(reviewInput, BorderLayout.CENTER);
                        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                        actions.add(save);
                        JButton close = createStyledButton("Đóng", new Color(108,117,125), Color.WHITE, 12);
                        close.addActionListener(e -> dialog.dispose());
                        actions.add(close);
                        footer.add(actions, BorderLayout.SOUTH);
                    } else {
                        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                        JButton close = createStyledButton("Đóng", new Color(108,117,125), Color.WHITE, 12);
                        close.addActionListener(e -> dialog.dispose());
                        actions.add(close);
                        footer.add(actions, BorderLayout.SOUTH);
                    }
                    root.add(footer, BorderLayout.SOUTH); dialog.setContentPane(root); dialog.setVisible(true);
                });
            } catch (Exception ex) { SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(mainFrame, ex.getMessage(), "F10 - Lịch sử", JOptionPane.ERROR_MESSAGE)); }
        }, "f10-history").start();
    }

    private void saveTicketReview(String ticketId, int rating, String comment, JDialog dialog) {
        new Thread(() -> {
            try {
                Protocol.Packet response = sendAndReceiveSync(new Protocol.Packet(Protocol.CMD_RATE_TICKET, "", "",
                        new F10_TicketHistoryReview.ReviewRequest(ticketId, rating, comment)));
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(dialog, response.message, "F10 - Đánh giá",
                            Protocol.STATUS_SUCCESS.equals(response.status) ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
                    if (Protocol.STATUS_SUCCESS.equals(response.status)) dialog.dispose();
                });
            } catch (Exception ex) { SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(dialog, ex.getMessage())); }
        }, "f10-review").start();
    }

    private void showTicketChatDialog(Models.Ticket ticket) {
        JPanel existingChat = openTicketChats.get(ticket.id);
        if (existingChat != null) {
            Window window = SwingUtilities.getWindowAncestor(existingChat);
            if (window != null) window.toFront();
            return;
        }

        JDialog dialog = new JDialog(mainFrame, "Trao đổi trực tuyến - " + ticket.id, false);
        dialog.setSize(620, 480);
        dialog.setLocationRelativeTo(mainFrame);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel chatPanel = createTicketChatPanel(ticket, dialog, false);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                JPanel transcript = (JPanel) chatPanel.getClientProperty("transcript");
                openTicketChats.remove(ticket.id, transcript);
            }
        });
        dialog.setContentPane(chatPanel);
        dialog.setVisible(true);
    }

    private JPanel createTicketChatPanel(Models.Ticket ticket, Component dialogParent, boolean embedded) {
        JPanel transcript = new JPanel();
        transcript.setLayout(new BoxLayout(transcript, BoxLayout.Y_AXIS));
        transcript.setBackground(Color.WHITE);
        JLabel loadingLabel = new JLabel("Đang tải lịch sử trao đổi...");
        loadingLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        transcript.add(loadingLabel);
        if (!embedded) openTicketChats.put(ticket.id, transcript);
        loadingTicketChats.add(ticket.id);

        JTextField messageInput = new JTextField();
        messageInput.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        messageInput.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER, 1, true), new EmptyBorder(4, 8, 4, 8)));
        messageInput.setPreferredSize(new Dimension(100, 34));
        JButton sendButton = createStyledButton("Gửi", BLUE, Color.WHITE, 11);
        JButton attachButton = createStyledButton("Gửi ảnh/tệp", new Color(108, 117, 125), Color.WHITE, 11);
        sendButton.setMargin(new Insets(4, 8, 4, 8));
        attachButton.setMargin(new Insets(4, 8, 4, 8));
        sendButton.setEnabled(!"Hoàn thành".equalsIgnoreCase(ticket.status)
                && !"Đã hủy".equalsIgnoreCase(ticket.status));
        attachButton.setEnabled(sendButton.isEnabled());
        sendButton.addActionListener(e -> {
            String content = messageInput.getText().trim();
            if (content.isEmpty()) return;
            sendButton.setEnabled(false);
            new Thread(() -> {
                try {
                    Protocol.Packet response = sendAndReceiveSync(new Protocol.Packet(
                            Protocol.CMD_SEND_TICKET_CHAT, "", "",
                            new F11_TicketChat.ChatRequest(ticket.id, content)));
                    if (!Protocol.STATUS_SUCCESS.equals(response.status)) {
                        throw new IOException(response.message);
                    }
                    SwingUtilities.invokeLater(() -> messageInput.setText(""));
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(dialogParent, ex.getMessage(),
                            "F11 - Trao đổi trực tuyến", JOptionPane.ERROR_MESSAGE));
                } finally {
                    SwingUtilities.invokeLater(() -> sendButton.setEnabled(true));
                }
            }, "f11-send-chat").start();
        });
        messageInput.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "send-chat-message");
        messageInput.getActionMap().put("send-chat-message", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                sendButton.doClick();
            }
        });
        attachButton.addActionListener(e -> chooseAndSendChatAttachment(ticket.id, messageInput, sendButton,
                attachButton, dialogParent));
        JPanel composerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        composerActions.setOpaque(false);
        composerActions.add(attachButton);
        composerActions.add(sendButton);
        JPanel composer = new JPanel(new BorderLayout(6, 0));
        composer.setBorder(new EmptyBorder(6, 0, 0, 0));
        composer.add(messageInput, BorderLayout.CENTER);
        composer.add(composerActions, BorderLayout.EAST);
        composer.setPreferredSize(new Dimension(1, 40));
        composer.setMinimumSize(new Dimension(1, 40));

        JScrollPane transcriptScroll = new JScrollPane(transcript);
        transcriptScroll.getVerticalScrollBar().setUnitIncrement(16);
        JPanel content = new JPanel(new BorderLayout(0, 4));
        if (!embedded) content.setBorder(new EmptyBorder(12, 12, 12, 12));
        if (!embedded) {
            content.add(new JLabel("Trao đổi và tệp đính kèm được lưu theo yêu cầu. Giới hạn 10 MB/tệp."),
                    BorderLayout.NORTH);
        }
        content.add(transcriptScroll, BorderLayout.CENTER);
        content.add(composer, BorderLayout.SOUTH);
        content.putClientProperty("transcript", transcript);

        new Thread(() -> {
            try {
                Protocol.Packet response = sendAndReceiveSync(new Protocol.Packet(
                        Protocol.CMD_GET_TICKET_CHAT, "", "",
                        new F11_TicketChat.ChatRequest(ticket.id, "")));
                if (!Protocol.STATUS_SUCCESS.equals(response.status)) throw new IOException(response.message);
                if (!(response.data instanceof List<?> rows)) throw new IOException("Lịch sử trao đổi nhận được không hợp lệ.");
                List<Models.TicketChatMessage> history = new ArrayList<>();
                for (Object row : rows) {
                    if (row instanceof Models.TicketChatMessage chatMessage) {
                        history.add(chatMessage);
                    }
                }
                SwingUtilities.invokeLater(() -> {
                    transcript.removeAll();
                    if (history.isEmpty()) {
                        JLabel emptyMessage = new JLabel("Chưa có tin nhắn. Hãy bắt đầu trao đổi.");
                        emptyMessage.setAlignmentX(Component.LEFT_ALIGNMENT);
                        transcript.add(emptyMessage);
                    }
                    for (Models.TicketChatMessage message : history) {
                        renderChatMessage(ticket.id, transcript, message);
                    }
                    loadingTicketChats.remove(ticket.id);
                    List<PendingChatEvent> pending = pendingTicketChatMessages.remove(ticket.id);
                    if (pending != null) {
                        for (PendingChatEvent event : pending) {
                            appendTicketChatMessage(ticket.id, event.message,
                                    event.attachmentId, event.attachmentName);
                        }
                    }
                    transcript.revalidate();
                    transcript.repaint();
                    transcriptScroll.getVerticalScrollBar().setValue(
                            transcriptScroll.getVerticalScrollBar().getMaximum());
                });
                refreshChatUnreadCounts();
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    loadingTicketChats.remove(ticket.id);
                    transcript.removeAll();
                    transcript.add(new JLabel("Không thể tải lịch sử: " + ex.getMessage()));
                    transcript.revalidate();
                    transcript.repaint();
                    sendButton.setEnabled(false);
                    attachButton.setEnabled(false);
                });
            }
        }, "f11-load-chat").start();
        return content;
    }

    private void chooseAndSendChatAttachment(String ticketId, JTextField caption,
                                             JButton sendButton, JButton attachButton, Component dialog) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn ảnh hoặc tệp đính kèm (tối đa 10 MB)");
        if (chooser.showOpenDialog(dialog) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        if (file.length() <= 0 || file.length() > 10L * 1024 * 1024) {
            JOptionPane.showMessageDialog(dialog, "Tệp phải lớn hơn 0 byte và không vượt quá 10 MB.",
                    "Kích thước tệp không hợp lệ", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String extension = getFileExtension(file.getName());
        if (!java.util.Set.of("png", "jpg", "jpeg", "gif", "webp", "pdf", "txt",
                "docx", "xlsx", "zip").contains(extension)) {
            JOptionPane.showMessageDialog(dialog, "Định dạng cho phép: PNG/JPEG/GIF/WebP, PDF/TXT/DOCX/XLSX/ZIP.",
                    "Định dạng tệp không hỗ trợ", JOptionPane.WARNING_MESSAGE);
            return;
        }
        sendButton.setEnabled(false);
        attachButton.setEnabled(false);
        new Thread(() -> {
            try {
                byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                Protocol.Packet response = sendAndReceiveSync(new Protocol.Packet(
                        Protocol.CMD_SEND_TICKET_CHAT, "", "",
                        new F11_TicketChat.ChatAttachmentRequest(ticketId, file.getName(),
                                caption.getText().trim(), bytes)));
                if (!Protocol.STATUS_SUCCESS.equals(response.status)) throw new IOException(response.message);
                SwingUtilities.invokeLater(() -> {
                    caption.setText("");
                    JOptionPane.showMessageDialog(dialog, "Đã gửi tệp " + file.getName() + " qua TCP.");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(dialog, ex.getMessage(),
                        "Không thể gửi tệp chat", JOptionPane.ERROR_MESSAGE));
            } finally {
                SwingUtilities.invokeLater(() -> {
                    sendButton.setEnabled(true);
                    attachButton.setEnabled(true);
                });
            }
        }, "f11-send-attachment").start();
    }

    private String getFileExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }

    private void renderChatMessage(String ticketId, JPanel transcript,
                                   Models.TicketChatMessage chatMessage) {
        boolean mine = currentUser != null
                && chatMessage.sender.equalsIgnoreCase(currentUser.username);
        String deliveryState = mine ? (chatMessage.read ? "Đã đọc" : "Đã gửi") : "";
        addChatMessageCard(ticketId, transcript, chatMessage.sender, chatMessage.content,
                chatMessage.sentAt, deliveryState, chatMessage.id, chatMessage.attachmentName,
                chatMessage.attachmentMimeType);
    }

    private void addChatMessageCard(String ticketId, JPanel transcript, String sender,
                                    String message, String sentAt, String deliveryState,
                                    long attachmentId, String attachmentName, String mimeType) {
        boolean mine = currentUser != null && sender.equalsIgnoreCase(currentUser.username);
        Color bubbleColor = mine ? new Color(224, 242, 254) : new Color(241, 245, 249);
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(5, 8, 5, 8));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel bubble = new JPanel();
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.setBackground(bubbleColor);
        bubble.setBorder(new EmptyBorder(8, 11, 8, 11));
        bubble.setMaximumSize(new Dimension(420, Integer.MAX_VALUE));

        JLabel senderLabel = new JLabel((mine ? "Bạn" : sender) + "  ·  " + sentAt);
        senderLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        senderLabel.setForeground(new Color(71, 85, 105));
        senderLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        bubble.add(senderLabel);

        JTextArea messageText = new JTextArea(message);
        messageText.setEditable(false);
        messageText.setLineWrap(true);
        messageText.setWrapStyleWord(true);
        messageText.setBorder(null);
        messageText.setBackground(bubbleColor);
        messageText.setForeground(new Color(30, 41, 59));
        messageText.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        messageText.setAlignmentX(Component.LEFT_ALIGNMENT);
        messageText.setMaximumSize(new Dimension(390, Integer.MAX_VALUE));
        bubble.add(messageText);

        if (attachmentId > 0 && attachmentName != null && !attachmentName.isBlank()) {
            JLabel preview = null;
            if (isChatImage(attachmentName, mimeType)) {
                preview = new JLabel("Đang tải bản xem trước ảnh...");
                preview.setAlignmentX(Component.LEFT_ALIGNMENT);
                bubble.add(Box.createVerticalStrut(5));
                bubble.add(preview);
            }
            JButton download = createStyledButton("Tải tệp: " + attachmentName,
                    Color.WHITE, new Color(20, 35, 60), 11);
            download.setAlignmentX(Component.LEFT_ALIGNMENT);
            bubble.add(Box.createVerticalStrut(5));
            bubble.add(download);
            if (preview == null) {
                download.addActionListener(e ->
                        downloadChatAttachment(ticketId, attachmentId, attachmentName));
            } else {
                JLabel imagePreview = preview;
                download.setEnabled(false);
                loadChatImagePreview(ticketId, attachmentId, attachmentName, imagePreview, download);
            }
        }

        if (!deliveryState.isBlank()) {
            JLabel state = new JLabel(deliveryState);
            state.setFont(new Font("Segoe UI", Font.PLAIN, 9));
            state.setForeground(new Color(100, 116, 139));
            state.setAlignmentX(Component.LEFT_ALIGNMENT);
            bubble.add(Box.createVerticalStrut(3));
            bubble.add(state);
        }

        if (mine) {
            row.add(bubble, BorderLayout.EAST);
        } else {
            JPanel incoming = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            incoming.setOpaque(false);
            JLabel avatar = new JLabel(chatInitials(sender), SwingConstants.CENTER);
            avatar.setOpaque(true);
            avatar.setBackground(new Color(219, 234, 254));
            avatar.setForeground(new Color(30, 64, 175));
            avatar.setFont(new Font("Segoe UI", Font.BOLD, 10));
            avatar.setPreferredSize(new Dimension(30, 30));
            incoming.add(avatar);
            incoming.add(bubble);
            row.add(incoming, BorderLayout.WEST);
        }
        transcript.add(row);
    }

    private String chatInitials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1))
                .toUpperCase(java.util.Locale.ROOT);
    }

    private boolean isChatImage(String fileName, String mimeType) {
        if (mimeType != null && mimeType.toLowerCase(java.util.Locale.ROOT).startsWith("image/")) {
            return true;
        }
        String extension = getFileExtension(fileName);
        return java.util.Set.of("png", "jpg", "jpeg", "gif", "webp").contains(extension);
    }

    private void loadChatImagePreview(String ticketId, long messageId, String fileName,
                                      JLabel preview, JButton download) {
        new Thread(() -> {
            try {
                Protocol.Packet response = sendAndReceiveSync(new Protocol.Packet(
                        Protocol.CMD_GET_CHAT_ATTACHMENT, "", "",
                        new F11_TicketChat.ChatAttachmentRequest(ticketId, messageId)));
                if (!Protocol.STATUS_SUCCESS.equals(response.status)
                        || !(response.data instanceof Models.ChatAttachment attachment)) {
                    throw new IOException(response.message);
                }
                Image image;
                try (javax.imageio.stream.ImageInputStream input =
                             javax.imageio.ImageIO.createImageInputStream(
                                     new ByteArrayInputStream(attachment.bytes))) {
                    if (input == null) throw new IOException("Không đọc được dữ liệu ảnh.");
                    java.util.Iterator<javax.imageio.ImageReader> readers =
                            javax.imageio.ImageIO.getImageReaders(input);
                    if (!readers.hasNext()) throw new IOException("Định dạng ảnh không hỗ trợ xem trước.");
                    javax.imageio.ImageReader reader = readers.next();
                    try {
                        reader.setInput(input, true, true);
                        int width = reader.getWidth(0);
                        int height = reader.getHeight(0);
                        if ((long) width * height > 25_000_000L) {
                            throw new IOException("Ảnh quá lớn để xem trước.");
                        }
                        image = reader.read(0);
                    } finally {
                        reader.dispose();
                    }
                }
                if (image == null) throw new IOException("Không giải mã được ảnh.");
                int imageWidth = image.getWidth(null);
                int imageHeight = image.getHeight(null);
                double scale = Math.min(1.0, Math.min(420.0 / imageWidth, 300.0 / imageHeight));
                Image scaled = image.getScaledInstance(
                        Math.max(1, (int) (imageWidth * scale)),
                        Math.max(1, (int) (imageHeight * scale)), Image.SCALE_SMOOTH);
                ImageIcon icon = new ImageIcon(scaled);
                SwingUtilities.invokeLater(() -> {
                    preview.setText("");
                    preview.setIcon(icon);
                    preview.setToolTipText(fileName);
                    preview.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
                    preview.addMouseListener(new java.awt.event.MouseAdapter() {
                        @Override
                        public void mouseClicked(java.awt.event.MouseEvent event) {
                            JLabel fullPreview = new JLabel(new ImageIcon(image));
                            JOptionPane.showMessageDialog(mainFrame, new JScrollPane(fullPreview),
                                    fileName, JOptionPane.PLAIN_MESSAGE);
                        }
                    });
                    download.setEnabled(true);
                    download.addActionListener(e -> saveChatAttachment(attachment));
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    preview.setText("Không thể xem trước ảnh: " + ex.getMessage());
                    download.setEnabled(true);
                    download.addActionListener(e ->
                            downloadChatAttachment(ticketId, messageId, fileName));
                });
            }
        }, "f11-load-chat-image").start();
    }

    private void downloadChatAttachment(String ticketId, long messageId, String fileName) {
        new Thread(() -> {
            try {
                Protocol.Packet response = sendAndReceiveSync(new Protocol.Packet(
                        Protocol.CMD_GET_CHAT_ATTACHMENT, "", "",
                        new F11_TicketChat.ChatAttachmentRequest(ticketId, messageId)));
                if (!Protocol.STATUS_SUCCESS.equals(response.status)
                        || !(response.data instanceof Models.ChatAttachment attachment)) {
                    throw new IOException(response.message);
                }
                SwingUtilities.invokeLater(() -> saveChatAttachment(attachment));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(mainFrame, ex.getMessage(),
                        "Không thể tải tệp chat", JOptionPane.ERROR_MESSAGE));
            }
        }, "f11-download-attachment").start();
    }

    private void saveChatAttachment(Models.ChatAttachment attachment) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(attachment.fileName));
        if (chooser.showSaveDialog(mainFrame) == JFileChooser.APPROVE_OPTION) {
            try {
                java.nio.file.Files.write(chooser.getSelectedFile().toPath(), attachment.bytes);
                JOptionPane.showMessageDialog(mainFrame, "Đã lưu tệp tại "
                        + chooser.getSelectedFile().getAbsolutePath());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(mainFrame, ex.getMessage(),
                        "Không thể lưu tệp", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void appendTicketChatMessage(String ticketId, String message,
                                         long attachmentId, String attachmentName) {
        if (loadingTicketChats.contains(ticketId)) {
            pendingTicketChatMessages.computeIfAbsent(ticketId, key -> new java.util.concurrent.CopyOnWriteArrayList<>())
                    .add(new PendingChatEvent(message, attachmentId, attachmentName));
            return;
        }
        JPanel transcript = openTicketChats.get(ticketId);
        if (transcript == null) transcript = inspectorTicketChats.get(ticketId);
        if (transcript == null) return;
        addChatMessageCard(ticketId, transcript, "Người gửi", message,
                java.time.LocalTime.now().withNano(0).toString(), "", attachmentId, attachmentName, null);
        transcript.revalidate();
        transcript.repaint();
        JScrollPane scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(
                JScrollPane.class, transcript);
        if (scrollPane != null) {
            SwingUtilities.invokeLater(() -> scrollPane.getVerticalScrollBar().setValue(
                    scrollPane.getVerticalScrollBar().getMaximum()));
        }
    }

    private JButton chatActionButton;

    private void refreshChatUnreadCounts() {
        if (currentUser == null || networkService == null || !networkService.isConnected()) return;
        new Thread(() -> {
            try {
                Protocol.Packet response = sendAndReceiveSync(new Protocol.Packet(
                        Protocol.CMD_GET_UNREAD_CHAT_COUNTS, "", "", null));
                if (!Protocol.STATUS_SUCCESS.equals(response.status)) throw new IOException(response.message);
                if (!(response.data instanceof java.util.Map<?, ?> counts)) {
                    throw new IOException("Dữ liệu số tin chưa đọc từ Server không hợp lệ.");
                }
                unreadChatCounts.clear();
                for (java.util.Map.Entry<?, ?> entry : counts.entrySet()) {
                    if (entry.getKey() instanceof String ticketId && entry.getValue() instanceof Integer count) {
                        unreadChatCounts.put(ticketId, count);
                    }
                }
                SwingUtilities.invokeLater(this::updateChatUnreadLabel);
            } catch (Exception ex) {
                System.err.println("[F11] Không thể tải số tin chưa đọc: " + ex.getMessage());
            }
        }, "f11-unread-counts").start();
    }

    private void markChatRead(String ticketId) {
        new Thread(() -> {
            try {
                Protocol.Packet response = sendAndReceiveSync(new Protocol.Packet(
                        Protocol.CMD_GET_TICKET_CHAT, "", "",
                        new F11_TicketChat.ChatRequest(ticketId, "")));
                if (!Protocol.STATUS_SUCCESS.equals(response.status)) throw new IOException(response.message);
                refreshChatUnreadCounts();
            } catch (Exception ex) {
                System.err.println("[F11] Không thể đồng bộ trạng thái đã đọc: " + ex.getMessage());
            }
        }, "f11-mark-read").start();
    }

    private void updateChatUnreadLabel() {
        if (chatActionButton == null) return;
        int total = unreadChatCounts.values().stream().mapToInt(Integer::intValue).sum();
        chatActionButton.setText(total == 0
                ? "Trao đổi trực tuyến"
                : "Trao đổi trực tuyến · Chưa đọc: " + total);
    }

    private void exportTicketListToFile() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("BaoCao_YeuCau_" + System.currentTimeMillis() + ".txt"));
        if (fc.showSaveDialog(mainFrame) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            F03_ViewTickets.FileExportResult res = F03_ViewTickets.exportTicketsToFile(cachedTickets, file);
            if (res.success) {
                JOptionPane.showMessageDialog(mainFrame,
                        "Kết quả lưu file:\n- Đường dẫn: " + res.filePath + "\n- Dung lượng: " + res.fileSize + " bytes\n- Trạng thái: Thành công!",
                        "F03 - Xuất báo cáo file", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(mainFrame, res.message, "Lỗi lưu file", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // =========================================================================
    // F04: TIẾP NHẬN & PHÂN CÔNG KỸ THUẬT VIÊN + TẢI TỆP ĐÍNH KÈM
    // =========================================================================
    private void showAssignDialog(String ticketId) {
        if (!"admin".equalsIgnoreCase(currentUser.role)) {
            JOptionPane.showMessageDialog(mainFrame, "Chỉ Quản trị viên mới có quyền phân công kỹ thuật viên.");
            return;
        }

        JDialog d = new JDialog(mainFrame, "Phân công kỹ thuật viên (F04)", true);
        d.setSize(400, 220);
        d.setLocationRelativeTo(mainFrame);

        JPanel p = new JPanel(new GridLayout(0, 1, 8, 8));
        p.setBorder(new EmptyBorder(18, 20, 18, 20));

        p.add(new JLabel("Mã yêu cầu: " + ticketId));
        p.add(new JLabel("Chọn kỹ thuật viên phụ trách:"));

        JComboBox<String> cbTech = new JComboBox<>(new String[]{"tech01", "tech02", currentUser.username});
        p.add(cbTech);

        JButton btnConfirm = createStyledButton("Xác nhận phân công qua Socket", new Color(13, 110, 253), Color.WHITE, 13);
        p.add(btnConfirm);

        btnConfirm.addActionListener(e -> {
            String selectedTech = (String) cbTech.getSelectedItem();
            F04_AssignTechnician.AssignRequest req = new F04_AssignTechnician.AssignRequest(ticketId, selectedTech, currentUser.username);
            Protocol.Packet packet = new Protocol.Packet(Protocol.CMD_ASSIGN_TECH, "", "", req);
            sendCommandAsync(packet, d, "Lỗi phân công", response -> {
                d.dispose();
                refreshTicketsDirectly();
            });
        });

        d.setContentPane(p);
        d.setVisible(true);
    }

    private void downloadAttachmentFile(String ticketId, String fileName) {
        new Thread(() -> {
            try {
                F04_AssignTechnician.FileDownloadRequest req =
                        new F04_AssignTechnician.FileDownloadRequest(fileName, ticketId);
                Protocol.Packet packet = new Protocol.Packet(Protocol.CMD_DOWNLOAD_FILE, "", "", req);
                Protocol.Packet response = sendAndReceiveSync(packet);
                if (!Protocol.STATUS_SUCCESS.equals(response.status)) {
                    throw new IOException(response.message);
                }
                if (!(response.data instanceof F04_AssignTechnician.FileDownloadResponse fileResponse)) {
                    throw new IOException("Dữ liệu file nhận từ Server không hợp lệ.");
                }

                File targetDir = new File("client_downloads");
                java.nio.file.Files.createDirectories(targetDir.toPath());
                String localFileName = FileTransferHelper.getOriginalTicketAttachmentName(
                        ticketId, fileResponse.fileName);
                File dest = new File(targetDir, localFileName);
                java.nio.file.Files.write(dest.toPath(), fileResponse.fileBytes);
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(mainFrame,
                            "Tệp đính kèm đã tải thành công!\n- Tên file: " + localFileName + "\n- Lưu tại: " + dest.getAbsolutePath() + "\n- Kích thước: " + fileResponse.fileSize + " bytes",
                            "F04 - Tải file hoàn tất", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(mainFrame, ex.getMessage(), "F04 - Lỗi tải file", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    // =========================================================================
    // F05: CẬP NHẬT TRẠNG THÁI XỬ LÝ & TIẾN ĐỘ (% HOÀN THÀNH / SỐ BYTE)
    // =========================================================================
    private void showUpdateStatusDialog(String ticketId) {
        if (currentUser == null || !"tech".equalsIgnoreCase(currentUser.role)) {
            JOptionPane.showMessageDialog(mainFrame, "Chỉ Kỹ thuật viên mới được cập nhật tiến độ.");
            return;
        }

        Models.Ticket t = cachedTickets.stream().filter(x -> x.id.equals(ticketId)).findFirst().orElse(null);
        if (t == null) return;

        JDialog d = new JDialog(mainFrame, "Cập nhật trạng thái xử lý (F05) - " + ticketId, true);
        d.setSize(480, 420);
        d.setLocationRelativeTo(mainFrame);

        JPanel p = new JPanel(new GridLayout(0, 1, 6, 6));
        p.setBorder(new EmptyBorder(18, 22, 18, 22));

        JComboBox<String> cbStatus = new JComboBox<>(new String[]{"Đang xử lý", "Hoàn thành", "Đã hủy"});
        cbStatus.setSelectedItem(t.status);

        JSlider sliderProgress = new JSlider(0, 100, t.progress);
        sliderProgress.setMajorTickSpacing(25);
        sliderProgress.setMinorTickSpacing(5);
        sliderProgress.setPaintTicks(true);
        sliderProgress.setPaintLabels(true);

        JLabel lblPercent = new JLabel("Tiến độ: " + t.progress + "% hoàn thành");
        lblPercent.setFont(new Font("Segoe UI", Font.BOLD, 13));
        sliderProgress.addChangeListener(e -> lblPercent.setText("Tiến độ: " + sliderProgress.getValue() + "% hoàn thành"));

        JTextField txtNote = new JTextField();
        JLabel lblAttach = new JLabel("Tệp nghiệm thu / kết quả: Chưa chọn");
        JButton btnAttach = createStyledButton("Đính kèm kết quả xử lý...", Color.WHITE, new Color(20, 35, 60), 12);
        final File[] chosenResult = new File[1];

        btnAttach.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (fc.showOpenDialog(d) == JFileChooser.APPROVE_OPTION) {
                chosenResult[0] = fc.getSelectedFile();
                lblAttach.setText(chosenResult[0].getName() + " (" + chosenResult[0].length() + " bytes)");
            }
        });

        p.add(new JLabel("Mã yêu cầu: " + ticketId));
        p.add(new JLabel("Trạng thái mới:"));
        p.add(cbStatus);
        p.add(lblPercent);
        p.add(sliderProgress);
        p.add(new JLabel("Ghi chú xử lý:"));
        p.add(txtNote);

        JPanel fileBox = new JPanel(new BorderLayout(5, 0));
        fileBox.add(btnAttach, BorderLayout.WEST);
        fileBox.add(lblAttach, BorderLayout.CENTER);
        p.add(fileBox);

        JButton btnSave = createStyledButton("Lưu cập nhật qua Socket", new Color(25, 135, 84), Color.WHITE, 13);
        p.add(btnSave);

        btnSave.addActionListener(e -> {
            byte[] fileBytes = null;
            String fileName = "";
            if (chosenResult[0] != null) {
                fileName = chosenResult[0].getName();
                try (FileInputStream fis = new FileInputStream(chosenResult[0])) {
                    fileBytes = fis.readAllBytes();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(d, "Không thể đọc tệp kết quả: " + ex.getMessage(),
                            "Lỗi tệp", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            int pct = sliderProgress.getValue();
            String st = (String) cbStatus.getSelectedItem();

            F05_UpdateProgress.UpdateProgressRequest req = new F05_UpdateProgress.UpdateProgressRequest(
                    ticketId, st, pct, txtNote.getText().trim(), currentUser.username, fileName, fileBytes
            );

            Protocol.Packet packet = new Protocol.Packet(Protocol.CMD_UPDATE_STATUS, "", "", req);
            sendCommandAsync(packet, d, "Lỗi cập nhật", response -> {
                d.dispose();
                refreshTicketsDirectly();
            });
        });

        d.setContentPane(p);
        d.setVisible(true);
    }

    // =========================================================================
    // F08: QUẢN LÝ TÀI KHOẢN VÀ QUYỀN (CHỈ DÀNH CHO ADMIN)
    // =========================================================================
    private void showUserManagementWindow() {
        if (!"admin".equalsIgnoreCase(currentUser.role)) {
            JOptionPane.showMessageDialog(mainFrame, "Chỉ Quản trị viên mới có quyền sử dụng chức năng này.");
            return;
        }

        JDialog d = new JDialog(mainFrame, "Quản lý tài khoản & phân quyền (F08)", true);
        d.setSize(850, 500);
        d.setLocationRelativeTo(mainFrame);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(15, 15, 15, 15));
        root.setBackground(LIGHT_BG);

        DefaultTableModel userModel = new DefaultTableModel(
                new Object[]{"Tên đăng nhập", "Họ tên", "Vai trò", "Trạng thái", "Phòng ban"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable userTable = new JTable(userModel);
        userTable.setRowHeight(30);

        Runnable loadUsers = () -> new Thread(() -> {
            try {
                Protocol.Packet response = sendAndReceiveSync(new Protocol.Packet(
                        Protocol.CMD_GET_USERS, "", "",
                        new F08_UserManagement.AdminUserRequest(currentUser.username, currentUser.role)));
                if (!Protocol.STATUS_SUCCESS.equals(response.status)) {
                    throw new IOException(response.message);
                }
                @SuppressWarnings("unchecked") List<Models.User> list = (List<Models.User>) response.data;
                SwingUtilities.invokeLater(() -> {
                    userModel.setRowCount(0);
                    for (Models.User user : list) {
                        userModel.addRow(new Object[]{user.username, user.displayName, user.role, user.status,
                                user.department});
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(d,
                        "Không thể tải danh sách tài khoản từ Server: " + ex.getMessage(),
                        "Quản lý tài khoản", JOptionPane.ERROR_MESSAGE));
            }
        }, "f08-load-users").start();
        loadUsers.run();

        root.add(new JScrollPane(userTable), BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        JButton btnAdd = createStyledButton("+ Thêm người dùng", new Color(13, 110, 253), Color.WHITE, 12);
        JButton btnEdit = createStyledButton("Sửa hồ sơ", Color.WHITE, new Color(20, 35, 60), 12);
        JButton btnLock = createStyledButton("Khóa / Mở khóa", new Color(220, 53, 69), Color.WHITE, 12);
        JButton btnClose = createStyledButton("Đóng", new Color(108, 117, 125), Color.WHITE, 12);

        btnPanel.add(btnAdd); btnPanel.add(btnEdit); btnPanel.add(btnLock); btnPanel.add(btnClose);
        root.add(btnPanel, BorderLayout.SOUTH);

        // Thêm tài khoản
        btnAdd.addActionListener(e -> {
            JTextField u = new JTextField();
            JPasswordField p = new JPasswordField();
            JTextField name = new JTextField();
            JTextField department = new JTextField();
            JComboBox<String> r = new JComboBox<>(new String[]{"requester", "tech", "admin"});
            JPanel form = new JPanel(new GridLayout(0, 1, 4, 4));
            form.add(new JLabel("Tên đăng nhập:")); form.add(u);
            form.add(new JLabel("Mật khẩu:")); form.add(p);
            form.add(new JLabel("Họ tên hiển thị:")); form.add(name);
            form.add(new JLabel("Phòng ban:")); form.add(department);
            form.add(new JLabel("Vai trò:")); form.add(r);

            if (JOptionPane.showConfirmDialog(d, form, "Thêm tài khoản mới", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                if (u.getText().trim().isEmpty() || p.getPassword().length < 6 || name.getText().trim().isEmpty()
                        || ("requester".equals(r.getSelectedItem()) && department.getText().trim().isEmpty())) {
                    JOptionPane.showMessageDialog(d,
                            "Vui lòng nhập đủ thông tin; người gửi phải có phòng ban và mật khẩu tối thiểu 6 ký tự.");
                    return;
                }
                Models.User newUser = new Models.User(u.getText().trim(), new String(p.getPassword()),
                        name.getText().trim(), (String) r.getSelectedItem(), "Đang hoạt động",
                        department.getText().trim());
                new Thread(() -> {
                    try {
                        Protocol.Packet response = sendAndReceiveSync(new Protocol.Packet(
                                Protocol.CMD_ADD_USER, "", "", newUser));
                        if (!Protocol.STATUS_SUCCESS.equals(response.status)) {
                            throw new IOException(response.message);
                        }
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(d, response.message,
                                    "Quản lý tài khoản", JOptionPane.INFORMATION_MESSAGE);
                            loadUsers.run();
                        });
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(d,
                                "Không thể thêm tài khoản: " + ex.getMessage(),
                                "Quản lý tài khoản", JOptionPane.ERROR_MESSAGE));
                    }
                }, "f08-add-user").start();
            }
        });

        // Sửa vai trò
        btnEdit.addActionListener(e -> {
            int row = userTable.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(d, "Chọn tài khoản cần sửa."); return; }
            String username = (String) userModel.getValueAt(row, 0);
            String name = (String) userModel.getValueAt(row, 1);
            String currentRole = (String) userModel.getValueAt(row, 2);
            String currentStatus = (String) userModel.getValueAt(row, 3);
            String currentDepartment = (String) userModel.getValueAt(row, 4);

            JComboBox<String> r = new JComboBox<>(new String[]{"requester", "tech", "admin"});
            r.setSelectedItem(currentRole);
            JTextField txtName = new JTextField(name);
            JTextField txtDepartment = new JTextField(currentDepartment == null ? "" : currentDepartment);
            JPanel form = new JPanel(new GridLayout(0, 1, 4, 4));
            form.add(new JLabel("Họ tên:")); form.add(txtName);
            form.add(new JLabel("Phòng ban:")); form.add(txtDepartment);
            form.add(new JLabel("Vai trò:")); form.add(r);

            if (JOptionPane.showConfirmDialog(d, form, "Cập nhật vai trò cho " + username, JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                if ("requester".equals(r.getSelectedItem()) && txtDepartment.getText().trim().isEmpty()) {
                    JOptionPane.showMessageDialog(d, "Người gửi yêu cầu phải được gán phòng ban.");
                    return;
                }
                Models.User updated = new Models.User(username, "", txtName.getText().trim(),
                        (String) r.getSelectedItem(), currentStatus, txtDepartment.getText().trim());
                Protocol.Packet packet = new Protocol.Packet(Protocol.CMD_UPDATE_USER, "", "", updated);
                sendCommandAsync(packet, d, "Lỗi sửa", response -> {
                    JOptionPane.showMessageDialog(d, response.message);
                    loadUsers.run();
                });
            }
        });

        // Khóa / Mở khóa
        btnLock.addActionListener(e -> {
            int row = userTable.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(d, "Chọn tài khoản cần khóa / mở khóa."); return; }
            String username = (String) userModel.getValueAt(row, 0);
            String status = (String) userModel.getValueAt(row, 3);
            if (currentUser.username.equalsIgnoreCase(username)) {
                JOptionPane.showMessageDialog(d, "Không thể tự khóa tài khoản của chính bạn!");
                return;
            }
            boolean shouldLock = !"Đã khóa".equalsIgnoreCase(status);
            String action = shouldLock ? "Khóa" : "Mở khóa";
            if (JOptionPane.showConfirmDialog(d, action + " tài khoản '" + username + "'?", "Xác nhận", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                F08_UserManagement.LockUserRequest req = new F08_UserManagement.LockUserRequest(currentUser.username, username, shouldLock);
                Protocol.Packet packet = new Protocol.Packet(Protocol.CMD_LOCK_USER, "", "", req);
                sendCommandAsync(packet, d, "Lỗi khóa tài khoản", response -> {
                    JOptionPane.showMessageDialog(d, response.message);
                    loadUsers.run();
                });
            }
        });

        btnClose.addActionListener(e -> d.dispose());
        d.setContentPane(root);
        d.setVisible(true);
    }

    private void refreshTicketsDirectly() {
        requestTicketList();
    }

    private void logout() {
        if (networkService != null) {
            networkService.close();
            networkService = null;
        }
        currentUser = null;
        showLoginWindow();
    }

    public static JButton createStyledButton(String text, Color bg, Color fg, int fontSize) {
        return new ModernButton(text, bg, fg, fontSize);
    }
}
