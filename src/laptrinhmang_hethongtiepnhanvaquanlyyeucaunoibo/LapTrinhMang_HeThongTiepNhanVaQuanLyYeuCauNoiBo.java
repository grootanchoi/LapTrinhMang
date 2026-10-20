package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

/** Client interface and user administration (F01, F02, F03, F08). */
public class LapTrinhMang_HeThongTiepNhanVaQuanLyYeuCauNoiBo {
    private static final Color NAVY = new Color(22, 43, 74), BLUE = new Color(42, 104, 190), PALE = new Color(242, 246, 251);
    private static final List<User> USERS = new ArrayList<>();
    private static final List<Ticket> TICKETS = new ArrayList<>();
    private static User current;
    private static JFrame frame;
    static { USERS.add(new User("admin", "admin123", "Quản trị viên", "admin", "Đang hoạt động")); }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) { }
            showLogin();
        });
    }

    private static void base(String title, int width, int height) {
        if (frame != null) frame.dispose();
        frame = new JFrame(title); frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(width, height); frame.setMinimumSize(new Dimension(760, 520));
        frame.setLocationRelativeTo(null);
    }
    private static JLabel label(String text) { JLabel l = new JLabel(text); l.setForeground(new Color(55, 69, 87)); return l; }
    private static JTextField field(String hint) { JTextField f = new JTextField(); f.setFont(new Font("Segoe UI", Font.PLAIN, 14)); f.setPreferredSize(new Dimension(280, 38)); f.setToolTipText(hint); return f; }
    private static JButton button(String text) {
        JButton b = new JButton(text); b.setFont(new Font("Segoe UI", Font.BOLD, 13)); b.setFocusPainted(false);
        b.setBackground(BLUE); b.setForeground(Color.WHITE); b.setBorder(new EmptyBorder(10, 18, 10, 18)); return b;
    }
    private static JPanel card() { JPanel p = new JPanel(); p.setBackground(Color.WHITE); p.setBorder(new EmptyBorder(28, 32, 28, 32)); return p; }

    // F01 - authenticate user and load the role assigned by the system.
    private static void showLogin() {
        base("Hệ thống tiếp nhận và quản lý yêu cầu nội bộ", 900, 600);
        JPanel root = new JPanel(new GridBagLayout()); root.setBackground(PALE);
        JPanel box = card(); box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS)); box.setPreferredSize(new Dimension(410, 450));
        JLabel brand = new JLabel("WORKDESK"); brand.setFont(new Font("Segoe UI", Font.BOLD, 14)); brand.setForeground(BLUE);
        JLabel title = new JLabel("Đăng nhập hệ thống"); title.setFont(new Font("Segoe UI", Font.BOLD, 25)); title.setForeground(NAVY);
        JTextField username = field("Tên đăng nhập"); JPasswordField password = new JPasswordField(); password.setPreferredSize(new Dimension(280,38));
        box.add(brand); box.add(Box.createVerticalStrut(10)); box.add(title); box.add(Box.createVerticalStrut(8));
        box.add(label("Quản lý yêu cầu nội bộ của bạn tại một nơi.")); box.add(Box.createVerticalStrut(28));
        box.add(label("Tên đăng nhập")); box.add(Box.createVerticalStrut(6)); box.add(username); box.add(Box.createVerticalStrut(15));
        box.add(label("Mật khẩu")); box.add(Box.createVerticalStrut(6)); box.add(password); box.add(Box.createVerticalStrut(22));
        JButton login = button("Đăng nhập"); login.setAlignmentX(Component.LEFT_ALIGNMENT); box.add(login);
        login.addActionListener(e -> {
            User match = USERS.stream().filter(u -> u.username.equals(username.getText().trim()) && u.password.equals(new String(password.getPassword())) && u.status.equals("Đang hoạt động")).findFirst().orElse(null);
            if (match == null) { JOptionPane.showMessageDialog(frame, "Thông tin đăng nhập không đúng hoặc tài khoản đã khóa.", "Không thể đăng nhập", JOptionPane.WARNING_MESSAGE); return; }
            current = match; showDashboard();
        });
        password.addActionListener(e -> login.doClick());
        root.add(box); frame.setContentPane(root); frame.setVisible(true);
    }

    // F03 - client workspace for listing and tracking support requests.
    private static void showDashboard() {
        base("WorkDesk | Không gian làm việc", 1100, 700); frame.setLayout(new BorderLayout());
        JPanel top = new JPanel(new BorderLayout()); top.setBackground(NAVY); top.setBorder(new EmptyBorder(15, 24, 15, 24));
        JLabel logo = new JLabel("WORKDESK   /   YÊU CẦU NỘI BỘ"); logo.setForeground(Color.WHITE); logo.setFont(new Font("Segoe UI", Font.BOLD, 16));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0)); actions.setOpaque(false);
        JLabel who = new JLabel(current.displayName + "  ·  " + roleLabel(current.role)); who.setForeground(Color.WHITE);
        JButton users = new JButton("Quản lý người dùng"), logout = new JButton("Đăng xuất"); actions.add(who);
        if (current.role.equals("admin")) { actions.add(users); users.addActionListener(e -> showUsers()); }
        actions.add(logout); logout.addActionListener(e -> { current = null; showLogin(); }); top.add(logo, BorderLayout.WEST); top.add(actions, BorderLayout.EAST); frame.add(top, BorderLayout.NORTH);
        JPanel content = new JPanel(new BorderLayout(0, 20)); content.setBackground(PALE); content.setBorder(new EmptyBorder(26, 32, 28, 32));
        JPanel heading = new JPanel(new BorderLayout()); heading.setOpaque(false); JPanel text = new JPanel(); text.setOpaque(false); text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel h = new JLabel("Xin chào, " + current.displayName); h.setFont(new Font("Segoe UI", Font.BOLD, 25)); h.setForeground(NAVY); text.add(h); text.add(Box.createVerticalStrut(5)); text.add(label("Gửi yêu cầu đến bộ phận phụ trách và theo dõi tiến độ xử lý.")); heading.add(text, BorderLayout.WEST);
        JButton newTicket = button("+  Tạo yêu cầu mới"); heading.add(newTicket, BorderLayout.EAST); content.add(heading, BorderLayout.NORTH);
        JPanel tableCard = card(); tableCard.setLayout(new BorderLayout(0, 12)); JLabel tableTitle = new JLabel("Yêu cầu của tôi"); tableTitle.setFont(new Font("Segoe UI", Font.BOLD, 17)); tableTitle.setForeground(NAVY); tableCard.add(tableTitle, BorderLayout.NORTH);
        DefaultTableModel model = new DefaultTableModel(new Object[]{"Mã yêu cầu", "Tiêu đề", "Danh mục", "Ngày tạo", "Trạng thái"}, 0) { public boolean isCellEditable(int r,int c){return false;} };
        JTable table = new JTable(model); table.setRowHeight(34); table.setFont(new Font("Segoe UI", Font.PLAIN, 13)); table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12)); table.setFillsViewportHeight(true);
        JTextField query = field("Tra cứu theo mã, tiêu đề hoặc danh mục"); tableCard.add(query, BorderLayout.SOUTH);
        Runnable reloadTickets = () -> { model.setRowCount(0); String q=query.getText().trim().toLowerCase(); for (Ticket t:TICKETS) if ((current.role.equals("admin") || current.role.equals("tech") || t.owner.equals(current.username)) && (t.id.toLowerCase().contains(q) || t.title.toLowerCase().contains(q) || t.category.toLowerCase().contains(q))) model.addRow(new Object[]{t.id,t.title,t.category,t.date,t.status}); };
        reloadTickets.run(); query.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){public void insertUpdate(javax.swing.event.DocumentEvent e){reloadTickets.run();}public void removeUpdate(javax.swing.event.DocumentEvent e){reloadTickets.run();}public void changedUpdate(javax.swing.event.DocumentEvent e){reloadTickets.run();}});
        table.addMouseListener(new java.awt.event.MouseAdapter(){@Override public void mouseClicked(java.awt.event.MouseEvent e){if(e.getClickCount()==2&&table.getSelectedRow()>=0){String id=(String)table.getValueAt(table.getSelectedRow(),0);Ticket t=TICKETS.stream().filter(x->x.id.equals(id)).findFirst().orElse(null);if(t!=null)JOptionPane.showMessageDialog(frame,"Mã yêu cầu: "+t.id+"\nTiêu đề: "+t.title+"\nDanh mục: "+t.category+"\nNgày tạo: "+t.date+"\nTrạng thái: "+t.status+"\nMô tả: "+t.detail,"Chi tiết yêu cầu",JOptionPane.INFORMATION_MESSAGE);}}});
        tableCard.add(new JScrollPane(table), BorderLayout.CENTER); content.add(tableCard, BorderLayout.CENTER); frame.add(content, BorderLayout.CENTER);
        newTicket.addActionListener(e -> createTicket()); frame.setVisible(true);
    }
    // F02 - submit a new support request for server validation and persistence.
    private static void createTicket() {
        JTextField title = new JTextField(); JComboBox<String> category = new JComboBox<>(new String[]{"IT", "Nhân sự", "Hành chính", "Tài chính", "Khác"}); JTextArea detail = new JTextArea(5, 25); detail.setLineWrap(true); detail.setWrapStyleWord(true);
        JPanel form = new JPanel(new GridLayout(0,1,5,5)); form.add(new JLabel("Tiêu đề yêu cầu")); form.add(title); form.add(new JLabel("Danh mục")); form.add(category); form.add(new JLabel("Mô tả")); form.add(new JScrollPane(detail));
        int result = JOptionPane.showConfirmDialog(frame, form, "Tạo yêu cầu mới", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;
        if (title.getText().trim().isEmpty() || detail.getText().trim().isEmpty()) { JOptionPane.showMessageDialog(frame, "Vui lòng nhập tiêu đề và mô tả."); return; }
        TICKETS.add(new Ticket("YC-" + String.format("%04d", TICKETS.size()+1), title.getText().trim(), (String)category.getSelectedItem(), java.time.LocalDate.now().toString(), "Mới", current.username, detail.getText().trim())); showDashboard();
    }

    // F08 - administrator account and role management; server must authorize each operation.
    private static void showUsers() {
        if (!"admin".equals(current.role)) { JOptionPane.showMessageDialog(frame, "Chức năng chỉ dành cho quản trị viên."); return; }
        base("WorkDesk | Quản lý người dùng", 1050, 660); frame.setLayout(new BorderLayout());
        JPanel top = new JPanel(new BorderLayout()); top.setBackground(NAVY); top.setBorder(new EmptyBorder(15,22,15,22)); JLabel title = new JLabel("QUẢN LÝ NGƯỜI DÙNG   /   F8"); title.setForeground(Color.WHITE); title.setFont(new Font("Segoe UI",Font.BOLD,16)); top.add(title,BorderLayout.WEST);
        JButton back = new JButton("←  Về trang chủ"); top.add(back,BorderLayout.EAST); back.addActionListener(e -> showDashboard()); frame.add(top,BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout(0,16)); body.setBackground(PALE); body.setBorder(new EmptyBorder(24,28,28,28));
        JPanel toolbar = new JPanel(new BorderLayout(12,0)); toolbar.setOpaque(false); JTextField search = field("Tìm theo tên, tài khoản hoặc vai trò"); JButton add = button("+  Thêm người dùng"); toolbar.add(search,BorderLayout.CENTER); toolbar.add(add,BorderLayout.EAST); body.add(toolbar,BorderLayout.NORTH);
        DefaultTableModel model = new DefaultTableModel(new Object[]{"Tên đăng nhập", "Họ tên", "Vai trò", "Trạng thái"},0) { public boolean isCellEditable(int r,int c){return false;} };
        JTable table = new JTable(model); table.setRowHeight(36); table.setFont(new Font("Segoe UI",Font.PLAIN,13)); table.getTableHeader().setFont(new Font("Segoe UI",Font.BOLD,12));
        Runnable reload = () -> { model.setRowCount(0); String q=search.getText().trim().toLowerCase(); for(User u:USERS) if(u.username.toLowerCase().contains(q)||u.displayName.toLowerCase().contains(q)||u.role.toLowerCase().contains(q)) model.addRow(new Object[]{u.username,u.displayName,u.role,u.status}); };
        reload.run(); search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){public void insertUpdate(javax.swing.event.DocumentEvent e){reload.run();}public void removeUpdate(javax.swing.event.DocumentEvent e){reload.run();}public void changedUpdate(javax.swing.event.DocumentEvent e){reload.run();}});
        body.add(new JScrollPane(table),BorderLayout.CENTER); JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT)); controls.setOpaque(false); JButton edit=button("Sửa vai trò / thông tin"), delete=new JButton("Khóa tài khoản"); controls.add(edit); controls.add(delete); body.add(controls,BorderLayout.SOUTH); frame.add(body,BorderLayout.CENTER);
        add.addActionListener(e -> {
            JTextField u=field("Tên đăng nhập"), n=field("Họ tên"); JPasswordField p=new JPasswordField(); JComboBox<String> role=new JComboBox<>(new String[]{"requester","tech","admin"});
            JPanel f=new JPanel(new GridLayout(0,1,4,4)); f.add(new JLabel("Tên đăng nhập"));f.add(u);f.add(new JLabel("Họ tên"));f.add(n);f.add(new JLabel("Mật khẩu ban đầu"));f.add(p);f.add(new JLabel("Vai trò"));f.add(role);
            if(JOptionPane.showConfirmDialog(frame,f,"Thêm người dùng",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION){if(u.getText().trim().isEmpty()||n.getText().trim().isEmpty()||p.getPassword().length<6){JOptionPane.showMessageDialog(frame,"Nhập đủ thông tin, mật khẩu tối thiểu 6 ký tự.");return;}if(USERS.stream().anyMatch(x->x.username.equalsIgnoreCase(u.getText().trim()))){JOptionPane.showMessageDialog(frame,"Tên đăng nhập đã tồn tại.");return;}USERS.add(new User(u.getText().trim(),new String(p.getPassword()),n.getText().trim(),(String)role.getSelectedItem(),"Đang hoạt động"));reload.run();}
        });
        edit.addActionListener(e->{int row=table.getSelectedRow();if(row<0){JOptionPane.showMessageDialog(frame,"Chọn một người dùng trước.");return;}User u=findUser((String)model.getValueAt(row,0));if(u==null)return;JComboBox<String> role=new JComboBox<>(new String[]{"requester","tech","admin"});role.setSelectedItem(u.role);JComboBox<String> status=new JComboBox<>(new String[]{"Đang hoạt động","Đã khóa"});status.setSelectedItem(u.status);JPanel f=new JPanel(new GridLayout(0,1,5,5));f.add(new JLabel("Vai trò"));f.add(role);f.add(new JLabel("Trạng thái"));f.add(status);if(JOptionPane.showConfirmDialog(frame,f,"Cập nhật người dùng",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION){u.role=(String)role.getSelectedItem();u.status=(String)status.getSelectedItem();reload.run();}});
        delete.addActionListener(e->{int row=table.getSelectedRow();if(row<0){JOptionPane.showMessageDialog(frame,"Chọn một người dùng trước.");return;}String id=(String)model.getValueAt(row,0);if(id.equals(current.username)){JOptionPane.showMessageDialog(frame,"Không thể khóa tài khoản đang đăng nhập.");return;}if(JOptionPane.showConfirmDialog(frame,"Khóa tài khoản '"+id+"'?","Xác nhận",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION){User u=findUser(id);if(u!=null)u.status="Đã khóa";reload.run();}});
        frame.setVisible(true);
    }
    private static String roleLabel(String role){return switch(role){case "admin"->"Quản trị viên";case "tech"->"Nhân viên kỹ thuật";default->"Người gửi yêu cầu";};}
    private static User findUser(String username){return USERS.stream().filter(u->u.username.equals(username)).findFirst().orElse(null);}
    private static class User { final String username,password; final String displayName; String role,status; User(String u,String p,String n,String r,String s){username=u;password=p;displayName=n;role=r;status=s;} }
    private static class Ticket { final String id,title,category,date,status,owner,detail; Ticket(String i,String t,String c,String d,String s,String o,String x){id=i;title=t;category=c;date=d;status=s;owner=o;detail=x;} }
}
