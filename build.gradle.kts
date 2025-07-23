plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.0"
}

group = "com.zachholt"
version = System.getenv("GITHUB_REF")?.let { 
    if (it.startsWith("refs/tags/v")) it.substring(11) else "1.0.0"
} ?: "1.0.0"

repositories {
    mavenCentral()
}

intellij {
    version.set("2023.3")
    type.set("IC") // IntelliJ IDEA Community Edition
    plugins.set(listOf())
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    patchPluginXml {
        sinceBuild.set("233")
        untilBuild.set("241.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
}