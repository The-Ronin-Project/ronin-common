package com.projectronin.contracttest

import com.projectronin.domaintest.DomainTestContext
import com.projectronin.domaintest.DomainTestSetupContext
import com.projectronin.domaintest.ProductEngineeringServiceContext
import java.io.File

fun contractTest(block: DomainTestContext.() -> Unit) {
    DomainTestContext().use { block(it) }
}

suspend fun coContractTest(block: suspend DomainTestContext.() -> Unit) {
    DomainTestContext().use { block(it) }
}

val DomainTestContext.localContractTestService: String
    get() = "service"

fun DomainTestSetupContext.withServiceUnderTest(file: File, fn: ProductEngineeringServiceContext.() -> Unit) {
    withProductEngineeringService("service", "ronin/base/java-springboot", "1.1.0") {
        fn(this)
        extraConfiguration {
            @Suppress("DEPRECATION")
            withFileSystemBind(file.absolutePath, "/app/app.jar")
        }
    }
}

//     private fun constructProcessArguments(
//         setupData: TestSetupData,
//         debug: Boolean,
//         debugPort: Int,
//         debugSuspend: Boolean,
//         additionalArguments: List<String>
//     ): List<String> {
//         val processArguments = mutableListOf<String>()
//         processArguments += additionalArguments
//         if (debug) {
//             processArguments += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=${if (debugSuspend) "y" else "n"},address=*:$debugPort"
//         }
//
//         val runtimeMxBean = ManagementFactory.getRuntimeMXBean()
//         val arguments = runtimeMxBean.inputArguments
//
//         val ideaArguments = arguments.filter { it.matches("""-D.*coverage.*""".toRegex()) }
//         val javaAgentArgument = arguments.firstOrNull { it.matches("""-javaagent.*?(intellij-coverage-agent.*?\.jar|jacocoagent.jar).*""".toRegex()) }
//
//         if (javaAgentArgument != null) {
//             val sourceJarLocation = File(javaAgentArgument.replace(".*-javaagent:(.*?(intellij-coverage-agent.*?\\.jar|jacocoagent.jar)).*".toRegex(), "$1"))
//             val destinationJarLocation = run {
//                 val tf = File.createTempFile("coverage-agent", ".jar")
//                 tf.deleteOnExit()
//                 sourceJarLocation.copyTo(tf, overwrite = true)
//                 tf
//             }
//
//             val newJavaAgentArgument = javaAgentArgument
//                 .replace("-javaagent:.*?(intellij-coverage-agent.*?\\.jar|jacocoagent.jar)".toRegex(), "-javaagent:$destinationJarLocation")
//                 .replace("build/jacoco/.*?\\.exec".toRegex(), "${setupData.projectBuildDir.absolutePath}/jacoco/test-${UUID.randomUUID()}.exec")
//
//             processArguments += newJavaAgentArgument
//             processArguments += ideaArguments
//         }
//
//         return processArguments
//     }
