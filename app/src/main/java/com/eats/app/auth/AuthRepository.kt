package com.eats.app.auth

import kotlinx.coroutines.delay

/** The signed-in person. [mobile] is digits only, without the leading 0 or +27. */
data class User(
    val id: String,
    val firstName: String,
    val lastName: String,
    val mobile: String,
    val email: String,
) {
    val fullName: String get() = "$firstName $lastName".trim()
    val initials: String
        get() = "${firstName.firstOrNull() ?: ' '}${lastName.firstOrNull() ?: ' '}".trim().uppercase()
}

data class SignUpForm(
    val firstName: String,
    val lastName: String,
    val mobile: String, // digits only, without the +27 prefix
    val email: String,
    val password: String,
)

/**
 * Everything the app needs from an account system. [LocalAuthRepository] implements it on the device;
 * a class that calls your API (Retrofit or Ktor) can replace it without touching any screen.
 * Failures carry a message that is safe to show to the user.
 */
interface AuthRepository {
    /** The user from a saved, unexpired session, or null when nobody is signed in. */
    fun restoreSession(): User?

    suspend fun login(identifier: String, password: String): Result<User>
    suspend fun signUp(form: SignUpForm): Result<User>

    /** Starts a password reset and returns the one-time code that was "sent" to the user. */
    suspend fun requestPasswordReset(identifier: String): Result<String>
    suspend fun resetPassword(identifier: String, code: String, newPassword: String): Result<Unit>

    fun logout()
}

/** Accepts everything. Used by the Android Studio previews, where there is no Context. */
class FakeAuthRepository : AuthRepository {
    private val demoUser = User("demo", "Thandi", "Nkosi", "821234567", "thandi@example.com")

    override fun restoreSession(): User? = null

    override suspend fun login(identifier: String, password: String): Result<User> {
        delay(600)
        return Result.success(demoUser)
    }

    override suspend fun signUp(form: SignUpForm): Result<User> {
        delay(600)
        return Result.success(demoUser)
    }

    override suspend fun requestPasswordReset(identifier: String): Result<String> = Result.success("123456")

    override suspend fun resetPassword(identifier: String, code: String, newPassword: String): Result<Unit> =
        Result.success(Unit)

    override fun logout() = Unit
}
