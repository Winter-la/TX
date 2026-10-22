@echo off
setlocal
set "BASE=%~dp0"
set "JAR=%BASE%backend\target\spectrum-1.0.0.jar"

if not exist "%JAR%" (
    echo [ERROR] jar not found: %JAR%
    echo Build it first: cd backend  then  mvn -DskipTests package
    pause
    exit /b 1
)

echo Starting TX platform ... http://localhost:8080
echo Database: thz_spectrum_tx  Uploads: %BASE%uploads
echo Close this window or press Ctrl+C to stop the service.
echo.

java -jar "%JAR%" "--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/thz_spectrum_tx?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai" --spring.datasource.username=root --spring.datasource.password=123456 "--app.upload.dir=%BASE%uploads" --server.port=8080

echo.
echo Service stopped.
pause
