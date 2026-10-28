package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.io.Serializable;
import java.io.IOException;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Trao đổi trực tuyến và lưu lịch sử thảo luận theo từng yêu cầu. */
public class F11_TicketChat {
    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "webp", "pdf", "txt", "docx", "xlsx", "zip");

    public static class ChatRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        public String ticketId, content;

        public ChatRequest(String ticketId, String content) {
            this.ticketId = ticketId;
            this.content = content;
        }
    }

    public static class ChatAttachmentRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        public String ticketId, fileName, caption;
        public long messageId;
        public byte[] fileBytes;

        public ChatAttachmentRequest(String ticketId, String fileName, String caption, byte[] fileBytes) {
            this.ticketId = ticketId;
            this.fileName = fileName;
            this.caption = caption;
            this.fileBytes = fileBytes;
        }

        public ChatAttachmentRequest(String ticketId, long messageId) {
            this.ticketId = ticketId;
            this.messageId = messageId;
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

        DatabaseManager.getInstance().markTicketChatRead(ticket.id, user.username);
        List<Models.TicketChatMessage> messages =
                DatabaseManager.getInstance().getTicketChatMessages(ticket.id, user.username);
        return Protocol.Packet.success(Protocol.CMD_GET_TICKET_CHAT, "Đã tải lịch sử trao đổi.", messages);
    }

    public static Protocol.Packet getUnreadCounts(Models.User user) {
        if (user == null) return Protocol.Packet.error(Protocol.CMD_GET_UNREAD_CHAT_COUNTS, "Bạn cần đăng nhập.");
        return Protocol.Packet.success(Protocol.CMD_GET_UNREAD_CHAT_COUNTS, "Đã tải số tin chưa đọc.",
                DatabaseManager.getInstance().getUnreadTicketChatCounts(user.username, user.role));
    }

    public static Protocol.Packet sendMessage(Protocol.Packet packet, Models.User user,
                                              F06_NotificationBroadcaster broadcaster) {
        if (user == null) {
            return Protocol.Packet.error(Protocol.CMD_SEND_TICKET_CHAT, "Bạn cần đăng nhập.");
        }
        if (packet.data instanceof ChatAttachmentRequest attachmentRequest) {
            return sendAttachment(attachmentRequest, user, broadcaster);
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
        broadcastChatMessage(broadcaster, ticket, user, displayMessage, 0, null);
        db.recordAuditEvent("CHAT_MESSAGE_SENT", ticket.id, user.username, "Gửi tin nhắn F11.");
        return Protocol.Packet.success(Protocol.CMD_SEND_TICKET_CHAT, "Đã gửi tin nhắn.", saved);
    }

    private static Protocol.Packet sendAttachment(ChatAttachmentRequest request, Models.User user,
                                                   F06_NotificationBroadcaster broadcaster) {
        if (request.ticketId == null || request.ticketId.isBlank() || request.fileName == null
                || request.fileBytes == null || request.fileBytes.length == 0
                || request.fileName.length() > 255
                || request.fileBytes.length > MAX_ATTACHMENT_BYTES
                || (request.caption != null && request.caption.length() > MAX_MESSAGE_LENGTH)) {
            return Protocol.Packet.error(Protocol.CMD_SEND_TICKET_CHAT,
                    "Tệp chat rỗng, quá 10 MB hoặc thông tin không hợp lệ.");
        }
        String leafName = safeFileName(request.fileName);
        if (leafName == null) {
            return Protocol.Packet.error(Protocol.CMD_SEND_TICKET_CHAT, "Tên tệp không hợp lệ.");
        }
        String extension = extensionOf(leafName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return Protocol.Packet.error(Protocol.CMD_SEND_TICKET_CHAT,
                    "Định dạng không hỗ trợ. Cho phép PNG/JPEG/GIF/WebP, PDF/TXT/DOCX/XLSX/ZIP.");
        }
        Models.Ticket ticket = DatabaseManager.getInstance().getTicketById(request.ticketId);
        if (!canAccess(ticket, user)) {
            return Protocol.Packet.error(Protocol.CMD_SEND_TICKET_CHAT,
                    "Bạn không có quyền tham gia trao đổi của yêu cầu này.");
        }
        if ("Hoàn thành".equalsIgnoreCase(ticket.status) || "Đã hủy".equalsIgnoreCase(ticket.status)) {
            return Protocol.Packet.error(Protocol.CMD_SEND_TICKET_CHAT, "Yêu cầu đã đóng, không thể gửi tệp.");
        }

        String cleanName = leafName;
        String storageName = "chat_" + UUID.randomUUID() + "." + extension;
        Path storageDirectory = Path.of(FileTransferHelper.SERVER_STORAGE_DIR).toAbsolutePath().normalize();
        Path target = storageDirectory.resolve(storageName).normalize();
        if (!target.startsWith(storageDirectory)) {
            return Protocol.Packet.error(Protocol.CMD_SEND_TICKET_CHAT, "Tên tệp không hợp lệ.");
        }

        Models.TicketChatMessage saved = null;
        try {
            Files.createDirectories(storageDirectory);
            Files.write(target, request.fileBytes);
            String content = request.caption == null || request.caption.isBlank()
                    ? "Đã gửi tệp đính kèm." : request.caption.trim();
            saved = DatabaseManager.getInstance().addTicketChatMessage(ticket.id, user.username, content);
            if (saved == null) throw new IOException("Không thể lưu tin nhắn đính kèm.");
            String mimeType = mimeTypeFor(extension);
            String hash = java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(request.fileBytes));
            if (!DatabaseManager.getInstance().storeChatAttachment(saved.id, cleanName, mimeType,
                    storageName, request.fileBytes, hash)) {
                throw new IOException("Không thể lưu thông tin tệp đính kèm.");
            }
        } catch (Exception e) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException cleanupError) {
                System.err.println("[F11] Không xóa được tệp tải lên lỗi: " + cleanupError.getMessage());
            }
            if (saved != null) DatabaseManager.getInstance().deleteTicketChatMessage(saved.id);
            return Protocol.Packet.error(Protocol.CMD_SEND_TICKET_CHAT,
                    "Không thể lưu tệp đính kèm: " + e.getMessage());
        }

        String displayMessage = saved.sentAt + " | " + saved.sender + ": " + saved.content
                + " [Tệp: " + cleanName + "]";
        broadcastChatMessage(broadcaster, ticket, user, displayMessage, saved.id, cleanName);
        DatabaseManager.getInstance().recordAuditEvent("CHAT_ATTACHMENT_SENT", ticket.id, user.username,
                "Gửi tệp chat " + cleanName + " (" + request.fileBytes.length + " byte).");
        return Protocol.Packet.success(Protocol.CMD_SEND_TICKET_CHAT, "Đã gửi tệp đính kèm.", saved);
    }

    public static Protocol.Packet getAttachment(Protocol.Packet packet, Models.User user) {
        if (user == null || !(packet.data instanceof ChatAttachmentRequest request)
                || request.ticketId == null || request.messageId <= 0) {
            return Protocol.Packet.error(Protocol.CMD_GET_CHAT_ATTACHMENT, "Yêu cầu tải tệp chat không hợp lệ.");
        }
        DatabaseManager db = DatabaseManager.getInstance();
        Models.Ticket ticket = db.getTicketById(request.ticketId);
        if (!canAccess(ticket, user)
                || !request.ticketId.equals(db.getTicketIdForChatMessage(request.messageId))) {
            return Protocol.Packet.error(Protocol.CMD_GET_CHAT_ATTACHMENT,
                    "Bạn không có quyền tải tệp của cuộc trò chuyện này.");
        }
        Models.ChatAttachment attachment = db.getChatAttachment(request.messageId);
        if (attachment == null) {
            return Protocol.Packet.error(Protocol.CMD_GET_CHAT_ATTACHMENT, "Không tìm thấy tệp đính kèm.");
        }
        return Protocol.Packet.success(Protocol.CMD_GET_CHAT_ATTACHMENT, "Đã tải tệp đính kèm.",
                attachment);
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String safeFileName(String fileName) {
        String normalized = fileName.replace('\\', '/');
        String leaf = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (leaf.isBlank() || leaf.equals(".") || leaf.equals("..")
                || leaf.chars().anyMatch(Character::isISOControl)) return null;
        return leaf;
    }

    private static String mimeTypeFor(String extension) {
        return switch (extension) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "pdf" -> "application/pdf";
            case "txt" -> "text/plain";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "zip" -> "application/zip";
            default -> "application/octet-stream";
        };
    }

    private static boolean canAccess(Models.Ticket ticket, Models.User user) {
        if (ticket == null || user == null) return false;
        return "admin".equalsIgnoreCase(user.role)
                || ("requester".equalsIgnoreCase(user.role)
                && user.username.equalsIgnoreCase(ticket.owner))
                || ("tech".equalsIgnoreCase(user.role)
                && user.username.equalsIgnoreCase(ticket.assignedTech));
    }

    private static void broadcastChatMessage(F06_NotificationBroadcaster broadcaster, Models.Ticket ticket,
                                             Models.User sender, String message, long attachmentMessageId,
                                             String attachmentName) {
        if (broadcaster == null) return;
        List<String> recipients = new ArrayList<>();
        if (ticket.owner != null) recipients.add(ticket.owner);
        if (ticket.assignedTech != null) recipients.add(ticket.assignedTech);
        if ("admin".equalsIgnoreCase(sender.role) && sender.username != null) {
            recipients.removeIf(username -> sender.username.equalsIgnoreCase(username));
        } else if (sender.username != null) {
            recipients.add(sender.username);
        }
        broadcaster.sendToUsers(recipients, "TICKET_CHAT_MESSAGE", ticket.id, message,
                attachmentMessageId, attachmentName);
        broadcaster.sendToRole("admin", "TICKET_CHAT_MESSAGE", ticket.id, message,
                attachmentMessageId, attachmentName);
    }
}
