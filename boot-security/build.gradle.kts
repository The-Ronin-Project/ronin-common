plugins {
    id("com.projectronin.services.gradle.boot")
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-security")
    api("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
}

// TODO: probably shouldn't use the boot plugin so this isn't necessary
// In fact, this project might shouldn't even be using spring-boot-starter-x projects considering it's not a spring boot
// program, but is rather a spring boot starter itself, essentially. Need to look into what the appropriate deps are
// if not starters.
tasks.getByName("bootJar") {
    enabled = false
}
