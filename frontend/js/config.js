/**
 * 前端全局配置（自动检测模式）
 * - 部署在 Cloudflare Pages（*.pages.dev）时：API_BASE 指向线上 Railway 后端
 * - 本地/自托管访问（localhost、127.0.0.1、其它主机名）：API_BASE 为空，走同源后端
 * 无需手动修改即可在本地开发与线上部署之间切换。
 */
(function () {
    var host = window.location.hostname || '';
    var apiBase = '';
    if (host.indexOf('pages.dev') !== -1) {
        apiBase = 'https://tx-production-53fa.up.railway.app';
    }
    window.APP_CONFIG = { API_BASE: apiBase };
})();

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
