package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

/** Application entry point. Choose one role to start its real application. */
public class LapTrinhMang_HeThongTiepNhanVaQuanLyYeuCauNoiBo {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) { }
            showLauncherWindow();
        });
    }

    private static void showLauncherWindow() {
        JFrame launcher = new JFrame("WorkDesk - Chọn Client hoặc Server");
        launcher.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        launcher.setSize(480, 300);
        launcher.setLocationRelativeTo(null);
        launcher.setResizable(false);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(245, 248, 252));
        panel.setBorder(new EmptyBorder(38, 42, 38, 42));

        JLabel title = new JLabel("WORKDESK", SwingConstants.CENTER);
        title.setAlignmentX(JLabel.CENTER_ALIGNMENT);
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(new Color(20, 35, 60));

        JLabel instruction = new JLabel("Chọn chức năng muốn khởi động", SwingConstants.CENTER);
        instruction.setAlignmentX(JLabel.CENTER_ALIGNMENT);
        instruction.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        instruction.setForeground(new Color(75, 85, 95));

        JButton serverButton = createButton("Khởi động Server (cổng 8888)",
                new Color(13, 110, 253), Color.WHITE, 14);
        JButton clientButton = createButton("Khởi động Client", new Color(22, 43, 74),
                Color.WHITE, 14);

        panel.add(title);
        panel.add(Box.createVerticalStrut(8));
        panel.add(instruction);
        panel.add(Box.createVerticalStrut(28));
        panel.add(serverButton);
        panel.add(Box.createVerticalStrut(12));
        panel.add(clientButton);

        serverButton.addActionListener(e -> {
            launcher.dispose();
            ServerMain server = new ServerMain();
            server.createAndShowGUI();
            server.startServer(ServerMain.DEFAULT_PORT);
        });

        clientButton.addActionListener(e -> {
            launcher.dispose();
            new ClientMainGUI().showLoginWindow();
        });

        launcher.setContentPane(panel);
        launcher.setVisible(true);
    }

    public static JButton createButton(String text, Color bg, Color fg, int fontSize) {
        ModernButton button = new ModernButton(text, bg, fg, fontSize);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        button.setAlignmentX(JButton.CENTER_ALIGNMENT);
        return button;
    }
}
