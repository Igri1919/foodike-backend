package com.example.foodike.user.domain.service

import com.example.foodike.auth.JwtService
import com.example.foodike.auth.TokenType
import com.example.foodike.auth.UserPrincipal
import com.example.foodike.common.exception.ForbiddenException
import com.example.foodike.common.exception.UnauthorizedException
import com.example.foodike.common.exception.ValidationException
import com.example.foodike.user.domain.model.AuthResult
import com.example.foodike.user.domain.model.GoogleProfile
import com.example.foodike.user.domain.model.OtpChallenge
import com.example.foodike.user.domain.model.RefreshTokenRecord
import com.example.foodike.user.domain.model.TokenPair
import com.example.foodike.user.domain.model.User
import com.example.foodike.user.domain.port.GoogleIdTokenVerifier
import com.example.foodike.user.domain.port.OtpChallengeStore
import com.example.foodike.user.domain.port.OtpProvider
import com.example.foodike.user.domain.port.OtpRateLimitStore
import com.example.foodike.user.domain.port.RefreshTokenStore
import com.example.foodike.user.domain.port.UserRepository
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.security.SecureRandom
import at.favre.lib.crypto.bcrypt.BCrypt

data class AuthProperties(
    val otpLength: Int = 6,
    val otpTtl: Duration = Duration.ofMinutes(5),
    val maxOtpSendsPerWindow: Int = 3,
    val otpSendWindow: Duration = Duration.ofMinutes(10),
    val maxOtpVerifyFailures: Int = 5,
    val otpVerifyWindow: Duration = Duration.ofMinutes(15),
    val otpBlockDuration: Duration = Duration.ofMinutes(15),
)

class AuthService(
    private val jwtService: JwtService,
    private val userRepository: UserRepository,
    private val otpChallengeStore: OtpChallengeStore,
    private val otpRateLimitStore: OtpRateLimitStore,
    private val refreshTokenStore: RefreshTokenStore,
    private val otpProvider: OtpProvider,
    private val googleIdTokenVerifier: GoogleIdTokenVerifier,
    private val properties: AuthProperties,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun sendOtp(phone: String) {
        val normalizedPhone = normalizePhone(phone)
        validatePhone(normalizedPhone)

        if (otpRateLimitStore.isBlocked(normalizedPhone)) {
            throw ForbiddenException("Too many failed OTP attempts. Try again later.")
        }

        val sendCount = otpRateLimitStore.incrementSendAttempts(normalizedPhone)
        if (sendCount.count > properties.maxOtpSendsPerWindow) {
            throw ForbiddenException("OTP request limit reached. Try again later.")
        }

        val otp = generateOtp()
        otpChallengeStore.save(
            OtpChallenge(
                phone = normalizedPhone,
                codeHash = sha256(otp),
                expiresAt = now().plus(properties.otpTtl),
            ),
        )

        val sent = otpProvider.sendOtp(normalizedPhone, otp)
        if (!sent) {
            throw ValidationException("Failed to send OTP")
        }
    }

    suspend fun verifyOtp(phone: String, otp: String): AuthResult {
        val normalizedPhone = normalizePhone(phone)
        validatePhone(normalizedPhone)
        validateOtp(otp)

        if (otpRateLimitStore.isBlocked(normalizedPhone)) {
            throw ForbiddenException("Too many failed OTP attempts. Try again later.")
        }

        val challenge = otpChallengeStore.find(normalizedPhone)
            ?: throw UnauthorizedException("OTP expired or not found")

        if (challenge.codeHash != sha256(otp)) {
            val failures = otpRateLimitStore.incrementVerifyFailure(normalizedPhone)
            if (failures.count >= properties.maxOtpVerifyFailures) {
                otpRateLimitStore.block(normalizedPhone)
            }
            throw UnauthorizedException("Invalid OTP")
        }

        otpChallengeStore.delete(normalizedPhone)
        otpRateLimitStore.clearVerifyFailures(normalizedPhone)

        val existingUser = userRepository.findByPhone(normalizedPhone)
        val user =
            existingUser ?: userRepository.save(
                User(
                    id = UUID.randomUUID().toString(),
                    phone = normalizedPhone,
                ),
            )

        return issueTokens(user = user, isNewUser = existingUser == null)
    }

    suspend fun authenticateWithGoogle(idToken: String): AuthResult {
        if (idToken.isBlank()) {
            throw ValidationException("Google ID token is required")
        }

        val profile = googleIdTokenVerifier.verify(idToken)
        val existingUser =
            userRepository.findByGoogleId(profile.subject)
                ?: profile.email?.let { userRepository.findByEmail(it) }

        val persistedUser =
            if (existingUser == null) {
                userRepository.save(profile.toUser())
            } else {
                userRepository.save(
                    existingUser.copy(
                        googleId = existingUser.googleId ?: profile.subject,
                        email = profile.email ?: existingUser.email,
                        name = profile.name ?: existingUser.name,
                        avatarUrl = profile.picture ?: existingUser.avatarUrl,
                    ),
                )
            }

        return issueTokens(user = persistedUser, isNewUser = existingUser == null)
    }

    suspend fun refresh(refreshToken: String): TokenPair {
        if (refreshToken.isBlank()) {
            throw ValidationException("Refresh token is required")
        }

        val decoded = try {
            jwtService.decode(refreshToken)
        } catch (_: Exception) {
            throw UnauthorizedException("Invalid refresh token")
        }

        val userId = decoded.getClaim("userId").asString()
            ?: throw UnauthorizedException("Invalid refresh token")
        val tokenType = decoded.getClaim("type").asString()
        if (tokenType != TokenType.REFRESH.name) {
            throw UnauthorizedException("Invalid refresh token")
        }

        try {
            jwtService.verifier(TokenType.REFRESH).verify(refreshToken)
        } catch (_: Exception) {
            refreshTokenStore.deleteByUserId(userId)
            throw UnauthorizedException("Invalid refresh token")
        }

        val record = refreshTokenStore.findValidByUserId(userId)
            ?: throw UnauthorizedException("Refresh token not found")

        val matches = BCrypt.verifyer().verify(refreshTokenDigest(refreshToken).toCharArray(), record.tokenHash).verified
        if (!matches) {
            refreshTokenStore.deleteByUserId(userId)
            throw UnauthorizedException("Refresh token replay detected. Please sign in again.")
        }

        val user = userRepository.findById(userId)
            ?: throw UnauthorizedException("User not found")

        val tokenPair = generateTokenPair(user)
        storeRefreshToken(userId = user.id, refreshToken = tokenPair.refreshToken)
        return tokenPair
    }

    suspend fun logout(userId: String, refreshToken: String) {
        if (refreshToken.isBlank()) {
            throw ValidationException("Refresh token is required")
        }

        val record = refreshTokenStore.findValidByUserId(userId) ?: return
        val matches = BCrypt.verifyer().verify(refreshTokenDigest(refreshToken).toCharArray(), record.tokenHash).verified
        if (!matches) {
            refreshTokenStore.deleteByUserId(userId)
            throw UnauthorizedException("Refresh token does not match current session")
        }

        refreshTokenStore.deleteByUserId(userId)
    }

    suspend fun me(userId: String): User =
        userRepository.findById(userId) ?: throw UnauthorizedException("User not found")

    private suspend fun issueTokens(user: User, isNewUser: Boolean): AuthResult {
        val tokenPair = generateTokenPair(user)
        storeRefreshToken(userId = user.id, refreshToken = tokenPair.refreshToken)
        return AuthResult(tokens = tokenPair, user = user, isNewUser = isNewUser)
    }

    private fun generateTokenPair(user: User): TokenPair {
        val principal = UserPrincipal(
            userId = user.id,
            role = user.role,
            phone = user.phone,
        )
        return TokenPair(
            accessToken = jwtService.generateAccessToken(principal),
            refreshToken = jwtService.generateRefreshToken(principal),
        )
    }

    private suspend fun storeRefreshToken(userId: String, refreshToken: String) {
        val decoded = jwtService.decode(refreshToken)
        refreshTokenStore.save(
            RefreshTokenRecord(
                id = UUID.randomUUID().toString(),
                userId = userId,
                tokenHash = BCrypt.withDefaults().hashToString(12, refreshTokenDigest(refreshToken).toCharArray()),
                expiresAt = decoded.expiresAt.toInstant(),
                createdAt = now(),
            ),
        )
    }

    private fun validatePhone(phone: String) {
        val pattern = Regex("^\\+[1-9][0-9]{7,14}$")
        if (!pattern.matches(phone)) {
            throw ValidationException("Phone must be in E.164 format", mapOf("phone" to "Use format like +919876543210"))
        }
    }

    private fun validateOtp(otp: String) {
        val pattern = Regex("^\\d{${properties.otpLength}}$")
        if (!pattern.matches(otp)) {
            throw ValidationException("OTP must be ${properties.otpLength} digits")
        }
    }

    private fun normalizePhone(phone: String): String = phone.trim().replace(" ", "")

    private val secureRandom = SecureRandom()

    private fun generateOtp(): String =
        buildString {
            repeat(properties.otpLength) {
                append(secureRandom.nextInt(10))
            }
        }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun refreshTokenDigest(refreshToken: String): String = sha256(refreshToken)

    private fun now(): Instant = Instant.now(clock)

    private fun GoogleProfile.toUser(): User =
        User(
            id = UUID.randomUUID().toString(),
            googleId = subject,
            email = email,
            name = name,
            avatarUrl = picture,
        )
}
