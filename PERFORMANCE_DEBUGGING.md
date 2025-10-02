# Performance Debugging Guide for IntelliJ Plugins

## 1. Built-in IntelliJ Profiling

### Run Plugin in Development Mode
```bash
./gradlew runIde
```
This starts a sandboxed IntelliJ instance with your plugin installed.

### Enable Internal Mode
In the dev IntelliJ instance:
1. Press `Ctrl+Shift+A` (or `Cmd+Shift+A` on Mac)
2. Type "Registry" and select "Registry..."
3. Enable these flags:
   - `ide.enable.internal.mode` - Shows internal menus
   - `idea.log.perf.stats` - Logs performance stats

### View Performance Metrics
With internal mode enabled:
- **Help → Diagnostic Tools → Activity Monitor** - See CPU/memory per plugin
- **Help → Diagnostic Tools → Analysis** - Thread dumps and memory analysis
- **View → Tool Windows → Profiler** (if available)

## 2. Logging Strategy

### Add Performance Logging
```java
private static final Logger LOG = Logger.getInstance(YourClass.class);

long start = System.currentTimeMillis();
// Your operation
long duration = System.currentTimeMillis() - start;
LOG.info("Operation took " + duration + "ms");
```

### View Logs
- **Help → Show Log in Finder/Explorer** (in dev instance)
- Look for `idea.log`
- Filter for your plugin's package name

## 3. Memory Profiling

### Using JProfiler/YourKit
1. Install profiler
2. Run with profiler attached:
```bash
./gradlew runIde --debug-jvm
```
3. Connect profiler to JVM process
4. Look for:
   - Memory leaks (growing heap after GC)
   - Object allocation hotspots
   - Thread contention

### Using VisualVM (Free)
1. Download VisualVM
2. Run plugin in dev mode
3. Attach VisualVM to the process
4. Monitor:
   - Heap usage over time
   - Thread activity
   - CPU sampling

## 4. IntelliJ Specific Metrics

### Check Thread Usage
```java
// Avoid blocking EDT (Event Dispatch Thread)
ApplicationManager.getApplication().executeOnPooledThread(() -> {
    // Long-running operation
});
```

### Verify No EDT Blocking
In dev instance logs, look for:
- `Slow operations are prohibited on EDT`
- `Long EDT operation` warnings

### Check Service Loading Time
Add logging to service constructor:
```java
@Service(Service.Level.PROJECT)
public final class ReferenceDataService {
    private static final Logger LOG = Logger.getInstance(ReferenceDataService.class);

    public ReferenceDataService() {
        LOG.info("ReferenceDataService initialized");
    }
}
```

## 5. Automated Performance Tests

### Gradle Task
Add to `build.gradle.kts`:
```kotlin
tasks.register("perfTest") {
    doLast {
        println("Running performance tests...")
        // Add custom perf tests
    }
}
```

### Measure Startup Impact
Check `idea.log` for plugin initialization time:
```
grep "ReferenceDataService" idea.log
```

## 6. Key Metrics to Monitor

### Memory
- **Heap size**: Should remain stable after GC
- **Metaspace**: Should not grow indefinitely
- **Object count**: Check for listener/disposable leaks

### CPU
- **EDT usage**: Should be < 10% for UI updates
- **Background threads**: Verify pooled threads are used
- **Search time**: Should be < 100ms for typical queries

### Threading
- **EDT blocking**: Any operation > 16ms causes UI lag
- **Background tasks**: Use `ProgressManager` for long ops
- **Lock contention**: ReadWriteLock should favor readers

## 7. Common Performance Issues

### ❌ Bad: Blocking EDT
```java
public void loadData() {
    List<Item> items = service.search(query); // Blocks UI!
    updateUI(items);
}
```

### ✅ Good: Background Loading
```java
public void loadData() {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
        List<Item> items = service.search(query);
        SwingUtilities.invokeLater(() -> updateUI(items));
    });
}
```

### ❌ Bad: Memory Leak
```java
public void setupListener() {
    editor.getDocument().addDocumentListener(listener);
    // Listener never removed!
}
```

### ✅ Good: Proper Cleanup
```java
public void dispose() {
    editor.getDocument().removeDocumentListener(listener);
}
```

## 8. Real-World Testing

### Test with Large Datasets
1. Create a Reference.java with 10,000+ constants
2. Measure search time
3. Check memory usage
4. Verify UI responsiveness

### Test Repeated Operations
1. Open/close tool window 100 times
2. Check for memory growth
3. Verify listeners are cleaned up

### Test Under Load
1. Run with other heavy plugins (e.g., Android Studio plugins)
2. Monitor CPU/memory impact
3. Ensure plugin remains responsive

## 9. Benchmarking Current Implementation

Our plugin's optimizations:
- ✅ **Async loading**: References load in background (ReferenceDataService.java:40)
- ✅ **Debouncing**: Search delayed 300ms (ReferenceBrowserWithTreePanel.java:78)
- ✅ **Thread-safe**: ReadWriteLock for concurrent access (ReferenceDataService.java:28)
- ✅ **Disposable**: Proper cleanup prevents leaks (ReferenceBrowserWithTreePanel.java:418)
- ✅ **Indexed search**: O(1) exact match, O(n) fuzzy (ReferenceDataService.java:179)

Expected metrics:
- Initial load: < 500ms for 1000 items
- Search: < 50ms for typical query
- Memory: < 10MB for 10,000 items
- EDT blocking: 0ms (all I/O in background)
