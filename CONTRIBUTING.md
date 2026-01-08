# Contributing to Reference Lookup

Thank you for your interest in contributing to Reference Lookup! This document provides guidelines and instructions for contributing.

## Getting Started

### Prerequisites

- **Java 21+** - Required for building and running
- **IntelliJ IDEA 2024.2+** - For development and testing
- **Git** - For version control

### Development Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/zachholt/intellij-reference-lookup.git
   cd intellij-reference-lookup
   ```

2. **Open in IntelliJ IDEA**
   - Open IntelliJ IDEA
   - Select **File > Open** and choose the project directory
   - Wait for Gradle to sync

3. **(Optional) Configure local IntelliJ path**
   
   Create `local.properties` to use your local IntelliJ installation:
   ```properties
   intellij.localPath=/Applications/IntelliJ IDEA CE.app
   ```

4. **Run the plugin**
   ```bash
   ./gradlew runIde
   ```

## Development Workflow

### Building

```bash
# Build the plugin
./gradlew buildPlugin

# Run tests
./gradlew check

# Run with a fresh IDE instance
./gradlew runIde
```

### Code Style

- Follow standard Java conventions
- Use meaningful variable and method names
- Add Javadoc comments for public APIs
- Keep methods focused and small

### Project Structure

```
src/main/java/com/zachholt/referencelookup/
├── action/      # IntelliJ actions
├── model/       # Data models
├── parser/      # File parsers
├── service/     # Business logic services
├── settings/    # Plugin configuration
└── ui/          # UI components
```

## Making Changes

### Branch Naming

- `feature/description` - New features
- `fix/description` - Bug fixes
- `docs/description` - Documentation updates

### Commit Messages

Write clear, descriptive commit messages:
- Use present tense ("Add feature" not "Added feature")
- Keep the first line under 72 characters
- Reference issues when applicable

Example:
```
Add fuzzy search to reference browser

- Implement fuzzy matching algorithm
- Add search score ranking
- Update UI to show match highlights

Fixes #123
```

### Pull Requests

1. Create a feature branch from `main`
2. Make your changes
3. Run tests: `./gradlew check`
4. Build the plugin: `./gradlew buildPlugin`
5. Push and create a PR

### PR Checklist

- [ ] Code compiles without errors
- [ ] Tests pass
- [ ] New features have tests
- [ ] Documentation updated if needed
- [ ] PR description explains the changes

## Reporting Issues

### Bug Reports

Include:
- IntelliJ IDEA version
- Plugin version
- Steps to reproduce
- Expected vs actual behavior
- Error messages or stack traces

### Feature Requests

Include:
- Clear description of the feature
- Use case / why it would be helpful
- Any implementation ideas

## Release Process

Releases are handled by maintainers:

1. Update version in `gradle.properties`
2. Update `CHANGELOG.md`
3. Run the **Trigger Release** workflow

## Questions?

Open an issue on GitHub for questions about contributing.
