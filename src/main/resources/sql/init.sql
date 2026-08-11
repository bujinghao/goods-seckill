-- 创建秒杀数据库
CREATE DATABASE IF NOT EXISTS `seckill` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE `seckill`;

-- 用户表
CREATE TABLE IF NOT EXISTS `seckill_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(100) NOT NULL COMMENT '密码（加密存储）',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='秒杀用户表';

-- 商品表
CREATE TABLE IF NOT EXISTS `seckill_goods` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '商品ID',
    `goods_name` VARCHAR(200) NOT NULL COMMENT '商品名称',
    `goods_title` VARCHAR(500) DEFAULT NULL COMMENT '商品标题',
    `goods_img` VARCHAR(500) DEFAULT NULL COMMENT '商品图片',
    `goods_price` DECIMAL(10,2) NOT NULL COMMENT '原价',
    `seckill_price` DECIMAL(10,2) NOT NULL COMMENT '秒杀价',
    `stock_count` INT NOT NULL COMMENT '库存数量',
    `stock_count_per_user` INT NOT NULL DEFAULT 1 COMMENT '每人限购数量',
    `start_time` DATETIME NOT NULL COMMENT '秒杀开始时间',
    `end_time` DATETIME NOT NULL COMMENT '秒杀结束时间',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-未开始，1-进行中，2-已结束',
    `goods_desc` TEXT COMMENT '商品描述',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_start_time` (`start_time`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='秒杀商品表';

-- 订单表
CREATE TABLE IF NOT EXISTS `seckill_order` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    `order_no` VARCHAR(64) NOT NULL COMMENT '订单编号',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `goods_id` BIGINT NOT NULL COMMENT '商品ID',
    `goods_name` VARCHAR(200) NOT NULL COMMENT '商品名称（冗余）',
    `seckill_price` DECIMAL(10,2) NOT NULL COMMENT '秒杀价格',
    `count` INT NOT NULL DEFAULT 1 COMMENT '购买数量',
    `total_price` DECIMAL(10,2) NOT NULL COMMENT '订单总价',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '订单状态：0-待支付，1-已支付，2-已取消，3-已超时',
    `pay_time` DATETIME DEFAULT NULL COMMENT '支付时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_goods_id` (`goods_id`),
    KEY `idx_create_time` (`create_time`),
    -- -- 防止同一用户重复下单同一商品（业务层也需要校验）； 可能业务需求可以购买多件
    -- UNIQUE KEY `uk_user_goods` (`user_id`, `goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='秒杀订单表';

-- 操作日志表
CREATE TABLE IF NOT EXISTS `operation_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `user_id` BIGINT COMMENT '操作用户ID',
    `username` VARCHAR(50) COMMENT '用户名',
    `operation` VARCHAR(100) NOT NULL COMMENT '操作类型（LOGIN/SECKILL_ORDER/UPDATE_GOODS等）',
    `method` VARCHAR(200) COMMENT '方法名',
    `params` TEXT COMMENT '请求参数（JSON格式）',
    `result` TEXT COMMENT '操作结果（JSON格式）',
    `ip` VARCHAR(50) COMMENT '操作IP',
    `location` VARCHAR(100) COMMENT 'IP归属地（可选）',
    `time_taken` INT COMMENT '执行时长（毫秒）',
    `status` TINYINT COMMENT '操作状态（1成功 0失败）',
    `error_msg` TEXT COMMENT '错误信息',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_operation` (`operation`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志表';

-- 插入测试数据
-- 测试用户（密码为123456的BCrypt加密结果）
INSERT INTO `seckill_user` (`username`, `password`, `phone`, `email`) VALUES
('testuser', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '13800138000', 'test@example.com');

-- 测试商品
INSERT INTO `seckill_goods` (`goods_name`, `goods_title`, `goods_img`, `goods_price`, `seckill_price`, `stock_count`, `stock_count_per_user`, `start_time`, `end_time`, `goods_desc`) VALUES
('iPhone 15 Pro', '苹果iPhone 15 Pro 256GB', 'https://example.com/iphone15.jpg', 8999.00, 6999.00, 100, 1, '2024-01-01 10:00:00', '2024-12-31 23:59:59', '苹果最新旗舰手机'),
('小米14', '小米14 12GB+256GB', 'https://example.com/mi14.jpg', 4999.00, 3999.00, 200, 2, '2024-01-01 10:00:00', '2024-12-31 23:59:59', '小米旗舰手机'),
('华为Mate60', '华为Mate60 Pro 512GB', 'https://example.com/mate60.jpg', 7999.00, 5999.00, 50, 1, '2024-01-01 10:00:00', '2024-12-31 23:59:59', '华为最新旗舰手机');
