@echo off
REM Use Android Studio's bundled JDK when JAVA_HOME is not set globally.
if not defined JAVA_HOME (
  set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
)
call "%~dp0gradlew.bat" :app:assembleDebug %*
