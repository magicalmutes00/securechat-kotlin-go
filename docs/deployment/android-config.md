# Android Configuration Guide

## Overview

This document describes how to configure the SecureChat Android application for development and testing against a local backend server.

---

## 1. Local Properties Configuration

Create `android/local.properties` with your backend server URLs:

```properties
# Backend API base URL (HTTP for development)
server.base.url=http://192.168.1.50:8080/api/v1

# WebSocket URL (WS for development)
server.ws.url=ws://192.168.1.50:8080/ws

# Optional: Cloudinary upload URL (if different from default)
# cloudinary.upload.url=https://api.cloudinary.com/v1_1/your_cloud_name
```

### Finding Your Server IP

**Windows:**
```powershell
ipconfig | findstr "IPv4"
```

**Linux/macOS:**
```bash
ip addr show | grep "inet " | grep -v 127.0.0.1
# or
hostname -I
```

Use the LAN IP (e.g., `192.168.1.50`, `10.0.0.50`), NOT `localhost` or `127.0.0.1`.

---

## 2. Network Security Configuration

The app includes `app/src/main/res/xml/network_security_config.xml` that permits cleartext HTTP for development on private networks:

```xml
<domain-config cleartextTrafficPermitted="true">
    <domain includeSubdomains="true">10.0.2.2</domain>        <!-- Android emulator -->
    <domain includeSubdomains="true">10.0.0.0/8</domain>       <!-- Private Class A -->
    <domain includeSubdomains="true">192.168.0.0/16</domain>   <!-- Private Class C -->
    <domain includeSubdomains="true">172.16.0.0/12</domain>    <!-- Private Class B -->
    <domain includeSubdomains="true">localhost</domain>
</domain-config>
```

### For Custom Network Ranges
Add your network range to the config if different from above.

### Production Build
For production releases, the network security config should only allow HTTPS:
```xml
<base-config cleartextTrafficPermitted="false">
    <trust-anchors>
        <certificates src="system" />
    </trust-anchors>
</base-config>
```

---

## 3. Building the App

### Debug Build (Development)
```bash
cd android
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

### Release Build (Production)
```bash
cd android
./gradlew assembleRelease
```

**Requires signing configuration** in `app/build.gradle.kts` or `keystore.properties`.

---

## 4. Running on Device/Emulator

### Physical Device (Recommended)
1. Enable **Developer Options** → **USB Debugging**
2. Connect via USB
3. Verify: `adb devices`
4. Install: `./gradlew installDebug`

### Emulator
```bash
# Create AVD (if not exists)
avdmanager create avd -n securechat_avd -k "system-images;android-34;google_apis;x86_64"

# Start emulator
emulator -avd securechat_avd

# Install
./gradlew installDebug
```

### Using ADB over WiFi (Android 11+)
```bash
# On device: Settings → Developer options → Wireless debugging
# Pair
adb pair <ip>:<port>
# Connect
adb connect <ip>:<port>
```

---

## 5. Testing Checklist

### Backend Connectivity
- [ ] `curl http://<server-ip>:8080/health` returns `{"status":"ok"}`
- [ ] `curl -X POST http://<server-ip>:8080/api/v1/auth/send-otp -d '{"phone_number":"+15551234567"}'` returns success
- [ ] WebSocket connection: `ws://<server-ip>:8080/ws`

### Authentication Flow
- [ ] Phone login screen loads
- [ ] OTP sent (check server logs for mock OTP)
- [ ] OTP verification works
- [ ] Tokens stored in EncryptedSharedPreferences
- [ ] Navigate to home screen

### Offline Functionality
- [ ] App works in airplane mode (shows cached conversations)
- [ ] Messages queued when offline
- [ ] Messages sync when back online

### Media Upload
- [ ] Image selection via Photo Picker
- [ ] Upload progress shown
- [ ] Message appears with media
- [ ] Thumbnail generated

### WebSocket Real-time
- [ ] Messages appear instantly on second device
- [ ] Typing indicators work
- [ ] Read receipts update
- [ ] Reconnection after network loss

---

## 6. Debugging

### View Logs
```bash
# All logs
adb logcat

# Filter by tag
adb logcat -s SecureChat

# Filter by package
adb logcat --pid=$(adb shell pidof -s com.securechat.debug)
```

### Network Traffic
```bash
# Enable network inspection (Android 9+)
# In app: Settings → Developer options → Enable network inspection

# Or use proxy (Charles, Proxyman, mitmproxy)
# Configure device WiFi → Proxy → Manual
```

### Database Inspection
```bash
# Copy Room database
adb exec-out run-as com.securechat.debug cat databases/securechat.db > securechat.db

# Open with DB Browser for SQLite
```

### Token Storage
```bash
# View encrypted prefs (requires root or backup)
adb backup -f backup.ab com.securechat.debug
# Extract and view with Android Backup Extractor
```

---

## 7. Common Issues

### "Cleartext HTTP traffic not permitted"
- Ensure `network_security_config.xml` includes your server IP range
- Check `local.properties` uses `http://` not `https://`
- Verify `android:usesCleartextTraffic="true"` in Manifest

### "Connection refused" / "Network unreachable"
- Verify server IP in `local.properties` matches laptop LAN IP
- Check Windows Firewall / UFW allows port 8080
- Ensure phone and laptop on same WiFi
- Try `adb shell ping <server-ip>`

### "WebSocket connection failed"
- Check server WebSocket endpoint: `ws://<ip>:8080/ws`
- Verify server logs for upgrade request
- Check proxy/firewall not blocking WebSocket upgrade

### "OTP not received" (Mock Provider)
- Check server console output for `[DEV MOCK OTP]`
- Verify phone number format: `+15551234567`

### Build Errors
```bash
# Clean build
./gradlew clean

# Invalidate caches (Android Studio)
File → Invalidate Caches / Restart

# Check Gradle version
./gradlew --version
```

### Keystore Issues (Release)
```bash
# Generate debug keystore (if missing)
keytool -genkey -v -keystore debug.keystore -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname "CN=Android Debug,O=Android,C=US"
```

---

## 8. Configuration Options

### Feature Flags (BuildConfig)
Add to `app/build.gradle.kts`:
```kotlin
buildConfigField("boolean", "ENABLE_AI_FEATURES", "false")
buildConfigField("boolean", "ENABLE_DEBUG_MENU", "true")
```

### Custom Application ID Suffixes
```kotlin
buildTypes {
    debug {
        applicationIdSuffix = ".debug"
        versionNameSuffix = "-debug"
    }
    staging {
        initWith(buildTypes.release)
        applicationIdSuffix = ".staging"
        versionNameSuffix = "-staging"
    }
}
```

---

## 9. Performance Testing

### Enable Profileable (Android 10+)
```xml
<!-- In AndroidManifest.xml -->
<application android:profileable="true" ...>
```

### Measure Startup Time
```bash
adb shell am start -W com.securechat.debug/com.securechat.MainActivity
```

### Memory Profiling
```bash
# In Android Studio: Profiler → Memory
# Or generate hprof
adb shell am dumpheap com.securechat.debug /data/local/tmp/heap.hprof
adb pull /data/local/tmp/heap.hprof
```

---

## 10. Release Checklist

- [ ] Update `versionCode` and `versionName` in `app/build.gradle.kts`
- [ ] Configure signing in `keystore.properties` (not committed)
- [ ] Run `./gradlew bundleRelease` for Play Store AAB
- [ ] Test release build on device
- [ ] Verify ProGuard/R8 rules don't break functionality
- [ ] Update `network_security_config.xml` for production (HTTPS only)
- [ ] Remove debug logging
- [ ] Test on multiple API levels (24, 28, 30, 33, 34)