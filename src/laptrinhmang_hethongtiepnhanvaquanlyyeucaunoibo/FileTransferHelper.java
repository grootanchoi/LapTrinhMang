package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Tiện ích truyền tải tệp tin qua Socket TCP với cơ chế chia nhỏ buffer (Chunking)
 * và thông báo tiến độ chi tiết (Số byte truyền / Tổng số byte / Phần trăm hoàn thành).
 * Áp dụng cho chức năng F04 (Đính kèm/Tải file) và F05 (Theo dõi tiến độ byte/%).
 */
public class FileTransferHelper {

    public interface ProgressListener {
        void onProgress(long transferredBytes, long totalBytes, int percent);
    }

    public static final String SERVER_STORAGE_DIR = "server_storage";
    public static final long MAX_ATTACHMENT_SIZE = 10L * 1024 * 1024;

    static {
        File dir = new File(SERVER_STORAGE_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * Gửi stream dữ liệu file qua OutputStream và báo cáo tiến độ
     */
    public static void sendFile(File file, OutputStream out, ProgressListener listener) throws IOException {
        long totalBytes = file.length();
        long transferredBytes = 0;
        byte[] buffer = new byte[8192];

        try (FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis)) {

            int bytesRead;
            while ((bytesRead = bis.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                transferredBytes += bytesRead;
                if (listener != null) {
                    int percent = (int) ((transferredBytes * 100) / (totalBytes > 0 ? totalBytes : 1));
                    listener.onProgress(transferredBytes, totalBytes, percent);
                }
            }
            out.flush();
        }
    }

    /**
     * Nhận stream dữ liệu file từ InputStream và ghi ra tệp tin
     */
    public static void receiveFile(InputStream in, File destinationFile, long expectedSize, ProgressListener listener) throws IOException {
        long transferredBytes = 0;
        byte[] buffer = new byte[8192];

        File parent = destinationFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (FileOutputStream fos = new FileOutputStream(destinationFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {

            while (transferredBytes < expectedSize) {
                int toRead = (int) Math.min(buffer.length, expectedSize - transferredBytes);
                int bytesRead = in.read(buffer, 0, toRead);
                if (bytesRead == -1) break;

                bos.write(buffer, 0, bytesRead);
                transferredBytes += bytesRead;

                if (listener != null) {
                    int percent = (int) ((transferredBytes * 100) / (expectedSize > 0 ? expectedSize : 1));
                    listener.onProgress(transferredBytes, expectedSize, percent);
                }
            }
            bos.flush();
        }
    }

    public static String storeTicketAttachment(String ticketId, String originalName, byte[] bytes)
            throws IOException {
        if (ticketId == null || !ticketId.matches("[A-Za-z0-9._-]+")) {
            throw new IOException("Mã yêu cầu không hợp lệ.");
        }
        if (originalName == null || originalName.isBlank()
                || originalName.contains("/") || originalName.contains("\\")
                || originalName.equals(".") || originalName.equals("..")) {
            throw new IOException("Tên tệp không hợp lệ.");
        }
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_ATTACHMENT_SIZE) {
            throw new IOException("Kích thước tệp không hợp lệ.");
        }

        String safeName = originalName.replaceAll("[^\\p{L}\\p{N}._-]", "_");
        if (safeName.length() > 120) safeName = safeName.substring(safeName.length() - 120);
        String storedName = ticketId + "_" + safeName;
        Path storage = Path.of(SERVER_STORAGE_DIR).toAbsolutePath().normalize();
        Files.createDirectories(storage);
        Path destination = storage.resolve(storedName).normalize();
        if (!destination.startsWith(storage)) throw new IOException("Đường dẫn tệp không hợp lệ.");
        Files.write(destination, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return storedName;
    }

    public static void deleteStoredFile(String fileName) throws IOException {
        if (fileName == null || fileName.isBlank()
                || fileName.contains("/") || fileName.contains("\\")) {
            throw new IOException("Tên tệp không hợp lệ.");
        }
        Path storage = Path.of(SERVER_STORAGE_DIR).toAbsolutePath().normalize();
        Path target = storage.resolve(fileName).normalize();
        if (!target.startsWith(storage)) throw new IOException("Đường dẫn tệp không hợp lệ.");
        Files.deleteIfExists(target);
    }

    public static String getOriginalTicketAttachmentName(String ticketId, String storedName) {
        String prefix = ticketId + "_";
        return storedName != null && storedName.startsWith(prefix)
                ? storedName.substring(prefix.length()) : storedName;
    }
}
