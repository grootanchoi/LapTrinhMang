package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
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

    private JFrame mainFrame;
    private volatile Socket clientSocket;
    private volatile ObjectOutputStream out;
    private volatile ObjectInputStream in;

    private Models.User currentUser;
    private String serverHost = "127.0.0.1";
    private int serverPort = 8888;
    private volatile boolean isListening = false;
    private final java.util.concurrent.atomic.AtomicBoolean isReconnecting =
            new java.util.concurrent.atomic.AtomicBoolean();
    private char[] reconnectPassword;
    private java.util.concurrent.ScheduledExecutorService heartbeatExecutor;
    private final java.util.Map<String, BlockingQueue<Protocol.Packet>> responseQueues = new ConcurrentHashMap<>();
    private final java.util.Map<String, Object> commandLocks = new ConcurrentHashMap<>();
    private final java.util.Map<String, JTextArea> openTicketChats = new ConcurrentHashMap<>();
    private final java.util.Map<String, JPanel> openTicketChatAttachments = new ConcurrentHashMap<>();
    private final java.util.Map<String, List<PendingChatEvent>> pendingTicketChatMessages = new ConcurrentHashMap<>();
    private final java.util.Set<String> loadingTicketChats = ConcurrentHashMap.newKeySet();
    private final java.util.Map<String, Integer> unreadChatCounts = new ConcurrentHashMap<>();

    // Bảng dữ liệu và cache
    private DefaultTableModel ticketTableModel;
    private List<Models.Ticket> cachedTickets = new ArrayList<>();
    private JLabel statusConnectionLabel;

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
        JTextField txtHost = new JTextField(serverHost);
        JTextField txtPort = new JTextField(String.valueOf(serverPort));
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

            statusConnectionLabel.setText("Đang kết nối đến " + host + ":" + port + "...");
            statusConnectionLabel.setForeground(BLUE);

            // Thực hiện F01 qua TCP Socket
            F01_Authentication.ConnectionResult res = F01_Authentication.performLogin(host, port, username, password);
            if (res.isConnected) {
                this.serverHost = host;
                this.serverPort = port;
                this.clientSocket = res.socket;
                this.out = res.out;
                this.in = res.in;
                this.currentUser = res.loggedInUser;
                this.reconnectPassword = password.toCharArray();

                statusConnectionLabel.setText("Kết nối thành công!");
                statusConnectionLabel.setForeground(GREEN);

                // Khởi động luồng nền lắng nghe thông báo thời gian thực F06
                startNotificationListener();
                startHeartbeat();

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

    // =========================================================================
    // F06: LUỒNG LẮNG NGHE THÔNG BÁO THỜI GIAN THỰC (MULTI-SOCKET NOTIFICATION)
    // =========================================================================
    private void startNotificationListener() {
        if (isListening) return;
        isListening = true;

        Thread listenerThread = new Thread(() -> {
            try {
                while (isListening && clientSocket != null && !clientSocket.isClosed()) {
                    Object obj = in.readObject();
                    if (obj instanceof Protocol.Packet packet) {
                        if (Protocol.CMD_NOTIFICATION.equals(packet.command)) {
                            // Nhận thông báo phát sóng từ Server (F06)
                            Models.NotificationMessage notif = (Models.NotificationMessage) packet.data;
                            SwingUtilities.invokeLater(() -> {
                                if ("TICKET_CHAT_MESSAGE".equals(notif.eventType)) {
                                    appendTicketChatMessage(notif.ticketId, notif.message,
                                            notif.attachmentMessageId, notif.attachmentName);
                                    if (openTicketChats.containsKey(notif.ticketId)) {
                                        markChatRead(notif.ticketId);
                                    } else {
                                        refreshChatUnreadCounts();
                                    }
                                }
                                // Tự động làm mới danh sách yêu cầu (F03)
                                requestTicketList();

                                // Hiển thị thông báo
                                if (!"TICKET_CHAT_MESSAGE".equals(notif.eventType)
                                        || !openTicketChats.containsKey(notif.ticketId)) {
                                    showToastNotification("THÔNG BÁO HỆ THỐNG (" + notif.timestamp + ")\n" + notif.message);
                                }
                            });
                        } else if (Protocol.CMD_GET_TICKETS.equals(packet.command)) {
                            if (Protocol.STATUS_SUCCESS.equals(packet.status) && packet.data instanceof List<?>) {
                                @SuppressWarnings("unchecked") List<Models.Ticket> rows = (List<Models.Ticket>) packet.data;
                                SwingUtilities.invokeLater(() -> displayTickets(rows));
                            }
                            responseQueues.computeIfAbsent(packet.command, k -> new LinkedBlockingQueue<>()).offer(packet);
                        } else {
                            responseQueues.computeIfAbsent(packet.command, k -> new LinkedBlockingQueue<>()).offer(packet);
                        }
                    }
                }
            } catch (Exception e) {
                if (isListening && currentUser != null) {
                    System.err.println("[Client] Mất kết nối tới Server: " + e.getMessage());
                    isListening = false;
                    beginReconnect();
                }
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void startHeartbeat() {
        if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) return;
        heartbeatExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "workdesk-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        heartbeatExecutor.scheduleWithFixedDelay(() -> {
            if (currentUser == null || isReconnecting.get() || clientSocket == null || clientSocket.isClosed()) return;
            try {
                Protocol.Packet response = sendAndReceiveSync(new Protocol.Packet(Protocol.CMD_PING, "", "", null));
                if (!Protocol.STATUS_SUCCESS.equals(response.status)) throw new IOException("Server heartbeat rejected.");
            } catch (Exception e) {
                System.err.println("[Client] Heartbeat thất bại: " + e.getMessage());
                beginReconnect();
            }
        }, 15, 15, TimeUnit.SECONDS);
    }

    private void beginReconnect() {
        if (currentUser == null || !isReconnecting.compareAndSet(false, true)) return;
        SwingUtilities.invokeLater(() -> {
            if (mainFrame != null) mainFrame.setTitle("WorkDesk | Đang mất kết nối - tự động kết nối lại...");
        });
        Thread reconnectThread = new Thread(() -> {
            long delaySeconds = 1;
            while (currentUser != null && isReconnecting.get()) {
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(delaySeconds));
                    String password = reconnectPassword == null ? "" : new String(reconnectPassword);
                    F01_Authentication.ConnectionResult result = F01_Authentication.performLogin(
                            serverHost, serverPort, currentUser.username, password);
                    if (!result.isConnected) {
                        System.err.println("[Client] Kết nối lại chưa thành công: " + result.statusMessage);
                        delaySeconds = Math.min(delaySeconds * 2, 30);
                        continue;
                    }
                    Socket previousSocket = clientSocket;
                    isListening = false;
                    clientSocket = result.socket;
                    out = result.out;
                    in = result.in;
                    currentUser = result.loggedInUser;
                    responseQueues.values().forEach(BlockingQueue::clear);
                    if (previousSocket != null && !previousSocket.isClosed()) previousSocket.close();
                    isListening = false;
                    isReconnecting.set(false);
                    SwingUtilities.invokeLater(() -> {
                        if (mainFrame != null) {
                            mainFrame.setTitle("WorkDesk | Đã kết nối lại - [" + currentUser.getRoleName() + "]");
                        }
                    });
                    startNotificationListener();
                    requestTicketList();
                    refreshChatUnreadCounts();
                    reloadOpenChatHistories();
                    System.out.println("[Client] Đã kết nối lại tới Server.");
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("[Client] Lỗi trong quá trình tự kết nối lại: " + e.getMessage());
                    delaySeconds = Math.min(delaySeconds * 2, 30);
                }
            }
            isReconnecting.set(false);
        }, "workdesk-reconnect");
        reconnectThread.setDaemon(true);
        reconnectThread.start();
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
                        JTextArea transcript = openTicketChats.get(ticketId);
                        JPanel attachments = openTicketChatAttachments.get(ticketId);
                        if (transcript == null || attachments == null) return;
                        transcript.setText("");
                        attachments.removeAll();
                        for (Models.TicketChatMessage message : messages) {
                            renderChatMessage(ticketId, transcript, attachments, message);
                        }
                        transcript.setCaretPosition(transcript.getDocument().getLength());
                        attachments.revalidate();
                        attachments.repaint();
                    });
                } catch (Exception e) {
                    System.err.println("[F11] Không thể đồng bộ lại lịch sử " + ticketId + ": " + e.getMessage());
                }
            }, "f11-reload-after-reconnect").start();
        }
    }

    private void showToastNotification(String message) {
        JOptionPane.showMessageDialog(mainFrame, message, "Thông báo thời gian thực (F06)", JOptionPane.INFORMATION_MESSAGE);
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
        boolean canAssignTechnician = isAdmin;
        mainFrame = new JFrame("WorkDesk | Bàn làm việc - [" + currentUser.displayName + " / " + currentUser.getRoleName() + "]");
        mainFrame.setSize(1180, 720);
        mainFrame.setMinimumSize(new Dimension(900, 600));
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(LIGHT_BG);

        // Header trên cùng
        JPanel header = new JPanel(new BorderLayout());
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

        JButton btnUsers = createStyledButton("Quản lý tài khoản (F08)", new Color(25, 135, 84), Color.WHITE, 12);
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

        header.add(lblLogo, BorderLayout.WEST);
        header.add(userPanel, BorderLayout.EAST);
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

        JButton btnNewTicket = createStyledButton("+  Tạo yêu cầu mới (F02)", new Color(13, 110, 253), Color.WHITE, 13);
        btnNewTicket.setVisible(isRequester);
        btnNewTicket.addActionListener(e -> showCreateTicketDialog());

        JButton btnExportFile = createStyledButton("Xuất danh sách ra File (F03)", Color.WHITE, new Color(20, 35, 60), 13);
        btnExportFile.addActionListener(e -> exportTicketListToFile());

        JButton btnRefresh = createStyledButton("Làm mới danh sách", Color.WHITE, new Color(20, 35, 60), 13);
        btnRefresh.addActionListener(e -> requestTicketList());
        JButton btnStats = createStyledButton("Thống kê (F09)", new Color(111, 66, 193), Color.WHITE, 12);
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
        JButton btnSearch = createStyledButton("Tìm / Lọc (F07)", BLUE, Color.WHITE, 12);
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

        ticketTableModel = new DefaultTableModel(
                new Object[]{"Mã YC", "Tiêu đề", "Danh mục", "Mức ưu tiên", "Người gửi", "Kỹ thuật viên", "Tiến độ", "Trạng thái", "Hạn SLA", "File đính kèm"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };

        JTable table = new JTable(ticketTableModel);
        table.setRowHeight(36);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Renderer cho cột tiến độ
        table.getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                l.setHorizontalAlignment(SwingConstants.CENTER);
                l.setFont(new Font("Segoe UI", Font.BOLD, 12));
                return l;
            }
        });

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
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

        JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        actionButtons.setOpaque(false);

        JButton btnDetail = createStyledButton("Xem chi tiết", Color.WHITE, new Color(20, 35, 60), 12);
        btnDetail.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(mainFrame, "Vui lòng chọn một yêu cầu trên bảng."); return; }
            showTicketDetailDialog((String) table.getValueAt(row, 0));
        });

        JButton btnAssign = createStyledButton("Phân công KTV (F04)", new Color(13, 110, 253), Color.WHITE, 12);
        btnAssign.setVisible(canAssignTechnician);
        btnAssign.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(mainFrame, "Vui lòng chọn một yêu cầu cần phân công."); return; }
            showAssignDialog((String) table.getValueAt(row, 0));
        });

        JButton btnUpdateStatus = createStyledButton("Cập nhật tiến độ & trạng thái (F05)", new Color(25, 135, 84), Color.WHITE, 12);
        btnUpdateStatus.setVisible(canManageTickets);
        btnUpdateStatus.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(mainFrame, "Vui lòng chọn một yêu cầu cần cập nhật."); return; }
            showUpdateStatusDialog((String) table.getValueAt(row, 0));
        });

        JButton btnDownloadAttach = createStyledButton("Tải tệp đính kèm (F04)", new Color(75, 85, 99), Color.WHITE, 12);
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

        JButton btnChat = createStyledButton("Trao đổi trực tuyến (F11)", new Color(111, 66, 193), Color.WHITE, 12);
        if (isAdmin) btnChat.setText("Lịch sử trò chuyện (F11)");
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

        JButton btnEditTicket = createStyledButton("Sửa yêu cầu", Color.WHITE, new Color(20, 35, 60), 12);
        btnEditTicket.setVisible(isAdmin);
        btnEditTicket.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(mainFrame, "Vui lòng chọn yêu cầu cần sửa."); return; }
            String ticketId = (String) table.getValueAt(row, 0);
            cachedTickets.stream().filter(item -> item.id.equals(ticketId)).findFirst()
                    .ifPresent(this::showEditTicketDialog);
        });

        JButton btnDeleteTicket = createStyledButton("Xóa yêu cầu", new Color(220, 53, 69), Color.WHITE, 12);
        btnDeleteTicket.setVisible(isAdmin);
        btnDeleteTicket.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(mainFrame, "Vui lòng chọn yêu cầu cần xóa."); return; }
            deleteTicket((String) table.getValueAt(row, 0));
        });

        actionButtons.add(btnDetail);
        actionButtons.add(btnAssign);
        actionButtons.add(btnUpdateStatus);
        actionButtons.add(btnDownloadAttach);
        actionButtons.add(btnChat);
        actionButtons.add(btnEditTicket);
        actionButtons.add(btnDeleteTicket);

        bottomBar.add(actionButtons, BorderLayout.WEST);
        tableCard.add(bottomBar, BorderLayout.SOUTH);

        body.add(tableCard, BorderLayout.CENTER);
        root.add(body, BorderLayout.CENTER);

        mainFrame.setContentPane(root);
        mainFrame.setVisible(true);

        // Lấy danh sách lần đầu
        requestTicketList();
        refreshChatUnreadCounts();
    }

    // =========================================================================
    // F03: GỬI LỆNH LẤY DANH SÁCH YÊU CẦU QUA SOCKET
    // =========================================================================
    public void requestTicketList() {
        new Thread(() -> {
            try {
                F03_ViewTickets.ViewTicketsRequest req = new F03_ViewTickets.ViewTicketsRequest(currentUser.username, currentUser.role);
                Protocol.Packet packet = new Protocol.Packet(Protocol.CMD_GET_TICKETS, "", "", req);
                sendAndReceiveSync(packet);
            } catch (Exception e) {
                System.err.println("Lỗi gửi yêu cầu danh sách: " + e.getMessage());
            }
        }).start();
    }

    public synchronized Protocol.Packet sendAndReceiveSync(Protocol.Packet sendPacket) throws Exception {
        String command = sendPacket.command;
        Object commandLock = commandLocks.computeIfAbsent(command, k -> new Object());
        synchronized (commandLock) {
            BlockingQueue<Protocol.Packet> queue = responseQueues.computeIfAbsent(command, k -> new LinkedBlockingQueue<>());
            queue.clear();
            synchronized (out) {
                out.writeObject(sendPacket);
                out.flush();
            }
            Protocol.Packet response = queue.poll(15, TimeUnit.SECONDS);
            if (response == null) throw new IOException("Server không phản hồi lệnh " + command + " trong 15 giây.");
            return response;
        }
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
        cachedTickets = new ArrayList<>(rows);
        if (ticketTableModel == null) return;
        ticketTableModel.setRowCount(0);
        for (Models.Ticket t : rows) ticketTableModel.addRow(new Object[]{t.id, t.title, t.category, t.priority,
                t.owner, t.assignedTech, t.progress + "%", t.status, formatSlaDeadline(t.slaDueAt), t.attachmentName});
    }

    private String formatSlaDeadline(String value) {
        if (value == null || value.isBlank()) return "Chưa có hạn";
        try {
            java.time.LocalDateTime due = java.time.LocalDateTime.parse(value);
            String prefix = due.isBefore(java.time.LocalDateTime.now()) ? "Quá hạn: " : "";
            return prefix + due.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"));
        } catch (java.time.format.DateTimeParseException e) {
            return value;
        }
    }

    private void showEditTicketDialog(Models.Ticket ticket) {
        if (!"admin".equalsIgnoreCase(currentUser.role)) return;
        JTextField title = new JTextField(ticket.title);
        JTextField category = new JTextField(ticket.category);
        JComboBox<String> priority = new JComboBox<>(new String[]{"Thấp", "Bình thường", "Cao", "Khẩn cấp"});
        priority.setSelectedItem(ticket.priority);
        JTextArea detail = new JTextArea(ticket.detail, 5, 24);
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);
        JPanel form = new JPanel(new GridLayout(0, 1, 5, 5));
        form.add(new JLabel("Tiêu đề:")); form.add(title);
        form.add(new JLabel("Danh mục:")); form.add(category);
        form.add(new JLabel("Ưu tiên:")); form.add(priority);
        form.add(new JLabel("Mô tả:")); form.add(new JScrollPane(detail));
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
        dialog.setSize(520, 500);
        dialog.setLocationRelativeTo(mainFrame);

        JPanel p = new JPanel(new GridLayout(0, 1, 6, 6));
        p.setBorder(new EmptyBorder(18, 22, 18, 22));

        JTextField txtTitle = new JTextField();
        JComboBox<String> cbCategory = new JComboBox<>(new String[]{"IT - Phần cứng", "Mạng & Internet", "Phần mềm & Cấp quyền", "Nhân sự", "Hành chính", "Tài chính"});
        JComboBox<String> cbPriority = new JComboBox<>(new String[]{"Bình thường", "Thấp", "Cao", "Khẩn cấp"});
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
            // Nếu có file đính kèm, sao chép vào server storage trước
            if (chosenFile[0] != null) {
                try {
                    File dest = new File(FileTransferHelper.SERVER_STORAGE_DIR, fileName);
                    try (FileInputStream fis = new FileInputStream(chosenFile[0]);
                         FileOutputStream fos = new FileOutputStream(dest)) {
                        fis.transferTo(fos);
                    }
                } catch (Exception ignored) {}
            }

            F02_CreateTicket.CreateTicketRequest req = new F02_CreateTicket.CreateTicketRequest(
                    title, (String) cbCategory.getSelectedItem(), (String) cbPriority.getSelectedItem(),
                    detail, currentUser.username, fileName
            );

            try {
                Protocol.Packet packet = new Protocol.Packet(Protocol.CMD_CREATE_TICKET, "", "", req);
                synchronized (out) {
                    out.writeObject(packet);
                    out.flush();
                }
                JOptionPane.showMessageDialog(dialog, "Yêu cầu đã được gửi lên Server qua Socket!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
                dialog.dispose();
                refreshTicketsDirectly();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Lỗi gửi gói tin Socket: " + ex.getMessage());
            }
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
        p.add(new JLabel("<html><b>Người tạo:</b> " + t.owner + " | <b>Ngày:</b> " + t.createdDate + "</html>"));
        p.add(new JLabel("<html><b>Kỹ thuật viên phụ trách:</b> " + t.assignedTech + "</html>"));
        p.add(new JLabel("<html><b>Trạng thái:</b> " + t.status + " | <b>Tiến độ:</b> " + t.progress + "%</html>"));
        p.add(new JLabel("<html><b>Tệp đính kèm:</b> " + (t.attachmentName.isEmpty() ? "Không có" : t.attachmentName) + "</html>"));

        JTextArea ta = new JTextArea(t.detail);
        ta.setEditable(false);
        ta.setLineWrap(true);
        p.add(new JLabel("Nội dung chi tiết:"));
        p.add(new JScrollPane(ta));

        JButton btnHistory = createStyledButton("Lịch sử xử lý & đánh giá (F10)", new Color(111, 66, 193), Color.WHITE, 12);
        btnHistory.addActionListener(e -> showHistoryReviewDialog(t));
        p.add(btnHistory);

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
                    JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                    boolean canRate = "requester".equalsIgnoreCase(currentUser.role) && currentUser.username.equalsIgnoreCase(ticket.owner) && "Hoàn thành".equalsIgnoreCase(ticket.status);
                    if (canRate) {
                        JComboBox<Integer> rating = new JComboBox<>(new Integer[]{5, 4, 3, 2, 1});
                        JTextField comment = new JTextField(23); comment.setToolTipText("Nhận xét về kết quả hỗ trợ");
                        JButton save = createStyledButton("Lưu đánh giá", BLUE, Color.WHITE, 12);
                        save.addActionListener(e -> saveTicketReview(ticket.id, (Integer) rating.getSelectedItem(), comment.getText(), dialog));
                        footer.add(new JLabel("Điểm: ")); footer.add(rating); footer.add(comment); footer.add(save);
                    }
                    JButton close = createStyledButton("Đóng", new Color(108,117,125), Color.WHITE, 12); close.addActionListener(e -> dialog.dispose()); footer.add(close);
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
        JTextArea existingChat = openTicketChats.get(ticket.id);
        if (existingChat != null) {
            Window window = SwingUtilities.getWindowAncestor(existingChat);
            if (window != null) window.toFront();
            return;
        }

        JDialog dialog = new JDialog(mainFrame, "Trao đổi trực tuyến - " + ticket.id, false);
        dialog.setSize(620, 480);
        dialog.setLocationRelativeTo(mainFrame);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JTextArea transcript = new JTextArea();
        transcript.setEditable(false);
        transcript.setLineWrap(true);
        transcript.setWrapStyleWord(true);
        transcript.setText("Đang tải lịch sử trao đổi...\n");
        JPanel attachments = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        openTicketChats.put(ticket.id, transcript);
        openTicketChatAttachments.put(ticket.id, attachments);
        loadingTicketChats.add(ticket.id);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                openTicketChats.remove(ticket.id, transcript);
                openTicketChatAttachments.remove(ticket.id, attachments);
                pendingTicketChatMessages.remove(ticket.id);
                loadingTicketChats.remove(ticket.id);
            }
        });

        JTextField messageInput = new JTextField();
        JButton sendButton = createStyledButton("Gửi qua TCP", BLUE, Color.WHITE, 12);
        JButton attachButton = createStyledButton("Gửi ảnh/tệp", new Color(108, 117, 125), Color.WHITE, 12);
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
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(dialog, ex.getMessage(),
                            "F11 - Trao đổi trực tuyến", JOptionPane.ERROR_MESSAGE));
                } finally {
                    SwingUtilities.invokeLater(() -> sendButton.setEnabled(true));
                }
            }, "f11-send-chat").start();
        });
        messageInput.addActionListener(e -> sendButton.doClick());

        attachButton.addActionListener(e -> chooseAndSendChatAttachment(ticket.id, messageInput, sendButton,
                attachButton, dialog));
        JPanel composerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        composerActions.add(attachButton);
        composerActions.add(sendButton);
        JPanel composer = new JPanel(new BorderLayout(8, 0));
        composer.setBorder(new EmptyBorder(10, 0, 0, 0));
        composer.add(messageInput, BorderLayout.CENTER);
        composer.add(composerActions, BorderLayout.EAST);

        JPanel transcriptPanel = new JPanel(new BorderLayout(4, 4));
        transcriptPanel.add(new JScrollPane(transcript), BorderLayout.CENTER);
        transcriptPanel.add(new JScrollPane(attachments), BorderLayout.SOUTH);
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(new EmptyBorder(12, 12, 12, 12));
        content.add(new JLabel("Trao đổi và tệp đính kèm được lưu theo yêu cầu. Giới hạn 10 MB/tệp."), BorderLayout.NORTH);
        content.add(transcriptPanel, BorderLayout.CENTER);
        content.add(composer, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.setVisible(true);

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
                    transcript.setText("");
                    attachments.removeAll();
                    if (history.isEmpty()) transcript.append("Chưa có tin nhắn. Hãy bắt đầu trao đổi.\n");
                    for (Models.TicketChatMessage message : history) {
                        renderChatMessage(ticket.id, transcript, attachments, message);
                    }
                    loadingTicketChats.remove(ticket.id);
                    List<PendingChatEvent> pending = pendingTicketChatMessages.remove(ticket.id);
                    if (pending != null) {
                        for (PendingChatEvent event : pending) {
                            transcript.append(event.message + "\n");
                            addChatAttachmentButton(ticket.id, attachments, event.attachmentId, event.attachmentName);
                        }
                    }
                    transcript.setCaretPosition(transcript.getDocument().getLength());
                    attachments.revalidate();
                    attachments.repaint();
                });
                refreshChatUnreadCounts();
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    loadingTicketChats.remove(ticket.id);
                    transcript.setText("Không thể tải lịch sử: " + ex.getMessage());
                    sendButton.setEnabled(false);
                });
            }
        }, "f11-load-chat").start();
    }

    private void chooseAndSendChatAttachment(String ticketId, JTextField caption,
                                             JButton sendButton, JButton attachButton, JDialog dialog) {
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

    private void renderChatMessage(String ticketId, JTextArea transcript, JPanel attachments,
                                   Models.TicketChatMessage chatMessage) {
        String line = chatMessage.sentAt + " | " + chatMessage.sender + ": " + chatMessage.content
                + (chatMessage.sender.equalsIgnoreCase(currentUser.username)
                        ? (chatMessage.read ? " [Đã đọc]" : " [Đã gửi]") : "");
        transcript.append(line + "\n");
        if (chatMessage.attachmentName != null) {
            addChatAttachmentButton(ticketId, attachments, chatMessage.id, chatMessage.attachmentName);
        }
    }

    private void addChatAttachmentButton(String ticketId, JPanel attachments, long messageId, String fileName) {
        if (messageId <= 0 || fileName == null || fileName.isBlank()) return;
        JButton download = createStyledButton("Tải tệp: " + fileName, Color.WHITE, new Color(20, 35, 60), 11);
        download.addActionListener(e -> downloadChatAttachment(ticketId, messageId, fileName));
        attachments.add(download);
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
                SwingUtilities.invokeLater(() -> {
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
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(mainFrame, ex.getMessage(),
                        "Không thể tải tệp chat", JOptionPane.ERROR_MESSAGE));
            }
        }, "f11-download-attachment").start();
    }

    private void appendTicketChatMessage(String ticketId, String message,
                                         long attachmentId, String attachmentName) {
        if (loadingTicketChats.contains(ticketId)) {
            pendingTicketChatMessages.computeIfAbsent(ticketId, key -> new java.util.concurrent.CopyOnWriteArrayList<>())
                    .add(new PendingChatEvent(message, attachmentId, attachmentName));
            return;
        }
        JTextArea transcript = openTicketChats.get(ticketId);
        if (transcript == null) return;
        transcript.append(message + "\n");
        transcript.setCaretPosition(transcript.getDocument().getLength());
        addChatAttachmentButton(ticketId, openTicketChatAttachments.get(ticketId), attachmentId, attachmentName);
    }

    private JButton chatActionButton;

    private void refreshChatUnreadCounts() {
        if (currentUser == null || clientSocket == null || clientSocket.isClosed()) return;
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
                ? "Trao đổi trực tuyến (F11)"
                : "Trao đổi trực tuyến (F11) · Chưa đọc: " + total);
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
            try {
                Protocol.Packet packet = new Protocol.Packet(Protocol.CMD_ASSIGN_TECH, "", "", req);
                synchronized (out) {
                    out.writeObject(packet);
                    out.flush();
                }
                JOptionPane.showMessageDialog(d, "Đã gửi phân công cho " + selectedTech + "!");
                d.dispose();
                refreshTicketsDirectly();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(d, "Lỗi phân công: " + ex.getMessage());
            }
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
                File dest = new File(targetDir, fileResponse.fileName);
                java.nio.file.Files.write(dest.toPath(), fileResponse.fileBytes);
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(mainFrame,
                            "Tệp đính kèm đã tải thành công!\n- Tên file: " + fileResponse.fileName + "\n- Lưu tại: " + dest.getAbsolutePath() + "\n- Kích thước: " + fileResponse.fileSize + " bytes",
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
        if ("requester".equalsIgnoreCase(currentUser.role)) {
            JOptionPane.showMessageDialog(mainFrame, "Chỉ Kỹ thuật viên hoặc Quản trị viên mới được cập nhật tiến độ.");
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

            try {
                Protocol.Packet packet = new Protocol.Packet(Protocol.CMD_UPDATE_STATUS, "", "", req);
                synchronized (out) {
                    out.writeObject(packet);
                    out.flush();
                }
                JOptionPane.showMessageDialog(d, "Cập nhật tiến độ " + pct + "% thành công!", "F05 - Hoàn tất", JOptionPane.INFORMATION_MESSAGE);
                d.dispose();
                refreshTicketsDirectly();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(d, "Lỗi cập nhật: " + ex.getMessage());
            }
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
        d.setSize(750, 500);
        d.setLocationRelativeTo(mainFrame);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(15, 15, 15, 15));
        root.setBackground(LIGHT_BG);

        DefaultTableModel userModel = new DefaultTableModel(new Object[]{"Tên đăng nhập", "Họ tên", "Vai trò", "Trạng thái"}, 0) {
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
                        userModel.addRow(new Object[]{user.username, user.displayName, user.role, user.status});
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
        JButton btnEdit = createStyledButton("Sửa vai trò", Color.WHITE, new Color(20, 35, 60), 12);
        JButton btnLock = createStyledButton("Khóa / Mở khóa", new Color(220, 53, 69), Color.WHITE, 12);
        JButton btnClose = createStyledButton("Đóng", new Color(108, 117, 125), Color.WHITE, 12);

        btnPanel.add(btnAdd); btnPanel.add(btnEdit); btnPanel.add(btnLock); btnPanel.add(btnClose);
        root.add(btnPanel, BorderLayout.SOUTH);

        // Thêm tài khoản
        btnAdd.addActionListener(e -> {
            JTextField u = new JTextField();
            JPasswordField p = new JPasswordField();
            JTextField name = new JTextField();
            JComboBox<String> r = new JComboBox<>(new String[]{"requester", "tech", "admin"});
            JPanel form = new JPanel(new GridLayout(0, 1, 4, 4));
            form.add(new JLabel("Tên đăng nhập:")); form.add(u);
            form.add(new JLabel("Mật khẩu:")); form.add(p);
            form.add(new JLabel("Họ tên hiển thị:")); form.add(name);
            form.add(new JLabel("Vai trò:")); form.add(r);

            if (JOptionPane.showConfirmDialog(d, form, "Thêm tài khoản mới", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                if (u.getText().trim().isEmpty() || p.getPassword().length < 6 || name.getText().trim().isEmpty()) {
                    JOptionPane.showMessageDialog(d, "Vui lòng nhập đủ thông tin và mật khẩu tối thiểu 6 ký tự.");
                    return;
                }
                Models.User newUser = new Models.User(u.getText().trim(), new String(p.getPassword()), name.getText().trim(), (String) r.getSelectedItem(), "Đang hoạt động");
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

            JComboBox<String> r = new JComboBox<>(new String[]{"requester", "tech", "admin"});
            r.setSelectedItem(currentRole);
            JTextField txtName = new JTextField(name);
            JPanel form = new JPanel(new GridLayout(0, 1, 4, 4));
            form.add(new JLabel("Họ tên:")); form.add(txtName);
            form.add(new JLabel("Vai trò:")); form.add(r);

            if (JOptionPane.showConfirmDialog(d, form, "Cập nhật vai trò cho " + username, JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                Models.User updated = new Models.User(username, "", txtName.getText().trim(), (String) r.getSelectedItem(), currentStatus);
                try {
                    Protocol.Packet packet = new Protocol.Packet(Protocol.CMD_UPDATE_USER, "", "", updated);
                    synchronized (out) { out.writeObject(packet); out.flush(); }
                    JOptionPane.showMessageDialog(d, "Cập nhật vai trò thành công!");
                    loadUsers.run();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(d, "Lỗi sửa: " + ex.getMessage());
                }
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
                try {
                    Protocol.Packet packet = new Protocol.Packet(Protocol.CMD_LOCK_USER, "", "", req);
                    synchronized (out) { out.writeObject(packet); out.flush(); }
                    JOptionPane.showMessageDialog(d, action + " tài khoản thành công!");
                    loadUsers.run();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(d, "Lỗi: " + ex.getMessage());
                }
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
        isListening = false;
        isReconnecting.set(false);
        if (heartbeatExecutor != null) heartbeatExecutor.shutdownNow();
        if (reconnectPassword != null) {
            java.util.Arrays.fill(reconnectPassword, '\0');
            reconnectPassword = null;
        }
        try {
            if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
        } catch (IOException ignored) {}
        currentUser = null;
        showLoginWindow();
    }

    public static JButton createStyledButton(String text, Color bg, Color fg, int fontSize) {
        return new ModernButton(text, bg, fg, fontSize);
    }
}
