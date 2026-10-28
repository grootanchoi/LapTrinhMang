-- ============================================================
-- HỆ THỐNG TIẾP NHẬN VÀ QUẢN LÝ YÊU CẦU NỘI BỘ (WORKDESK)
-- Script khởi tạo cơ sở dữ liệu MySQL
-- ============================================================

CREATE DATABASE IF NOT EXISTS `workdesk_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `workdesk_db`;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 1. Bảng người dùng (Users) - F01, F08
CREATE TABLE IF NOT EXISTS `users` (
    `username` VARCHAR(50) NOT NULL PRIMARY KEY,
    `password` VARCHAR(100) NOT NULL,
    `display_name` VARCHAR(100) NOT NULL,
    `role` ENUM('requester', 'tech', 'admin') NOT NULL DEFAULT 'requester',
    `status` VARCHAR(30) NOT NULL DEFAULT 'Đang hoạt động',
    `department` VARCHAR(100) NOT NULL DEFAULT '',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE `users`
    ADD COLUMN IF NOT EXISTS `department` VARCHAR(100) NOT NULL DEFAULT '';

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
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `owner_department` VARCHAR(100) NOT NULL DEFAULT '',
    `source_ip` VARCHAR(45) NOT NULL DEFAULT '',
    `sla_due_at` DATETIME NULL,
    `sla_warning_sent` BOOLEAN NOT NULL DEFAULT FALSE,
    `sla_overdue_sent` BOOLEAN NOT NULL DEFAULT FALSE,
    `deleted_at` DATETIME NULL,
    FOREIGN KEY (`owner`) REFERENCES `users`(`username`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `tickets`
    ADD COLUMN IF NOT EXISTS `created_at` DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00',
    ADD COLUMN IF NOT EXISTS `owner_department` VARCHAR(100) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS `source_ip` VARCHAR(45) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS `sla_due_at` DATETIME NULL,
    ADD COLUMN IF NOT EXISTS `sla_warning_sent` BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS `sla_overdue_sent` BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS `deleted_at` DATETIME NULL;
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

-- F10: Đánh giá sau khi hoàn thành yêu cầu
CREATE TABLE IF NOT EXISTS `ticket_reviews` (
    `ticket_id` VARCHAR(30) NOT NULL PRIMARY KEY,
    `reviewer` VARCHAR(50) NOT NULL,
    `rating` TINYINT NOT NULL,
    `comment` TEXT,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT `fk_review_ticket` FOREIGN KEY (`ticket_id`) REFERENCES `tickets`(`id`) ON DELETE CASCADE,
    CONSTRAINT `chk_review_rating` CHECK (`rating` BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- F11: Tin nhắn trao đổi trực tuyến theo từng yêu cầu
CREATE TABLE IF NOT EXISTS `ticket_messages` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `ticket_id` VARCHAR(30) NOT NULL,
    `sender` VARCHAR(50) NOT NULL,
    `content` VARCHAR(2000) NOT NULL,
    `sent_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_ticket_messages_ticket_sent` (`ticket_id`, `sent_at`, `id`),
    CONSTRAINT `fk_message_ticket` FOREIGN KEY (`ticket_id`) REFERENCES `tickets`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_message_sender` FOREIGN KEY (`sender`) REFERENCES `users`(`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ticket_message_reads` (
    `message_id` BIGINT NOT NULL,
    `username` VARCHAR(50) NOT NULL,
    `read_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`message_id`, `username`),
    CONSTRAINT `fk_message_read_message` FOREIGN KEY (`message_id`) REFERENCES `ticket_messages`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_message_read_user` FOREIGN KEY (`username`) REFERENCES `users`(`username`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ticket_chat_attachments` (
    `message_id` BIGINT NOT NULL PRIMARY KEY,
    `file_name` VARCHAR(255) NOT NULL,
    `mime_type` VARCHAR(100) NOT NULL,
    `storage_name` VARCHAR(80) NOT NULL,
    `file_size` BIGINT NOT NULL,
    `sha256` CHAR(64) NOT NULL,
    CONSTRAINT `fk_chat_attachment_message` FOREIGN KEY (`message_id`) REFERENCES `ticket_messages`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `audit_events` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `event_type` VARCHAR(80) NOT NULL,
    `entity_id` VARCHAR(80) NOT NULL,
    `actor` VARCHAR(50) NOT NULL,
    `detail` TEXT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_audit_events_created` (`created_at`, `id`),
    INDEX `idx_audit_events_entity` (`entity_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Chuẩn hóa dữ liệu cũ khi chạy lại script; tránh cột cũ còn charset khác.
ALTER TABLE `users` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `tickets` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `ticket_history` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `ticket_reviews` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `ticket_messages` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `ticket_message_reads` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `ticket_chat_attachments` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `audit_events` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Thêm tài khoản mẫu ban đầu:
-- Admin: admin / admin123
-- Kỹ thuật viên: tech01 / tech123
-- Nhân viên gửi yêu cầu: user01 / user123
INSERT INTO `users` (`username`, `password`, `display_name`, `role`, `status`) VALUES
('admin', 'pbkdf2-sha256$210000$m2KYdwsTBM6HFKa1W1yElA==$byvQpPO3pdnY6PUxKOu53c+TSwblMzDuoOqWxZ9dYhQ=', 'Quản trị viên hệ thống', 'admin', 'Đang hoạt động'),
('tech01', 'pbkdf2-sha256$210000$GlwOt/KdCJLkUneSpZs7Og==$MPOWCFJ8N/nLX0Noy9GYidJe2y9U3Mvzq/88NIUFRsA=', 'Nguyễn Văn Kỹ Thuật', 'tech', 'Đang hoạt động'),
('tech02', 'pbkdf2-sha256$210000$4+Kii0ijBcujDLJQMIcdUA==$XcEOUZ81KlTmcvydcNmxMzlENOZeB6Gwr70SctpfzOQ=', 'Trần Thị Support', 'tech', 'Đang hoạt động'),
('user01', 'pbkdf2-sha256$210000$iQNK/YFlc4sFI+ZxxlHfxQ==$U6Nu8fSigLxjJ7p3nkeLQqScf6oZ7Z12n7xyM2rkg5Q=', 'Lê Văn Nhân Viên', 'requester', 'Đang hoạt động'),
('user02', 'pbkdf2-sha256$210000$0767svk+/9KMBNwZWSBAVA==$5Miuqkc5IOV1LL1Z5v3kOG35wpnQodqtIqiLWZs5dSA=', 'Phạm Thị Kế Toán', 'requester', 'Đang hoạt động')
ON DUPLICATE KEY UPDATE `username`=`username`;

UPDATE `users` SET `department` = 'IT' WHERE `username` = 'user01' AND `department` = '';
UPDATE `users` SET `department` = 'Kế toán' WHERE `username` = 'user02' AND `department` = '';
UPDATE `users` SET `department` = 'IT'
WHERE `username` IN ('admin', 'tech01', 'tech02') AND `department` = '';

-- Thêm các yêu cầu mẫu ban đầu
INSERT INTO `tickets` (`id`, `title`, `category`, `priority`, `created_date`, `created_at`, `status`, `owner`, `assigned_tech`, `detail`, `progress`, `attachment_name`) VALUES
('YC-2026-0001', 'Máy in phòng Kế toán không nhận lệnh in', 'Hành chính', 'Cao', '2026-10-20', '2026-10-20 08:00:00', 'Đang xử lý', 'user02', 'tech01', 'Máy in Canon 2900 báo lỗi offline liên tục, đã khởi động lại nhưng không được.', 40, 'error_printer.txt'),
('YC-2026-0002', 'Xin cấp quyền truy cập VPN nội bộ làm việc từ xa', 'IT', 'Bình thường', '2026-10-21', '2026-10-21 08:00:00', 'Mới', 'user01', 'Chưa phân công', 'Cần tài khoản VPN kết nối máy chủ dữ liệu từ chi nhánh.', 0, '')
ON DUPLICATE KEY UPDATE `id`=`id`;
UPDATE `tickets` SET `created_at` = COALESCE(
    STR_TO_DATE(CONCAT(`created_date`, ' 08:00:00'), '%Y-%m-%d %H:%i:%s'),
    '1970-01-01 00:00:00')
WHERE `created_at` = '1970-01-01 00:00:00';
UPDATE `tickets` t
JOIN `users` u ON u.`username` = t.`owner`
SET t.`owner_department` = u.`department`
WHERE t.`owner_department` = '';
