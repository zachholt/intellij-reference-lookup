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

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    
    intellijPlatform {
        intellijIdeaCommunity("2024.3")
        pluginVerifier()
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
            sinceBuild = "243"
            untilBuild = "253.*"
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