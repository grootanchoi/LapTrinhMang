@echo off
chcp 65001 > nul
echo ========================================================
echo DANG BIEN DICH DU AN VOI JDK (UTF-8)...
echo ========================================================
if not exist "build\classes" mkdir "build\classes"
"C:\Program Files\Apache NetBeans\jdk\bin\javac.exe" -encoding UTF-8 -d build/classes src/laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo/*.java
if %errorlevel% neq 0 (
    echo [LOI] Bien dich that bai!
    pause
    exit /b %errorlevel%
)

echo ========================================================
echo KHOI CHAY HE THONG WORKDESK (CLIENT / SERVER)...
echo ========================================================
"C:\Program Files\Apache NetBeans\jdk\bin\java.exe" -Dfile.encoding=UTF-8 -cp build/classes laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo.LapTrinhMang_HeThongTiepNhanVaQuanLyYeuCauNoiBo
