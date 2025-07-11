// settings.gradle.kts
import java.util.Properties

val gradleProperties = Properties()
val propertiesFile = file("gradle.properties")
if (propertiesFile.exists()) {
    propertiesFile.inputStream().use { gradleProperties.load(it) }
}

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            credentials {
                username = gradleProperties.getProperty("jitpack.username")
                password = gradleProperties.getProperty("jitpack.token")
            }
        }
    }
}

rootProject.name = "Toutie Budget"
include(":app")
