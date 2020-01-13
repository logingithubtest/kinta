import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm").version(Versions.kotlin).apply(false)
    id("org.jetbrains.kotlin.plugin.serialization").version(Versions.kotlin).apply(false)
    id("com.apollographql.apollo").version(Versions.apollo).apply(false)
}

subprojects {
    repositories {
        jcenter()
        maven {
            url = uri("https://repo.gradle.org/gradle/libs-releases-local/")
        }
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlinx.serialization.ImplicitReflectionSerializer"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlinx.serialization.UnstableDefault"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlin.Experimental"
        kotlinOptions {
            allWarningsAsErrors = true
        }
    }

    group = "com.dailymotion.kinta"
    version = "0.1.0"

    apply(plugin = "maven-publish")

    afterEvaluate {
        configureMavenPublish()
    }
}

fun Project.configureMavenPublish() {

    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("default") {
                from(components.findByName("java"))

                pom {
                    groupId = group.toString()
                    artifactId = findProperty("POM_ARTIFACT_ID") as String?
                    version = version

                    name.set(findProperty("POM_NAME") as String?)
                    packaging = "jar"
                    description.set(findProperty("POM_DESCRIPTION") as String?)
                    url.set("https://github.com/dailymotion/kinta")

                    scm {
                        url.set("https://github.com/dailymotion/kinta")
                        connection.set("https://github.com/dailymotion/kinta")
                        developerConnection.set("https://github.com/dailymotion/kinta")
                    }

                    licenses {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }

                    developers {
                        developer {
                            id.set("Dailymotion")
                            name.set("Dailymotion")
                        }
                    }
                }
            }
        }

        repositories {
            maven {
                name = "pluginTest"
                url = uri("file://${rootProject.buildDir}/localMaven")
            }
            maven {
                name = "oss"
                url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
                credentials {
                    username = findProperty("SONATYPE_NEXUS_USERNAME") as String?
                    password = findProperty("SONATYPE_NEXUS_PASSWORD") as String?
                }
            }
        }
    }
}