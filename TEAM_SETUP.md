# Team Setup Guide for Reference Lookup Plugin

This guide helps your team set up and use the Reference Lookup plugin with your shared Reference.java file.

## Quick Start

1. **Get the plugin**: Download the latest `reference-lookup-x.x.x.zip` from your team's repository or shared drive

2. **Install in IntelliJ**:
   - Open IntelliJ IDEA
   - Go to `Settings` → `Plugins` → `⚙️` → `Install Plugin from Disk`
   - Select the ZIP file
   - Restart IntelliJ IDEA

3. **Set up your Reference.java**:
   ```bash
   # Windows
   mkdir %USERPROFILE%\.reference-lookup
   copy \\network\share\Reference.java %USERPROFILE%\.reference-lookup\

   # Mac/Linux
   mkdir -p ~/.reference-lookup
   cp /network/share/Reference.java ~/.reference-lookup/
   ```

## How to Use

1. **Select any code/text** in the editor
2. **Right-click** and choose "Lookup Reference" OR press `Ctrl+Shift+L`
3. **Search** for your reference
4. **Press Enter** or double-click to copy the code

## Customization Options

### Using a Different Location

If your team prefers a different location for Reference.java, you can:

1. Set an environment variable:
   ```bash
   export REFERENCE_LOOKUP_PATH=/path/to/your/Reference.java
   ```

2. Or create a symbolic link:
   ```bash
   ln -s /network/share/Reference.java ~/.reference-lookup/Reference.java
   ```

### Performance Tips

For files with 15,000+ constants:
- The first search might take a second to load
- Subsequent searches are instant (cached in memory)
- The plugin only loads when you first use it

## Troubleshooting

**Plugin doesn't find references:**
- Check that `~/.reference-lookup/Reference.java` exists
- Ensure the file contains `public static final` constants
- Check IntelliJ logs: `Help` → `Show Log in Explorer/Finder`

**Search is slow:**
- Normal for first search with large files
- If consistently slow, check available memory in IntelliJ

**Can't install plugin:**
- Ensure you have IntelliJ IDEA 2023.3 or newer
- Check that you're using the correct ZIP file (not the source code)

## Support

For issues or questions, contact your team lead or check the internal repository.