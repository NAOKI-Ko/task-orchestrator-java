import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins {
    `java-library`
    jacoco
    checkstyle
    id("com.github.spotbugs") version "6.5.11"
    id("me.champeau.jmh") version "0.7.3"
}

group = "io.github.naoki-ko"
version = "0.1.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    withSourcesJar()
    withJavadocJar()
}

val openTelemetryVersion = "1.65.0"

dependencies {
    api("io.opentelemetry:opentelemetry-api:$openTelemetryVersion")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.4")
    testImplementation("net.jqwik:jqwik:1.10.1")
    testImplementation("io.opentelemetry:opentelemetry-sdk:$openTelemetryVersion")
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing:$openTelemetryVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addBooleanOption("Werror", true)
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = "0.8.14"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.90".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}

checkstyle {
    toolVersion = "10.26.1"
    maxWarnings = 0
}

spotbugs {
    ignoreFailures = false
}

jmh {
    warmupIterations.set(2)
    iterations.set(3)
    fork.set(1)
    timeOnIteration.set("1s")
}

val examplesSourceSet = sourceSets.create("examples") {
    java.srcDir("examples/src/main/java")
    compileClasspath += sourceSets.main.get().output + configurations.runtimeClasspath.get()
    runtimeClasspath += output + compileClasspath
}

fun registerExample(taskName: String, className: String) {
    tasks.register<JavaExec>(taskName) {
        group = "application"
        dependsOn(tasks.named(examplesSourceSet.classesTaskName))
        classpath = examplesSourceSet.runtimeClasspath
        mainClass = className
    }
}

registerExample("runBasicExample", "io.github.naokiko.orchestrator.examples.BasicExample")
registerExample("runRetryExample", "io.github.naokiko.orchestrator.examples.RetryExample")
registerExample("runConcurrencyExample", "io.github.naokiko.orchestrator.examples.ConcurrencyExample")
registerExample("runBuilderExample", "io.github.naokiko.orchestrator.examples.BuilderExample")
registerExample("runUtilitiesExample", "io.github.naokiko.orchestrator.examples.UtilitiesExample")
registerExample("runOpenTelemetryExample", "io.github.naokiko.orchestrator.examples.OpenTelemetryExample")

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
