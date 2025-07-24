# Changelog

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