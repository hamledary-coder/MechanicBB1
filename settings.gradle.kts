pluginManagement {
    repositories {
        // اولویت اول: آینه ایران
        maven { url = uri("https://maven.myket.ir") }
        maven { url = uri("https://maven.devneeds.ir") }
        maven { url = uri("https://gradle.iranrepo.ir") }

        // پشتیبان: آینه علی‌بابا (چین)
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }

        // رسمی در انتها
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // اولویت اول: آینه ایران
        maven { url = uri("https://maven.myket.ir") }
        maven { url = uri("https://maven.devneeds.ir") }
        maven { url = uri("https://gradle.iranrepo.ir") }

        // پشتیبان: آینه علی‌بابا
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }

        // رسمی در انتها
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}


rootProject.name = "MechanicBB1"
include(":app")
