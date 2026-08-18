-- ============================================================
-- 数据迁移: products.json → MySQL
-- 执行方式: mysql -u root -p < data/migrate.sql
-- ============================================================

USE thz_spectrum;

-- -----------------------------------------------------------
-- 插入产品数据
-- -----------------------------------------------------------
INSERT INTO products (code, name, alias, type, origin, latin, part, description, conclusion) VALUES
('001', '当归',   '岷县当归',   '中草药', '甘肃 · 岷县',        'Angelica sinensis',   '根',       '伞形科植物当归的干燥根，具有补血活血、调经止痛之功效',             '正品 · 无掺假'),
('002', '茯苓',   '湖南茯苓',   '中草药', '湖南 · 怀化',        'Wolfiporia extensa',  '菌核',     '多孔菌科真菌茯苓的干燥菌核，具有利水渗湿、健脾宁心之功效',         '正品 · 无掺假'),
('003', '人参',   '长白山人参', '中草药', '吉林 · 长白山',      'Panax ginseng',       '根',       '五加科植物人参的干燥根和根茎，具有大补元气、复脉固脱之功效',       '正品 · 野生'),
('008', '三七',   '文山三七',   '中草药', '云南 · 文山',        'Panax notoginseng',   '根及根茎', '五加科植物三七的干燥根和根茎，具有散瘀止血、消肿定痛之功效',       '正品 · 无掺假'),
('010', '汤臣倍健蛋白粉', '汤臣倍健蛋白粉', '保健品', '广东 · 广州', NULL, NULL, '保健食品 · 蛋白粉类，主要成分为乳清蛋白和大豆蛋白',               '正品 · 成分达标'),
('015', '飞鹤星飞帆奶粉', '飞鹤奶粉',       '奶制品', '黑龙江 · 齐齐哈尔', NULL, NULL, '婴幼儿配方奶粉，飞鹤乳业旗下产品',                                  '正品 · 品质优良');

-- -----------------------------------------------------------
-- 插入光谱检测记录 (product_id 通过子查询获取)
-- -----------------------------------------------------------
INSERT INTO spectrum_records (product_id, tech, test_date, match_rate, grade)
SELECT p.id, '太赫兹时域光谱 + 拉曼光谱', '2024-06-18', 98.7, '一级'
FROM products p WHERE p.code = '001';

INSERT INTO spectrum_records (product_id, tech, test_date, match_rate, grade)
SELECT p.id, '太赫兹时域光谱 + 拉曼光谱', '2024-06-20', 97.2, '一级'
FROM products p WHERE p.code = '002';

INSERT INTO spectrum_records (product_id, tech, test_date, match_rate, grade)
SELECT p.id, '太赫兹时域光谱 + 拉曼光谱', '2024-05-12', 99.1, '特级'
FROM products p WHERE p.code = '003';

INSERT INTO spectrum_records (product_id, tech, test_date, match_rate, grade)
SELECT p.id, '太赫兹时域光谱 + 拉曼光谱', '2024-07-02', 96.8, '一级'
FROM products p WHERE p.code = '008';

INSERT INTO spectrum_records (product_id, tech, test_date, match_rate, grade)
SELECT p.id, '太赫兹时域光谱',             '2024-06-25', 95.3, '合格'
FROM products p WHERE p.code = '010';

INSERT INTO spectrum_records (product_id, tech, test_date, match_rate, grade)
SELECT p.id, '太赫兹时域光谱 + 拉曼光谱', '2024-07-05', 98.5, '特级'
FROM products p WHERE p.code = '015';

-- -----------------------------------------------------------
-- 验证
-- -----------------------------------------------------------
SELECT 'products'  AS tbl, COUNT(*) AS rows FROM products
UNION ALL
SELECT 'spectrum_records', COUNT(*) FROM spectrum_records;
