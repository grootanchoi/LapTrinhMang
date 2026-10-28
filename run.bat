@echo off
chcp 65001 > nul
echo ========================================================
echo DANG BIEN DICH DU AN VOI JDK (UTF-8)...
echo ========================================================
set "JAVAC_EXE="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "JAVAC_EXE=%JAVA_HOME%\bin\javac.exe"
if not defined JAVAC_EXE for /f "delims=" %%J in ('where javac 2^>nul') do if not defined JAVAC_EXE set "JAVAC_EXE=%%J"
if not defined JAVAC_EXE if exist "%ProgramFiles%\Apache NetBeans\jdk\bin\javac.exe" set "JAVAC_EXE=%ProgramFiles%\Apache NetBeans\jdk\bin\javac.exe"
if not defined JAVAC_EXE (
    echo [LOI] Khong tim thay JDK 21+. Hay cai JDK hoac cau hinh JAVA_HOME/PATH.
    pause
    exit /b 1
)
for %%I in ("%JAVAC_EXE%") do set "JAVA_EXE=%%~dpIjava.exe"
if not exist "build\classes" mkdir "build\classes"
"%JAVAC_EXE%" -encoding UTF-8 -cp "lib\mariadb-java-client-3.5.10.jar" -d build/classes src/laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo/*.java
if %errorlevel% neq 0 (
    echo [LOI] Bien dich that bai!
    pause
    exit /b %errorlevel%
)

echo ========================================================
echo KHOI CHAY HE THONG WORKDESK (CLIENT / SERVER)...
echo ========================================================
"%JAVA_EXE%" -Dfile.encoding=UTF-8 -cp "build/classes;lib\mariadb-java-client-3.5.10.jar" laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo.LapTrinhMang_HeThongTiepNhanVaQuanLyYeuCauNoiBo
