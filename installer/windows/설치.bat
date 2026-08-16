@echo off
chcp 949 > nul
title fitbalance 설치
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0설치.ps1"
if errorlevel 1 (
  echo.
  echo 설치가 끝나지 못했습니다. 위 내용을 그대로 알려 주세요.
  pause
)