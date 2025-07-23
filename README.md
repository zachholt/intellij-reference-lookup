# Reference Lookup IntelliJ Plugin

[![Build](https://github.com/zachholt/intellij-reference-lookup/workflows/Build/badge.svg)](https://github.com/zachholt/intellij-reference-lookup/actions)
[![Version](https://img.shields.io/jetbrains/plugin/v/PLUGIN_ID.svg)](https://plugins.jetbrains.com/plugin/PLUGIN_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/PLUGIN_ID.svg)](https://plugins.jetbrains.com/plugin/PLUGIN_ID)

A fast and easy-to-use reference lookup tool for IntelliJ IDEA that allows you to quickly search through reference codes and their descriptions. Perfect for developers working with large codebases containing thousands of constants, error codes, or reference values.

## Features

- **Context Menu Integration**: Right-click on any selected text to look up references
- **Quick Search Popup**: Fast, responsive search with fuzzy matching
- **Keyboard Shortcuts**: Use `Ctrl+Shift+L` to quickly open the lookup
- **Smart Search**: Supports partial matches and fuzzy search
- **Copy to Clipboard**: Double-click or press Enter to copy the code

## Installation

1. Build the plugin:
   ```bash
   ./gradlew buildPlugin
   ```

2. The plugin JAR will be created in `build/distributions/`

3. Install in IntelliJ IDEA:
   - Go to Settings → Plugins → ⚙️ → Install Plugin from Disk
   - Select the JAR file
   - Restart IntelliJ IDEA

## Usage

1. **Using Context Menu**:
   - Select any text in the editor
   - Right-click and choose "Lookup Reference"
   - Search for your reference in the popup

2. **Using Keyboard Shortcut**:
   - Select text and press `Ctrl+Shift+L`

3. **In the Popup**:
   - Type to search (supports fuzzy matching)
   - Use arrow keys to navigate results
   - Press Enter or double-click to copy the code
   - Press Escape to close

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

## Using Your Existing Reference.java

Since you have a Reference.java file with 15,220 lines of constants:

1. Copy your Reference.java file to:
   ```
   ~/.reference-lookup/Reference.java
   ```

2. The plugin will automatically:
   - Parse all `public static final` constants
   - Extract JavaDoc and inline comments as descriptions
   - Create searchable entries for each constant
   - Support fuzzy search on constant names

3. No conversion needed! The plugin works directly with your Java file.

## Development

To run the plugin in a sandbox IDE:
```bash
./gradlew runIde
```

## Requirements

- IntelliJ IDEA 2023.3 or later
- Java 17 or later