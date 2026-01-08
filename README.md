# Reference Lookup

<!-- Plugin description -->
A fast and easy-to-use reference lookup tool for IntelliJ IDEA.

Features:
- **Reference Browser Tool Window**: Browse references by category in a tree view.
- **Quick Access**: Right-click any selected text to look up references.
- **Fuzzy Search**: Quickly find what you need with fuzzy matching.
- **Performance**: Optimized for large datasets with asynchronous loading.
<!-- Plugin description end -->

## Installation

### From JetBrains Marketplace

1. Open IntelliJ IDEA
2. Go to **Settings/Preferences** > **Plugins** > **Marketplace**
3. Search for "Reference Lookup"
4. Click **Install**

### Manual Installation

1. Download the latest release `.zip` file from [GitHub Releases](https://github.com/zachholt/intellij-reference-lookup/releases)
2. In IntelliJ IDEA, go to **Settings/Preferences** > **Plugins**
3. Click the gear icon and select **Install Plugin from Disk...**
4. Select the downloaded `.zip` file

## Configuration

### Reference File Setup

1. Go to **Settings/Preferences** > **Tools** > **Reference Lookup**
2. Set the path to your Java reference file

Your reference file should be a Java class with static final constants. Example:

```java
package com.example.reference;

public class Reference {

    // HTTP Status Codes
    /** OK - The request has succeeded (200) */
    public static final int HTTP_OK = 200;

    /** Not Found - The server can not find the requested resource */
    public static final int HTTP_NOT_FOUND = 404;

    // Database Error Codes
    public static final String DB_CONNECTION_FAILED = "DB001";
    public static final String DB_QUERY_TIMEOUT = "DB002";

    // Application Constants
    public static final int MAX_RETRY_ATTEMPTS = 3;
    public static final long DEFAULT_TIMEOUT_MS = 5000L;
}
```

The plugin parses:
- **Constant names** (e.g., `HTTP_OK`, `DB_CONNECTION_FAILED`)
- **Values** (e.g., `200`, `"DB001"`)
- **Javadoc comments** for descriptions
- **Categories** based on comment sections (e.g., `// HTTP Status Codes`)

### Jira Integration (Optional)

To use the "Create SQL from Jira Ticket" feature:

1. Go to **Settings/Preferences** > **Tools** > **Reference Lookup**
2. Configure:
   - **Jira Base URL**: Your Jira instance URL (e.g., `https://yourcompany.atlassian.net`)
   - **Jira Email**: Your Jira account email
   - **Jira API Token**: Generate at [Atlassian API Tokens](https://id.atlassian.com/manage-profile/security/api-tokens)

## Usage

### Reference Browser

1. Open the **Reference Browser** tool window (right side panel)
2. Browse references organized by category in the tree view
3. Use the search bar for fuzzy matching across all references

### Quick Lookup (Context Menu)

1. Select text in the editor
2. Right-click to open the context menu
3. Choose **Reference Lookup** to see matching references
4. Click a reference to copy its value

### Keyboard Shortcut

Press `Ctrl+Shift+L` (or `Cmd+Shift+L` on macOS) to open the reference search dialog.

### Create SQL from Jira

1. Right-click on a folder in the Project view
2. Select **Create SQL from Jira Ticket**
3. Enter a Jira ticket URL or key (e.g., `PROJ-123`)
4. A new SQL file is created with the ticket information as header comments

## Development

### Prerequisites

- Java 21+
- IntelliJ IDEA 2024.2+ (for running/debugging the plugin)

### Building

```bash
./gradlew buildPlugin
```

The built plugin will be in `build/distributions/`.

### Running in Development

```bash
./gradlew runIde
```

This launches a sandboxed IntelliJ IDEA instance with the plugin installed.

### Local IntelliJ Installation (Optional)

To avoid downloading IntelliJ during builds, create a `local.properties` file:

```properties
# macOS
intellij.localPath=/Applications/IntelliJ IDEA CE.app

# Windows
# intellij.localPath=C:\Program Files\JetBrains\IntelliJ IDEA 2024.2

# Linux
# intellij.localPath=/opt/intellij-idea-community
```

### Running Tests

```bash
./gradlew check
```

## Release Process

### Creating a Release

1. Update `pluginVersion` in `gradle.properties`
2. Update `CHANGELOG.md` with release notes under a `## [X.Y.Z]` heading
3. Commit and push to `main`
4. Go to **Actions** > **Trigger Release** workflow
5. Enter the version number and run the workflow

The workflow will:
- Validate the version matches `gradle.properties`
- Build the plugin
- Create a GitHub release with the plugin `.zip` attached
- Publish to JetBrains Marketplace (if secrets are configured)

### GitHub Actions Workflows

| Workflow | Trigger | Description |
|----------|---------|-------------|
| **Build** | Push to `main`, PRs | Builds, tests, and creates draft release |
| **Release** | GitHub Release published | Publishes to JetBrains Marketplace |
| **Trigger Release** | Manual | Creates a tagged release with artifact |

## Project Structure

```
.
├── src/main/java/com/zachholt/referencelookup/
│   ├── action/              # Action classes (Jira integration)
│   ├── model/               # Data models
│   ├── parser/              # Java file parsers
│   ├── service/             # Services (data, Jira)
│   ├── settings/            # Plugin settings
│   ├── ui/                  # UI components
│   ├── LookupAction.java    # Keyboard shortcut action
│   ├── QuickLookupAction.java # Context menu action
│   └── ReferenceBundle.java # i18n resources
├── src/main/resources/
│   ├── META-INF/plugin.xml  # Plugin configuration
│   └── messages/            # Localization
├── sample-reference/        # Example reference file
├── build.gradle.kts         # Build configuration
└── gradle.properties        # Plugin metadata
```

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
