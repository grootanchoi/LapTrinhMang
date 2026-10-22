package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * CHỨC NĂNG F08: QUẢN LÝ TÀI KHOẢN VÀ QUYỀN
 * =========================================================================
 * - Xử lý chính: Server xác thực quyền quản trị (Admin), kiểm tra tính hợp lệ
 *                của dữ liệu và thực hiện thêm, sửa hoặc khóa tài khoản trong MySQL.
 * - Đầu vào / đầu ra: Dữ liệu người dùng / Trạng thái cập nhật CSDL.
 * =========================================================================
 */
public class F08_UserManagement {

    /**
     * DTO yêu cầu danh sách người dùng (chỉ Admin)
     */
    public static class AdminUserRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        public String requestingUser;
        public String requestingRole;

        public AdminUserRequest(String requestingUser, String requestingRole) {
            this.requestingUser = requestingUser;
            this.requestingRole = requestingRole;
        }
    }

    /**
     * DTO yêu cầu khóa/mở khóa tài khoản
     */
    public static class LockUserRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        public String requestingUser;
        public String targetUsername;
        public boolean lock; // true: khóa, false: mở khóa

        public LockUserRequest(String requestingUser, String targetUsername, boolean lock) {
            this.requestingUser = requestingUser;
            this.targetUsername = targetUsername;
            this.lock = lock;
        }
    }

    // =========================================================================
    // XỬ LÝ PHÍA SERVER (Server-Side Logic)
    // =========================================================================

    /**
     * Lấy danh sách toàn bộ tài khoản (Xác thực quyền Admin)
     */
    public static Protocol.Packet handleServerGetUsers(Protocol.Packet requestPacket) {
        if (!(requestPacket.data instanceof AdminUserRequest req)) {
            return Protocol.Packet.error(Protocol.CMD_GET_USERS, "Yêu cầu không hợp lệ.");
        }

        if (!"admin".equalsIgnoreCase(req.requestingRole)) {
            return Protocol.Packet.error(Protocol.CMD_GET_USERS, "Từ chối truy cập: Bạn không có quyền Quản trị viên.");
        }

        DatabaseManager db = DatabaseManager.getInstance();
        List<Models.User> users = db.getAllUsers();
        // Xóa mật khẩu trước khi gửi về client vì lý do bảo mật
        List<Models.User> sanitized = new ArrayList<>();
        for (Models.User u : users) {
            sanitized.add(new Models.User(u.username, "******", u.displayName, u.role, u.status));
        }

        return Protocol.Packet.success(Protocol.CMD_GET_USERS, "Lấy danh sách người dùng thành công.", sanitized);
    }

    /**
     * Thêm tài khoản mới vào MySQL
     */
    public static Protocol.Packet handleServerAddUser(Protocol.Packet requestPacket, Models.User operator) {
        if (operator == null || !"admin".equalsIgnoreCase(operator.role)) {
            return Protocol.Packet.error(Protocol.CMD_ADD_USER, "Từ chối truy cập: Chỉ Quản trị viên mới có quyền thêm người dùng.");
        }

        if (!(requestPacket.data instanceof Models.User newUser)) {
            return Protocol.Packet.error(Protocol.CMD_ADD_USER, "Dữ liệu người dùng không hợp lệ.");
        }

        // Kiểm tra validation
        if (newUser.username == null || newUser.username.trim().isEmpty()) {
            return Protocol.Packet.error(Protocol.CMD_ADD_USER, "Tên đăng nhập không được để trống.");
        }
        if (newUser.password == null || newUser.password.length() < 6) {
            return Protocol.Packet.error(Protocol.CMD_ADD_USER, "Mật khẩu phải có tối thiểu 6 ký tự.");
        }
        if (newUser.displayName == null || newUser.displayName.trim().isEmpty()) {
            return Protocol.Packet.error(Protocol.CMD_ADD_USER, "Họ và tên không được để trống.");
        }

        DatabaseManager db = DatabaseManager.getInstance();
        boolean ok;
        try {
            ok = db.addUser(newUser);
        } catch (java.sql.SQLException e) {
            System.err.println("[F08] Lỗi SQL khi thêm tài khoản: " + e.getMessage());
            return Protocol.Packet.error(Protocol.CMD_ADD_USER,
                    "Không thể lưu tài khoản vào cơ sở dữ liệu: " + e.getMessage());
        }
        if (!ok) {
            return Protocol.Packet.error(Protocol.CMD_ADD_USER, "Tên đăng nhập '" + newUser.username + "' đã tồn tại trong cơ sở dữ liệu.");
        }

        return Protocol.Packet.success(Protocol.CMD_ADD_USER, "Thêm tài khoản thành công: " + newUser.username, null);
    }

    /**
     * Cập nhật thông tin và vai trò người dùng trong MySQL
     */
    public static Protocol.Packet handleServerUpdateUser(Protocol.Packet requestPacket, Models.User operator) {
        if (operator == null || !"admin".equalsIgnoreCase(operator.role)) {
            return Protocol.Packet.error(Protocol.CMD_UPDATE_USER, "Từ chối truy cập: Chỉ Quản trị viên mới có quyền sửa tài khoản.");
        }

        if (!(requestPacket.data instanceof Models.User user)) {
            return Protocol.Packet.error(Protocol.CMD_UPDATE_USER, "Dữ liệu người dùng không hợp lệ.");
        }

        DatabaseManager db = DatabaseManager.getInstance();
        boolean ok = db.updateUser(user);
        if (!ok) {
            return Protocol.Packet.error(Protocol.CMD_UPDATE_USER, "Không thể cập nhật người dùng trong CSDL.");
        }

        return Protocol.Packet.success(Protocol.CMD_UPDATE_USER, "Cập nhật tài khoản '" + user.username + "' thành công.", null);
    }

    /**
     * Khóa hoặc mở khóa tài khoản người dùng trong MySQL
     */
    public static Protocol.Packet handleServerLockUser(Protocol.Packet requestPacket, Models.User operator) {
        if (operator == null || !"admin".equalsIgnoreCase(operator.role)) {
            return Protocol.Packet.error(Protocol.CMD_LOCK_USER, "Từ chối truy cập: Chỉ Quản trị viên mới có quyền khóa tài khoản.");
        }

        if (!(requestPacket.data instanceof LockUserRequest req)) {
            return Protocol.Packet.error(Protocol.CMD_LOCK_USER, "Yêu cầu khóa không hợp lệ.");
        }

        // Không cho phép Quản trị viên tự khóa tài khoản của chính mình
        if (operator.username.equalsIgnoreCase(req.targetUsername)) {
            return Protocol.Packet.error(Protocol.CMD_LOCK_USER, "Không thể khóa tài khoản của chính bạn đang đăng nhập!");
        }

        DatabaseManager db = DatabaseManager.getInstance();
        boolean ok = db.setLockUser(req.targetUsername, req.lock);
        if (!ok) {
            return Protocol.Packet.error(Protocol.CMD_LOCK_USER, "Không tìm thấy hoặc không thể đổi trạng thái tài khoản: " + req.targetUsername);
        }

        String actionText = req.lock ? "Khóa" : "Mở khóa";
        return Protocol.Packet.success(Protocol.CMD_LOCK_USER, actionText + " tài khoản '" + req.targetUsername + "' thành công.", null);
    }
}
