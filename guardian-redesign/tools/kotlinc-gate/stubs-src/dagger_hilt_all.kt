// GATE STUB — dagger.hilt.
package dagger.hilt

import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class InstallIn(vararg val value: KClass<*>)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class EntryPoint
