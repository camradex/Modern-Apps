plugins {
    id("common-conventions-app")
}

android {
    namespace = "com.vayunmathur.email"
    defaultConfig {
        applicationId = "com.vayunmathur.email"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/LICENSE.md"
        }
    }
}

dependencies {
    implementation(libs.jakarta.mail)
    implementation(libs.jakarta.activation)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(project(":library:widgets"))
}
