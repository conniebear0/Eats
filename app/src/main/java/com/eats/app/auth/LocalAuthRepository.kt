package com.eats.app.auth

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * On-device accounts so sign up, login, logout and password reset really work without a server.
 *
 * - Passwords are stored only as a salted PBKDF2 hash ([PasswordHasher]).
 * - A successful login creates a random session token with an expiry. The app restores the user from it
 *   on the next launch, and [logout] deletes it.
 * - Repeated wrong passwords lock login for a short time.
 * - Wrong-password and unknown-account failures share one message so it does not reveal which accounts exist.
 *
 * Data lives in app-private storage (backup is switched off in the manifest). When a real backend exists,
 * implement [AuthRepository] against its API and keep the token in encrypted storage.
 */
class LocalAuthRepository(
    context: Context,
    private val now: () -> Long = System::currentTimeMillis,
) : AuthRepository {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Reset codes live in memory only: a code does not survive the app being killed.
    private val resetCodes = mutableMapOf<String, ResetCode>()
    private var failedLogins = 0
    private var lockedUntil = 0L

    private class Account(val user: User, val salt: String, val hash: String)
    private class ResetCode(val code: String, val expiresAt: Long, var attemptsLeft: Int = MAX_CODE_ATTEMPTS)

    // ---------- Session ----------

    override fun restoreSession(): User? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val expiresAt = prefs.getLong(KEY_EXPIRES, 0L)
        val userId = prefs.getString(KEY_SESSION_USER, null)
        val user = readAccounts().firstOrNull { it.user.id == userId }?.user
        if (token.isBlank() || expiresAt < now() || user == null) {
            clearSession()
            return null
        }
        return user
    }

    override fun logout() = clearSession()

    private fun startSession(user: User) {
        prefs.edit()
            .putString(KEY_TOKEN, PasswordHasher.newToken())
            .putString(KEY_SESSION_USER, user.id)
            .putLong(KEY_EXPIRES, now() + SESSION_MILLIS)
            .apply()
    }

    private fun clearSession() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_SESSION_USER).remove(KEY_EXPIRES).apply()
    }

    // ---------- Login and sign up ----------

    override suspend fun login(identifier: String, password: String): Result<User> =
        withContext(Dispatchers.Default) {
            val waitSeconds = (lockedUntil - now() + 999) / 1000
            if (waitSeconds > 0) {
                return@withContext Result.failure(Exception("Too many attempts. Try again in $waitSeconds seconds."))
            }
            val account = findAccount(identifier)
            // Hash even when the account is missing so both failures take the same time.
            val valid = if (account != null) {
                PasswordHasher.verify(password, account.salt, account.hash)
            } else {
                PasswordHasher.hash(password, DUMMY_SALT)
                false
            }
            if (!valid || account == null) {
                failedLogins++
                if (failedLogins >= MAX_FAILED_LOGINS) {
                    failedLogins = 0
                    lockedUntil = now() + LOCK_MILLIS
                }
                return@withContext Result.failure(Exception("Incorrect email, mobile number or password"))
            }
            failedLogins = 0
            startSession(account.user)
            Result.success(account.user)
        }

    override suspend fun signUp(form: SignUpForm): Result<User> =
        withContext(Dispatchers.Default) {
            val accounts = readAccounts()
            val email = form.email.trim().lowercase()
            if (accounts.any { it.user.email == email }) {
                return@withContext Result.failure(Exception("An account with this email already exists. Try signing in."))
            }
            if (accounts.any { it.user.mobile == form.mobile }) {
                return@withContext Result.failure(Exception("An account with this mobile number already exists. Try signing in."))
            }
            val salt = PasswordHasher.newSalt()
            val user = User(
                id = UUID.randomUUID().toString(),
                firstName = form.firstName.trim(),
                lastName = form.lastName.trim(),
                mobile = form.mobile,
                email = email,
            )
            writeAccounts(accounts + Account(user, salt, PasswordHasher.hash(form.password, salt)))
            startSession(user)
            Result.success(user)
        }

    // ---------- Password reset ----------

    override suspend fun requestPasswordReset(identifier: String): Result<String> {
        val account = findAccount(identifier)
            ?: return Result.failure(Exception("We couldn't find an account with those details"))
        val code = PasswordHasher.newCode()
        resetCodes[account.user.id] = ResetCode(code, now() + CODE_MILLIS)
        // A real system sends this by SMS or email. With no server, the caller shows it on screen.
        return Result.success(code)
    }

    override suspend fun resetPassword(identifier: String, code: String, newPassword: String): Result<Unit> =
        withContext(Dispatchers.Default) {
            val account = findAccount(identifier)
                ?: return@withContext Result.failure(Exception("We couldn't find an account with those details"))
            val pending = resetCodes[account.user.id]
            if (pending == null || pending.expiresAt < now()) {
                resetCodes.remove(account.user.id)
                return@withContext Result.failure(Exception("That code has expired. Request a new one."))
            }
            if (pending.code != code.trim()) {
                pending.attemptsLeft--
                if (pending.attemptsLeft <= 0) resetCodes.remove(account.user.id)
                return@withContext Result.failure(Exception("That code is not correct"))
            }
            val salt = PasswordHasher.newSalt()
            val updated = Account(account.user, salt, PasswordHasher.hash(newPassword, salt))
            writeAccounts(readAccounts().map { if (it.user.id == account.user.id) updated else it })
            resetCodes.remove(account.user.id)
            Result.success(Unit)
        }

    // ---------- Storage ----------

    /** Matches an email (any case) or a mobile number (with or without the leading 0). */
    private fun findAccount(identifier: String): Account? {
        val id = identifier.trim()
        val accounts = readAccounts()
        return if (id.contains("@")) {
            accounts.firstOrNull { it.user.email == id.lowercase() }
        } else {
            val digits = id.filter { it.isDigit() }.removePrefix("0")
            accounts.firstOrNull { it.user.mobile == digits }
        }
    }

    private fun readAccounts(): List<Account> {
        val json = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Account(
                user = User(
                    id = o.getString("id"),
                    firstName = o.getString("firstName"),
                    lastName = o.getString("lastName"),
                    mobile = o.getString("mobile"),
                    email = o.getString("email"),
                ),
                salt = o.getString("salt"),
                hash = o.getString("hash"),
            )
        }
    }

    private fun writeAccounts(accounts: List<Account>) {
        val array = JSONArray()
        accounts.forEach { a ->
            array.put(
                JSONObject()
                    .put("id", a.user.id)
                    .put("firstName", a.user.firstName)
                    .put("lastName", a.user.lastName)
                    .put("mobile", a.user.mobile)
                    .put("email", a.user.email)
                    .put("salt", a.salt)
                    .put("hash", a.hash),
            )
        }
        prefs.edit().putString(KEY_ACCOUNTS, array.toString()).apply()
    }

    private companion object {
        const val PREFS = "eats_auth"
        const val KEY_ACCOUNTS = "accounts"
        const val KEY_TOKEN = "session_token"
        const val KEY_SESSION_USER = "session_user"
        const val KEY_EXPIRES = "session_expires"

        const val SESSION_MILLIS = 30L * 24 * 60 * 60 * 1000
        const val CODE_MILLIS = 10L * 60 * 1000
        const val LOCK_MILLIS = 30_000L
        const val MAX_FAILED_LOGINS = 5
        const val MAX_CODE_ATTEMPTS = 5
        const val DUMMY_SALT = "AAAAAAAAAAAAAAAAAAAAAA=="
    }
}
