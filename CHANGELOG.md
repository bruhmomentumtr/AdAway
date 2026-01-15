# Changelog

## [Custom Fork - Initial Release] - 2026-01-15

### Added
- **Statistics Card:** New UI element on Home screen showing Total, Blocked, and Allowed requests with block percentage.
- **Recent Logs Display:** Statistics card now shows last 5 blocked and last 5 allowed domains with auto-refresh.
- **VpnStatistics Class:** Thread-safe singleton for managing VPN traffic stats with LiveData support.
- **Improved Notification:** VPN notification now shows real-time blocked request count.
- **Build Scripts:** Added `build.bat` and `smartbuild.ps1` for automated Windows build and signing workflow.
- **Scroll Fix:** Fixed home screen scrolling issues caused by layout constraints.
- **UI Simplification:** Removed Log, Help, and Support cards from main menu.
- **Drawer Menu Enhancement:** Added Help option to hamburger menu.
- **Configurable Logging:** VPN Settings now include adjustable refresh interval (0-60s) for recent logs.
- **Manual Refresh:** Added refresh button to statistics card for on-demand log updates.
- **Auto-Logging Control:** DNS logging automatically enables/disables based on refresh interval preference.

### Changed
- Refactored `HomeActivity` to support live statistics binding.
- Updated `VpnService` to broadcast statistics updates to the UI.
