package com.example.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.ProfileRow
import com.example.data.CloudSyncManager
import com.example.data.toMaterialLot
import com.example.model.MaterialLot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Database-driven snapshot for the CPCB / MoEFCC oversight portal. */
data class AdminMetrics(
    val collectors: Long = 0,
    val recyclers: Long = 0,
    val admins: Long = 0,
    val lots: Long = 0,
    val pendingLots: Long = 0,
    val confirmedLots: Long = 0,
    val totalWeightKg: Double = 0.0,
    val settledValueInr: Double = 0.0,
    val formalizationPercent: Double = 0.0
)

/**
 * Government-admin portal data source.
 *
 * Reads the shared Supabase source of truth through the admin role views
 * (`v_admin_metrics`, `v_admin_lots`). Every number shown is computed by the
 * database from live rows — there is no hardcoded statistic and no per-role
 * copy of the dataset.
 */
class AdminViewModel(application: Application) : AndroidViewModel(application) {

    private val _metrics = MutableStateFlow(AdminMetrics())
    val metrics: StateFlow<AdminMetrics> = _metrics.asStateFlow()

    private val _lots = MutableStateFlow<List<MaterialLot>>(emptyList())
    val lots: StateFlow<List<MaterialLot>> = _lots.asStateFlow()

    /** Dynamic directory: every registered informal collector + their lots. */
    private val _collectors = MutableStateFlow<List<UserDirectoryEntry>>(emptyList())
    val collectors: StateFlow<List<UserDirectoryEntry>> = _collectors.asStateFlow()

    /** Dynamic directory: every registered formal recycler + routed lots. */
    private val _recyclers = MutableStateFlow<List<UserDirectoryEntry>>(emptyList())
    val recyclers: StateFlow<List<UserDirectoryEntry>> = _recyclers.asStateFlow()

    /** Cloud lots whose owner matches no registered profile (honest overflow). */
    private val _unlinkedLots = MutableStateFlow<List<MaterialLot>>(emptyList())
    val unlinkedLots: StateFlow<List<MaterialLot>> = _unlinkedLots.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val dto = CloudSyncManager.fetchAdminMetrics()
                val remoteLots = CloudSyncManager.fetchAdminLots()
                val materialLots = remoteLots.map { it.toMaterialLot() }
                _lots.value = materialLots
                buildDirectory(remoteLots, materialLots)
                if (dto != null) {
                    val formalization = if (dto.lot_count > 0L) {
                        (dto.confirmed_lot_count.toDouble() / dto.lot_count.toDouble()) * 100.0
                    } else {
                        0.0
                    }
                    _metrics.value = AdminMetrics(
                        collectors = dto.collector_count,
                        recyclers = dto.recycler_count,
                        admins = dto.admin_count,
                        lots = dto.lot_count,
                        pendingLots = dto.pending_lot_count,
                        confirmedLots = dto.confirmed_lot_count,
                        totalWeightKg = dto.total_weight_kg,
                        settledValueInr = dto.settled_value_inr,
                        formalizationPercent = formalization
                    )
                    _lastError.value = null
                } else {
                    _lastError.value = "Live admin data could not be loaded. Verify the Supabase admin profile and migrations."
                }
            } catch (e: Exception) {
                _lastError.value = e.message
            } finally {
                _isSyncing.value = false
            }
        }
    }

    /**
     * Builds the live directories from `profiles` + the shared lot registry.
     *
     * - Collectors own the lots whose `collector_user_id` equals their
     *   `auth_user_id`.
     * - Recyclers own the lots routed to them (`matched_recycler_id` equals
     *   their statutory identifier, or the routed name matches).
     * - Lots matching no profile are surfaced as unlinked instead of hidden.
     *
     * Empty when the session is not an authenticated admin (RLS) — the UI
     * says so instead of showing fabricated rows.
     */
    private suspend fun buildDirectory(
        remoteLots: List<CloudSyncManager.CollectorLotDto>,
        materialLots: List<MaterialLot>
    ) {
        val collectorProfiles = CloudSyncManager.fetchProfilesByRole("informal_collector")
        val recyclerProfiles = CloudSyncManager.fetchProfilesByRole("formal_recycler")

        val lotsByCollector = remoteLots.groupBy { it.collector_user_id }
        val knownOwnerIds = collectorProfiles.mapNotNull { it.auth_user_id }.toSet()

        _collectors.value = collectorProfiles.map { profile ->
            val lots = lotsByCollector[profile.auth_user_id].orEmpty()
                .map { it.toMaterialLot() }
            UserDirectoryEntry(profile = profile, lots = lots)
        }

        _recyclers.value = recyclerProfiles.map { profile ->
            val routed = remoteLots.filter { lot ->
                (lot.matched_recycler_id != null &&
                    lot.matched_recycler_id == profile.statutory_identifier) ||
                    (!lot.matched_recycler_name.isNullOrBlank() &&
                        (lot.matched_recycler_name == profile.display_name ||
                            lot.matched_recycler_name == profile.entity_name))
            }.map { it.toMaterialLot() }
            UserDirectoryEntry(profile = profile, lots = routed)
        }

        _unlinkedLots.value = remoteLots
            .filter { it.collector_user_id !in knownOwnerIds }
            .map { it.toMaterialLot() }
    }
}

/** One registered user plus the live lots attached to them. */
data class UserDirectoryEntry(
    val profile: ProfileRow,
    val lots: List<MaterialLot> = emptyList()
) {
    val lotCount: Int get() = lots.size
    val totalKg: Double get() = lots.sumOf { it.weightKg }
    val totalValueInr: Double get() = lots.sumOf { it.estimatedValueInr }
    val displayName: String get() =
        profile.display_name?.ifBlank { null }
            ?: profile.entity_name?.ifBlank { null }
            ?: profile.email?.substringBefore("@")
            ?: "Unnamed account"
    /** Privacy-preserving: middle digits of the phone are masked. */
    val maskedPhone: String get() {
        val digits = profile.phone_number?.filter { it.isDigit() }?.takeLast(10) ?: return "—"
        return if (digits.length == 10) "+91 ${digits.take(2)}•••••${digits.takeLast(3)}" else "+91 $digits"
    }
}
