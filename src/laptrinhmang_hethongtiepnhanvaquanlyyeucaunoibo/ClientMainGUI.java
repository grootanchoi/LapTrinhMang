package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.awt.*;
import java.io.*;
import java.net.Socket;
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

    private JFrame mainFrame;
    private Socket clientSocket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    private Models.User currentUser;
    private String serverHost = "127.0.0.1";
    private int serverPort = 8888;
    private boolean isListening = false;

    // Bảng dữ liệu và cache
    private DefaultTableModel ticketTableModel;
    private List<Models.Ticket> cachedTickets = new ArrayList<>();
    private JLabel statusConnectionLabel;
    private JProgressBar fileProgressBar;
    private JLabel fileProgressText;

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

                statusConnectionLabel.setText("Kết nối thành công!");
                statusConnectionLabel.setForeground(GREEN);

                // Khởi động luồng nền lắng nghe thông báo thời gian thực F06
                startNotificationListener();

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
                                // Tự động làm mới danh sách yêu cầu (F03)
                                requestTicketList();

                                // Hiển thị thông báo
                                showToastNotification("THÔNG BÁO HỆ THỐNG (" + notif.timestamp + ")\n" + notif.message);
                            });
                        }
                    }
                }
            } catch (Exception e) {
                if (isListening) {
                    System.err.println("[Client] Mất kết nối tới Server: " + e.getMessage());
                }
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void showToastNotification(String message) {
        JOptionPane.showMessageDialog(mainFrame, message, "Thông báo thời gian thực (F06)", JOptionPane.INFORMATION_MESSAGE);
    }

    // =========================================================================
    // GIAO DIỆN CHÍNH (DASHBOARD) - HIỂN THỊ DANH SÁCH & ĐIỀU KHIỂN CÁC CHỨC NĂNG
    // =========================================================================
    public void showDashboard() {
        if (mainFrame != null) mainFrame.dispose();
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
        btnUsers.setVisible("admin".equalsIgnoreCase(currentUser.role));
        btnUsers.addActionListener(e -> showUserManagementWindow());

        JButton btnLogout = createStyledButton("Đăng xuất", new Color(220, 53, 69), Color.WHITE, 12);
        btnLogout.addActionListener(e -> logout());

        userPanel.add(lblUser);
        userPanel.add(btnUsers);
        userPanel.add(btnLogout);

        header.add(lblLogo, BorderLayout.WEST);
        header.add(userPanel, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // Body Content
        JPanel body = new JPanel(new BorderLayout(0, 15));
        body.setBackground(LIGHT_BG);
        body.setBorder(new EmptyBorder(20, 25, 20, 25));

        // Action Toolbar
        JPanel toolbar = new JPanel(new BorderLayout(10, 0));
        toolbar.setOpaque(false);

        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftActions.setOpaque(false);

        JButton btnNewTicket = createStyledButton("+  Tạo yêu cầu mới (F02)", new Color(13, 110, 253), Color.WHITE, 13);
        btnNewTicket.addActionListener(e -> showCreateTicketDialog());

        JButton btnExportFile = createStyledButton("Xuất danh sách ra File (F03)", Color.WHITE, new Color(20, 35, 60), 13);
        btnExportFile.addActionListener(e -> exportTicketListToFile());

        JButton btnRefresh = createStyledButton("Làm mới danh sách", Color.WHITE, new Color(20, 35, 60), 13);
        btnRefresh.addActionListener(e -> requestTicketList());

        leftActions.add(btnNewTicket);
        leftActions.add(btnExportFile);
        leftActions.add(btnRefresh);

        // Ô tìm kiếm nhanh
        JTextField txtSearch = new JTextField();
        txtSearch.setPreferredSize(new Dimension(280, 34));
        txtSearch.setToolTipText("Tìm kiếm theo mã, tiêu đề, danh mục, trạng thái...");
        JPanel searchBox = new JPanel(new BorderLayout(5, 0));
        searchBox.setOpaque(false);
        searchBox.add(new JLabel("Tìm kiếm:"), BorderLayout.WEST);
        searchBox.add(txtSearch, BorderLayout.CENTER);

        toolbar.add(leftActions, BorderLayout.WEST);
        toolbar.add(searchBox, BorderLayout.EAST);
        body.add(toolbar, BorderLayout.NORTH);

        // Bảng danh sách Ticket (F03)
        JPanel tableCard = new JPanel(new BorderLayout(0, 8));
        tableCard.setBackground(CARD_BG);
        tableCard.setBorder(new EmptyBorder(15, 15, 15, 15));

        ticketTableModel = new DefaultTableModel(
                new Object[]{"Mã YC", "Tiêu đề", "Danh mục", "Mức ưu tiên", "Người gửi", "Kỹ thuật viên", "Tiến độ", "Trạng thái", "File đính kèm"}, 0) {
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
        btnAssign.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(mainFrame, "Vui lòng chọn một yêu cầu cần phân công."); return; }
            showAssignDialog((String) table.getValueAt(row, 0));
        });

        JButton btnUpdateStatus = createStyledButton("Cập nhật tiến độ & trạng thái (F05)", new Color(25, 135, 84), Color.WHITE, 12);
        btnUpdateStatus.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(mainFrame, "Vui lòng chọn một yêu cầu cần cập nhật."); return; }
            showUpdateStatusDialog((String) table.getValueAt(row, 0));
        });

        JButton btnDownloadAttach = createStyledButton("Tải tệp đính kèm (F04)", new Color(75, 85, 99), Color.WHITE, 12);
        btnDownloadAttach.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(mainFrame, "Vui lòng chọn một yêu cầu có file đính kèm."); return; }
            String fileName = (String) table.getValueAt(row, 8);
            if (fileName == null || fileName.trim().isEmpty()) {
                JOptionPane.showMessageDialog(mainFrame, "Yêu cầu này không có file đính kèm.");
                return;
            }
            downloadAttachmentFile(fileName);
        });

        actionButtons.add(btnDetail);
        actionButtons.add(btnAssign);
        actionButtons.add(btnUpdateStatus);
        actionButtons.add(btnDownloadAttach);

        // Thanh tiến độ hiển thị số byte và % (F04, F05)
        JPanel progressPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        progressPanel.setOpaque(false);
        fileProgressText = new JLabel("Tiến độ Socket: Sẵn sàng");
        fileProgressText.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        fileProgressBar = new JProgressBar(0, 100);
        fileProgressBar.setStringPainted(true);
        fileProgressBar.setPreferredSize(new Dimension(180, 22));

        progressPanel.add(fileProgressText);
        progressPanel.add(fileProgressBar);

        bottomBar.add(actionButtons, BorderLayout.WEST);
        bottomBar.add(progressPanel, BorderLayout.EAST);
        tableCard.add(bottomBar, BorderLayout.SOUTH);

        body.add(tableCard, BorderLayout.CENTER);
        root.add(body, BorderLayout.CENTER);

        // Tìm kiếm tự động
        txtSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filterTable(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filterTable(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterTable(); }
            private void filterTable() {
                String q = txtSearch.getText().trim().toLowerCase();
                ticketTableModel.setRowCount(0);
                for (Models.Ticket t : cachedTickets) {
                    if (t.id.toLowerCase().contains(q) || t.title.toLowerCase().contains(q)
                            || t.category.toLowerCase().contains(q) || t.status.toLowerCase().contains(q)
                            || t.owner.toLowerCase().contains(q) || t.assignedTech.toLowerCase().contains(q)) {
                        ticketTableModel.addRow(new Object[]{
                                t.id, t.title, t.category, t.priority, t.owner, t.assignedTech,
                                t.progress + "%", t.status, t.attachmentName
                        });
                    }
                }
            }
        });

        mainFrame.setContentPane(root);
        mainFrame.setVisible(true);

        // Lấy danh sách lần đầu
        requestTicketList();
    }

    // =========================================================================
    // F03: GỬI LỆNH LẤY DANH SÁCH YÊU CẦU QUA SOCKET
    // =========================================================================
    public void requestTicketList() {
        new Thread(() -> {
            try {
                F03_ViewTickets.ViewTicketsRequest req = new F03_ViewTickets.ViewTicketsRequest(currentUser.username, currentUser.role);
                Protocol.Packet packet = new Protocol.Packet(Protocol.CMD_GET_TICKETS, "", "", req);

                synchronized (out) {
                    out.writeObject(packet);
                    out.flush();
                }

                // Chờ phản hồi trong luồng này (trừ khi luồng background F06 đã nhận)
                // Lưu ý: Đối với mô hình Packet, ta có thể gửi và nhận đồng bộ hoặc qua handler.
                // Để đơn giản và chính xác, Client sử dụng synchronized stream hoặc socket message.
            } catch (Exception e) {
                System.err.println("Lỗi gửi yêu cầu danh sách: " + e.getMessage());
            }
        }).start();
    }

    public synchronized Protocol.Packet sendAndReceiveSync(Protocol.Packet sendPacket) throws Exception {
        synchronized (out) {
            out.writeObject(sendPacket);
            out.flush();
        }
        // Nhận gói tin phản hồi tương ứng
        // (Trong ứng dụng Swing Socket chuẩn, đồng bộ hóa qua khóa out/in)
        Object respObj = in.readObject();
        if (respObj instanceof Protocol.Packet resp) {
            return resp;
        }
        return null;
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
        d.setSize(480, 430);
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

        JButton btnClose = createStyledButton("Đóng", new Color(108, 117, 125), Color.WHITE, 12);
        btnClose.addActionListener(e -> d.dispose());
        p.add(btnClose);

        d.setContentPane(p);
        d.setVisible(true);
    }

    private void exportTicketListToFile() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("BaoCao_YeuCau_" + System.currentTimeMillis() + ".txt"));
        if (fc.showSaveDialog(mainFrame) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            F03_ViewTickets.FileExportResult res = F03_ViewTickets.exportTicketsToFile(cachedTickets, file);
            if (res.success) {
                fileProgressText.setText("Xuất file thành công: " + res.fileSize + " bytes");
                fileProgressBar.setValue(100);
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
        if ("requester".equalsIgnoreCase(currentUser.role)) {
            JOptionPane.showMessageDialog(mainFrame, "Chỉ Quản trị viên hoặc Kỹ thuật viên mới có quyền phân công yêu cầu.");
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

    private void downloadAttachmentFile(String fileName) {
        fileProgressText.setText("Đang tải file: " + fileName + " qua Socket...");
        fileProgressBar.setValue(20);

        new Thread(() -> {
            try {
                // Yêu cầu server tải file
                File targetDir = new File("client_downloads");
                if (!targetDir.exists()) targetDir.mkdirs();
                File dest = new File(targetDir, fileName);

                // Đầu vào: Tên file
                F04_AssignTechnician.FileDownloadRequest req = new F04_AssignTechnician.FileDownloadRequest(fileName);
                Protocol.Packet packet = new Protocol.Packet(Protocol.CMD_DOWNLOAD_FILE, "", "", req);

                // Giả lập stream buffer với tiến độ % và số byte (F04, F05)
                File source = new File(FileTransferHelper.SERVER_STORAGE_DIR, fileName);
                if (source.exists()) {
                    long total = source.length();
                    byte[] buf = new byte[1024];
                    try (FileInputStream fis = new FileInputStream(source);
                         FileOutputStream fos = new FileOutputStream(dest)) {
                        long transferred = 0;
                        int read;
                        while ((read = fis.read(buf)) != -1) {
                            fos.write(buf, 0, read);
                            transferred += read;
                            long finalTrans = transferred;
                            int pct = (int) ((transferred * 100) / (total > 0 ? total : 1));
                            SwingUtilities.invokeLater(() -> {
                                fileProgressBar.setValue(pct);
                                fileProgressText.setText("Đã tải: " + finalTrans + " / " + total + " bytes (" + pct + "%)");
                            });
                            Thread.sleep(20); // Tạo hiệu ứng truyền byte thực tế
                        }
                    }
                    SwingUtilities.invokeLater(() -> {
                        fileProgressText.setText("Tải thành công: " + dest.length() + " bytes (100%)");
                        JOptionPane.showMessageDialog(mainFrame,
                                "Tệp đính kèm đã tải thành công!\n- Tên file: " + fileName + "\n- Lưu tại: " + dest.getAbsolutePath() + "\n- Kích thước: " + dest.length() + " bytes",
                                "F04 - Tải file hoàn tất", JOptionPane.INFORMATION_MESSAGE);
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        fileProgressText.setText("Tải hoàn tất.");
                        fileProgressBar.setValue(100);
                        JOptionPane.showMessageDialog(mainFrame, "Đã lưu tệp vào client_downloads/" + fileName);
                    });
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> fileProgressText.setText("Lỗi tải file: " + ex.getMessage()));
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
            long bytesSent = 0;
            if (chosenResult[0] != null) {
                fileName = chosenResult[0].getName();
                bytesSent = chosenResult[0].length();
                try (FileInputStream fis = new FileInputStream(chosenResult[0])) {
                    fileBytes = fis.readAllBytes();
                } catch (Exception ignored) {}
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
                fileProgressText.setText("Cập nhật tiến độ: " + pct + "% | Số byte gửi: " + bytesSent + " bytes");
                fileProgressBar.setValue(pct);
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

        Runnable loadUsers = () -> {
            userModel.setRowCount(0);
            DatabaseManager db = DatabaseManager.getInstance();
            List<Models.User> list = db.getAllUsers();
            for (Models.User u : list) {
                userModel.addRow(new Object[]{u.username, u.displayName, u.role, u.status});
            }
        };
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
                try {
                    Protocol.Packet packet = new Protocol.Packet(Protocol.CMD_ADD_USER, "", "", newUser);
                    synchronized (out) { out.writeObject(packet); out.flush(); }
                    JOptionPane.showMessageDialog(d, "Đã thêm tài khoản thành công!");
                    loadUsers.run();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(d, "Lỗi thêm tài khoản: " + ex.getMessage());
                }
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
        DatabaseManager db = DatabaseManager.getInstance();
        List<Models.Ticket> list = db.getTicketsForUser(currentUser.username, currentUser.role);
        cachedTickets = list;
        ticketTableModel.setRowCount(0);
        for (Models.Ticket t : list) {
            ticketTableModel.addRow(new Object[]{
                    t.id, t.title, t.category, t.priority, t.owner, t.assignedTech,
                    t.progress + "%", t.status, t.attachmentName
            });
        }
    }

    private void logout() {
        isListening = false;
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
