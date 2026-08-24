import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 서버 주소는 local.properties에서 읽는다(코드에 하드코딩 금지).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// 에뮬레이터에서 호스트 PC의 localhost는 10.0.2.2
val debugBaseUrl: String = localProps.getProperty("BASE_URL_DEBUG") ?: "http://10.0.2.2:8000/"
val releaseBaseUrl: String = localProps.getProperty("BASE_URL_RELEASE") ?: debugBaseUrl

// 카카오 지도 네이티브 앱 키. 없으면 빈 문자열이고, 앱은 자체 도식 지도로 넘어간다.
val kakaoMapKey: String = localProps.getProperty("KAKAO_MAP_KEY").orEmpty()

// 릴리스 서명 정보. local.properties 에만 두고 커밋하지 않는다.
// 네 값이 다 있을 때만 서명하고, 하나라도 없으면 서명 없이 빌드한다.
// (개발 중에는 없는 게 정상이므로 빌드를 실패시키지 않는다.)
val releaseStoreFile: String? = localProps.getProperty("RELEASE_STORE_FILE")
val releaseStorePassword: String? = localProps.getProperty("RELEASE_STORE_PASSWORD")
val releaseKeyAlias: String? = localProps.getProperty("RELEASE_KEY_ALIAS")
val releaseKeyPassword: String? = localProps.getProperty("RELEASE_KEY_PASSWORD")
val hasReleaseSigning: Boolean = listOf(
    releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword,
).all { !it.isNullOrBlank() } && rootProject.file(releaseStoreFile!!).exists()

android {
    namespace = "com.fitbalance.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fitbalance.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"

        buildConfigField("String", "KAKAO_MAP_KEY", "\"$kakaoMapKey\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"$debugBaseUrl\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "BASE_URL", "\"$releaseBaseUrl\"")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.navigation:navigation-compose:2.8.9")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation("com.kakao.maps.open:android:2.12.8")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
