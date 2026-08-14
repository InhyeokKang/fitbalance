@echo off
title fitbalance 데모
cd /d "%~dp0"

rem 파이썬을 찾는다. py 런처가 있으면 그걸 쓰고, 없으면 python 을 쓴다.
where py >nul 2>nul
if %errorlevel%==0 (
    py -3 tools\launch.py
    goto end
)

where python >nul 2>nul
if %errorlevel%==0 (
    python tools\launch.py
    goto end
)

echo.
echo   ============================================================
echo   파이썬이 설치되어 있지 않습니다.
echo   ============================================================
echo.
echo   1. https://www.python.org/downloads/ 에서 내려받아 설치하세요.
echo   2. 설치 첫 화면의 "Add python.exe to PATH" 를 반드시 체크하세요.
echo   3. 설치가 끝나면 이 파일을 다시 더블클릭하세요.
echo.
pause
exit /b 1

:end
if errorlevel 1 pause
