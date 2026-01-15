# Releasing

This project uses a custom Windows build system.

## How to Build & Release

1.  Run `build.bat` in the root directory.
2.  Select **Release Build** from the menu.
3.  The script will automatically:
    *   Clean the project.
    *   Generate a keystore (if explicitly requested or missing).
    *   Build the signed APK using `smartbuild.ps1`.
4.  The output APK will be located in `app\build\outputs\apk\release\app-release.apk`.

See [README.md](README.md) for more details.
