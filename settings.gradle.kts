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
    }
}

rootProject.name = "wave-toolbox"

include(":app")

// 引擎与公共层
include(":core:common")
include(":core:model")
include(":core:selector")
include(":core:rules")
include(":core:accessibility")
include(":core:automation")
include(":core:feature-api")
include(":core:data")
include(":core:design")

// 功能特性（每个特性 = 一个可独立开关的模块）
include(":feature:settings")
include(":feature:adskip")
include(":feature:wechat")
