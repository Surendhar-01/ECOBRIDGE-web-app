package com.example.payment

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Builds and launches a UPI payment request as an Android deep link.
 *
 * A UPI intent is the standard way one Indian app pays another: the payer hands
 * the amount to whichever UPI app the payee user already trusts (Google Pay,
 * PhonePe, Paytm, BHIM) and the bank moves the money. It needs no merchant
 * account, no API key and no server round trip, which is why it is used here
 * instead of a gateway.
 *
 * Format per the NPCI UPI specification:
 *   upi://pay?pa=<payee VPA>&pn=<payee name>&am=<amount>&cu=INR&tn=<note>&tr=<ref>
 *
 * The payee is addressed by a Virtual Payment Address, `name@bank`. If the
 * collector has not recorded one, [buildUri] returns null and the caller should
 * fall back to cash or bank transfer rather than guessing an address.
 */
object UpiPayment {

    /** Deep links are the documented entry point; a couple of apps only register these. */
    private val SCHEMES = listOf("upi")

    /**
     * A VPA is `handle@bank`, e.g. `eco.reclaim@okhdfcbank`. The handle may contain
     * letters, digits, dot, dash and underscore.
     */
    private val VPA = Regex("^[A-Za-z0-9._-]{2,64}@[A-Za-z][A-Za-z0-9.-]{1,62}$")

    /** UPI transaction references are capped at 35 characters by the spec. */
    private const val MAX_NOTE = 50
    private const val MAX_REF = 35

    fun isValidVpa(vpa: String?): Boolean {
        val v = vpa?.trim().orEmpty()
        return v.isNotEmpty() && VPA.matches(v)
    }

    /**
     * @return the amount rounded to 2 decimals, or null if it is not a usable positive
     *         figure. Guards against paying a negative or absurd amount.
     */
    fun normaliseAmount(amountInr: Double): String? {
        if (amountInr.isNaN() || amountInr.isInfinite() || amountInr <= 0.0) return null
        if (amountInr > 10_000_000.0) return null
        val rounded = Math.round(amountInr * 100.0) / 100.0
        return String.format("%.2f", rounded)
    }

    fun buildUri(
        payeeVpa: String?,
        payeeName: String,
        amountInr: Double,
        note: String,
        transactionRef: String
    ): Uri? {
        if (!isValidVpa(payeeVpa)) return null
        val amt = normaliseAmount(amountInr) ?: return null
        // A reference longer than the spec limit is rejected by some UPI apps, and a
        // note containing & or = would break the query string.
        val ref = transactionRef.trim().take(MAX_REF)
        val safeNote = note.trim().take(MAX_NOTE)

        val builder = Uri.parse("upi://pay").buildUpon()
            .appendQueryParameter("pa", payeeVpa!!.trim())
            .appendQueryParameter("pn", payeeName.trim().ifEmpty { "ECOBRIDGES Collector" })
            .appendQueryParameter("am", amt)
            .appendQueryParameter("cu", "INR")
        if (safeNote.isNotEmpty()) builder.appendQueryParameter("tn", safeNote)
        if (ref.isNotEmpty()) builder.appendQueryParameter("tr", ref)
        return builder.build()
    }

    /**
     * Opens the payment in the user's UPI app.
     *
     * @return null on success, or a short human-readable reason when no UPI app is
     *         installed, so the caller can explain why nothing happened.
     */
    fun launch(
        context: Context,
        payeeVpa: String?,
        payeeName: String,
        amountInr: Double,
        note: String,
        transactionRef: String
    ): String? {
        val uri = buildUri(payeeVpa, payeeName, amountInr, note, transactionRef)
            ?: return "invalid_payment_details"

        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            null
        } catch (e: ActivityNotFoundException) {
            "no_upi_app"
        } catch (e: SecurityException) {
            "no_upi_app"
        }
    }
}
