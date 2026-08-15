@echo off
chcp 949 > nul
title fitbalance 설치
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0설치.ps1"
