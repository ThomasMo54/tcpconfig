group = "com.motompro.tcpconfig"
version = "2.3.1"

tasks.register<Copy>("addExecutablesToDistribution") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(
        // App executable
        project(":app").layout.buildDirectory.dir("launch4j"),
        // Updater executable
        project(":updater").layout.buildDirectory.dir("launch4j"),
        // JRE
        zipTree("jre/win_jre_17.zip"),
        // Net interface manager executable
        project(":net-interface-manager").layout.buildDirectory.file("msbuild/bin/NetInterfaceManager.exe"),
    )

    into(layout.buildDirectory.dir("dist"))
}

tasks.register<Zip>("packageDistribution") {
    from(layout.buildDirectory.dir("dist"))

    archiveFileName = "TCPConfig.zip"
    destinationDirectory = layout.buildDirectory.dir("package")
}

tasks["addExecutablesToDistribution"].dependsOn(":app:createExe")
tasks["addExecutablesToDistribution"].dependsOn(":updater:createExe")
tasks["addExecutablesToDistribution"].dependsOn(":net-interface-manager:buildSolution")

tasks["packageDistribution"].dependsOn("addExecutablesToDistribution")
