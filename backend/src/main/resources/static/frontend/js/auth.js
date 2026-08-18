/**
 * 全站统一登录/登出逻辑（依赖各页面已有的模态框元素，元素不存在时自动跳过）
 * Token 统一存 localStorage：adminToken / userRole / userName
 */

function getToken() { return localStorage.getItem('adminToken') || ''; }
function getUserRole() { return localStorage.getItem('userRole') || ''; }
function getUserName() { return localStorage.getItem('userName') || ''; }

function saveSession(d) {
    localStorage.setItem('adminToken', d.token);
    localStorage.setItem('userRole', d.role);
    localStorage.setItem('userName', d.username);
}

function clearSession() {
    localStorage.removeItem('adminToken');
    localStorage.removeItem('userRole');
    localStorage.removeItem('userName');
}

/* ================= 管理员登录（数据上传入口） ================= */
function openAdminLogin() {
    if (getToken() && getUserRole() === 'admin') {
        window.location.href = apiUrl('/admin/index.html');
        return;
    }
    showModal('adminLoginModal');
}

function closeAdminLogin() {
    hideModal('adminLoginModal');
}

function adminLogin() {
    doLogin('adminLoginUser', 'adminLoginPwd', 'adminLoginError', function (d) {
        if (d.role === 'admin') {
            window.location.href = apiUrl('/admin/index.html');
        } else {
            setError('adminLoginError', '该账号无管理员权限');
        }
    });
}

/* ================= 会员登录 ================= */
function openMemberLogin() {
    showModal('memberLoginModal');
}

function closeMemberLogin() {
    hideModal('memberLoginModal');
}

function memberLogin(onSuccess) {
    doLogin('memberLoginUser', 'memberLoginPwd', 'memberLoginError', function (d) {
        hideModal('memberLoginModal');
        updateLoginState();
        if (typeof onSuccess === 'function') onSuccess();
    });
}

/* ================= 通用登录请求 ================= */
function doLogin(userId, pwdId, errId, onSuccess) {
    var u = document.getElementById(userId);
    var p = document.getElementById(pwdId);
    var e = document.getElementById(errId);
    if (!u || !p) return;
    var username = u.value.trim();
    var password = p.value;
    if (!username || !password) { setError(errId, '请输入用户名和密码'); return; }
    fetch(apiUrl('/admin/login'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'username=' + encodeURIComponent(username) + '&password=' + encodeURIComponent(password)
    }).then(function (r) { return r.json(); }).then(function (d) {
        if (d.success) {
            saveSession(d);
            setError(errId, '');
            onSuccess(d);
        } else {
            setError(errId, d.error || '登录失败');
        }
    }).catch(function () {
        setError(errId, '无法连接服务器，请确认服务已启动');
    });
}

/* ================= 页头登录状态（仅首页有对应元素时生效） ================= */
function updateLoginState() {
    var b = document.getElementById('btnLoginText');
    var u = document.getElementById('userInfo');
    if (!b || !u) return;
    if (getToken() && getUserRole() && getUserRole() !== 'guest') {
        b.style.display = 'none';
        u.style.display = '';
        document.getElementById('userNameDisp').textContent = getUserName() + ' (' + getUserRole() + ')';
    } else {
        b.style.display = '';
        u.style.display = 'none';
    }
}

function doLogout() {
    clearSession();
    updateLoginState();
    closeMemberLogin();
    closeAdminLogin();
}

/* ================= 数据下载访问控制（登录后可用） ================= */
function checkDownloadAccess() {
    if (!getToken() || !getUserRole() || getUserRole() === 'guest') {
        openMemberLogin();
        return;
    }
    window.location.href = 'download.html';
}

/* ================= 内部辅助 ================= */
function showModal(id) {
    var m = document.getElementById(id);
    if (!m) return;
    m.style.display = 'flex';
    setError(id === 'adminLoginModal' ? 'adminLoginError' : 'memberLoginError', '');
    var user = document.getElementById(id === 'adminLoginModal' ? 'adminLoginUser' : 'memberLoginUser');
    if (user) setTimeout(function () { user.focus(); }, 50);
}

function hideModal(id) {
    var m = document.getElementById(id);
    if (m) m.style.display = 'none';
}

function setError(id, text) {
    var e = document.getElementById(id);
    if (e) e.textContent = text;
}

/* 页面加载后刷新页头登录状态（无对应元素的页面自动跳过） */
updateLoginState();
