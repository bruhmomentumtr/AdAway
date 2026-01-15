# AdAway (Custom Fork)

This project is a customized fork of the original [AdAway](https://github.com/AdAway/AdAway) ad blocker for Android. It includes enhanced statistics, UI improvements, and a streamlined build process for Windows environments.

## ✨ Key Features & Changes

### 📊 Live Statistics Dashboard
A new statistics card has been added to the home screen, providing real-time insights into your DNS traffic:
*   **Total Requests:** Count of all DNS requests handled by the local VPN.
*   **Blocked Requests:** Number of ads and trackers blocked.
*   **Block Rate:** Real-time percentage of blocked traffic.
*   **Reset Capability:** Easy access to reset statistics counters.

### 🔔 Enhanced VPN Notification
The persistent VPN notification now displays a **live counter** of blocked requests, keeping you informed without needing to open the app. Main statistics are synchronized in real-time between the background service and the UI.

### 🛠️ Advanced Build System (Windows)
Complete overhaul of the build process for Windows developers:
*   **build.bat / smartbuild.ps1:** Automated scripts to handle cleaning, building, and signing.
*   **Automatic Keystore Management:** The build system automatically generates a debug/release keystore if one doesn't exist, ensuring painless first-time builds.
*   **Dependency Handling:** Integrated NDK and SDK checks.

### 🎯 Enhanced UI/UX
*   **Simplified Main Menu:** Removed redundant Log, Help, and Support cards for a cleaner interface.
*   **Help in Drawer Menu:** Quick access to help via the hamburger menu.
*   **One-Tap Statistics Access:** Tap the statistics card to instantly view full DNS logs.

### ⚙️ Configurable Logging
*   **Adjustable Refresh Interval:** Set recent logs update frequency (0-60 seconds) in VPN Settings.
*   **Disable Logging:** Set interval to 0 to completely disable DNS logging (like original AdAway).
*   **Manual Refresh:** Dedicated button to manually update recent logs on demand.

## 🚀 Planned Features
*   **Advanced DNS Log UI:** Color-coded logs (Red for blocked, Green for allowed), search/filtering by domain, and sticky statistics header.

## 📦 Building
To build this project on Windows:
1.  Run `build.bat`.
2.  Choose your build type (Debug/Release).
3.  The script will handle Gradle, Keystore generation, and APK signing automatically.

## ℹ️ Original Project
AdAway is an open source ad blocker for Android using the hosts file and local vpn.
For more information about the original project, visit [adaway.org](https://adaway.org).

## 📄 License
This fork retains the original **GPLv3+** license. See [LICENSE](LICENSE) for details.
