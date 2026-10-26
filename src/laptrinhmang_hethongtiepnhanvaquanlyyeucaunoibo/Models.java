package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.io.Serializable;

/**
 * Các đối tượng truyền nhận dữ liệu (DTO) trong hệ thống
 */
public class Models {

    public static class User implements Serializable {
        private static final long serialVersionUID = 1L;
        public String username;
        public String password;
        public String displayName;
        public String role; // "admin", "tech", "requester"
        public String status; // "Đang hoạt động", "Đã khóa"

        public User() {}

        public User(String username, String password, String displayName, String role, String status) {
            this.username = username;
            this.password = password;
            this.displayName = displayName;
            this.role = role;
            this.status = status;
        }

        public String getRoleName() {
            if ("admin".equalsIgnoreCase(role)) return "Quản trị viên";
            if ("tech".equalsIgnoreCase(role)) return "Kỹ thuật viên";
            return "Người gửi yêu cầu";
        }

        @Override
        public String toString() {
            return username + " (" + displayName + " - " + getRoleName() + ")";
        }
    }

    public static class Ticket implements Serializable {
        private static final long serialVersionUID = 1L;
        public String id;
        public String title;
        public String category;
        public String priority; // Thấp, Bình thường, Cao, Khẩn cấp
        public String createdDate;
        public String status; // Mới, Đang xử lý, Hoàn thành, Đã hủy
        public String owner;
        public String assignedTech; // "Chưa phân công" hoặc tên tài khoản kỹ thuật viên
        public String detail;
        public int progress; // 0 - 100%
        public String attachmentName; // Tên file đính kèm

        public Ticket() {}

        public Ticket(String id, String title, String category, String priority,
                      String createdDate, String status, String owner,
                      String assignedTech, String detail, int progress, String attachmentName) {
            this.id = id;
            this.title = title;
            this.category = category;
            this.priority = priority;
            this.createdDate = createdDate;
            this.status = status;
            this.owner = owner;
            this.assignedTech = (assignedTech == null || assignedTech.isEmpty()) ? "Chưa phân công" : assignedTech;
            this.detail = detail;
            this.progress = progress;
            this.attachmentName = (attachmentName == null) ? "" : attachmentName;
        }
    }

    public static class NotificationMessage implements Serializable {
        private static final long serialVersionUID = 1L;
        public String eventType; // TICKET_CREATED, TICKET_ASSIGNED, STATUS_CHANGED, USER_UPDATED...
        public String ticketId;
        public String message;
        public String timestamp;

        public NotificationMessage() {}

        public NotificationMessage(String eventType, String ticketId, String message, String timestamp) {
            this.eventType = eventType;
            this.ticketId = ticketId;
            this.message = message;
            this.timestamp = timestamp;
        }
    }

    public static class TicketHistory implements Serializable {
        private static final long serialVersionUID = 1L;
        public String action, updatedBy, updatedAt, note;
        public TicketHistory(String action, String updatedBy, String updatedAt, String note) {
            this.action = action; this.updatedBy = updatedBy; this.updatedAt = updatedAt; this.note = note;
        }
    }

    public static class TicketStatistic implements Serializable {
        private static final long serialVersionUID = 1L;
        public String groupName;
        public int count;
        public TicketStatistic(String groupName, int count) { this.groupName = groupName; this.count = count; }
    }

    public static class TicketReview implements Serializable {
        private static final long serialVersionUID = 1L;
        public String ticketId, reviewer, comment, createdAt;
        public int rating;
        public TicketReview(String ticketId, String reviewer, int rating, String comment, String createdAt) {
            this.ticketId = ticketId; this.reviewer = reviewer; this.rating = rating; this.comment = comment; this.createdAt = createdAt;
        }
    }

    public static class TicketChatMessage implements Serializable {
        private static final long serialVersionUID = 1L;
        public String ticketId, sender, content, sentAt;
        public long id;
        public boolean read;

        public TicketChatMessage(long id, String ticketId, String sender, String content, String sentAt, boolean read) {
            this.id = id;
            this.ticketId = ticketId;
            this.sender = sender;
            this.content = content;
            this.sentAt = sentAt;
            this.read = read;
        }
    }
}
