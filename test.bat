@echo off
setlocal
chcp 65001 > nul

set "JAVAC_EXE="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "JAVAC_EXE=%JAVA_HOME%\bin\javac.exe"
if not defined JAVAC_EXE for /f "delims=" %%J in ('where javac 2^>nul') do if not defined JAVAC_EXE set "JAVAC_EXE=%%J"
if not defined JAVAC_EXE if exist "%ProgramFiles%\Apache NetBeans\jdk\bin\javac.exe" set "JAVAC_EXE=%ProgramFiles%\Apache NetBeans\jdk\bin\javac.exe"
if not defined JAVAC_EXE (
    echo [ERROR] JDK 21+ not found. Configure JAVA_HOME or PATH.
    exit /b 1
)
for %%I in ("%JAVAC_EXE%") do set "JAVA_HOME_DIR=%%~dpI.."
set "JAVA_EXE=%JAVA_HOME_DIR%\bin\java.exe"
set "KEYTOOL_EXE=%JAVA_HOME_DIR%\bin\keytool.exe"

if not exist "build\classes" mkdir "build\classes"
if not exist "build\test\classes" mkdir "build\test\classes"
"%JAVAC_EXE%" -encoding UTF-8 -cp "lib\mariadb-java-client-3.5.10.jar" -d build/classes src/laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo/*.java
if errorlevel 1 exit /b 1

"%JAVAC_EXE%" -encoding UTF-8 -cp "build\classes;lib\mariadb-java-client-3.5.10.jar" -d build/test/classes test/laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo/*.java
if errorlevel 1 exit /b 1

set "WORKDESK_TLS="
set "WORKDESK_KEYSTORE="
set "WORKDESK_KEYSTORE_PASSWORD="
set "WORKDESK_TRUSTSTORE="
set "WORKDESK_TRUSTSTORE_PASSWORD="
echo Running plaintext TCP integration tests...
"%JAVA_EXE%" -Dfile.encoding=UTF-8 -Dworkdesk.testMode=true -Djava.awt.headless=true -cp "build\classes;build\test\classes;lib\mariadb-java-client-3.5.10.jar" laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo.NetworkIntegrationTests
if errorlevel 1 exit /b 1

if not exist "build\test-tls" mkdir "build\test-tls"
if not exist "%KEYTOOL_EXE%" (
    echo [ERROR] keytool.exe not found; TLS integration tests cannot create a temporary test certificate.
    exit /b 1
)
"%KEYTOOL_EXE%" -delete -alias workdesk-test -keystore build\test-tls\server.p12 -storepass changeit > nul 2>&1
"%KEYTOOL_EXE%" -delete -alias workdesk-test -keystore build\test-tls\client-trust.p12 -storepass changeit > nul 2>&1
"%KEYTOOL_EXE%" -genkeypair -alias workdesk-test -keyalg RSA -keysize 2048 -validity 2 -storetype PKCS12 -keystore build\test-tls\server.p12 -storepass changeit -keypass changeit -dname "CN=127.0.0.1" -ext "SAN=IP:127.0.0.1,DNS:localhost" -noprompt
if errorlevel 1 exit /b 1
"%KEYTOOL_EXE%" -exportcert -alias workdesk-test -keystore build\test-tls\server.p12 -storepass changeit -file build\test-tls\server.cer
if errorlevel 1 exit /b 1
"%KEYTOOL_EXE%" -importcert -alias workdesk-test -file build\test-tls\server.cer -storetype PKCS12 -keystore build\test-tls\client-trust.p12 -storepass changeit -noprompt
if errorlevel 1 exit /b 1

set "WORKDESK_TLS=true"
set "WORKDESK_KEYSTORE=build\test-tls\server.p12"
set "WORKDESK_KEYSTORE_PASSWORD=changeit"
set "WORKDESK_TRUSTSTORE=build\test-tls\client-trust.p12"
set "WORKDESK_TRUSTSTORE_PASSWORD=changeit"
echo Running TLS integration tests with an ephemeral test certificate...
"%JAVA_EXE%" -Dfile.encoding=UTF-8 -Dworkdesk.testMode=true -Djava.awt.headless=true -cp "build\classes;build\test\classes;lib\mariadb-java-client-3.5.10.jar" laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo.NetworkIntegrationTests
exit /b %errorlevel%
