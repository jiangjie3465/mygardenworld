import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import javax.inject.Inject
import org.gradle.process.ExecOperations

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Protobuf Java Lite classes are generated at build time from the repository
// root proto/ directory. Nothing under android/ contains generated code.
val protobufVersion = "4.36.1"
val protoRoot = rootProject.layout.projectDirectory.dir("../proto")

val protocTool: Configuration by configurations.creating { isCanBeConsumed = false }
val protoIncludes: Configuration by configurations.creating { isCanBeConsumed = false }

fun protocClassifier(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val osPart = when {
        os.contains("mac") || os.contains("darwin") -> "osx"
        os.contains("windows") -> "windows"
        else -> "linux"
    }
    val archPart = when {
        arch == "aarch64" || arch == "arm64" -> "aarch_64"
        arch.contains("ppc64") -> "ppcle_64"
        else -> "x86_64"
    }
    return "$osPart-$archPart"
}

dependencies {
    protocTool("com.google.protobuf:protoc:$protobufVersion:${protocClassifier()}@exe")
    protoIncludes("com.google.protobuf:protobuf-java:$protobufVersion")
}

abstract class GenerateProtoTask : DefaultTask() {
    @get:Inject abstract val execOperations: ExecOperations
    @get:Inject abstract val fs: FileSystemOperations

    @get:InputFiles abstract val protocExecutable: ConfigurableFileCollection
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) abstract val protoDirectory: DirectoryProperty
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) abstract val includeDirectory: DirectoryProperty
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val out = outputDirectory.get().asFile
        fs.delete { delete(out) }
        out.mkdirs()
        val protoc = protocExecutable.singleFile
        protoc.setExecutable(true)
        val root = protoDirectory.get().asFile
        val files = root.walkTopDown().filter { it.isFile && it.extension == "proto" }.map { it.absolutePath }.sorted().toList()
        require(files.isNotEmpty()) { "no .proto files under $root" }
        execOperations.exec {
            executable(protoc.absolutePath)
            args("-I", root.absolutePath)
            args("-I", includeDirectory.get().asFile.absolutePath)
            args("--java_out=lite:${out.absolutePath}")
            args(files)
        }
    }
}

// The Web UI ships the client-derived game catalog. The app only needs item
// names and flower metadata, so a trimmed copy is produced at build time.
abstract class TrimCatalogTask : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val source: RegularFileProperty
    /** Registered as a generated assets root; receives catalog.json. */
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun trim() {
        @Suppress("UNCHECKED_CAST")
        val catalog = JsonSlurper().parse(source.get().asFile) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val items = (catalog["items"] as? Map<String, Map<String, Any?>>).orEmpty()
        @Suppress("UNCHECKED_CAST")
        val flowers = (catalog["flowers"] as? Map<String, Map<String, Any?>>).orEmpty()
        val names = LinkedHashMap<String, String>()
        for ((id, item) in items) {
            val display = (item["display_name"] as? String)?.trim().orEmpty()
            val name = (item["name"] as? String)?.trim().orEmpty()
            val resolved = display.takeIf { it.isNotEmpty() && it != "0" } ?: name.takeIf { it.isNotEmpty() && it != "0" } ?: continue
            names[id] = resolved
        }
        val flowerMeta = LinkedHashMap<String, Map<String, Any?>>()
        for ((id, flower) in flowers) {
            flowerMeta[id] = mapOf(
                "seed_id" to (flower["seed_id"] ?: 0),
                "sort" to (flower["sort"] ?: 0),
                "gold" to (flower["gold"] ?: 0),
                "experience" to (flower["experience"] ?: 0),
            )
        }
        val out = outputDirectory.get().asFile.resolve("catalog.json")
        out.parentFile.mkdirs()
        out.writeText(JsonOutput.toJson(mapOf("items" to names, "flowers" to flowerMeta)))
    }
}

val trimCatalog by tasks.registering(TrimCatalogTask::class) {
    source.set(rootProject.layout.projectDirectory.file("../web/src/lib/game/catalog.json"))
    outputDirectory.set(layout.buildDirectory.dir("generated/catalog/assets"))
}

val extractProtoIncludes by tasks.registering(Sync::class) {
    from(protoIncludes.elements.map { jars -> jars.map { zipTree(it) } }) {
        include("google/protobuf/**/*.proto")
    }
    into(layout.buildDirectory.dir("proto-includes"))
}

val generateProto by tasks.registering(GenerateProtoTask::class) {
    protocExecutable.from(protocTool)
    protoDirectory.set(protoRoot)
    includeDirectory.set(extractProtoIncludes.map { layout.buildDirectory.dir("proto-includes").get() })
    outputDirectory.set(layout.buildDirectory.dir("generated/source/proto/java"))
}

android {
    namespace = "com.silkage.mygardenworld"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.silkage.mygardenworld"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Emulator development may talk to http://10.0.2.2:50051 or a
            // user-installed CA. See src/debug/res/xml/network_security_config.xml.
            buildConfigField("boolean", "ALLOW_INSECURE_ENDPOINTS", "true")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("boolean", "ALLOW_INSECURE_ENDPOINTS", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = false
    }
    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.java?.addGeneratedSourceDirectory(generateProto, GenerateProtoTask::outputDirectory)
        variant.sources.assets?.addGeneratedSourceDirectory(trimCatalog, TrimCatalogTask::outputDirectory)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.12.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.6")
    implementation("com.google.protobuf:protobuf-javalite:$protobufVersion")
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.squareup.okhttp3:okhttp:5.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    testImplementation("com.squareup.okhttp3:mockwebserver3:5.3.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
