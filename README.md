# Reference Lookup IntelliJ Plugin

[![Build](https://github.com/zachholt/intellij-reference-lookup/workflows/Build/badge.svg)](https://github.com/zachholt/intellij-reference-lookup/actions)

A fast and easy-to-use reference lookup tool for IntelliJ IDEA that allows you to quickly search through reference codes and their descriptions. Perfect for developers working with large codebases containing thousands of constants, error codes, or reference values.

This plugin is designed for internal team use and supports direct parsing of Java constant files.

## Features

- **NEW: Reference Browser Tool Window**: Persistent window with tabbed interface
  - List view showing all references with search
  - Tree view organized by categories
  - Details panel for complete reference information
- **Context Menu Integration**: Right-click on any selected text to look up references
- **Quick Access**: View top 10 matches directly in the context menu
- **Keyboard Shortcuts**: Use `Ctrl+Shift+L` to open the Reference Browser
- **Smart Search**: Supports partial matches and fuzzy search
- **Copy to Clipboard**: Click on quick matches to copy code instantly

## Team Installation

### Option 1: Install from ZIP file (Recommended)

1. Download the latest plugin ZIP from the [Releases](https://github.com/zachholt/intellij-reference-lookup/releases) page
   
2. Install in IntelliJ IDEA:
   - Go to Settings → Plugins → ⚙️ → Install Plugin from Disk
   - Select the downloaded ZIP file
   - Restart IntelliJ IDEA

### Option 2: Build from source

1. Clone the repository:
   ```bash
   git clone https://github.com/zachholt/intellij-reference-lookup.git
   cd intellij-reference-lookup
   ```

2. Build the plugin:
   ```bash
   ./gradlew buildPlugin
   ```

3. The plugin ZIP will be created in `build/distributions/`

4. Install the ZIP file as described in Option 1

## Usage

1. **Using Context Menu**:
   - Select any text in the editor
   - Right-click and choose "Reference Lookup"
   - View quick matches in the submenu or click "Open Reference Browser"

2. **Using Keyboard Shortcut**:
   - Press `Ctrl+Shift+L` to open the Reference Browser

3. **In the Reference Browser**:
   - Use the search field to filter references
   - Switch between "All References" and "By Category" tabs
   - Double-click any reference to insert it
   - View full details in the bottom panel

## Custom Reference Data

The plugin now supports **Java constant files directly**! This is the preferred method.

### Option 1: Java Constants File (Recommended)

Place your Reference.java file at:
```
~/.reference-lookup/Reference.java
```

The plugin will automatically parse:
- `public static final` constants (String, int, long, etc.)
- JavaDoc comments as descriptions
- Single-line comments (`//`) as descriptions
- Constant names as the lookup codes

Example format:
```java
public class Reference {
    /**
     * The request has succeeded.
     */
    public static final int HTTP_OK = 200;
    
    public static final String ERROR_AUTH_FAILED = "AUTH001"; // Authentication failed
    
    // Database connection timeout
    public static final String DB_TIMEOUT = "DB_CONN_TIMEOUT";
}
```

### Option 2: JSON File

Alternatively, create a JSON file at:
```
~/.reference-lookup/references.json
```

The format should be:
```json
[
  {
    "code": "YOUR_CODE",
    "description": "Description of what this code means",
    "category": "Optional category",
    "tags": ["tag1", "tag2"]
  }
]
```

## Team Setup

### Setting up the Reference File

Each team member needs to place the team's Reference.java file in their home directory:

1. Create the directory:
   ```bash
   mkdir -p ~/.reference-lookup
   ```

2. Copy your team's Reference.java file:
   ```bash
   cp /path/to/team/Reference.java ~/.reference-lookup/Reference.java
   ```

3. The plugin will automatically:
   - Parse all `public static final` constants
   - Extract JavaDoc and inline comments as descriptions
   - Create searchable entries for each constant
   - Support fuzzy search on constant names

### Alternative: Shared Network Location

You can also modify the plugin to read from a shared network location by updating the path in `ReferenceDataService.java`.

## Development

To run the plugin in a sandbox IDE:
```bash
./gradlew runIde
```

## Requirements

- IntelliJ IDEA 2024.2 or later
- Java 17 or later

### Building with a local IntelliJ install (recommended)
To avoid downloading the IntelliJ distribution during builds, point the Gradle IntelliJ Platform plugin to your installed IDE:
```bash
export INTELLIJ_LOCAL_PATH="/Applications/IntelliJ IDEA.app" # adjust for your OS
# or: ./gradlew build -Pintellij.localPath=/path/to/IDE
./gradlew build -x buildSearchableOptions
```
