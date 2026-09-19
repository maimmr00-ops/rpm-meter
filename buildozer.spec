[app]

# Название приложения
title = RPM Meter
package.name = rpmmeter
package.domain = org.test

# Исходный код
source.dir = .
source.include_exts = py,png,jpg,kv,atlas

version = 0.1

# Зависимости и фиксация Cython
requirements = python3,kivy==2.3.0,cython==0.29.33

orientation = portrait
fullscreen = 0

[buildozer]
log_level = 2
warn_on_root = 1

[app:android]
# Параметры Android SDK/NDK
android.api = 33
android.minapi = 21
android.ndk = 25b
android.accept_sdk_license = True
android.archs = arm64-v8a, armeabi-v7a
android.allow_backup = True
