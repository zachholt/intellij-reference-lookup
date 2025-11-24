import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.7.2"
}

group = "com.zachholt"
version = System.getenv("GITHUB_REF")?.let { 
    if (it.startsWith("refs/tags/v")) it.substring(11) else "1.0.0"
} ?: "1.0.0"

repositories {
    mavenCentral()
    
    intellijPlatform {
        defaultRepositories()
    }
}

// Allow local IntelliJ installation to avoid remote downloads when available.
// Set INTELLIJ_LOCAL_PATH env var or -Pintellij.localPath=/path/to/IDE.
val localIdePath = providers.environmentVariable("INTELLIJ_LOCAL_PATH")
    .orElse(providers.gradleProperty("intellij.localPath"))
    .orNull

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.assertj:assertj-core:3.24.2")
    
    intellijPlatform {
        localIdePath?.let {
            localPath(it)
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
        version = System.getenv("GITHUB_REF")?.let { 
            if (it.startsWith("refs/tags/v")) it.substring(11) else "1.0.0"
        } ?: "1.0.0"
        
        ideaVersion {
            sinceBuild = "242"
            untilBuild = "242.*"
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
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
