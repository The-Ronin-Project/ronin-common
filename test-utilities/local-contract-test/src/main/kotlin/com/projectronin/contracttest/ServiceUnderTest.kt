package com.projectronin.contracttest

import com.projectronin.domaintest.ServiceDef

object ServiceUnderTest : ServiceDef(
    serviceName = "service",
    imageName = "ronin/base/java-springboot"
)
