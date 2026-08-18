/**
 * 驻马店区域内中药材质量安全追溯体系信息平台 — 主脚本
 * 所有数据通过后端API查询，图谱由管理员上传
 */

$(function () {
    initKeypad();
});

/* ===========================================================
   数字键盘输入
   =========================================================== */
function initKeypad() {
    // 数字键
    $('.key[data-num]').on('click', function () {
        var input = $('#traceCodeInput');
        var val = input.val();
        if (val.length < 12) {
            input.val(val + $(this).data('num'));
        }
    });

    // 清除键 X
    $('#keyClear').on('click', function () {
        $('#traceCodeInput').val('');
        $('#traceResult').removeClass('show');
        $('#detailSection').hide();
        $('#showDetailBtn').html('&#x1f50d; 查看完整数据');
    });

    // 退格键 ←
    $('#keyBack').on('click', function () {
        var input = $('#traceCodeInput');
        input.val(input.val().slice(0, -1));
    });
}

/* ===========================================================
   追溯码查询 — 走后端 API
   =========================================================== */

function searchTrace() {
    var code = $('#traceCodeInput').val().trim();
    if (!code) { alert('请输入追溯码'); return; }
    doTraceSearch(code);
}

function doTraceSearch(code) {
    $.get(apiUrl('/api/query?code=' + code))
        .done(function (resp) {
            if (resp.found) {
                fillResult(resp, code);
            } else {
                showNotFound(code);
            }
        })
        .fail(function () {
            showError('无法连接后端服务器，请确认服务已启动');
        });
}

function fillResult(resp, code) {
    var p = resp.product;
    var s = resp.spectrum || {};
    var $r = $('#traceResult');
    $('#detailSection').hide();
    $('#showDetailBtn').html('&#x1f50d; 查看完整数据');

    // 基础信息
    $('#resultTitle').text('产品名称: ' + (p.name || ''));
    $('#rCode').text(code);
    $('#rType').text(p.type || '\u2014');
    $('#rOrigin').text(p.origin || '\u2014');
    $('#rLatin').text(p.latin || '\u2014');
    $('#rPart').text(p.part || '\u2014');
    $('#rConclusion').text(p.conclusion || '\u2014');

    // 产品简介
    if (p.description) {
        $('#productDescRow').show();
        $('#rDesc').text(p.description);
    } else {
        $('#productDescRow').hide();
    }

    // 检测信息
    $('#rTech').text(s.tech || '\u2014');
    $('#rDate').text(s.test_date || '\u2014');
    $('#rMatch').text(s.match_rate ? s.match_rate + '%' : '\u2014');
    $('#rGrade').text(s.grade || '\u2014');

    // 图谱图片（管理员上传）
    var hasTime = s.image_time && s.image_time.length > 0;
    var hasFreq = s.image_freq && s.image_freq.length > 0;

    if (hasTime) {
        $('#imgTimeDomain').attr('src', fileUrl(s.image_time)).show();
        $('#noImgTime').hide();
        if (s.desc_time) { $('#descTime').text(s.desc_time).show(); } else { $('#descTime').hide(); }
    } else {
        $('#imgTimeDomain').hide();
        $('#noImgTime').show();
        $('#descTime').hide();
    }

    if (hasFreq) {
        $('#imgFrequency').attr('src', fileUrl(s.image_freq)).show();
        $('#noImgFreq').hide();
        if (s.desc_freq) { $('#descFreq').text(s.desc_freq).show(); } else { $('#descFreq').hide(); }
    } else {
        $('#imgFrequency').hide();
        $('#noImgFreq').show();
        $('#descFreq').hide();
    }

    $r.addClass('show');
    $r.find('.status').text('\u2705 已认证').css('background', '#27ae60');

    scrollToResult();
}

function showNotFound(code) {
    var $r = $('#traceResult');
    $('#detailSection').hide();
    $('#showDetailBtn').html('&#x1f50d; 查看完整数据');
    $('#resultTitle').text('查询结果: 未找到该追溯码');
    $('#rCode,#rType,#rOrigin,#rLatin,#rPart,#rTech,#rDate,#rMatch,#rGrade').text('\u2014');
    $('#rConclusion').text('\u26a0\ufe0f 数据库中未匹配到该追溯码\u2014');
    $('#productDescRow').hide();
    $r.addClass('show');
    $r.find('.status').text('\u274c 未匹配').css('background', '#e53935');
    scrollToResult();
}

function showError(msg) {
    var $r = $('#traceResult');
    $('#detailSection').hide();
    $('#showDetailBtn').html('&#x1f50d; 查看完整数据');
    $('#resultTitle').text('查询失败');
    $('#rCode,#rType,#rOrigin,#rLatin,#rPart,#rTech,#rDate,#rMatch,#rGrade').text('\u2014');
    $('#rConclusion').text('\u26a0\ufe0f ' + msg);
    $('#productDescRow').hide();
    $r.addClass('show');
    $r.find('.status').text('\u26a0\ufe0f 错误').css('background', '#e53935');
    scrollToResult();
}

function scrollToResult() {
    setTimeout(function () {
        $('html,body').animate({ scrollTop: $('#traceResult').offset().top - 100 }, 300);
    }, 100);
}

function toggleDetail() {
    var $section = $('#detailSection');
    var $btn = $('#showDetailBtn');
    if ($section.is(':visible')) {
        $section.slideUp();
        $btn.html('&#x1f50d; 查看完整数据');
    } else {
        $section.slideDown();
        $btn.html('&#x1f4a1; 收起详情');
    }
}

/* 回车触发查询（备用，键盘只输入数字） */
$(document).on('keydown', '#traceCodeInput', function (e) {
    if (e.key === 'Enter') searchTrace();
});
