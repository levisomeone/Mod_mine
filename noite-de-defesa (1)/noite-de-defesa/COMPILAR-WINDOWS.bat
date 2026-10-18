@echo off
call gradlew.bat build
if errorlevel 1 (echo.& echo ERRO NO BUILD - copie a mensagem acima e me mande.& pause & exit /b 1)
if not exist MOD-PRONTO mkdir MOD-PRONTO
copy /Y build\libs\noite-de-defesa-1.0.0.jar MOD-PRONTO\ >nul
echo.
echo PRONTO! O mod esta em MOD-PRONTO\noite-de-defesa-1.0.0.jar
pause
