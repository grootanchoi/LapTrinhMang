package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * ĐIỂM KHỞI CHẠY CHÍNH CỦA ĐỒ ÁN LẬP TRÌNH MẠNG
 * Hệ thống tiếp nhận và quản lý yêu cầu nội bộ (WorkDesk)
 *
 * Kiến trúc module hóa theo đúng yêu cầu đề bài:
 * - F01_Authentication: Đăng nhập & xác thực tài khoản qua TCP Socket (IP, Port / trạng thái kết nối).
 * - F02_CreateTicket: Tạo yêu cầu hỗ trợ kỹ thuật (Sự kiện Socket / thông báo).
 * - F03_ViewTickets: Xem danh sách, chi tiết yêu cầu & xuất file báo cáo (File / kết quả lưu file).
 * - F04_AssignTechnician: Tiếp nhận, phân công KTV & truyền file đính kèm (Tên file / file đã tải).
 * - F05_UpdateProgress: Cập nhật trạng thái xử lý & theo dõi tiến độ (Số byte / % hoàn thành).
 * - F06_NotificationBroadcaster: Gửi thông báo khi trạng thái thay đổi tới nhiều Socket / nhiều phiên.
 * - F08_UserManagement: Quản lý tài khoản và quyền hạn (MySQL / Phân quyền Admin).
 */
public class LapTrinhMang_HeThongTiepNhanVaQuanLyYeuCauNoiBo {

    public static void main(String[] args) {
        System.setProperty("file.encoding", "UTF-8");
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            showLauncherWindow();
        });
    }

    private static void showLauncherWindow() {
        JFrame launcher = new JFrame("WorkDesk - Hệ Thống Tiếp Nhận & Quản Lý Yêu Cầu Nội Bộ");
        launcher.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        launcher.setSize(580, 480);
        launcher.setLocationRelativeTo(null);
        launcher.setResizable(false);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(245, 248, 252));
        panel.setBorder(new EmptyBorder(30, 40, 30, 40));

        JLabel brand = new JLabel("LẬP TRÌNH MẠNG MÁY TÍNH - TCP/IP SOCKET ARCHITECTURE");
        brand.setFont(new Font("Segoe UI", Font.BOLD, 12));
        brand.setForeground(new Color(30, 110, 220));

        JLabel title = new JLabel("HỆ THỐNG QUẢN LÝ YÊU CẦU NỘI BỘ");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(new Color(20, 35, 60));

        JLabel desc = new JLabel("<html>Đầy đủ các chức năng theo đặc tả:<br>"
                + "• <b>F01:</b> Đăng nhập và xác thực tài khoản (IP, Port / trạng thái kết nối)<br>"
                + "• <b>F02:</b> Tạo yêu cầu hỗ trợ kỹ thuật (Sự kiện Socket / thông báo)<br>"
                + "• <b>F03:</b> Xem danh sách và chi tiết yêu cầu (File / kết quả lưu file)<br>"
                + "• <b>F04:</b> Tiếp nhận và phân công KTV (Tên file / file đã tải)<br>"
                + "• <b>F05:</b> Cập nhật trạng thái xử lý (Số byte / % hoàn thành)<br>"
                + "• <b>F06:</b> Gửi thông báo thời gian thực (Nhiều Socket / nhiều phiên)<br>"
                + "• <b>F08:</b> Quản lý tài khoản và phân quyền quản trị</html>");
        desc.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        desc.setForeground(new Color(60, 70, 80));

        JButton btnStartAll = createButton(" Khởi chạy cả Server & Client (Khuyến nghị Demo)", new Color(25, 135, 84), Color.WHITE, 14);
        JButton btnStartServer = createButton("1. Chỉ khởi động Máy chủ Server (Port 8888)", new Color(13, 110, 253), Color.WHITE, 13);
        JButton btnStartClient = createButton("2. Chỉ khởi động Giao diện Client (Kết nối IP:Port)", new Color(22, 43, 74), Color.WHITE, 13);

        panel.add(brand);
        panel.add(Box.createVerticalStrut(5));
        panel.add(title);
        panel.add(Box.createVerticalStrut(15));
        panel.add(desc);
        panel.add(Box.createVerticalStrut(25));
        panel.add(btnStartAll);
        panel.add(Box.createVerticalStrut(10));
        panel.add(btnStartServer);
        panel.add(Box.createVerticalStrut(10));
        panel.add(btnStartClient);

        // Sự kiện các nút
        btnStartAll.addActionListener(e -> {
            launcher.dispose();
            // Khởi động server
            ServerMain server = new ServerMain();
            server.createAndShowGUI();
            server.startServer(ServerMain.DEFAULT_PORT);

            // Chờ 300ms rồi bật client
            new Thread(() -> {
                try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                SwingUtilities.invokeLater(() -> new ClientMainGUI().showLoginWindow());
            }).start();
        });

        btnStartServer.addActionListener(e -> {
            launcher.dispose();
            ServerMain server = new ServerMain();
            server.createAndShowGUI();
            server.startServer(ServerMain.DEFAULT_PORT);
        });

        btnStartClient.addActionListener(e -> {
            launcher.dispose();
            new ClientMainGUI().showLoginWindow();
        });

        launcher.setContentPane(panel);
        launcher.setVisible(true);
    }

    public static JButton createButton(String text, Color bg, Color fg, int fontSize) {
        ModernButton b = new ModernButton(text, bg, fg, fontSize);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        return b;
    }
}
