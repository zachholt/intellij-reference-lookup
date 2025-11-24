# Changelog

## [2.2.3] - 2025-11-24

### Fixed
- **CI/CD**: Updated GitHub Actions workflow to use Java 21, fixing build failures caused by the project's upgrade to Java 21.

## [2.2.2] - 2025-11-24

### Fixed
- **Compatibility**: Removed upper version limit (`until-build`) to support IntelliJ IDEA 2024.3 and upcoming 2025.x releases.

## [2.2.1] - 2025-11-24

### Fixed
- **Java Parser**: Fixed regex to correctly identify primitive types (`int`, `boolean`, etc.) in constants.
- **JavaDoc Parsing**: Fixed issue where single-line JavaDoc comments (`/** ... */`) were incorrectly reading preceding lines.

### Added
- **Performance Logging**: Added detailed timing logs for data loading, indexing, and slow search queries to `idea.log`.
- **Local Development**: Added support for `local.properties` to configure local IntelliJ paths without affecting git.

### Changed
- **Build System**: Updated project to use Java 21 for compatibility with IntelliJ 2024.3+.
- **Dependencies**: Fixed missing `instrumentationTools` and repository configurations for local builds.

## [2.0.0] - 2024-07-24

### Added
- **Reference Browser Tool Window**: New persistent window for browsing references
  - Tabbed interface with "All References" and "By Category" views
  - Tree view organization by categories
  - Real-time search filtering
  - Details panel showing full reference information
  - Status bar showing filtered results count
- Context menu option "Open Reference Browser" for quick access

### Changed
- Replaced freezing popup window with stable tool window
- Keyboard shortcut (Ctrl+Shift+L) now opens the Reference Browser
- "Search All References..." menu option now opens the tool window
- Improved overall UI responsiveness

### Fixed
- Fixed freezing issues when searching references
- Fixed context menu display for long descriptions
- Fixed package structure inconsistencies

### Removed
- Removed problematic ReferenceLookupPopup that was causing freezes

## [1.0.0] - Previous Release
- Initial release with popup-based reference lookup
- Context menu integration
- Quick lookup with top 10 matches
- Fuzzy search support
- Keyboard shortcuts