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
# Фиксация стабильных версий API и Build-Tools (избавляет от ошибки с aidl)
android.api = 33
android.minapi = 21
android.sdk = 33
android.build_tools_version = 33.0.2
android.ndk = 25b

# Автоматически принимать лицензии SDK
android.accept_sdk_license = True

# Архитектуры процессоров
android.archs = arm64-v8a, armeabi-v7a

# Дополнительные разрешения
android.allow_backup = True
