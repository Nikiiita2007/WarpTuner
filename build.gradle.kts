// Top-level build file where you can add configuration options common to all sub-projects/modules.

// Build tooling (AGP, lint, signing) pulls these libraries transitively. They never ship in
// the APK, but older versions have published advisories, so the build classpath is pinned
// to patched releases. Remove an entry once AGP depends on a fixed version itself.
buildscript {
    dependencies {
        constraints {
            classpath("org.bouncycastle:bcprov-jdk18on:1.87") { because("GHSA-9pwp-9qqc-pr26, GHSA-qp49-qgx5-5m26") }
            classpath("org.bouncycastle:bcpkix-jdk18on:1.87") { because("GHSA-wg6q-6289-32hp") }
            classpath("org.bouncycastle:bcutil-jdk18on:1.87") { because("keep Bouncy Castle modules aligned") }
            classpath("com.google.guava:guava:33.7.2-jre") { because("GHSA-xxph-c9ww-hj94") }
            classpath("org.bitbucket.b_c:jose4j:0.9.7") { because("GHSA-3677-xxcr-wjqv") }
            classpath("org.jdom:jdom2:2.0.6.1") { because("GHSA-2363-cqg2-863c") }
            classpath("org.apache.commons:commons-lang3:3.22.0") { because("GHSA-j288-q9x7-2f5v") }
            classpath("org.apache.httpcomponents:httpclient:4.5.14") { because("GHSA-7r82-7xv7-xcpj") }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
