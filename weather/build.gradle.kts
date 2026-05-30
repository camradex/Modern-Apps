plugins {
    id("common-conventions-app")
    alias(libs.plugins.ksp)
}

android {
    defaultConfig {
        applicationId = "com.vayunmathur.weather"
    }
}

dependencies {
    implementation(project(":library:network"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.materialkolor)
    implementation(libs.reorderable)
    implementRoom(libs)
}
