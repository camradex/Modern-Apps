import gradle.kotlin.dsl.accessors._b19cf9d93fc61995065701c774b7d1db.implementation
import gradle.kotlin.dsl.accessors._b19cf9d93fc61995065701c774b7d1db.ksp
import org.gradle.kotlin.dsl.DependencyHandlerScope

fun DependencyHandlerScope.implementRoom(libs: org.gradle.accessors.dm.LibrariesForLibs) {
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
}
