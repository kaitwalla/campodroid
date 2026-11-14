plugins { id("org.jetbrains.kotlin.jvm") }
kotlin { jvmToolchain(17) }
dependencies { testImplementation(kotlin("test")) }
dependencies { implementation("org.bouncycastle:bcprov-jdk18on:1.81") }
tasks.test { useJUnitPlatform() }
