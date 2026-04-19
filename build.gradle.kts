plugins {
    id("java")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven {
        url = uri("https://repo.infernalsuite.com/repository/maven-snapshots/")
        content {
            includeGroup("com.infernalsuite.asp")
        }
    }
    maven {
        url = uri("https://maven.vanutp.dev/main")
        content {
            includeGroup("com.infernalsuite.asp")
        }
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.infernalsuite.asp:api:4.2.1-vanutp")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:deprecation")
}
