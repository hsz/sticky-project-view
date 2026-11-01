<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Sticky Project View Changelog

## [Unreleased]

## [0.3.0] - 2026-11-01

### Fixed

- Keep sticky parents expanded when clicked with "Open directories with single click" enabled.

## [0.2.0] - 2026-10-16

### Added

- Add toggle action to project view toolbar

### Fixed

- Preserve source and excluded directory backgrounds around selected sticky rows.

## [0.1.0] - 2026-10-15

### Added

- Add a keyboard-accessible sticky-ancestor chooser.
- Allow configuring sticky content height between 10% and 90% of the viewport.
- Add an IDE-wide, persistent Project View toggle for sticky rows.
- Show full-path tooltips for sticky rows.
- Add donation links in settings and a Project View sponsor action, plus a delayed reminder with snooze and permanent dismissal.
- Add GitHub funding links and document GitHub Sponsors, Ko-fi, and PayPal support.
- Include a free, sponsor-supported notice in Marketplace release notes.
- Open the plugin documentation from settings Help and include sticky preferences in the UI category of Settings Sync.

### Changed

- Guard ancestor action tests against lost project context and shared presentation state.
- Replace deprecated action-event construction in ancestor-navigation tests.
- Run Starter/Driver UI tests against the configured IDEA 2025.3.6.1 baseline.
- Update GitHub Actions and upload installable plugin ZIP artifacts.
- Update Gradle to 9.8.1 and IntelliJ Platform Gradle Plugin to 2.19.0, with an explicit Java 21 toolchain.
- Generate the Marketplace description, including donation links, from the README.

### Fixed

- Highlight drag-and-drop targets in the sticky stack with the native Project View feedback.
- Route drag-and-drop over sticky directories to the highlighted directory instead of the covered tree node ([#15](https://github.com/hsz/sticky-project-view/issues/15)).
- Prevent a `NullPointerException` when initializing sticky rows before the Project View tree is ready ([#16](https://github.com/hsz/sticky-project-view/issues/16)).
- Coalesce tree-model and expansion bursts into one deferred sticky refresh, avoiding repeated layout queries while native tree listeners are updating.
- Keep programmatically selected files visible below the sticky stack near subtree boundaries.
- Avoid full descendant scans and unnecessary debug formatting when scrolling large Project View trees.
- Keep sticky rows correctly positioned in trees with variable row heights.
- Use the actual plugin version for packaged notes and detect release changelog changes before creating a pull request.
- Restore sticky-overlay mouse interactions.
- Fix horizontally scrolled sticky rows and stale rows after selection changes.
- Apply reloaded or synchronized preferences to open projects, without publishing duplicate updates.

## [0.0.2] - 2026-04-13

### Added

- Click-to-reveal navigation for sticky items.
- UI integration tests based on IntelliJ UI Testing Starter/Driver.

### Changed

- Preserved native Project View rendering in the sticky stack.
- Added a subtle separator and shadow for the sticky area.

### Fixed

- Fixed nested sticky row handoff and click-to-reveal positioning.
- Fixed theme handling for the stack edge so the border stays appropriate in dark themes.

## [0.0.1] - 2025-12-02

### Added

- Initial Sticky Project View implementation.
- Sticky parent directories for expanded Project View nodes while scrolling.

[Unreleased]: https://github.com/hsz/sticky-project-view/compare/v0.3.0...HEAD
[0.3.0]: https://github.com/hsz/sticky-project-view/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/hsz/sticky-project-view/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/hsz/sticky-project-view/compare/v0.0.2...v0.1.0
[0.0.2]: https://github.com/hsz/sticky-project-view/compare/v0.0.1...v0.0.2
[0.0.1]: https://github.com/hsz/sticky-project-view/commits/v0.0.1
