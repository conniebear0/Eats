package com.eats.app.auth

/** Each function returns an error message, or null when the value is fine. */
object Validators {
    private val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    // South African mobile numbers: 082 123 4567 or 82 123 4567 (06x, 07x, 08x ranges)
    private val saMobileRegex = Regex("^0?[678]\\d{8}$")

    fun required(value: String, message: String): String? =
        if (value.isBlank()) message else null

    fun email(value: String): String? =
        if (emailRegex.matches(value.trim())) null else "Enter a valid email address"

    fun mobile(value: String): String? {
        val digits = value.filter { it.isDigit() }
        return if (saMobileRegex.matches(digits)) null else "Enter a valid South African mobile number"
    }

    /** Login accepts either an email address or a mobile number. */
    fun emailOrMobile(value: String): String? = when {
        value.isBlank() -> "Enter your email or mobile number"
        value.contains("@") -> email(value)
        else -> mobile(value)
    }

    fun newPassword(value: String): String? =
        if (value.length >= 8) null else "Use at least 8 characters"

    fun confirmPassword(password: String, confirmation: String): String? =
        if (password == confirmation) null else "Passwords do not match"

    /** The 6 digit code sent during password reset. */
    fun resetCode(value: String): String? =
        if (value.trim().length == 6 && value.trim().all { it.isDigit() }) null else "Enter the 6 digit code"

    /** A delivery address needs enough detail for a driver to find it, e.g. "12 Oak Street, Vincent". */
    fun address(value: String): String? = when {
        value.isBlank() -> "Enter your delivery address"
        value.trim().length < 8 -> "Add a street and suburb so the driver can find you"
        else -> null
    }
}
