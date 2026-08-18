@echo off
REM ============================================================
REM  前端同步脚本 - 将 frontend/ 和 admin/ 同步到后端
REM  运行方式：双击本文件（或右键"以管理员身份运行"）
REM  同步到：src/main/resources/static/ 和 target/classes/static/
REM  同步前会先清空旧文件，避免残留已删除的页面/资源
REM ============================================================
chcp 65001 >nul
setlocal

set ROOT=%~dp0
set FRONT=%ROOT%frontend
set ADMIN=%ROOT%admin
set STATIC_SRC=%ROOT%backend\src\main\resources\static
set STATIC_TARGET=%ROOT%backend\target\classes\static

echo 正在清理旧的静态文件...
if exist "%STATIC_SRC%\frontend" rmdir /s /q "%STATIC_SRC%\frontend"
if exist "%STATIC_SRC%\admin"    rmdir /s /q "%STATIC_SRC%\admin"
if exist "%STATIC_SRC%\css"      rmdir /s /q "%STATIC_SRC%\css"
if exist "%STATIC_SRC%\images"   rmdir /s /q "%STATIC_SRC%\images"
if exist "%STATIC_SRC%\js"       rmdir /s /q "%STATIC_SRC%\js"
if exist "%STATIC_TARGET%\frontend" rmdir /s /q "%STATIC_TARGET%\frontend"
if exist "%STATIC_TARGET%\admin"    rmdir /s /q "%STATIC_TARGET%\admin"
if exist "%STATIC_TARGET%\css"      rmdir /s /q "%STATIC_TARGET%\css"
if exist "%STATIC_TARGET%\images"   rmdir /s /q "%STATIC_TARGET%\images"
if exist "%STATIC_TARGET%\js"       rmdir /s /q "%STATIC_TARGET%\js"

echo 正在同步前端文件...

REM ---- 同步到 src/main/resources/static ----
xcopy /y /e /q "%FRONT%\*" "%STATIC_SRC%\frontend\" >nul
xcopy /y /e /q "%ADMIN%\*" "%STATIC_SRC%\admin\" >nul

REM ---- 同步到 target/classes/static ----
xcopy /y /e /q "%FRONT%\*" "%STATIC_TARGET%\frontend\" >nul
xcopy /y /e /q "%ADMIN%\*" "%STATIC_TARGET%\admin\" >nul

echo.
echo 同步完成！
echo   frontend  -^> backend\src\main\resources\static\frontend
echo   frontend  -^> backend\target\classes\static\frontend
echo   admin     -^> backend\src\main\resources\static\admin
echo   admin     -^> backend\target\classes\static\admin
echo.
echo 如果后端正在运行，请重启 Spring Boot 并硬刷新浏览器(Ctrl+F5)。
pause
