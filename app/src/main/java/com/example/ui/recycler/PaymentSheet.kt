package com.example.ui.recycler

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MaterialLot
import com.example.model.PaymentMode
import com.example.payment.UpiPayment
import com.example.ui.theme.EcoBlueBg
import com.example.ui.theme.EcoBlueAccent
import com.example.ui.theme.EcoBorder
import com.example.ui.theme.EcoGreenPrimary
import com.example.ui.theme.EcoMint
import com.example.ui.theme.EcoOrangeAccent
import com.example.ui.theme.EcoTextPrimary
import com.example.ui.theme.EcoTextSecondary
import com.example.ui.theme.EcoWhite

/**
 * Payment sheet shown to a formal recycler once a lot has been handed over.
 *
 * The amount is derived from the lot's agreed rate and the weigh-in figure, and is
 * displayed but not editable: the recycler confirms how they are paying, not how
 * much. Choosing UPI opens a `upi://pay` deep link in whichever UPI app the user
 * has, pre-filled with the exact amount, after which they return here and confirm.
 * Cash and bank transfer are recorded directly, with an optional reference.
 */
@Composable
fun PaymentSheet(
    lot: MaterialLot,
    collectorName: String,
    collectorUpiId: String?,
    onConfirm: (PaymentMode, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf(PaymentMode.UPI) }
    var reference by remember { mutableStateOf("") }
    var verifiedWeight by remember { mutableStateOf(lot.weightKg) }
    var error by remember { mutableStateOf<String?>(null) }

    val weight = if (verifiedWeight > 0.0) verifiedWeight else lot.weightKg
    val amount = Math.round(weight * lot.quotedRatePerKg * 100.0) / 100.0
    val canPayUpi = UpiPayment.isValidVpa(collectorUpiId)
    val referenceRequired = mode == PaymentMode.BANK_TRANSFER

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable { onDismiss() }
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = EcoWhite,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .testTag("payment_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pay informal collector",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = EcoTextPrimary
                        )
                        Text(
                            text = "Lot ${lot.lotId} · $collectorName",
                            fontSize = 11.sp,
                            color = EcoTextSecondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = EcoTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Amount breakdown. Not editable: the rate was agreed when the lot
                // was matched, and the server recomputes it on save.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EcoMint, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Text("Amount payable", fontSize = 11.sp, color = EcoTextSecondary)
                    Text(
                        text = "₹${"%,.2f".format(amount)}",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = EcoGreenPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BreakdownRow("Verified weight", "${"%.2f".format(weight)} kg")
                    BreakdownRow("Agreed rate", "₹${"%,.2f".format(lot.quotedRatePerKg)} / kg")
                    BreakdownRow("Material", lot.category.titleEn)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Verified weight (kg)", fontSize = 12.sp, color = EcoTextSecondary)
                OutlinedTextField(
                    value = if (verifiedWeight > 0.0) verifiedWeight.toString() else "",
                    onValueChange = { v -> verifiedWeight = v.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0 },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .testTag("payment_weight")
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("Payment method", fontSize = 12.sp, color = EcoTextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                PaymentMode.entries.forEach { option ->
                    PaymentModeRow(
                        label = option.label,
                        selected = mode == option,
                        badge = if (option == PaymentMode.UPI && !canPayUpi) "No UPI ID on file" else null,
                        onClick = {
                            mode = option
                            error = null
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (mode == PaymentMode.UPI && !canPayUpi) {
                    Text(
                        text = "This collector has not recorded a UPI ID, so a UPI request cannot be addressed. Record the payment as cash or bank transfer, or ask the collector to add their UPI ID.",
                        fontSize = 11.sp,
                        color = EcoOrangeAccent,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = reference,
                    onValueChange = { reference = it.take(35) },
                    label = {
                        Text(
                            when (mode) {
                                PaymentMode.UPI -> "UPI transaction reference (optional)"
                                PaymentMode.CASH -> "Cash receipt / note (optional)"
                                PaymentMode.BANK_TRANSFER -> "UTR / cheque number (required)"
                            }
                        )
                    },
                    singleLine = true,
                    isError = referenceRequired && reference.isBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payment_reference")
                )

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error!!, fontSize = 12.sp, color = EcoOrangeAccent)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        error = when {
                            amount <= 0.0 -> "Enter a verified weight greater than zero."
                            referenceRequired && reference.isBlank() -> "A bank transfer needs a UTR or cheque number."
                            else -> null
                        }
                        if (error == null) onConfirm(mode, reference.trim().ifBlank { null })
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payment_confirm")
                ) {
                    Text(
                        text = if (mode == PaymentMode.UPI) "Pay ₹${"%,.2f".format(amount)} via UPI" else "Record payment of ₹${"%,.2f".format(amount)}",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "UPI opens your installed payment app with the amount filled in. Confirm the transfer there, then this payment is recorded against the lot and a receipt is generated.",
                    fontSize = 11.sp,
                    color = EcoTextSecondary
                )
            }
        }
    }
}

@Composable
private fun BreakdownRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = EcoTextSecondary)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = EcoTextPrimary)
    }
}

@Composable
private fun PaymentModeRow(
    label: String,
    selected: Boolean,
    badge: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) EcoBlueBg else EcoWhite, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(
                    color = if (selected) EcoBlueAccent else EcoBorder,
                    shape = RoundedCornerShape(10.dp)
                )
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) EcoBlueAccent else EcoTextPrimary
            )
            if (badge != null) {
                Text(badge, fontSize = 10.sp, color = EcoOrangeAccent)
            }
        }
    }
}
