package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.awt.*;
import javax.swing.*;

/**
 * Nút bấm đồ họa hiện đại (Modern Custom Button)
 * Khắc phục triệt để lỗi Windows Look and Feel bỏ qua màu nền và làm chữ bị tàng hình.
 * Tự vẽ nền, viền và chữ với công nghệ khử răng cưa (Anti-Aliasing), tương thích 100% mọi OS.
 */
public class ModernButton extends JButton {
    private final Color normalBg;
    private final Color hoverBg;
    private final Color pressedBg;
    private final Color textColor;
    private final int cornerRadius;

    public ModernButton(String text, Color bg, Color fg, int fontSize) {
        this(text, bg, fg, fontSize, 6);
    }

    public ModernButton(String text, Color bg, Color fg, int fontSize, int cornerRadius) {
        super(text);
        this.normalBg = bg;
        this.hoverBg = calculateHover(bg);
        this.pressedBg = calculatePressed(bg);
        this.textColor = fg;
        this.cornerRadius = cornerRadius;

        setFont(new Font("Segoe UI", Font.BOLD, fontSize));
        setForeground(fg);
        setFocusPainted(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
    }

    private Color calculateHover(Color c) {
        if (c.equals(Color.WHITE) || (c.getRed() > 220 && c.getGreen() > 220 && c.getBlue() > 220)) {
            return new Color(230, 238, 250);
        }
        return new Color(
                Math.min(255, c.getRed() + 25),
                Math.min(255, c.getGreen() + 25),
                Math.min(255, c.getBlue() + 25)
        );
    }

    private Color calculatePressed(Color c) {
        return new Color(
                Math.max(0, c.getRed() - 35),
                Math.max(0, c.getGreen() - 35),
                Math.max(0, c.getBlue() - 35)
        );
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        int w = getWidth();
        int h = getHeight();

        // 1. Vẽ nền màu sắc nét
        if (getModel().isPressed()) {
            g2.setColor(pressedBg);
        } else if (getModel().isRollover()) {
            g2.setColor(hoverBg);
        } else {
            g2.setColor(normalBg);
        }
        g2.fillRoundRect(0, 0, w, h, cornerRadius, cornerRadius);

        // 2. Vẽ viền nút rõ ràng
        boolean isLight = normalBg.equals(Color.WHITE) || (normalBg.getRed() > 210 && normalBg.getGreen() > 210 && normalBg.getBlue() > 210);
        if (isLight) {
            g2.setColor(new Color(150, 165, 185));
            g2.drawRoundRect(0, 0, w - 1, h - 1, cornerRadius, cornerRadius);
        } else {
            g2.setColor(normalBg.darker());
            g2.drawRoundRect(0, 0, w - 1, h - 1, cornerRadius, cornerRadius);
        }

        // 3. Vẽ chữ với màu sắc chỉ định (không bị Windows LookAndFeel ghi đè)
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        String text = getText();
        if (text != null && !text.isEmpty()) {
            int textW = fm.stringWidth(text);
            int textH = fm.getAscent();
            int x = (w - textW) / 2;
            int y = (h + textH) / 2 - 2;

            g2.setColor(textColor);
            g2.drawString(text, x, y);
        }

        g2.dispose();
    }
}
