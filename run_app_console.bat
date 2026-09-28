@echo off
setlocal
title VSI Studio Pro (Console Output)
echo ============================================================
echo Starting VSI Studio Pro with Console Logs...
echo ============================================================
cd /d "%~dp0dist\output\VSIStudioPro"
".\runtime\bin\java.exe" -cp "app\ome-converter-ui-1.0.0-SNAPSHOT.jar" org.ome.converter.ui.Main %*
pause
