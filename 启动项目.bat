@echo off
title 健康管理项目启动器
color 0A

echo.
echo #################################################################
echo #                                                               #
echo #         个人健康管理系统 - 项目启动器                          #
echo #         Personal Health Management System Launcher            #
echo #                                                               #
echo #################################################################
echo.

cd /d "%~dp0"

echo [步骤 1/4] 正在检查环境配置...
echo.

where java >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到 Java，请先安装 JDK 17 或更高版本
    pause
    exit /b 1
)

where mvn >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到 Maven，请先安装 Maven
    pause
    exit /b 1
)

where npm >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到 npm，请先安装 Node.js
    pause
    exit /b 1
)

echo [OK] Java、Maven 和 npm 已安装
echo.

echo [步骤 2/4] 正在启动后端服务 (端口 8080)...
echo.
echo [提示] 后端服务启动可能需要 1-3 分钟，请耐心等待...
echo.

start "健康管理后端服务" cmd /k "cd /d "%~dp0health-management-system" && mvn spring-boot:run"

echo [步骤 3/4] 正在检查前端依赖...
echo.

if not exist "%~dp0health-management-web\node_modules" (
    echo [提示] 正在安装前端依赖，请稍候...
    cd /d "%~dp0health-management-web"
    call npm install
)

echo.
echo [步骤 4/4] 正在启动前端服务 (端口 5173)...
echo.

start "健康管理前端服务" cmd /k "cd /d "%~dp0health-management-web" && npm run dev"

echo.
echo.
echo #################################################################
echo #                                                               #
echo #                    所有服务正在启动中...                       #
echo #                                                               #
echo #################################################################
echo.
echo 请稍等 10-30 秒让服务完全启动，然后访问：
echo.
echo   前端界面:  http://localhost:5173
echo   后端 API:  http://localhost:8080
echo   API 文档:  http://localhost:8080/swagger-ui.html
echo.
echo 默认登录账号：
echo   用户名: testuser
echo   密码:   Test123456
echo.
echo #################################################################
echo.
pause
