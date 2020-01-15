rootProject.name = "kinta"

include(":kinta-cli", ":kinta-lib", "kinta-workflows")

pluginManagement {
    repositories {
        mavenCentral()
        google()
        jcenter()
        gradlePluginPortal()
    }

    resolutionStrategy {
        eachPlugin {
            if(requested.id.namespace == "com.apollographql") {
                useModule("com.apollographql.apollo:apollo-gradle-plugin-incubating:${requested.version}")
            }
        }
    }
}