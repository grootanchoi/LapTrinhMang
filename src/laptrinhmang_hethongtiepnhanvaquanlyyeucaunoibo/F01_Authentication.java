package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.io.*;
import java.net.Socket;

/**
 * CHỨC NĂNG F01: ĐĂNG NHẬP VÀ XÁC THỰC TÀI KHOẢN
 * =========================================================================
 * - Xử lý chính: Client gửi thông tin đăng nhập (username, password) đến Server qua TCP Socket.
 *                Server xác thực tài khoản trong CSDL (MySQL / Datastore), kiểm tra trạng thái,
 *                trả về kết quả đăng nhập và vai trò người dùng (admin, tech, requester).
 * - Đầu vào / đầu ra: IP, Port / Trạng thái kết nối Socket.
 * =========================================================================
 */
public class F01_Authentication {

    /**
     * Dữ liệu yêu cầu đăng nhập gửi qua Socket
     */
    public static class LoginRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        public String username;
        public String password;

        public LoginRequest(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }

    /**
     * Dữ liệu kết quả đăng nhập trả về từ Server
     */
    public static class LoginResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        public boolean success;
        public String message;
        public Models.User user;

        public LoginResponse(boolean success, String message, Models.User user) {
            this.success = success;
            this.message = message;
            this.user = user;
        }
    }

    // =========================================================================
    // XỬ LÝ PHÍA SERVER (Server-Side Logic)
    // =========================================================================
    public static Protocol.Packet handleServerLogin(Protocol.Packet requestPacket) {
        if (!(requestPacket.data instanceof LoginRequest req)) {
            return Protocol.Packet.error(Protocol.CMD_LOGIN, "Định dạng dữ liệu đăng nhập không hợp lệ.");
        }

        if (req.username == null || req.username.trim().isEmpty() || req.password == null) {
            return Protocol.Packet.error(Protocol.CMD_LOGIN, "Tài khoản và mật khẩu không được để trống.");
        }

        // Truy vấn CSDL xác thực thông tin tài khoản
        DatabaseManager db = DatabaseManager.getInstance();
        Models.User user = db.authenticate(req.username.trim(), req.password);

        if (user == null) {
            return Protocol.Packet.error(Protocol.CMD_LOGIN, "Tên đăng nhập hoặc mật khẩu không chính xác.");
        }

        if ("Đã khóa".equalsIgnoreCase(user.status)) {
            return Protocol.Packet.error(Protocol.CMD_LOGIN, "Tài khoản này hiện đang bị khóa bởi Quản trị viên.");
        }

        // Đăng nhập thành công, trả về thông tin người dùng và vai trò
        Models.User sanitizedUser = new Models.User(user.username, "", user.displayName, user.role, user.status,
                user.department);
        LoginResponse responseData = new LoginResponse(true, "Đăng nhập thành công!", sanitizedUser);
        return Protocol.Packet.success(Protocol.CMD_LOGIN, "Xác thực thành công.", responseData);
    }

    // =========================================================================
    // XỬ LÝ PHÍA CLIENT (Client-Side Logic)
    // =========================================================================
    public static class ConnectionResult {
        public boolean isConnected;
        public String statusMessage;
        public Socket socket;
        public ObjectOutputStream out;
        public ObjectInputStream in;
        public Models.User loggedInUser;
    }

    /**
     * Client kết nối đến Server qua IP và Port, gửi thông tin đăng nhập qua TCP Socket
     */
    public static ConnectionResult performLogin(String host, int port, String username, String password) {
        ConnectionResult res = new ConnectionResult();
        Socket socket = null;
        try {
            socket = NetworkSecurityConfig.createClientSocket(host, port, 5000);

            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            // Đóng gói gói tin F01
            LoginRequest req = new LoginRequest(username, password);
            Protocol.Packet packet = new Protocol.Packet(Protocol.CMD_LOGIN, "", "", req);
            packet.correlationId = java.util.UUID.randomUUID().toString();

            // Gửi qua TCP Socket
            out.writeObject(packet);
            out.flush();

            // Đợi phản hồi từ Server
            Object responseObj = in.readObject();
            if (responseObj instanceof Protocol.Packet respPacket) {
                if (Protocol.STATUS_SUCCESS.equals(respPacket.status)) {
                    LoginResponse loginResp = (LoginResponse) respPacket.data;
                    res.isConnected = true;
                    res.statusMessage = "Đăng nhập thành công. Vai trò: " + loginResp.user.getRoleName();
                    res.socket = socket;
                    res.out = out;
                    res.in = in;
                    res.loggedInUser = loginResp.user;
                } else {
                    res.isConnected = false;
                    res.statusMessage = respPacket.message;
                    socket.close();
                }
            } else {
                res.isConnected = false;
                res.statusMessage = "Giao thức không tương thích từ máy chủ.";
                socket.close();
            }
        } catch (java.net.ConnectException e) {
            closeFailedSocket(socket);
            res.isConnected = false;
            res.statusMessage = "Không thể kết nối đến máy chủ tại " + host + ":" + port + ". Hãy chắc chắn Server đang chạy.";
        } catch (Exception e) {
            closeFailedSocket(socket);
            res.isConnected = false;
            res.statusMessage = "Lỗi kết nối Socket: " + e.getMessage();
        }
        return res;
    }

    private static void closeFailedSocket(Socket socket) {
        if (socket == null || socket.isClosed()) return;
        try {
            socket.close();
        } catch (IOException e) {
            System.err.println("[F01] Không đóng được socket đăng nhập thất bại: " + e.getMessage());
        }
    }
}
