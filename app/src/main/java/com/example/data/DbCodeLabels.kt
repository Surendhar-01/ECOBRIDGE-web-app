package com.example.data

import androidx.annotation.StringRes
import com.example.R
import java.time.Duration
import java.time.Instant

/**
 * Display labels for the code-valued columns stored in Supabase.
 *
 * The codes are the stable contract; only their label is localized, so a row
 * written under one language renders correctly in all three. Each set mirrors a
 * CHECK constraint added in supabase/migrations/0008_localized_content.sql, and
 * an unrecognised code degrades to a readable fallback rather than a blank.
 */
object DbCodeLabels {

    @StringRes
    fun trend(code: String?): Int = when (code?.uppercase()) {
        "UP" -> R.string.trend_up
        "DOWN" -> R.string.trend_down
        "STABLE" -> R.string.trend_stable
        // Unknown codes are still shown, just unstyled, rather than hidden.
        else -> R.string.trend_stable
    }

    @StringRes
    fun alertLevel(code: String?): Int = when (code?.uppercase()) {
        "CRITICAL" -> R.string.alert_critical
        "HIGH" -> R.string.alert_high
        "MEDIUM" -> R.string.alert_medium
        "LOW" -> R.string.alert_low
        else -> R.string.alert_medium
    }

    @StringRes
    fun paymentMode(code: String?): Int = when (code?.uppercase()) {
        "CASH" -> R.string.payment_cash
        "UPI" -> R.string.payment_upi
        "BANK_TRANSFER" -> R.string.payment_bank
        else -> R.string.payment_cash
    }

    @StringRes
    fun requestStatus(code: String?): Int = when (code?.uppercase()) {
        "PENDING" -> R.string.request_pending
        "ACCEPTED" -> R.string.request_accepted
        "REJECTED" -> R.string.request_rejected
        "BLOCKED" -> R.string.request_blocked
        "REPORTED" -> R.string.request_reported
        else -> R.string.request_pending
    }

    @StringRes
    fun quotationStatus(code: String?): Int = when (code?.uppercase()) {
        "PENDING" -> R.string.quotation_pending
        "SENT" -> R.string.quotation_sent
        "ACCEPTED" -> R.string.quotation_accepted
        "REJECTED" -> R.string.quotation_rejected
        "EXPIRED" -> R.string.quotation_expired
        "WITHDRAWN" -> R.string.quotation_withdrawn
        else -> R.string.quotation_pending
    }

    @StringRes
    fun authorizationStatus(code: String?): Int = when (code?.lowercase()) {
        "active" -> R.string.authz_active
        "pending" -> R.string.authz_pending
        "expired" -> R.string.authz_expired
        "suspended" -> R.string.authz_suspended
        else -> R.string.authz_pending
    }

    @StringRes
    fun uploadStatus(code: String?): Int = when (code?.uppercase()) {
        "PENDING" -> R.string.upload_pending
        "UPLOADED" -> R.string.upload_complete
        "FAILED" -> R.string.upload_failed
        else -> R.string.upload_pending
    }

    /**
     * Renders an ISO-8601 instant as a localized relative time.
     *
     * Replaces the frozen English strings the `date_updated` column used to
     * carry ('Today', '2 days ago'), which went stale and could not be
     * translated. `now` is injectable so the wording is testable.
     */
    @StringRes
    fun relativeTime(isoInstant: String?, now: Instant = Instant.now()): Int? {
        val instant = isoInstant?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
        val elapsed = Duration.between(instant, now)
        val minutes = elapsed.toMinutes()
        val hours = elapsed.toHours()
        val days = elapsed.toDays()
        return when {
            minutes < 1 -> R.string.relative_just_now
            hours < 1 -> R.string.relative_minutes_ago
            days < 1 -> R.string.relative_hours_ago
            else -> R.string.relative_days_ago
        }
    }
}
