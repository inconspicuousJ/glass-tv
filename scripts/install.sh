#!/bin/bash
# Installs or updates Glass on an Android TV / Fire TV over network ADB.
# Usage: scripts/install.sh <TV IP> [path to APK]
# Requires `adb` on the PATH and ADB debugging enabled on the TV.
set -e
IP="${1:?Usage: scripts/install.sh <TV IP> [APK]}"
APK="${2:-app/build/outputs/apk/release/app-release.apk}"
T="$IP:5555"
adb connect "$T"
sleep 2
adb -s "$T" install -r "$APK"
# Optional but recommended: Wi-Fi/Bluetooth scanning without a permission prompt,
# custom wallpaper / fonts, and animation speed from the Control Center.
for p in ACCESS_COARSE_LOCATION ACCESS_FINE_LOCATION NEARBY_WIFI_DEVICES READ_EXTERNAL_STORAGE WRITE_SECURE_SETTINGS; do
  adb -s "$T" shell pm grant com.fares.glass "android.permission.$p" 2>/dev/null || true
done
# Android 6-12: the system only returns Wi-Fi scan results while location is on.
adb -s "$T" shell cmd location set-location-enabled true 2>/dev/null \
  || adb -s "$T" shell settings put secure location_mode 3 2>/dev/null || true
adb -s "$T" shell am start -n com.fares.glass/.MainActivity
echo "Glass is installed. Set it as your home screen (see README)."
