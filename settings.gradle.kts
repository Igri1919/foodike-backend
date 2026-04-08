rootProject.name = "foodike-backend"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(
    ":shared:common",
    ":shared:events",
    ":shared:auth",
    ":shared:persistence",
    ":services:user-service",
    ":services:restaurant-service",
    ":services:order-service",
    ":services:payment-service",
    ":services:notification-service",
    ":services:tracking-service",
    ":app",
    ":tests",
)
