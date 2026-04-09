package com.example.foodike.plugins

import com.example.foodike.auth.JwtConfig
import com.example.foodike.auth.JwtService
import com.example.foodike.auth.installJwtAuthentication
import com.example.foodike.di.serviceModules
import com.example.foodike.persistence.DatabaseConfig
import com.example.foodike.persistence.DatabaseFactory
import com.example.foodike.persistence.initializeSchema
import com.example.foodike.user.domain.service.AuthProperties
import com.example.foodike.user.infrastructure.auth.OtpProviderConfig
import com.example.foodike.user.infrastructure.persistence.RefreshTokensTable
import com.example.foodike.user.infrastructure.persistence.UsersTable
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.ktor.server.application.Application
import io.ktor.server.application.install
import org.jetbrains.exposed.sql.Database
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.koin.dsl.module
import java.time.Duration
import org.slf4j.LoggerFactory

fun Application.configureInfrastructure() {
    val logger = LoggerFactory.getLogger("InfraConfig")

    val database = initializeDatabase()
    val redisResources = initializeRedis(logger)
    initializeSchema(UsersTable, RefreshTokensTable)

    installDependencyInjection(database, redisResources)
    registerShutdownHooks(redisResources)
}

fun Application.configureAuthentication() {
    val koin = org.koin.core.context.GlobalContext.get()
    installJwtAuthentication(koin.get(), koin.get())
}

private fun Application.initializeDatabase(): Database {
    val databaseConfig = DatabaseConfig(
        jdbcUrl = configValue("database.jdbcUrl"),
        driverClassName = configValue("database.driverClassName"),
        username = configValue("database.username"),
        password = configValue("database.password"),
        maximumPoolSize = configValue("database.maximumPoolSize").toInt(),
    )
    return DatabaseFactory.create(databaseConfig)
}

private fun Application.initializeRedis(logger: org.slf4j.Logger): RedisResources? {
    val redisUrl = configValue("redis.url")
    return try {
        val client = RedisClient.create(redisUrl)
        val connection: StatefulRedisConnection<String, String> = client.connect()
        RedisResources(client = client, connection = connection)
    } catch (exception: Exception) {
        logger.warn("Redis unavailable at {}. Falling back to in-memory OTP stores.", redisUrl)
        null
    }
}

private fun Application.installDependencyInjection(database: Database, redisResources: RedisResources?) {
    val jwtConfig = JwtConfig(
        secret = configValue("jwt.secret"),
        issuer = configValue("jwt.issuer"),
        audience = configValue("jwt.audience"),
        accessExpiryMillis = configValue("jwt.accessExpiryMillis").toLong(),
        refreshExpiryMillis = configValue("jwt.refreshExpiryMillis").toLong(),
    )
    val jwtService = JwtService(jwtConfig)
    val authProperties = AuthProperties(
        otpLength = configValue("auth.otp.length").toInt(),
        otpTtl = Duration.ofSeconds(configValue("auth.otp.ttlSeconds").toLong()),
        maxOtpSendsPerWindow = configValue("auth.otp.sendLimit").toInt(),
        otpSendWindow = Duration.ofSeconds(configValue("auth.otp.sendWindowSeconds").toLong()),
        maxOtpVerifyFailures = configValue("auth.otp.verifyFailureLimit").toInt(),
        otpVerifyWindow = Duration.ofSeconds(configValue("auth.otp.verifyWindowSeconds").toLong()),
        otpBlockDuration = Duration.ofSeconds(configValue("auth.otp.blockDurationSeconds").toLong()),
    )
    val otpProviderConfig = OtpProviderConfig(
        provider = configValue("auth.otp.provider"),
        twilioAccountSid = configValue("auth.otp.twilio.accountSid").ifBlank { null },
        twilioAuthToken = configValue("auth.otp.twilio.authToken").ifBlank { null },
        twilioFromNumber = configValue("auth.otp.twilio.fromNumber").ifBlank { null },
    )

    install(Koin) {
        slf4jLogger()
        modules(
            serviceModules +
                module {
                    single { database }
                    single { jwtConfig }
                    single { jwtService }
                    single { authProperties }
                    single { otpProviderConfig }
                } + listOfNotNull(
                    redisResources?.let { resources ->
                        module {
                            single { resources.client }
                            single<StatefulRedisConnection<String, String>> { resources.connection }
                        }
                    },
                ),
        )
    }
}

private fun Application.registerShutdownHooks(redisResources: RedisResources?) {
    monitor.subscribe(io.ktor.server.application.ApplicationStopped) {
        redisResources?.connection?.close()
        redisResources?.client?.shutdown()
    }
}

private fun Application.configValue(path: String): String =
    environment.config.property(path).getString()

private data class RedisResources(
    val client: RedisClient,
    val connection: StatefulRedisConnection<String, String>,
)
