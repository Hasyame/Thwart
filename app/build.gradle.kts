import java.io.File
import java.net.URI
import java.util.Properties

plugins {
    // AGP 9 ships Kotlin support built in; the standalone
    // org.jetbrains.kotlin.android plugin must not be applied alongside it.
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.hasyame.marvelchampions"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hasyame.marvelchampions"
        minSdk = 28
        targetSdk = 37
        // Must increase for every release. v1.0.0 is already published, and a
        // device refuses an install whose versionCode is not higher than the
        // one it already has — silently, from the user's point of view.
        versionCode = 63
        versionName = "1.40.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        // Locales the app actually ships translations for. Keeps the per-app
        // language picker (res/xml/locales_config.xml) in sync with reality.
        localeFilters += listOf("en", "fr")
    }

    /**
     * Keeps the dependency list out of the APK.
     *
     * The Android plugin otherwise writes a Google-encrypted block naming every
     * library and version into the signing block, for the Play Console to read.
     * This app is not on Play, nobody here can decrypt it, and F-Droid's scanner
     * refuses an APK that carries one — "found extra signing block 'Dependency
     * metadata'" is what stopped the submission.
     */
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    /**
     * Release signing.
     *
     * The signing password has to be plain text somewhere — Gradle hands the
     * real value to the signing engine, so nothing reversible-by-the-build is
     * safe from anyone holding the file. What can be controlled is *where* it
     * sits, and the safest place is outside the repository: inside it, the key
     * is one zipped folder, one cloud backup or one bad `.gitignore` edit away
     * from being shared by accident.
     *
     * Searched in order:
     *  1. `$MCC_KEYSTORE_PROPERTIES` — an explicit path, for CI or an unusual setup
     *  2. `~/.mcc/keystore.properties` — the recommended home, outside every repo
     *  3. `keystore.properties` in the repo root — still honoured, but see above
     *
     * Without any of them the release is signed with the debug key and says so
     * loudly. See README.
     */
    val keystorePropertiesFile = listOfNotNull(
        System.getenv("MCC_KEYSTORE_PROPERTIES")?.let { File(it) },
        File(System.getProperty("user.home"), ".mcc/keystore.properties"),
        rootProject.file("keystore.properties"),
    ).firstOrNull { it.exists() }

    val keystoreProperties = Properties().apply {
        keystorePropertiesFile?.inputStream()?.use { load(it) }
    }

    // A relative storeFile is resolved against the properties file's own folder,
    // so the key and its passwords travel together and neither has to know
    // where the checkout is.
    val declaredKeystore = keystoreProperties.getProperty("storeFile")?.let { path ->
        val named = File(path)
        when {
            named.isAbsolute -> named
            keystorePropertiesFile != null -> File(keystorePropertiesFile.parentFile, path)
            else -> rootProject.file(path)
        }
    }

    // A keystore.properties naming a file that is not there is a typo or a key
    // left behind on another machine. Failing here beats shipping a build that
    // is quietly debug-signed because a path was wrong.
    if (declaredKeystore != null && !declaredKeystore.exists()) {
        throw GradleException(
            "${keystorePropertiesFile?.absolutePath} points at " +
                "${declaredKeystore.absolutePath}, which does not exist. Fix " +
                "storeFile, or delete that file to fall back to the debug key.",
        )
    }

    val hasReleaseKeystore = declaredKeystore != null

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = declaredKeystore
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // A different package from the release build, so the two can sit on
            // the same phone at once.
            //
            // Without this they share one application id, and Android refuses
            // to install one over the other because the signatures differ — the
            // only way through is to uninstall, which takes every campaign,
            // deck and play with it. Testing a change should not cost the data
            // you were testing against.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                // A debug-signed release looks exactly like a real one, and the
                // mistake only surfaces when a properly signed build refuses to
                // install over it and takes every campaign with it. Say so.
                logger.warn(
                    "\n" +
                        "  ==============================================================\n" +
                        "   RELEASE IS BEING SIGNED WITH THE DEBUG KEY\n" +
                        "   No keystore.properties found.\n" +
                        "   Fine for testing on your own device. Do not distribute it:\n" +
                        "   a real signed build cannot upgrade over it, and installing\n" +
                        "   one means uninstalling first, which erases all app data.\n" +
                        "  ==============================================================\n",
                )
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Robolectric needs the merged resources and assets to serve
            // pack_metadata.json to PackMetadataAssetTest.
            isIncludeAndroidResources = true
        }
    }

    kotlin {
        jvmToolchain(21)
    }

    lint {
        warningsAsErrors = true
        checkDependencies = true
        // Both of these fire because time passed rather than because anything
        // is wrong: a new library version, or a new Gradle, was published. With
        // warningsAsErrors that turns every release into a version bump, and a
        // build tool upgrade deserves its own change and its own verification
        // rather than being smuggled into a bug fix.
        disable += "GradleDependency"
        disable += "AndroidGradlePluginVersion"
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

/**
 * Fails on an unescaped apostrophe in a string resource, before aapt2 sees it.
 *
 * There is a unit test that checks exactly this, and it can never fire: resource
 * merging is a dependency of the test task, so aapt2 kills the build first, with
 * "Can not extract resource from ParsedResource@4ee71c15" and no line number and
 * no file that a person wrote. The test was right and arrived too late, which is
 * the worst combination a guard can have.
 *
 * Same rule, run early enough to be the thing that reports it. French is full of
 * apostrophes; this mistake has cost more time than any other in the project.
 */
val checkStringEscaping = tasks.register("checkStringEscaping") {
    group = "verification"
    description = "Checks that every string resource escapes its apostrophes."

    // Resolved at configuration time: reaching for the project inside doLast
    // would break the configuration cache.
    val stringFiles = layout.projectDirectory.dir("src/main/res").asFile
        .listFiles().orEmpty()
        .filter { it.isDirectory && it.name.startsWith("values") }
        .map { File(it, "strings.xml") }
        .filter { it.exists() }
        .sortedBy { it.path }
    val stamp = layout.buildDirectory.file("checks/string-escaping.txt")

    inputs.files(stringFiles).withPropertyName("stringResources")
    outputs.file(stamp)

    doLast {
        check(stringFiles.size >= 2) {
            "found ${stringFiles.size} strings.xml files, expected at least two"
        }
        val pattern = Regex("""<string name="([^"]+)">(.*)</string>""")
        val offenders = stringFiles.flatMap { file ->
            file.readLines().mapNotNull { line ->
                val match = pattern.find(line) ?: return@mapNotNull null
                val (name, body) = match.destructured
                val bare = body.withIndex().any { (index, char) ->
                    char == '\'' && (index == 0 || body[index - 1] != '\\')
                }
                if (bare) "${file.parentFile.name}/$name" else null
            }
        }
        check(offenders.isEmpty()) {
            "unescaped apostrophe in string resources: $offenders. " +
                "Write it as a backslash before the apostrophe; aapt2 would " +
                "otherwise fail with a message naming neither file nor line."
        }
        stamp.get().asFile.apply { parentFile.mkdirs() }.writeText("ok\n")
    }
}

// Before the resource compiler, which is the whole point.
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Resources") }
    .configureEach { dependsOn(checkStringEscaping) }

/**
 * Downloads the card snapshot bundled into the APK.
 *
 * The output is gitignored on purpose: it is Fantasy Flight's card text, and
 * committing it would republish it (see README, Legal). Run this before an
 * install if you want the app usable offline on first launch; without it the
 * app simply asks for a sync, which is how CI builds.
 */
tasks.register("fetchCardSeed") {
    group = "marvelchampions"
    description = "Downloads the MarvelCDB card and pack snapshot into assets/seed (not committed)."

    val outputDir = layout.projectDirectory.dir("src/main/assets/seed")
    outputs.dir(outputDir)
    // Always hits the network; caching a snapshot of a live API would defeat
    // the point of the task.
    outputs.upToDateWhen { false }

    // Resolved at configuration time. Referencing anything from the build
    // script inside doLast would break the configuration cache.
    val seedDir = outputDir.asFile
    val targets = mapOf(
        // encounter=1 is required. Without it the endpoint silently returns
        // only player cards and omits every encounter card.
        "cards_en.json" to "https://marvelcdb.com/api/public/cards/?encounter=1",
        "cards_fr.json" to "https://fr.marvelcdb.com/api/public/cards/?encounter=1",
        "packs_en.json" to "https://marvelcdb.com/api/public/packs/",
        "packs_fr.json" to "https://fr.marvelcdb.com/api/public/packs/",
    )

    doLast {
        seedDir.mkdirs()
        targets.forEach { (fileName, url) ->
            val destination = File(seedDir, fileName)
            logger.lifecycle("fetchCardSeed: $url")
            URI(url).toURL().openStream().use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            // MarvelCDB nests each card's linked card inside it as well as
            // listing it separately. CardDto does not model the nested copy —
            // every linked card is its own entry — so it is dead weight in an
            // asset the app ships and parses on first launch.
            if (fileName.startsWith("cards_")) {
                val before = destination.length()
                @Suppress("UNCHECKED_CAST")
                val cards = groovy.json.JsonSlurper().parse(destination)
                    as List<Map<String, Any?>>
                destination.writeText(
                    groovy.json.JsonOutput.toJson(cards.map { it - "linked_card" }),
                )
                logger.lifecycle(
                    "fetchCardSeed: slimmed ${destination.name} " +
                        "($before -> ${destination.length()} bytes)",
                )
            }
            logger.lifecycle(
                "fetchCardSeed: wrote ${destination.name} (${destination.length()} bytes)",
            )
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    // hiltViewModel() moved here from hilt-navigation-compose, which now only
    // re-exports it as a deprecation. Declared rather than leant on as a
    // transitive of the other, so the import cannot break under a version bump
    // that drops the old artefact.
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)

    // Navigation is covered by the ordinary test task, through Robolectric and
    // a real NavController rather than a device. Two bugs reached players
    // through this gap: tapping the tab you were already on doing nothing, and
    // a one-shot navigation re-firing on every rebuilt composition.
    testImplementation(libs.androidx.navigation.testing)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
}
