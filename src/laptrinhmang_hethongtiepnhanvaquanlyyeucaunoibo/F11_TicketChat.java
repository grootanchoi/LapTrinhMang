package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.io.Serializable;
import java.util.List;

/** Trao đổi trực tuyến và lưu lịch sử thảo luận theo từng yêu cầu. */
public class F11_TicketChat {
    private static final int MAX_MESSAGE_LENGTH = 2000;

    public static class ChatRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        public String ticketId, content;

        public ChatRequest(String ticketId, String content) {
            this.ticketId = ticketId;
            this.content = content;
        }
    }

    public static Protocol.Packet getMessages(Protocol.Packet packet, Models.User user) {
        if (user == null) {
            return Protocol.Packet.error(Protocol.CMD_GET_TICKET_CHAT, "Bạn cần đăng nhập.");
        }
        if (!(packet.data instanceof ChatRequest request) || request.ticketId == null
                || request.ticketId.trim().isEmpty()) {
            return Protocol.Packet.error(Protocol.CMD_GET_TICKET_CHAT, "Mã yêu cầu không hợp lệ.");
        }

        Models.Ticket ticket = DatabaseManager.getInstance().getTicketById(request.ticketId);
        if (!canAccess(ticket, user)) {
            return Protocol.Packet.error(Protocol.CMD_GET_TICKET_CHAT, "Bạn không có quyền tham gia trao đổi của yêu cầu này.");
        }

        List<Models.TicketChatMessage> messages =
                DatabaseManager.getInstance().getTicketChatMessages(ticket.id);
        return Protocol.Packet.success(Protocol.CMD_GET_TICKET_CHAT, "Đã tải lịch sử trao đổi.", messages);
    }

    public static Protocol.Packet sendMessage(Protocol.Packet packet, Models.User user,
                                              F06_NotificationBroadcaster broadcaster) {
        if (user == null) {
            return Protocol.Packet.error(Protocol.CMD_SEND_TICKET_CHAT, "Bạn cần đăng nhập.");
        }
        if (!(packet.data instanceof ChatRequest request) || request.ticketId == null
                || request.ticketId.trim().isEmpty() || request.content == null
                || request.content.trim().isEmpty() || request.content.trim().length() > MAX_MESSAGE_LENGTH) {
            return Protocol.Packet.error(Protocol.CMD_SEND_TICKET_CHAT,
                    "Tin nhắn không được để trống và không vượt quá " + MAX_MESSAGE_LENGTH + " ký tự.");
        }

        DatabaseManager db = DatabaseManager.getInstance();
        Models.Ticket ticket = db.getTicketById(request.ticketId);
        if (!canAccess(ticket, user)) {
            return Protocol.Packet.error(Protocol.CMD_SEND_TICKET_CHAT, "Bạn không có quyền tham gia trao đổi của yêu cầu này.");
        }
        if ("Hoàn thành".equalsIgnoreCase(ticket.status) || "Đã hủy".equalsIgnoreCase(ticket.status)) {
            return Protocol.Packet.error(Protocol.CMD_SEND_TICKET_CHAT, "Yêu cầu đã đóng, không thể gửi tin nhắn mới.");
        }

        Models.TicketChatMessage saved = db.addTicketChatMessage(ticket.id, user.username, request.content.trim());
        if (saved == null) {
            return Protocol.Packet.error(Protocol.CMD_SEND_TICKET_CHAT, "Không thể lưu tin nhắn trao đổi.");
        }

        String displayMessage = saved.sentAt + " | " + saved.sender + ": " + saved.content;
        if (broadcaster != null) {
            broadcaster.sendToUsers(
                    List.of(ticket.owner, ticket.assignedTech, user.username),
                    "TICKET_CHAT_MESSAGE", ticket.id, displayMessage);
        }
        return Protocol.Packet.success(Protocol.CMD_SEND_TICKET_CHAT, "Đã gửi tin nhắn.", saved);
    }

    private static boolean canAccess(Models.Ticket ticket, Models.User user) {
        if (ticket == null || user == null) return false;
        return "admin".equalsIgnoreCase(user.role)
                || user.username.equalsIgnoreCase(ticket.owner)
                || user.username.equalsIgnoreCase(ticket.assignedTech);
    }
}
