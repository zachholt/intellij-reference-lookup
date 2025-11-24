import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.util.Properties

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.2.1"
    id("org.jetbrains.changelog") version "2.2.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    
    intellijPlatform {
        defaultRepositories()
        releases()
        marketplace()
        maven("https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/releases")
        maven("https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/snapshots")
        intellijDependencies()
    }
}

// Allow local IntelliJ installation to avoid remote downloads when available.
val localIdePath = run {
    var path: String? = null
    val localPropsFile = file("local.properties")
    if (localPropsFile.exists()) {
        val props = Properties()
        localPropsFile.inputStream().use { props.load(it) }
        path = props.getProperty("intellij.localPath")
    }
    if (path == null) path = System.getenv("INTELLIJ_LOCAL_PATH")
    if (path == null) path = providers.gradleProperty("intellij.localPath").orNull
    path
}

dependencies {
    implementation("com.google.code.gson:gson:${providers.gradleProperty("gsonVersion").get()}")

    testImplementation("junit:junit:${providers.gradleProperty("junitVersion").get()}")
    testImplementation("org.assertj:assertj-core:${providers.gradleProperty("assertjVersion").get()}")
    
    intellijPlatform {
        localIdePath?.let {
            local(it)
        } ?: create(
            providers.gradleProperty("platformType"),
            providers.gradleProperty("platformVersion")
        )
        
        bundledPlugin("com.intellij.java")
        pluginVerifier()
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        id = providers.gradleProperty("pluginId")
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
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
    
    buildSearchableOptions = false
}

changelog {
    version = providers.gradleProperty("pluginVersion").get()
    path = file("CHANGELOG.md").canonicalPath
    groups.set(emptyList())
}

java {
    sourceCompatibility = JavaVersion.toVersion(providers.gradleProperty("javaVersion").get())
    targetCompatibility = JavaVersion.toVersion(providers.gradleProperty("javaVersion").get())
}

tasks {
    // Set the compatibility of the compile tasks
    withType<JavaCompile> {
        sourceCompatibility = providers.gradleProperty("javaVersion").get()
        targetCompatibility = providers.gradleProperty("javaVersion").get()
    }
}