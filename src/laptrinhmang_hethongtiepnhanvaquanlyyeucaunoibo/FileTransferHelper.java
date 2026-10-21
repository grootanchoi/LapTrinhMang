package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.io.*;

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
}
