#!/usr/bin/env bash
# ============================================================
#  智能指纹图谱平台 · 全量冒烟测试脚本
#  用法: bash scripts/smoke-test.sh [base_url]   (默认 http://localhost:8080)
#  依赖: curl, python
#  说明: 会创建临时测试数据（产品 TST001 / 会员 smoke_member / 内容/反馈等），
#       脚本结束前尽量通过接口清理；数据库级清理见脚本末尾提示。
# ============================================================
set -u
BASE="${1:-http://localhost:8080}"
TMPDIR="./smoke_tmp"
mkdir -p "$TMPDIR"

PASS=0; FAIL=0
pass() { PASS=$((PASS+1)); printf '  [PASS] %s\n' "$1"; }
fail() { FAIL=$((FAIL+1)); printf '  [FAIL] %s  %s\n' "$1" "$2"; }

# ---------- Python HTTP 助手（正确处理 UTF-8） ----------
cat > "$TMPDIR/httputil.py" <<'PYEOF'
import urllib.request, uuid, json, sys, os
# 用法: httputil.py <method> <url> <fields_json> <token> <json|form> [filefield:path]...
method, url, fields, token, mode = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5]
files = [a.split(':', 1) for a in sys.argv[6:]]
headers = {}
if mode == "json":
    body = fields.encode("utf-8")
    headers["Content-Type"] = "application/json"
else:
    boundary = uuid.uuid4().hex
    body = b''
    for k, v in json.loads(fields).items():
        body += ("--%s\r\nContent-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n" % (boundary, k, v)).encode("utf-8")
    for ff, fp in files:
        name = os.path.basename(fp)
        body += ("--%s\r\nContent-Disposition: form-data; name=\"%s\"; filename=\"%s\"\r\nContent-Type: application/octet-stream\r\n\r\n" % (boundary, ff, name)).encode("utf-8")
        body += open(fp, 'rb').read() + b"\r\n"
    body += ("--%s--\r\n" % boundary).encode("utf-8")
    headers["Content-Type"] = "multipart/form-data; boundary=%s" % boundary
if token and token != "-":
    headers["Authorization"] = "Bearer " + token
req = urllib.request.Request(url, data=body, headers=headers, method=method)
try:
    print(urllib.request.urlopen(req, timeout=30).read().decode("utf-8"))
except urllib.error.HTTPError as e:
    print(e.read().decode("utf-8", "replace"))
    sys.exit(100 + (e.code // 100))
PYEOF

py_json_post() { # desc url fields_json token
  local resp; resp=$(python "$TMPDIR/httputil.py" POST "$BASE$2" "$3" "$4" json)
  if echo "$resp" | python -c "import sys,json
try:
  d=json.load(sys.stdin); sys.exit(0 if d.get('success') is True else 1)
except Exception: sys.exit(1)"; then pass "$1"; else fail "$1" "resp: ${resp:0:120}"; fi
}
py_form_post() { # desc url fields_json token filefield:path...
  local desc="$1" url="$2" fields="$3" token="$4"
  shift 4
  local resp; resp=$(python "$TMPDIR/httputil.py" POST "$BASE$url" "$fields" "$token" form "$@")
  if echo "$resp" | python -c "import sys,json
try:
  d=json.load(sys.stdin); sys.exit(0 if d.get('success') is True else 1)
except Exception: sys.exit(1)"; then pass "$desc"; else fail "$desc" "resp: ${resp:0:120}"; fi
}

# ---------- 工具函数 ----------
expect_status() { # desc path want_code
  local got; got=$(curl -s -o /dev/null -w "%{http_code}" -m 10 "$BASE$2")
  if [ "$got" = "$3" ]; then pass "$1"; else fail "$1" "want $3 got $got ($2)"; fi
}
expect_status_auth() { # desc path want_code token
  local got; got=$(curl -s -o /dev/null -w "%{http_code}" -m 10 -H "Authorization: Bearer $4" "$BASE$2")
  if [ "$got" = "$3" ]; then pass "$1"; else fail "$1" "want $3 got $got ($2)"; fi
}
expect_status_auth_method() { # desc method path want_code token
  local got; got=$(curl -s -o /dev/null -w "%{http_code}" -m 10 -X "$2" -H "Authorization: Bearer $5" "$BASE$3")
  if [ "$got" = "$4" ]; then pass "$1"; else fail "$1" "want $4 got $got ($2 $3)"; fi
}
json_get() { curl -s -m 10 "$BASE$1"; }
json_get_auth() { curl -s -m 10 -H "Authorization: Bearer $2" "$BASE$1"; }
post_json() { # desc path body token expect_success_bool(True/False)
  local resp; resp=$(curl -s -m 10 -X POST "$BASE$2" -H "Authorization: Bearer $4" -H "Content-Type: application/json" -d "$3")
  if echo "$resp" | python -c "import sys,json
try:
  d=json.load(sys.stdin); sys.exit(0 if ((d.get('success') is True) is $5) else 1)
except Exception: sys.exit(1)"; then pass "$1"; else fail "$1" "resp: ${resp:0:120}"; fi
}
has_error() { # desc resp
  if echo "$2" | python -c "import sys,json
try:
  sys.exit(0 if json.load(sys.stdin).get('error') else 1)
except Exception: sys.exit(1)"; then pass "$1"; else fail "$1" "resp: ${2:0:120}"; fi
}

echo "==================================================="
echo " 冒烟测试目标: $BASE"
echo "==================================================="

# ---------- A. 静态页面 ----------
echo "--- A. 静态页面与资源 ---"
expect_status "根路径 302 跳转首页" "/" "302"
expect_status "首页 /frontend/index.html" "/frontend/index.html" "200"
expect_status "药材种类页" "/frontend/herbs.html" "200"
expect_status "平台介绍页" "/frontend/about.html" "200"
expect_status "新闻政策公告页" "/frontend/policy.html" "200"
expect_status "追溯码查询页" "/frontend/trace.html" "200"
expect_status "数据下载页" "/frontend/download.html" "200"
expect_status "意见反馈页" "/frontend/feedback.html" "200"
expect_status "管理后台页 /admin/index.html" "/admin/index.html" "200"
expect_status "管理后台路径 /admin 302" "/admin" "302"
expect_status "本地 jQuery" "/frontend/js/vendor/jquery.min.js" "200"
expect_status "本地 Chart.js" "/frontend/js/vendor/chart.umd.min.js" "200"
expect_status "auth.js" "/frontend/js/auth.js" "200"
expect_status "main.js" "/frontend/js/main.js" "200"
expect_status "样式表" "/frontend/css/style.css" "200"
expect_status "站点图片" "/frontend/images/tu/background.jpg" "200"

# ---------- B. 公开 API ----------
echo "--- B. 公开 API ---"
resp=$(json_get "/api/query?code=001"); echo "$resp" | python -c "import sys,json; sys.exit(0 if json.load(sys.stdin).get('found') is True else 1)" && pass "产品码查询 001 命中" || fail "产品码查询" "$resp"
resp=$(json_get "/api/query?code=NOTEXIST999"); echo "$resp" | python -c "import sys,json; sys.exit(0 if json.load(sys.stdin).get('found') is False else 1)" && pass "产品码查询未命中" || fail "产品码未命中" "$resp"
resp=$(json_get "/api/products?page=0&size=5"); echo "$resp" | python -c "import sys,json; sys.exit(0 if 'items' in json.load(sys.stdin) else 1)" && pass "产品列表分页" || fail "产品列表" "$resp"
resp=$(json_get "/api/health"); echo "$resp" | python -c "import sys,json; d=json.load(sys.stdin); sys.exit(0 if d.get('status')=='UP' and d.get('database')=='UP' else 1)" && pass "健康检查(含数据库)" || fail "健康检查" "$resp"
resp=$(json_get "/api/content/news"); echo "$resp" | python -c "import sys,json; sys.exit(0 if isinstance(json.load(sys.stdin), list) else 1)" && pass "新闻列表为数组" || fail "新闻列表" "$resp"
resp=$(json_get "/api/content/policy-links"); echo "$resp" | python -c "import sys,json; sys.exit(0 if isinstance(json.load(sys.stdin), list) else 1)" && pass "政策链接为数组" || fail "政策链接" "$resp"
resp=$(json_get "/api/content/announcements"); echo "$resp" | python -c "import sys,json; sys.exit(0 if isinstance(json.load(sys.stdin), list) else 1)" && pass "公告为数组" || fail "公告" "$resp"
resp=$(json_get "/api/trace/query?code=987654")
echo "$resp" | python -c "import sys,json
d=json.load(sys.stdin)
f=d.get('flows',[{}])[0]
sys.exit(0 if (d.get('found') is True and '*' in f.get('buyerName','') and '*' in f.get('buyerPhone','')) else 1)" && pass "追溯流向公开查询已脱敏" || fail "追溯脱敏" "$resp"
resp=$(json_get "/api/trace/query?code=NOTEXIST99"); echo "$resp" | python -c "import sys,json; sys.exit(0 if json.load(sys.stdin).get('found') is False else 1)" && pass "追溯查询未命中" || fail "追溯未命中" "$resp"

# ---------- C. 鉴权与 RBAC ----------
echo "--- C. 鉴权与 RBAC ---"
expect_status_auth "无 token 访问管理接口 401" "/admin/products" "401" ""
ADMIN_LOGIN=$(curl -s -m 10 -X POST "$BASE/admin/login" -H "Content-Type: application/x-www-form-urlencoded" -d "username=admin&password=admin")
ADMIN_TOKEN=$(echo "$ADMIN_LOGIN" | python -c "import sys,json; print(json.load(sys.stdin).get('token',''))")
[ -n "$ADMIN_TOKEN" ] && pass "管理员登录获取 token" || fail "管理员登录" "$ADMIN_LOGIN"
BAD_LOGIN=$(curl -s -m 10 -X POST "$BASE/admin/login" -H "Content-Type: application/x-www-form-urlencoded" -d "username=admin&password=wrongpass")
echo "$BAD_LOGIN" | python -c "import sys,json; d=json.load(sys.stdin); sys.exit(0 if (d.get('success') is not True and d.get('error')) else 1)" && pass "错误密码登录被拒绝" || fail "错误密码登录" "$BAD_LOGIN"
expect_status_auth "管理员 token 访问管理接口" "/admin/products" "200" "$ADMIN_TOKEN"
expect_status_auth "管理员 token 访问 /admin/check" "/admin/check" "200" "$ADMIN_TOKEN"
expect_status_auth "伪造 token 401" "/admin/products" "401" "invalid.token.here"

# ---------- D. 会员管理 ----------
echo "--- D. 会员管理 ---"
py_json_post "创建测试会员" "/admin/members" '{"username":"smoke_member","password":"test123456","role":"member","email":"m@t.com"}' "$ADMIN_TOKEN"
MEM_LOGIN=$(curl -s -m 10 -X POST "$BASE/admin/login" -H "Content-Type: application/x-www-form-urlencoded" -d "username=smoke_member&password=test123456")
MEM_TOKEN=$(echo "$MEM_LOGIN" | python -c "import sys,json; d=json.load(sys.stdin); print(d.get('token',''))")
MEM_ROLE=$(echo "$MEM_LOGIN" | python -c "import sys,json; d=json.load(sys.stdin); print(d.get('role',''))")
[ "$MEM_ROLE" = "member" ] && pass "会员角色正确" || fail "会员角色" "$MEM_LOGIN"
expect_status_auth "会员访问管理接口 403" "/admin/products" "403" "$MEM_TOKEN"
expect_status_auth_method "会员调用管理写接口 403" "DELETE" "/admin/members/999999" "403" "$MEM_TOKEN"
resp=$(curl -s -m 10 -X DELETE "$BASE/api/content/news/999999" -H "Authorization: Bearer $MEM_TOKEN")
has_error "会员调用删除接口被拒(403)" "$resp"
expect_status_auth "会员可访问光谱数据(下载权限)" "/api/spectrum/list" "200" "$MEM_TOKEN"
expect_status_auth "会员访问会员管理 403" "/admin/members" "403" "$MEM_TOKEN"
resp=$(curl -s -m 10 -X POST "$BASE/admin/password" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d '{"oldPassword":"wrong","newPassword":"whatever123"}')
echo "$resp" | python -c "import sys,json; sys.exit(0 if '原密码错误' in json.load(sys.stdin).get('error','') else 1)" && pass "修改密码校验原密码" || fail "修改密码" "$resp"

# ---------- E. 产品新增与图谱上传 ----------
echo "--- E. 产品新增与图谱上传 ---"
py_json_post "新增测试产品 TST001(含中文)" "/admin/products/add" '{"code":"TST001","name":"测试药材","type":"中草药","origin":"测试产地"}' "$ADMIN_TOKEN"
post_json "重复产品码被拒绝" "/admin/products/add" '{"code":"TST001","name":"dup"}' "$ADMIN_TOKEN" False
resp=$(python "$TMPDIR/httputil.py" POST "$BASE/admin/products/add" '{"code":"DUPNAME","name":"测试药材"}' "$ADMIN_TOKEN" json)
echo "$resp" | python -c "import sys,json; sys.exit(0 if '已存在' in json.load(sys.stdin).get('error','') else 1)" && pass "重复产品名称被拒绝" || fail "重复产品名称" "$resp"
resp=$(curl -s -m 10 -X POST "$BASE/admin/products/add" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d '{"code":"TST!!非法","name":"x"}')
has_error "非法产品码被拒绝" "$resp"
python - <<'PYEOF'
import struct, zlib
def png(path):
    sig=b'\x89PNG\r\n\x1a\n'
    ihdr=struct.pack('>IIBBBBB',1,1,8,6,0,0,0)
    idat=zlib.compress(b'\x00\xff\xff\xff')
    def chunk(t,d): return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d))
    open(path,'wb').write(sig+chunk(b'IHDR',ihdr)+chunk(b'IDAT',idat)+chunk(b'IEND',b''))
png('smoke_tmp/test.png')
open('smoke_tmp/time.txt','w').write('0.1 1.2\n0.2 2.3\n0.3 3.4\n0.4 4.5\n')
open('smoke_tmp/freq.txt','w').write('0.5 5.6\n0.6 6.7\n')
PYEOF
[ -s smoke_tmp/test.png ] && pass "测试素材生成" || fail "测试素材生成" ""
py_form_post "上传图谱+源数据(含中文字段)" "/admin/upload" '{"code":"TST001","tech":"太赫兹时域光谱","matchRate":"97.5","grade":"一级"}' "$ADMIN_TOKEN" imageTime:smoke_tmp/test.png spectrumTimeFile:smoke_tmp/time.txt
resp=$(json_get "/api/query?code=TST001")
echo "$resp" | python -c "import sys,json; d=json.load(sys.stdin); sys.exit(0 if d.get('spectrum',{}).get('image_time') else 1)" && pass "TST001 查询返回图片" || fail "TST001 图片" "$resp"
resp=$(curl -s -m 10 -X POST "$BASE/admin/upload" -H "Authorization: Bearer $ADMIN_TOKEN" -F "code=TST001" -F "testDate=2026-99-99")
echo "$resp" | python -c "import sys,json; sys.exit(0 if '日期' in json.load(sys.stdin).get('error','') else 1)" && pass "非法日期被拒绝" || fail "非法日期" "$resp"
resp=$(curl -s -m 10 -X POST "$BASE/admin/upload" -H "Authorization: Bearer $ADMIN_TOKEN" -F "code=TST001" -F "matchRate=abc")
has_error "非法匹配度返回 JSON 错误" "$resp"
resp=$(curl -s -m 10 -X POST "$BASE/admin/upload" -H "Authorization: Bearer $ADMIN_TOKEN" -F "code=TST001" -F "imageTime=@smoke_tmp/time.txt;type=text/plain")
echo "$resp" | python -c "import sys,json; sys.exit(0 if '图片' in json.load(sys.stdin).get('error','') else 1)" && pass "非图片文件被拒绝" || fail "非图片文件" "$resp"
img=$(echo "$resp" >/dev/null; json_get "/api/query?code=TST001" | python -c "import sys,json; print(json.load(sys.stdin).get('spectrum',{}).get('image_time',''))")
if [ -n "$img" ]; then expect_status "上传的图片可访问" "$img" "200"; else fail "上传的图片可访问" "无 image_time"; fi

# ---------- F. 光谱源文件管理 ----------
echo "--- F. 光谱源文件管理 ---"
PRODUCT_ID=$(curl -s -m 10 "$BASE/admin/products?size=100" -H "Authorization: Bearer $ADMIN_TOKEN" | python -c "import sys,json
d=json.load(sys.stdin)
print(next((str(i['id']) for i in d['items'] if i['code']=='TST001'),''))")
[ -n "$PRODUCT_ID" ] && pass "获取 TST001 产品 ID" || fail "获取 TST001 产品 ID" ""
py_form_post "光谱频域文件上传" "/api/spectrum/upload" "{\"productId\":\"$PRODUCT_ID\",\"dataType\":\"频域\"}" "$ADMIN_TOKEN" file:smoke_tmp/freq.txt
resp=$(python "$TMPDIR/httputil.py" POST "$BASE/api/spectrum/upload" '{"productId":"'$PRODUCT_ID'","dataType":"非法类型"}' "$ADMIN_TOKEN" form file:smoke_tmp/freq.txt)
echo "$resp" | python -c "import sys,json; sys.exit(0 if '时域' in json.load(sys.stdin).get('error','') else 1)" && pass "非法 dataType 被拒绝" || fail "非法 dataType" "$resp"
resp=$(python "$TMPDIR/httputil.py" POST "$BASE/api/spectrum/upload" '{"productId":"999999","dataType":"时域"}' "$ADMIN_TOKEN" form file:smoke_tmp/time.txt)
echo "$resp" | python -c "import sys,json; sys.exit(0 if '产品不存在' in json.load(sys.stdin).get('error','') else 1)" && pass "不存在的产品被拒绝" || fail "不存在产品" "$resp"
FILE_ID=$(json_get_auth "/api/spectrum/list" "$ADMIN_TOKEN" | python -c "import sys,json
d=json.load(sys.stdin)
p=[x for x in d if str(x.get('productId'))=='$PRODUCT_ID'][0]
f=p.get('freqFile') or {}
print(f.get('id') if f.get('hasFile') else '')")
[ -n "$FILE_ID" ] && pass "光谱列表含已上传频域文件" || fail "光谱列表" ""
if [ -n "$FILE_ID" ]; then
  resp=$(json_get_auth "/api/spectrum/preview?id=$FILE_ID" "$ADMIN_TOKEN")
  echo "$resp" | python -c "import sys,json; d=json.load(sys.stdin); sys.exit(0 if isinstance(d.get('data'), list) and d.get('pointCount',0)>0 else 1)" && pass "光谱预览返回数据点" || fail "光谱预览" "$resp"
  expect_status_auth "光谱文件下载" "/api/spectrum/download?id=$FILE_ID" "200" "$ADMIN_TOKEN"
  zipcode=$(curl -s -o smoke_tmp/batch.zip -w "%{http_code}" -m 15 -X POST "$BASE/api/spectrum/batch-download" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d "{\"ids\":[$FILE_ID]}")
  if [ "$zipcode" = "200" ] && python -c "import zipfile; zipfile.ZipFile('smoke_tmp/batch.zip')" 2>/dev/null; then pass "批量下载 zip 有效"; else fail "批量下载" "HTTP $zipcode"; fi
fi

# ---------- G. 内容发布 ----------
echo "--- G. 内容发布 ---"
post_json "发布政策链接" "/api/content/policy-link/add" '{"title":"SmokePolicy","url":"https://example.com/p","summary":"test"}' "$ADMIN_TOKEN" True
resp=$(curl -s -m 10 -X POST "$BASE/api/content/policy-link/add" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d '{"title":"","url":"javascript:alert(1)"}')
has_error "空标题/js 链接被拒绝" "$resp"
post_json "发布新闻" "/api/content/news/add" '{"title":"SmokeNews","url":"","summary":""}' "$ADMIN_TOKEN" True
py_form_post "发布公告(带附件)" "/api/content/announcement/add" '{"title":"SmokeAnnounce","content":"test body"}' "$ADMIN_TOKEN" file:smoke_tmp/time.txt
resp=$(curl -s -m 10 "$BASE/api/content/all?page=0&size=10" -H "Authorization: Bearer $ADMIN_TOKEN")
echo "$resp" | python -c "import sys,json; d=json.load(sys.stdin); sys.exit(0 if d.get('total',0)>0 else 1)" && pass "内容聚合列表" || fail "内容聚合" "$resp"
POLICY_ID=$(echo "$resp" | python -c "import sys,json
d=json.load(sys.stdin)
print(next((str(i['id']) for i in d['items'] if i['title']=='SmokePolicy'),''))")
if [ -n "$POLICY_ID" ]; then
  resp=$(curl -s -m 10 -X DELETE "$BASE/api/content/policy-link/$POLICY_ID" -H "Authorization: Bearer $ADMIN_TOKEN")
  echo "$resp" | python -c "import sys,json; sys.exit(0 if json.load(sys.stdin).get('success') is True else 1)" && pass "管理员删除政策链接" || fail "删除政策链接" "$resp"
fi

# ---------- H. 追溯录入 ----------
echo "--- H. 追溯录入 ---"
resp=$(python "$TMPDIR/httputil.py" POST "$BASE/api/admin/trace/add" '{"productCode":"TST001","buyerName":"张三","buyerPhone":"13800000000","buyerAddress":"河南省驻马店市驿城区","idCard":"412801199001011234","saleTime":"2026-08-18T10:00"}' "$ADMIN_TOKEN" json)
TRACE_CODE=$(echo "$resp" | python -c "import sys,json; d=json.load(sys.stdin); print(d.get('traceCode',''))")
[ -n "$TRACE_CODE" ] && pass "追溯录入自动生成追溯码" || fail "追溯录入" "$resp"
resp=$(python "$TMPDIR/httputil.py" POST "$BASE/api/admin/trace/add" "{\"productCode\":\"TST001\",\"buyerName\":\"李四\",\"buyerPhone\":\"13900000000\",\"traceCode\":\"$TRACE_CODE\"}" "$ADMIN_TOKEN" json)
echo "$resp" | python -c "import sys,json; sys.exit(0 if '已存在' in json.load(sys.stdin).get('error','') else 1)" && pass "重复追溯码被拒绝" || fail "重复追溯码" "$resp"
resp=$(curl -s -m 10 -X POST "$BASE/api/admin/trace/add" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d '{"productCode":"TST001","buyerName":""}')
has_error "缺购买者被拒绝" "$resp"
resp=$(curl -s -m 10 -X POST "$BASE/api/admin/trace/add" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d '{"productCode":"TST001","buyerName":"AB","buyerPhone":"13800000000"}')
echo "$resp" | python -c "import sys,json; sys.exit(0 if '姓名应为2-4个汉字' in json.load(sys.stdin).get('error','') else 1)" && pass "姓名非2-4个汉字被拒绝" || fail "姓名校验" "$resp"
resp=$(python "$TMPDIR/httputil.py" POST "$BASE/api/admin/trace/add" '{"productCode":"TST001","buyerName":"王五","buyerPhone":"12345"}' "$ADMIN_TOKEN" json)
echo "$resp" | python -c "import sys,json; sys.exit(0 if '11位' in json.load(sys.stdin).get('error','') else 1)" && pass "电话位数不符被拒绝" || fail "电话校验" "$resp"
resp=$(python "$TMPDIR/httputil.py" POST "$BASE/api/admin/trace/add" '{"productCode":"TST001","buyerName":"赵六","buyerPhone":"13700000000","idCard":"12345"}' "$ADMIN_TOKEN" json)
echo "$resp" | python -c "import sys,json; sys.exit(0 if '18位' in json.load(sys.stdin).get('error','') else 1)" && pass "身份证位数不符被拒绝" || fail "身份证校验" "$resp"
resp=$(curl -s -m 10 "$BASE/api/admin/trace/by-product?productCode=TST001" -H "Authorization: Bearer $ADMIN_TOKEN")
echo "$resp" | python -c "import sys,json
d=json.load(sys.stdin); items=d.get('items',[])
sys.exit(0 if any(i.get('buyerName')=='张三' for i in items) else 1)" && pass "按产品编码查询追溯记录" || fail "按产品查追溯" "$resp"
if [ -n "$TRACE_CODE" ]; then
  resp=$(json_get "/api/trace/query?code=$TRACE_CODE")
  echo "$resp" | python -c "import sys,json
d=json.load(sys.stdin); f=d.get('flows',[{}])[0]
sys.exit(0 if (d.get('found') is True and f.get('buyerName')=='张*' and f.get('buyerPhone')=='138****0000') else 1)" && pass "公开查询返回脱敏数据" || fail "公开查询脱敏" "$resp"
fi
resp=$(curl -s -m 10 "$BASE/api/admin/trace/list?page=0&size=5" -H "Authorization: Bearer $ADMIN_TOKEN")
echo "$resp" | python -c "import sys,json; sys.exit(0 if 'items' in json.load(sys.stdin) else 1)" && pass "管理端追溯列表" || fail "追溯列表" "$resp"

# ---------- I. 意见反馈 ----------
echo "--- I. 意见反馈 ---"
resp=$(python "$TMPDIR/httputil.py" POST "$BASE/api/feedback" '{"type":"功能建议","content":"测试反馈：希望增加更多药材品种","contact":"13800000000"}' "-" form)
echo "$resp" | python -c "import sys,json; sys.exit(0 if json.load(sys.stdin).get('success') is True else 1)" && pass "UTF-8 反馈提交成功" || fail "反馈提交" "$resp"
resp=$(curl -s -m 10 -X POST "$BASE/api/feedback" -F "type=other" -F "content=")
echo "$resp" | python -c "import sys,json; sys.exit(0 if '请填写' in json.load(sys.stdin).get('error','') else 1)" && pass "空内容反馈被拒绝" || fail "空内容反馈" "$resp"
# 限流：同一 IP 15 分钟内最多 10 次（含上面的请求），继续刷到 429
RATE_HIT=""
for i in $(seq 1 12); do
  code=$(curl -s -o /dev/null -w "%{http_code}" -m 10 -X POST "$BASE/api/feedback" -F "type=other" -F "content=rate test $i")
  if [ "$code" = "429" ]; then RATE_HIT="$i"; break; fi
done
[ -n "$RATE_HIT" ] && pass "反馈限流生效(第 $RATE_HIT 次返回 429)" || fail "反馈限流" "未触发 429"
resp=$(curl -s -m 10 "$BASE/admin/feedback" -H "Authorization: Bearer $ADMIN_TOKEN")
echo "$resp" | python -c "import sys,json; d=json.load(sys.stdin); sys.exit(0 if isinstance(d, list) and any('测试反馈' in i.get('content','') for i in d) else 1)" && pass "管理端反馈列表含 UTF-8 内容" || fail "反馈列表" "$resp"
FB_IDS=$(echo "$resp" | python -c "import sys,json
d=json.load(sys.stdin)
print(' '.join(str(i['id']) for i in d if '测试反馈' in i.get('content','') or i.get('content','').startswith('rate test')))")
for fid in $FB_IDS; do
  curl -s -m 10 -X DELETE "$BASE/admin/feedback/$fid" -H "Authorization: Bearer $ADMIN_TOKEN" >/dev/null
done
[ -n "$FB_IDS" ] && pass "测试反馈已清理" || pass "无测试反馈需清理"

# ---------- J. 错误处理 ----------
echo "--- J. 错误处理 ---"
expect_status "未知 API 404" "/api/nonexistent" "404"
resp=$(json_get "/api/nonexistent"); echo "$resp" | python -c "import sys,json; sys.exit(0 if 'error' in json.load(sys.stdin) else 1)" && pass "404 返回 JSON" || fail "404 JSON" "$resp"
resp=$(json_get "/api/products?page=abc"); echo "$resp" | python -c "import sys,json; sys.exit(0 if 'error' in json.load(sys.stdin) else 1)" && pass "参数类型错误返回 JSON" || fail "参数类型" "$resp"
resp=$(curl -s -m 10 -X POST "$BASE/admin/products/add" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d 'not-a-json')
echo "$resp" | python -c "import sys,json; sys.exit(0 if 'error' in json.load(sys.stdin) else 1)" && pass "非法 JSON 请求体返回错误" || fail "非法 JSON" "$resp"
expect_status_auth_method "错误方法返回 405" "GET" "/admin/members/999999" "405" "$ADMIN_TOKEN"
python -c "open('smoke_tmp/big.txt','wb').write(b'0.1 1.2\n'*2000000)"
resp=$(curl -s -m 30 -o /dev/null -w "%{http_code}" -X POST "$BASE/api/spectrum/upload" -H "Authorization: Bearer $ADMIN_TOKEN" -F "productId=$PRODUCT_ID" -F "dataType=time" -F "file=@smoke_tmp/big.txt")
[ "$resp" = "413" ] && pass "超大文件返回 413" || fail "超大文件 413" "got $resp"
HDR=$(curl -s -I -m 10 "$BASE/api/query?code=001" | tr -d '\r')
echo "$HDR" | grep -qi "X-Content-Type-Options: nosniff" && pass "安全响应头 X-Content-Type-Options" || fail "安全响应头" ""
echo "$HDR" | grep -qi "X-Frame-Options: SAMEORIGIN" && pass "安全响应头 X-Frame-Options" || fail "X-Frame-Options" ""

# ---------- 清理 ----------
echo "--- 清理 ---"
MEM_ID=$(curl -s -m 10 "$BASE/admin/members" -H "Authorization: Bearer $ADMIN_TOKEN" | python -c "import sys,json
d=json.load(sys.stdin)
print(next((str(m['id']) for m in d if m['username']=='smoke_member'),''))")
if [ -n "$MEM_ID" ]; then
  resp=$(curl -s -m 10 -X DELETE "$BASE/admin/members/$MEM_ID" -H "Authorization: Bearer $ADMIN_TOKEN")
  echo "$resp" | python -c "import sys,json; sys.exit(0 if json.load(sys.stdin).get('success') is True else 1)" && pass "测试会员已删除" || fail "删除测试会员" "$resp"
fi
for it in $(curl -s -m 10 "$BASE/api/content/all?page=0&size=100" -H "Authorization: Bearer $ADMIN_TOKEN" | python -c "import sys,json
d=json.load(sys.stdin)
[print(str(i['id'])+':'+i['type']) for i in d['items'] if i['title'] in ('SmokeNews','SmokeAnnounce')]"); do
  id="${it%%:*}"; type="${it##*:}"
  curl -s -m 10 -X DELETE "$BASE/api/content/$type/$id" -H "Authorization: Bearer $ADMIN_TOKEN" >/dev/null
done
pass "测试内容已清理"
rm -rf "$TMPDIR"

echo "==================================================="
echo " 结果: PASS=$PASS FAIL=$FAIL"
echo "==================================================="
echo " 注意: 产品 TST001、追溯记录与相应上传文件请通过数据库清理:"
echo "   DELETE FROM trace_flow WHERE product_code='TST001';"
echo "   DELETE FROM products WHERE code='TST001';  (级联删除检测记录/光谱文件行)"
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
