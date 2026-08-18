# Fix Dependency Resolution Errors (Ktor & Coil)

The project is currently failing to build because several dependencies cannot be resolved. This is due to incorrect artifact names for Ktor 3.x and a non-existent artifact for Coil 2.6.0.

## User Review Required

> [!IMPORTANT]
> I am downgrading the Ktor version to `3.0.0` to ensure stability and availability, as `3.0.1` might not be fully synced to all repositories yet. I am also removing the non-existent Coil artifact; Coil 2.x handles OkHttp integration internally.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///C:/Users/Prath/Desktop/Projects/securechat/android/gradle/libs.versions.toml)
- Update `ktor` version to `3.0.0`.
- Remove `ktor-client-auth-jwt` (JWT support is included in `ktor-client-auth` for clients).
- Rename `ktor-websocket` to `ktor-client-websockets`.
- Remove `coil-network-okhttp` (not available in Coil 2.x).

#### [MODIFY] [app/build.gradle.kts](file:///C:/Users/Prath/Desktop/Projects/securechat/android/app/build.gradle.kts)
- Remove `libs.ktor.client.auth.jwt` and `libs.coil.network.okhttp` from dependencies.
- Update `libs.ktor.websocket` reference to `libs.ktor.client.websockets` (after renaming in TOML).

### Source Code Cleanup

#### [MODIFY] [NetworkModule.kt](file:///C:/Users/Prath/Desktop/Projects/securechat/android/app/src/main/java/com/securechat/core/network/NetworkModule.kt)
- Remove the unused and unresolvable import `io.ktor.client.auth.jwt.*`.
- *Note: The HttpClient configuration in this file uses an older Ktor 1.x syntax (`JsonFeature`). I will update it to the modern `ContentNegotiation` syntax to ensure it compiles with Ktor 3.x.*

#### [MODIFY] [WebSocketClient.kt](file:///C:/Users/Prath/Desktop/Projects/securechat/android/app/src/main/java/com/securechat/data/remote/websocket/WebSocketClient.kt)
- Ensure imports match the new `ktor-client-websockets` artifact (usually no change needed to code, just the dependency).

## Verification Plan

### Automated Tests
- Run `./gradlew assembleDebug` to verify that all dependencies now resolve correctly and the project builds.
- Run a Gradle sync to verify the IDE no longer shows resolution errors.
