<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Custom Splash Screen Changelog

## Unreleased

### Added

- Two splash screens (`airwaves-1.png`, `airwaves-2.png`) from @antonarhipov 
- Duke, Kodee, and Runzo transparent splash screen (`crew.png`) 

## 0.2.1 - 2026-11-12

### Added

- Bundled splash image gallery with two included images, alongside the existing local image option.
- Automatic discovery of bundled PNG images, including subdirectories and packaged plugins.
- Documentation for selecting and contributing bundled splash images.

### Changed

- Preview now displays the IDE's actual splash screen using the selected image, without applying settings or changing the startup splash cache.
- Rename the settings page to "Custom Splash Screen".

### Fixed

- Remove the IDE-version restriction and support newer splash cache formats while retaining legacy compatibility.
- Refresh the splash cache when the welcome screen is displayed or the plugin is loaded, and remove custom cache entries before the plugin is unloaded.
- Keep splash previews above the Settings dialog.
- Dispose splash previews on click, key input, focus loss, replacement, or when Settings closes.
