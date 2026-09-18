@echo off
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "ANDROID_HOME=C:\Users\DANIE\AppData\Local\Android\Sdk"
set "ANDROID_SDK_ROOT=C:\Users\DANIE\AppData\Local\Android\Sdk"
"C:\Users\DANIE\AppData\Local\Android\Sdk\emulator\emulator.exe" -avd Medium_Phone -gpu swiftshader_indirect
