import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.util.Properties

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.zachholt"
version = "2.2.4" // Starting with a fresh version bump

repositories {
    mavenCentral()
    
    intellijPlatform {
        defaultRepositories()
        releases()
        marketplace()
        // Add explicit JetBrains repositories for reliability
        maven("https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/releases")
        maven("https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/snapshots")
        intellijDependencies()
    }
}

// Allow local IntelliJ installation to avoid remote downloads when available.
val localIdePath = run {
    var path: String? = null
    
    // 1. Check local.properties
    val localPropsFile = file("local.properties")
    if (localPropsFile.exists()) {
        val props = Properties()
        localPropsFile.inputStream().use { props.load(it) }
        path = props.getProperty("intellij.localPath")
    }
    
    // 2. Check Env Var
    if (path == null) {
        path = System.getenv("INTELLIJ_LOCAL_PATH")
    }
    
    // 3. Check Gradle Property
    if (path == null) {
        path = providers.gradleProperty("intellij.localPath").orNull
    }
    
    path
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.assertj:assertj-core:3.24.2")
    
    intellijPlatform {
        localIdePath?.let {
            local(it)
        } ?: intellijIdeaCommunity("2024.2.4")
        bundledPlugin("com.intellij.java")
        pluginVerifier()
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.zachholt.reference-lookup"
        name = "Reference Lookup"
        version = project.version.toString()
        
        ideaVersion {
            sinceBuild = "241" // Support 2024.1+
            // No untilBuild to support future versions (2025.x+)
        }
    }
    
    signing {
        certificateChain = System.getenv("CERTIFICATE_CHAIN")
        privateKey = System.getenv("PRIVATE_KEY")
        password = System.getenv("PRIVATE_KEY_PASSWORD")
    }
    
    publishing {
        token = System.getenv("PUBLISH_TOKEN")
    }
}

java {
    // Target Java 17 for broad compatibility while running on Java 21 (2024.3+)
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
