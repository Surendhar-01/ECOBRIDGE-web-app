package com.example.ui.recycler

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.SupabaseAuthConfig
import com.example.auth.SupabaseAuthService
import com.example.data.CloudSyncManager
import com.example.data.EwasteDatabase
import com.example.data.EwasteRepository
import com.example.data.TransactionLedgerEntity
import com.example.model.MaterialLot
import com.example.model.PaymentMode
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Formal-recycler portal data source.
 *
 * Reads the shared on-device Room database (the single source of truth
 * for this app instance). All roles (collector, formal recycler, government
 * admin) observe the same local rows. Cloud sync is optional best-effort;
 * the UI works fully offline.
 */
class RecyclerViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "RecyclerViewModel"
    }

    private val _lots = MutableStateFlow<List<MaterialLot>>(emptyList())
    val lots: StateFlow<List<MaterialLot>> = _lots.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

  private val _lastError = MutableStateFlow<String?>(null)
  val lastError: StateFlow<String?> = _lastError.asStateFlow()
  private val _lastPayment = MutableStateFlow<TransactionLedgerEntity?>(null)
  val lastPayment: StateFlow<TransactionLedgerEntity?> = _lastPayment.asStateFlow()

    private val repository: EwasteRepository

    init {
        val database = EwasteDatabase.getDatabase(application, viewModelScope)
        repository = EwasteRepository(
            database.materialLotDao(),
            database.priceDao(),
            database.recyclerDao(),
            database.transactionLedgerDao(),
            database.safetyGuidelineDao(),
            database.lotPhotoDao(),
            database.collectorLocationDao(),
            database.connectionRequestDao(),
            database.quotationDao(),
            database.auditLogDao()
        )
        // Observe the shared local rows; a lot created by the collector appears
        // here without any network round-trip.
        viewModelScope.launch {
            repository.allLots.collect { lots -> _lots.value = lots }
        }
    }

    /** Refresh the local cache from the cloud (best-effort). */
    fun refresh() = refreshFromCloud()

    /** Refresh the local cache from the cloud (best-effort). */
    fun refreshFromCloud() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val uid = SupabaseAuthService.getInstance(getApplication())
                    .authenticatedUser.value?.userId ?: ""
                CloudSyncManager.syncLotsAndTransactions(
                    lots = repository.unsyncedLotsNow(),
                    transactions = repository.allTransactionsNow(),
                    userId = uid
                )
                _lastError.value = null
            } catch (e: Exception) {
                _lastError.value = e.message
            } finally {
                _isSyncing.value = false
            }
        }
    }

    /** Generates a PDF receipt for the given lot and shares it. */
    private fun shareReceipt(lotId: String) {
        viewModelScope.launch {
            val lot = repository.lotById(lotId) ?: return@launch
            val txn = repository.transactionByLot(lotId)
            val ctx = getApplication<Application>()
            val dir = File(ctx.getExternalFilesDir(null), "receipts").apply { mkdirs() }
            val file = File(dir, "receipt_${lot.lotId}.pdf")
            val receiptNo = txn?.receiptNumber ?: lot.handoverReceiptNumber ?: ("RC-" + lot.lotId)
            try {
                val pdfWriter = android.graphics.pdf.PdfDocument()
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = pdfWriter.startPage(pageInfo)
                val canvas = page.canvas
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 12f
                }
                var y = 30f
                fun addLine(text: String) {
                    canvas.drawText(text, 30f, y, paint)
                    y += 20f
                }
                addLine("E-Waste Handover Receipt")
                addLine("Receipt No: $receiptNo")
                addLine("Lot ID: ${lot.lotId}")
                addLine("Category: ${lot.category.titleEn}")
                addLine("Weight: ${lot.weightKg} kg")
                addLine("Rate: Rs.${lot.quotedRatePerKg.toInt()}/kg")
                addLine("Total: Rs.${lot.estimatedValueInr.toInt()}")
                addLine("EPR Certificate: ${lot.eprCertificateNo ?: "Pending"}")
                addLine("Recycler: ${lot.matchedRecyclerName ?: "EcoReclaim Green Refineries"}")
                addLine("Date: ${android.text.format.DateFormat.format("dd-MM-yyyy", System.currentTimeMillis())}")
                pdfWriter.finishPage(page)
                pdfWriter.writeTo(FileOutputStream(file))
                pdfWriter.close()

                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(share, "Share receipt via").apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ctx.startActivity(chooser)
            } catch (e: Exception) {
                Log.w(TAG, "PDF receipt failed: ${e.message}")
                _lastError.value = "Could not generate receipt."
            }
        }
    }

    fun confirmRecyclerHandover(lotId: String, verifiedWeight: Double? = null, markPaid: Boolean = true) {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                repository.confirmRecyclerHandover(lotId, verifiedWeight, markPaid)
                shareReceipt(lotId)
                _lastError.value = null
            } catch (e: Exception) {
                _lastError.value = e.message
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun saveManifest(lotId: String, details: String) {
        viewModelScope.launch {
            try {
                repository.saveManifest(lotId, details)
                _lastError.value = null
            } catch (e: Exception) { _lastError.value = e.message }
        }
    }

    /**
     * Settles a payment to the collector for [lotId] and issues the receipt.
     *
     * For UPI the intent has already been handed to the payment app by the time
     * this runs, so the transfer is treated as done and the reference (if the user
     * typed one) is stored against the ledger row.
     */
    fun recordPayment(
        lotId: String,
        mode: PaymentMode,
        reference: String?,
        verifiedWeight: Double? = null
    ) {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val txn = repository.recordPayment(lotId, mode, reference, verifiedWeight)
                if (txn == null) {
                    _lastError.value = "Could not record payment: the lot is unknown or has no agreed rate."
                    return@launch
                }
                _lastError.value = null
                _lastPayment.value = txn
                shareReceipt(lotId)
            } catch (e: Exception) {
                _lastError.value = e.message
            } finally {
                _isSyncing.value = false
            }
        }
    }

    /** Payee details for one lot: who to pay, and the UPI id to address the request to. */
    data class Payee(
        val name: String,
        val phone: String?,
        val upiId: String?
    )

    private val _payee = MutableStateFlow<Payee?>(null)
    val payee: StateFlow<Payee?> = _payee.asStateFlow()

    private val _payeeLoading = MutableStateFlow(false)
    val payeeLoading: StateFlow<Boolean> = _payeeLoading.asStateFlow()

    /**
     * Loads the collector's payment details for [lotId].
     *
     * Uses the lot_payee_details RPC, which only answers for the recycler matched
     * to the lot, so no broad profile access is needed. Failure is not fatal: the
     * sheet simply offers cash or bank transfer instead of UPI.
     */
    fun loadPayee(lotId: String, fallbackName: String) {
        viewModelScope.launch {
            _payeeLoading.value = true
            _payee = try {
                val row = SupabaseAuthConfig.client
                    .rpc("lot_payee_details", mapOf("p_lot_id" to lotId))
                    .decodeList<PayeeDto>()
                    .firstOrNull()
                Payee(
                    name = row?.collector_name?.takeIf { it.isNotBlank() } ?: fallbackName,
                    phone = row?.collector_phone,
                    upiId = row?.upi_id
                )
            } catch (e: Exception) {
                Log.w(TAG, "Payee lookup failed for $lotId: ${e.message}")
                Payee(fallbackName, null, null)
            }
            _payeeLoading.value = false
        }
    }

    @kotlinx.serialization.Serializable
    private data class PayeeDto(
        val collector_name: String? = null,
        val collector_phone: String? = null,
        val upi_id: String? = null
    )

    fun clearPayee() {
        _payee = null
    }

    /** Clears the one-shot success banner after it has been shown. */
    fun consumePayment() {
        _lastPayment.value = null
    }

    fun pendingCount(): Int = _lots.value.count { !it.recyclerConfirmed }

    fun confirmedWeightMt(): Double =
        _lots.value.filter { it.recyclerConfirmed }.sumOf { it.weightKg } / 1000.0
}
