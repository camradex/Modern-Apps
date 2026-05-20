import gradle.kotlin.dsl.accessors._13d918f2d1799539fdb965664f82a6d2.implementation
import gradle.kotlin.dsl.accessors._3a362114ed480d2f6880b82bdbd2b2ca.ksp
import org.gradle.kotlin.dsl.DependencyHandlerScope

fun DependencyHandlerScope.implementRoom(libs: org.gradle.accessors.dm.LibrariesForLibs) {
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
}
