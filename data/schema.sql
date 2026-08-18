-- ============================================================
-- 智能指纹图谱平台 · MySQL 数据库
-- 数据库名: thz_spectrum
-- 字符集: utf8mb4
-- ============================================================

CREATE DATABASE IF NOT EXISTS thz_spectrum
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE thz_spectrum;

-- ============================================================
-- 1. 基础信息表 products
-- ============================================================
DROP TABLE IF EXISTS spectrum_records;
DROP TABLE IF EXISTS products;

CREATE TABLE products (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(20)   NOT NULL UNIQUE       COMMENT '追溯码（唯一标识）',
    name        VARCHAR(100)  NOT NULL              COMMENT '产品名称',
    alias       VARCHAR(100)  DEFAULT NULL           COMMENT '产品别名/产地简称',
    type        VARCHAR(20)   NOT NULL DEFAULT '中草药' COMMENT '产品类型',
    origin      VARCHAR(200)  DEFAULT NULL           COMMENT '产地',
    latin       VARCHAR(200)  DEFAULT NULL           COMMENT '拉丁学名',
    part        VARCHAR(100)  DEFAULT NULL           COMMENT '药用部位',
    description TEXT          DEFAULT NULL           COMMENT '产品简介',
    conclusion  VARCHAR(200)  DEFAULT NULL           COMMENT '鉴定结论',
    created_at  DATETIME      DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    updated_at  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_code (code),
    INDEX idx_type (type),
    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基础信息表（产品）';


-- ============================================================
-- 2. 检测信息表 spectrum_records
-- ============================================================
CREATE TABLE spectrum_records (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id      BIGINT        NOT NULL               COMMENT '关联产品ID',
    tech            VARCHAR(200)  DEFAULT NULL           COMMENT '检测技术',
    test_date       DATE          DEFAULT NULL           COMMENT '检测日期',
    match_rate      DECIMAL(5,2)  DEFAULT NULL           COMMENT '匹配度 (%)',
    grade           VARCHAR(20)   DEFAULT NULL           COMMENT '质量等级',
    image_path_time VARCHAR(500)  DEFAULT NULL           COMMENT '时域光谱图路径',
    image_path_freq VARCHAR(500)  DEFAULT NULL           COMMENT '频域光谱图路径',
    image_desc_time VARCHAR(500)  DEFAULT NULL           COMMENT '时域图描述',
    image_desc_freq VARCHAR(500)  DEFAULT NULL           COMMENT '频域图描述',
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_product_id (product_id),
    INDEX idx_test_date  (test_date),

    CONSTRAINT fk_spectrum_product
        FOREIGN KEY (product_id) REFERENCES products(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='检测信息表';

-- ============================================================
-- 3. 用户表 members（管理员与会员统一，对应 Member 实体）
-- ============================================================
DROP TABLE IF EXISTS members;

CREATE TABLE members (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE        COMMENT '用户名',
    password_hash VARCHAR(200) NOT NULL               COMMENT 'BCrypt 密码哈希（salt rounds=10），禁止存储明文',
    role          VARCHAR(20)  NOT NULL DEFAULT 'member' COMMENT '角色: admin / member',
    email         VARCHAR(100) DEFAULT NULL           COMMENT '邮箱',
    phone         VARCHAR(30)  DEFAULT NULL           COMMENT '手机号',
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表（管理员与会员统一，首次启动自动创建默认管理员）';

-- ============================================================
-- 4. 光谱源文件表 spectrum_files
-- ============================================================
DROP TABLE IF EXISTS spectrum_files;
CREATE TABLE spectrum_files (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id    BIGINT NOT NULL,
    data_type     VARCHAR(10) NOT NULL COMMENT '时域/频域',
    file_path     VARCHAR(500) DEFAULT NULL,
    file_size     BIGINT DEFAULT 0,
    original_name VARCHAR(300) DEFAULT NULL,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_product (product_id),
    CONSTRAINT fk_sf_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='光谱源文件表';

-- ============================================================
-- 5. 新闻资讯表 news_articles
-- ============================================================
DROP TABLE IF EXISTS news_articles;
CREATE TABLE news_articles (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(300) NOT NULL,
    url         VARCHAR(1000) DEFAULT NULL,
    summary     VARCHAR(500) DEFAULT NULL,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='新闻资讯表';

-- ============================================================
-- 6. 政策链接表 policy_links
-- ============================================================
DROP TABLE IF EXISTS policy_links;
CREATE TABLE policy_links (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(300) NOT NULL,
    url         VARCHAR(1000) NOT NULL,
    summary     VARCHAR(500) DEFAULT NULL,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='政策超链接表';

-- ============================================================
-- 7. 公告表 announcements
-- ============================================================
DROP TABLE IF EXISTS announcements;
CREATE TABLE announcements (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(300) NOT NULL,
    content     TEXT DEFAULT NULL,
    file_path   VARCHAR(500) DEFAULT NULL,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台公告表';

-- ============================================================
-- 8. 追溯流向表 trace_flow
-- ============================================================
DROP TABLE IF EXISTS trace_flow;
CREATE TABLE trace_flow (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_code    VARCHAR(20) NOT NULL,
    trace_code      VARCHAR(50) NOT NULL UNIQUE,
    buyer_name      VARCHAR(100) NOT NULL,
    buyer_phone     VARCHAR(30) NOT NULL,
    buyer_address   VARCHAR(300) DEFAULT NULL,
    id_card         VARCHAR(30) DEFAULT NULL,
    sale_time       DATETIME DEFAULT CURRENT_TIMESTAMP,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_product_code (product_code),
    INDEX idx_trace_code (trace_code),
    CONSTRAINT fk_trace_product FOREIGN KEY (product_code) REFERENCES products(code) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='追溯流向表';

-- ============================================================
-- 9. 意见反馈表 feedbacks
-- ============================================================
DROP TABLE IF EXISTS feedbacks;
CREATE TABLE feedbacks (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    type        VARCHAR(20)  NOT NULL DEFAULT '其他' COMMENT '反馈类型',
    content     TEXT         NOT NULL COMMENT '反馈内容',
    image_path  VARCHAR(500) DEFAULT NULL COMMENT '截图路径',
    contact     VARCHAR(100) DEFAULT NULL COMMENT '联系方式(选填)',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='意见反馈表';

-- ============================================================
-- 10. 回收站表 recycle_bin
-- ============================================================
DROP TABLE IF EXISTS recycle_bin;
CREATE TABLE recycle_bin (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_type   VARCHAR(20)  NOT NULL DEFAULT 'product' COMMENT '条目类型',
    original_id BIGINT       DEFAULT NULL COMMENT '被删产品原 ID',
    code        VARCHAR(20)  DEFAULT NULL COMMENT '追溯码(便于展示/检索)',
    name        VARCHAR(100) DEFAULT NULL COMMENT '产品名(便于展示)',
    snapshot    LONGTEXT     NOT NULL COMMENT 'JSON 快照(产品+记录+文件+流向)',
    deleted_by  VARCHAR(50)  DEFAULT NULL COMMENT '删除操作人',
    deleted_at  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '删除时间',
    INDEX idx_recycle_type (item_type),
    INDEX idx_recycle_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='回收站表';
