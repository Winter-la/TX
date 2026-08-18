/**
 * 前端全局配置
 * - 本地 / 单 jar 部署：API_BASE 保持空字符串（走同源相对路径）
 * - 前后端分离部署（Cloudflare Pages 前端 + Railway 后端）：
 *   把 API_BASE 改为后端地址，例如 https://你的项目.up.railway.app
 */
window.APP_CONFIG = {
    API_BASE: ''
};

/** 拼接后端接口地址 */
function apiUrl(path) {
    return (window.APP_CONFIG.API_BASE || '') + path;
}

/** 处理后端返回的文件/图片地址（相对路径自动补后端地址，完整 URL 原样返回） */
function fileUrl(path) {
    if (!path) return '';
    if (/^https?:\/\//i.test(path)) return path;
    return (window.APP_CONFIG.API_BASE || '') + path;
}
