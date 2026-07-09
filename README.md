# ShotRena . *Live Screenshot Renamer*

**ShotRena** is a system-level Android utility built in Kotlin that actively monitors live device telemetry for screenshot capture events. Upon detection, it triggers a background service that prompts the user for immediate file renaming, streamlining media classification and keyword search retrieval.

## 📥 Installation & Setup
The latest compiled APK is available in the [Releases](../../releases) tab.

### ⚙️ How to Activate (Quick Settings Tile)
*Note: ShotRena operates purely as a background system utility. It does not install a standard app drawer icon.*

1. Install the APK on your Android device.
2. Swipe down twice from the top of your screen to open the **Quick Settings** panel.
3. Tap the **Edit (Pencil)** icon.
4. Scroll down, locate the **ShotRena** tile, and drag it into your active tiles.
5. Tap the tile to grant necessary permissions and start the background monitoring service.

## 🏗️ Technical Architecture
* **Background Telemetry:** Utilizes Android `ContentObserver` to monitor system MediaStore URIs for new image generation in real-time.
* **Foreground Service:** Runs a persistent, battery-optimized foreground service (`ShotRenaService`) to ensure the OS does not kill the monitor during deep sleep.
* **System Overlay:** Employs `SYSTEM_ALERT_WINDOW` to seamlessly present the renaming dialog over any currently running application without disrupting the user's workflow.

## 🔐 Security & Privacy (100% Offline)
ShotRena was designed with absolute data privacy in mind. 
* **No Internet Permission:** A review of the `AndroidManifest.xml` confirms the absence of the `android.permission.INTERNET` flag. The application physically cannot transmit data, metadata, or images off the device.
* **Local Sandboxing:** All renaming operations happen purely on the local file system.

## 🛠️ Built With
* Kotlin
* Android SDK (Min API 24 / Target API 36)
* Gradle Kotlin DSL (`build.gradle.kts`)
