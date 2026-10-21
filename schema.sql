-- ============================================================
-- HỆ THỐNG TIẾP NHẬN VÀ QUẢN LÝ YÊU CẦU NỘI BỘ (WORKDESK)
-- Script khởi tạo cơ sở dữ liệu MySQL
-- ============================================================

CREATE DATABASE IF NOT EXISTS `workdesk_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `workdesk_db`;

-- 1. Bảng người dùng (Users) - F01, F08
CREATE TABLE IF NOT EXISTS `users` (
    `username` VARCHAR(50) NOT NULL PRIMARY KEY,
    `password` VARCHAR(100) NOT NULL,
    `display_name` VARCHAR(100) NOT NULL,
    `role` ENUM('requester', 'tech', 'admin') NOT NULL DEFAULT 'requester',
    `status` ENUM('Đang hoạt động', 'Đã khóa') NOT NULL DEFAULT 'Đang hoạt động',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Bảng yêu cầu hỗ trợ (Tickets) - F02, F03, F04, F05, F06
CREATE TABLE IF NOT EXISTS `tickets` (
    `id` VARCHAR(30) NOT NULL PRIMARY KEY,
    `title` VARCHAR(255) NOT NULL,
    `category` VARCHAR(50) NOT NULL,
    `priority` VARCHAR(20) DEFAULT 'Bình thường',
    `created_date` VARCHAR(30) NOT NULL,
    `status` VARCHAR(50) NOT NULL DEFAULT 'Mới',
    `owner` VARCHAR(50) NOT NULL,
    `assigned_tech` VARCHAR(50) DEFAULT 'Chưa phân công',
    `detail` TEXT,
    `progress` INT DEFAULT 0,
    `attachment_name` VARCHAR(255) DEFAULT '',
    FOREIGN KEY (`owner`) REFERENCES `users`(`username`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Bảng lịch sử xử lý (Ticket History) - F05, F10
CREATE TABLE IF NOT EXISTS `ticket_history` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `ticket_id` VARCHAR(30) NOT NULL,
    `action` VARCHAR(100) NOT NULL,
    `updated_by` VARCHAR(50) NOT NULL,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `note` TEXT,
    FOREIGN KEY (`ticket_id`) REFERENCES `tickets`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Thêm tài khoản mẫu ban đầu:
-- Admin: admin / admin123
-- Kỹ thuật viên: tech01 / tech123
-- Nhân viên gửi yêu cầu: user01 / user123
INSERT INTO `users` (`username`, `password`, `display_name`, `role`, `status`) VALUES
('admin', 'admin123', 'Quản trị viên hệ thống', 'admin', 'Đang hoạt động'),
('tech01', 'tech123', 'Nguyễn Văn Kỹ Thuật', 'tech', 'Đang hoạt động'),
('tech02', 'tech123', 'Trần Thị Support', 'tech', 'Đang hoạt động'),
('user01', 'user123', 'Lê Văn Nhân Viên', 'requester', 'Đang hoạt động'),
('user02', 'user123', 'Phạm Thị Kế Toán', 'requester', 'Đang hoạt động')
ON DUPLICATE KEY UPDATE `username`=`username`;

-- Thêm các yêu cầu mẫu ban đầu
INSERT INTO `tickets` (`id`, `title`, `category`, `priority`, `created_date`, `status`, `owner`, `assigned_tech`, `detail`, `progress`, `attachment_name`) VALUES
('YC-2026-0001', 'Máy in phòng Kế toán không nhận lệnh in', 'Hành chính', 'Cao', '2026-10-20', 'Đang xử lý', 'user02', 'tech01', 'Máy in Canon 2900 báo lỗi offline liên tục, đã khởi động lại nhưng không được.', 40, 'error_printer.txt'),
('YC-2026-0002', 'Xin cấp quyền truy cập VPN nội bộ làm việc từ xa', 'IT', 'Bình thường', '2026-10-21', 'Mới', 'user01', 'Chưa phân công', 'Cần tài khoản VPN kết nối máy chủ dữ liệu từ chi nhánh.', 0, '')
ON DUPLICATE KEY UPDATE `id`=`id`;
