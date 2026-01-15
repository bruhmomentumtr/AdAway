# Changelog

## [Custom Fork - Initial Release] - 2026-01-15

### Added
- **Statistics Card:** New UI element on Home screen showing Total, Blocked, and Allowed requests with block percentage.
- **VpnStatistics Class:** Thread-safe singleton for managing VPN traffic stats with LiveData support.
- **Improved Notification:** VPN notification now shows real-time blocked request count.
- **Build Scripts:** Added `build.bat` and `smartbuild.ps1` for automated Windows build and signing workflow.
- **Scroll Fix:** Fixed home screen scrolling issues caused by layout constraints.

### Changed
- Refactored `HomeActivity` to support live statistics binding.
- Updated `VpnService` to broadcast statistics updates to the UI.
