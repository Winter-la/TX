-- ============================================================
-- 常用查询示例
-- ============================================================
USE thz_spectrum;

-- -----------------------------------------------------------
-- Q1: 按追溯码查询产品完整信息（含最近一次光谱数据）
-- -----------------------------------------------------------
SELECT
    p.code          AS 追溯码,
    p.name          AS 产品名称,
    p.type          AS 类型,
    p.origin        AS 产地,
    p.latin         AS 拉丁学名,
    p.part          AS 药用部位,
    p.description   AS 产品简介,
    p.conclusion    AS 鉴定结论,
    sr.tech         AS 检测技术,
    sr.test_date    AS 检测日期,
    sr.match_rate   AS 匹配度,
    sr.grade        AS 质量等级
FROM products p
LEFT JOIN spectrum_records sr ON sr.product_id = p.id
WHERE p.code = '001'
ORDER BY sr.test_date DESC
LIMIT 1;


-- -----------------------------------------------------------
-- Q2: 产品类型统计
-- -----------------------------------------------------------
SELECT
    type AS 产品类型,
    COUNT(*) AS 数量
FROM products
GROUP BY type;


-- -----------------------------------------------------------
-- Q3: 已认证产品列表（匹配度 > 95%）
-- -----------------------------------------------------------
SELECT
    p.code, p.name, p.type, p.origin, sr.match_rate, sr.grade
FROM products p
JOIN spectrum_records sr ON sr.product_id = p.id
WHERE sr.match_rate > 95
ORDER BY sr.match_rate DESC;


-- -----------------------------------------------------------
-- Q4: 按月统计检测数量
-- -----------------------------------------------------------
SELECT
    DATE_FORMAT(test_date, '%Y-%m') AS 月份,
    COUNT(*) AS 检测次数
FROM spectrum_records
GROUP BY 月份
ORDER BY 月份 DESC;


-- -----------------------------------------------------------
-- Q5: 模糊搜索产品（按名称或产地）
-- -----------------------------------------------------------
SELECT code, name, type, origin
FROM products
WHERE name LIKE '%参%' OR origin LIKE '%甘肃%';


-- -----------------------------------------------------------
-- Q6: 某产品最近一次检测记录
-- -----------------------------------------------------------
SELECT
    p.code, p.name,
    sr.tech, sr.test_date, sr.match_rate, sr.grade
FROM products p
JOIN spectrum_records sr ON sr.product_id = p.id
WHERE sr.id = (
    SELECT sr2.id FROM spectrum_records sr2
    WHERE sr2.product_id = p.id
    ORDER BY sr2.test_date DESC
    LIMIT 1
);


-- -----------------------------------------------------------
-- Q7: 导出全部产品 JSON 格式
-- -----------------------------------------------------------
SELECT
    JSON_OBJECT(
        'code',       p.code,
        'name',       p.name,
        'type',       p.type,
        'origin',     p.origin,
        'conclusion', p.conclusion
    ) AS json_out
FROM products p;
