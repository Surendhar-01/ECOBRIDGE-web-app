package com.example.auth

import androidx.annotation.StringRes
import com.example.R
import com.example.model.RoleType

enum class AuthMethod(@StringRes val titleRes: Int) {
    OTP_AUTHENTICATION(R.string.auth_otp_tab),
    MOBILE_OTP(R.string.auth_tab_mobile),
    EMAIL_PASSWORD(R.string.auth_email_tab),
    GOOGLE_OAUTH(R.string.auth_tab_google_continue)
}

enum class OtpDeliveryDestination(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val iconEmoji: String,
    @StringRes val badgeRes: Int
) {
    ALL_CHANNELS(
        id = "all",
        titleRes = R.string.channel_all_title,
        subtitleRes = R.string.channel_all_subtitle,
        iconEmoji = "🚀",
        badgeRes = R.string.channel_all_badge
    ),
    MOBILE_SMS(
        id = "mobile",
        titleRes = R.string.channel_mobile_title,
        subtitleRes = R.string.channel_mobile_subtitle,
        iconEmoji = "📱",
        badgeRes = R.string.channel_mobile_badge
    ),
    REGISTERED_EMAIL(
        id = "email",
        titleRes = R.string.channel_email_title,
        subtitleRes = R.string.channel_email_subtitle,
        iconEmoji = "📧",
        badgeRes = R.string.channel_email_badge
    ),
    GOOGLE_ACCOUNT(
        id = "google",
        titleRes = R.string.channel_google_title,
        subtitleRes = R.string.channel_google_subtitle,
        iconEmoji = "🌐",
        badgeRes = R.string.channel_google_badge
    )
}

enum class AccountStatus(@StringRes val labelRes: Int, val isAllowed: Boolean) {
    ACTIVE(R.string.status_active_verified, true),
    PENDING_VERIFICATION(R.string.status_pending_verification, false),
    REJECTED(R.string.status_rejected, false),
    SUSPENDED(R.string.status_suspended, false),
    DISABLED(R.string.status_disabled, false)
}

enum class AuthPipelineStep(val stepNumber: Int, @StringRes val descriptionRes: Int) {
    IDLE(0, R.string.step_awaiting_credentials),
    AUTHENTICATING_USER(1, R.string.step_authenticating_user),
    VERIFYING_ACCOUNT(2, R.string.step_verifying_account),
    VERIFYING_ROLE(3, R.string.step_verifying_role),
    VERIFYING_PERMISSIONS(4, R.string.step_verifying_permissions),
    SUCCESS(5, R.string.step_login_success),
    FAILED(5, R.string.step_authentication_failed)
}

data class UserProfile(
    val userId: String,
    val displayName: String,
    val email: String?,
    val phoneNumber: String?,
    val role: RoleType,
    val accountStatus: AccountStatus,
    val permissions: List<String>,
    val statutoryIdentifier: String,
    val entityName: String,
    val sessionToken: String,
    val authMethod: AuthMethod,
    val verifiedTimestamp: Long = System.currentTimeMillis()
)

data class OtpSession(
    val destination: OtpDeliveryDestination = OtpDeliveryDestination.MOBILE_SMS,
    val phoneNumber: String? = null,
    val email: String? = null,
    val authUserId: String? = null,
    val otpCode: String,
    val targetRole: RoleType? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val expiryTimestamp: Long = System.currentTimeMillis() + 5 * 60 * 1000L, // 5 minutes
    var attemptsRemaining: Int = 3,
    var isLocked: Boolean = false,
    var lockExpiryTimestamp: Long? = null
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() > expiryTimestamp

    val remainingSeconds: Long
        get() = maxOf(0L, (expiryTimestamp - System.currentTimeMillis()) / 1000L)
}

/**
 * Machine-readable error codes for the auth layer so UI/tests can branch on
 * failures without parsing localized messages.
 */
enum class AuthErrorCode(@StringRes val userMessageRes: Int) {
    PROVIDER_NOT_CONFIGURED(R.string.err_provider_not_configured),
    NETWORK_ERROR(R.string.err_network),
    INVALID_OTP(R.string.err_invalid_otp),
    OTP_NOT_REQUESTED(R.string.err_otp_not_requested),
    INVALID_CREDENTIALS(R.string.err_invalid_credentials),
    EMAIL_NOT_CONFIRMED(R.string.err_email_not_confirmed),
    ACCOUNT_NOT_FOUND(R.string.err_account_not_found),
    ACCOUNT_GATED(R.string.err_account_gated),
    ROLE_MISMATCH(R.string.err_role_mismatch),
    ROLE_NOT_ASSIGNED(R.string.err_role_not_assigned),
    EMAIL_ALREADY_REGISTERED(R.string.err_email_registered),
    GENERIC(R.string.err_generic)
}

data class AuthResult(
    val isSuccess: Boolean,
    val userProfile: UserProfile? = null,
    val errorMessage: String? = null,
    val failureStep: AuthPipelineStep? = null,
    val errorCode: AuthErrorCode? = null,
    /**
     * True when credentials were accepted but the login is parked at the OTP
     * second-factor step. The UI must show the OTP challenge next; the dashboard
     * opens only after [SupabaseAuthService.verifyLoginOtp] succeeds.
     */
    val awaitingOtp: Boolean = false,
    /** Non-error notice for the UI (e.g. "Account created — sign in now"). */
    val infoMessage: String? = null
)
