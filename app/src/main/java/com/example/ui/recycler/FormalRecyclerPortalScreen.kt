package com.example.ui.recycler

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.Language
import com.example.model.MaterialLot
import com.example.ui.theme.BackgroundCream
import com.example.ui.theme.EcoBlueAccent
import com.example.ui.theme.EcoBlueBg
import com.example.ui.theme.EcoGreenBright
import com.example.ui.theme.EcoGreenDeep
import com.example.ui.theme.EcoGreenPrimary
import com.example.ui.theme.EcoMint
import com.example.ui.theme.EcoMintLight
import com.example.ui.theme.EcoOrangeAccent
import com.example.ui.theme.EcoOrangeBg
import com.example.ui.theme.EcoTextPrimary
import com.example.ui.theme.EcoTextSecondary
import com.example.ui.theme.EcoWhite
import com.example.ui.theme.MintBorder
import com.example.ui.theme.MintLight
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryMuted
import com.example.ui.theme.WarningAmber
import com.example.ui.voice.BottomsFloatingDockInset
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormalRecyclerPortalScreen(
    viewModel: RecyclerViewModel,
    facilityName: String,
    cpcbNumber: String,
    language: Language,
    onBack: () -> Unit
) {
    val lots by viewModel.lots.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val pendingCount = lots.count { !it.recyclerConfirmed }
    val confirmedMt = lots.filter { it.recyclerConfirmed }.sumOf { it.weightKg } / 1000.0
    val totalWeightKg = lots.sumOf { it.weightKg }
    val totalCollectors = lots.map { it.collectionLocation }.filter { it.isNotBlank() }.distinct().count()

    var recyclerTab by remember { mutableIntStateOf(0) } // 0 Dashboard, 1 Inbound Lots, 2 Weigh-in, 3 Reports, 4 Profile
    var showQrDialog by remember { mutableStateOf(false) }
    var showManualWeighIn by remember { mutableStateOf(false) }
    var manifestLot by remember { mutableStateOf<MaterialLot?>(null) }
    var recyclerLotFilter by remember { mutableStateOf<String?>(null) } // null All, "awaiting", "verified"
    var showLotFilterMenu by remember { mutableStateOf(false) }

    val displayLots = when (recyclerLotFilter) {
        "awaiting" -> lots.filter { !it.recyclerConfirmed }
        "verified" -> lots.filter { it.recyclerConfirmed }
        else -> lots
    }

    // Lift the global voice dock above this screen's bottom navigation bar.
    androidx.compose.runtime.DisposableEffect(Unit) {
        BottomsFloatingDockInset.height.value = 84.dp
        onDispose {
            BottomsFloatingDockInset.height.value = 0.dp
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Recycler Inbound Weigh-In",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = EcoTextPrimary
                        )
                        Text(
                            text = "CPCB Authenticated",
                            fontSize = 11.sp,
                            color = EcoTextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("recycler_nav_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = EcoTextSecondary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        modifier = Modifier.testTag("recycler_refresh")
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = EcoGreenPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = EcoGreenPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 6.dp
            ) {
                val recyclerTabs = listOf(
                    Triple("Dashboard", Icons.Default.Home, 0),
                    Triple("Inbound Lots", Icons.Default.Inventory2, 1),
                    Triple("Weigh-in", Icons.Default.Straighten, 2),
                    Triple("Reports", Icons.Default.Description, 3),
                    Triple("Profile", Icons.Default.Person, 4)
                )
                recyclerTabs.forEach { (label, icon, index) ->
                    val selected = recyclerTab == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { recyclerTab = index },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                modifier = Modifier.size(22.dp),
                                tint = if (selected) EcoGreenPrimary else EcoTextSecondary
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) EcoGreenPrimary else EcoTextSecondary
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = EcoGreenPrimary,
                            selectedTextColor = EcoGreenPrimary,
                            indicatorColor = EcoMint
                        ),
                        modifier = Modifier.testTag("recycler_tab_$index")
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(EcoMintLight)
                .padding(padding)
        ) {
        when (recyclerTab) {
            0 -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            // ----- Identity card: facility + CPCB ID + online state -----
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = EcoGreenPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Recycler Dashboard",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Default.Factory, contentDescription = null, tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "CPCB Registered Recycler",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = facilityName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "CPCB ID: $cpcbNumber",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.White.copy(alpha = 0.16f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(if (isSyncing) Color(0xFFFFD54F) else EcoGreenBright)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isSyncing) "System Active • syncing" else "Online • System Active",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ----- 2 x 2 dashboard stat cards (real derived values) -----
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RecyclerStatCard(
                            modifier = Modifier.weight(1f),
                            label = "Active Inbound Lots",
                            value = "$pendingCount",
                            subtitle = "Awaiting weigh-in",
                            bgColor = EcoMint,
                            valueColor = EcoGreenPrimary
                        )
                        RecyclerStatCard(
                            modifier = Modifier.weight(1f),
                            label = "EPR Credits Accrued",
                            value = String.format(Locale.US, "%.2f MT", confirmedMt),
                            subtitle = "Verified weight",
                            bgColor = EcoBlueBg,
                            valueColor = EcoBlueAccent
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RecyclerStatCard(
                            modifier = Modifier.weight(1f),
                            label = "Total Weight Received",
                            value = "${totalWeightKg.toInt()} kg",
                            subtitle = "All lots",
                            bgColor = EcoGreenDeep,
                            valueColor = Color.White,
                            labelColor = Color.White.copy(alpha = 0.8f),
                            subtitleColor = EcoGreenBright
                        )
                        RecyclerStatCard(
                            modifier = Modifier.weight(1f),
                            label = "Total Collectors",
                            value = "$totalCollectors",
                            subtitle = "Partner Collectors",
                            bgColor = EcoOrangeBg,
                            valueColor = EcoOrangeAccent
                        )
                    }
                }
            }

            // ----- Two action cards: QR handover + manual weigh-in -----
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = EcoGreenPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .height(132.dp)
                            .clickable { showQrDialog = true }
                            .testTag("recycler_scan_qr_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode2,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Scan Collector Handover QR",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Text(
                                text = "Instant digital scale sync & automatic EPR credit logging",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 9.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = EcoWhite),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(132.dp)
                            .clickable { showManualWeighIn = true }
                            .testTag("recycler_manual_weigh_in_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = EcoGreenPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Manual Weigh-In",
                                color = EcoTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Enter lot details manually",
                                color = EcoTextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Collector Lots Awaiting Scale Weigh-In",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimaryDark,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        Text(
                            text = "Filter",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = EcoGreenPrimary,
                            modifier = Modifier
                                .clickable { showLotFilterMenu = true }
                                .padding(4.dp)
                        )
                        DropdownMenu(
                            expanded = showLotFilterMenu,
                            onDismissRequest = { showLotFilterMenu = false }
                        ) {
                            listOf("All" to null, "Awaiting weigh-in" to "awaiting", "Verified" to "verified").forEach { (label, key) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = label,
                                            fontSize = 13.sp,
                                            fontWeight = if (recyclerLotFilter == key) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        recyclerLotFilter = key
                                        showLotFilterMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (displayLots.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recycler_empty_state")
                    ) {
                        Text(
                            text = "No inbound lots yet. A record created by the informal collector appears here automatically after the next refresh.",
                            fontSize = 12.sp,
                            color = TextSecondaryMuted,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            items(displayLots) { lot ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recycler_lot_card_${lot.lotId.lowercase()}")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = lot.lotId, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = EcoGreenPrimary)
                                Text(text = "${lot.category.getTitle(language)} (${lot.weightKg} kg)", fontSize = 13.sp, color = TextPrimaryDark)
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (lot.recyclerConfirmed) SuccessGreen.copy(alpha = 0.15f) else WarningAmber.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (lot.recyclerConfirmed) "VERIFIED" else "AWAITING WEIGH-IN",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (lot.recyclerConfirmed) SuccessGreen else WarningAmber,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Collection Point: ${lot.collectionLocation}", fontSize = 11.sp, color = TextSecondaryMuted)
                            Text(text = "Value: ₹${lot.estimatedValueInr.toInt()}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = EcoGreenPrimary)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (!lot.recyclerConfirmed) {
                            var verifiedWeight by remember(lot.lotId) { mutableStateOf(lot.weightKg.toString()) }
                            val parsedWeight = verifiedWeight.replace(",", ".").toDoubleOrNull()?.takeIf { it > 0.0 }
                            OutlinedTextField(
                                value = verifiedWeight,
                                onValueChange = { verifiedWeight = it },
                                label = { Text("Manually entered weight at scale (kg)", fontSize = 11.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("recycler_weight_${lot.lotId}")
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.confirmRecyclerHandover(lot.lotId, verifiedWeight = parsedWeight, markPaid = false) },
                                enabled = parsedWeight != null,
                                colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("recycler_confirm_${lot.lotId}")
                            ) {
                                Text("Verify Weight at Scale", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "EPR Traceability Certificate Issued (${lot.eprCertificateNo ?: "CPCB-EPR"})", fontSize = 11.sp, color = SuccessGreen, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(140.dp))
            }
        }
            1 -> RecyclerInboundLotsTab(
                lots = lots,
                language = language,
                onOpenWeighIn = { recyclerTab = 2 },
                onOpenManifest = { manifestLot = it }
            )
            2 -> RecyclerWeighInTab(
                lots = lots,
                language = language,
                onConfirm = { lotId, weight ->
                    viewModel.confirmRecyclerHandover(lotId, verifiedWeight = weight, markPaid = false)
                }
            )
            3 -> RecyclerReportsTab(
                lots = lots,
                language = language
            )
            4 -> RecyclerProfileTab(
                facilityName = facilityName,
                cpcbNumber = cpcbNumber,
                onSignOut = onBack
            )
        }
        }
    }

    if (showQrDialog) {
        HandoverQrPickerDialog(
            lots = lots.filter { !it.recyclerConfirmed },
            onWeighIn = { lotId ->
                showQrDialog = false
                viewModel.confirmRecyclerHandover(lotId, markPaid = true)
            },
            onDismiss = { showQrDialog = false }
        )
    }

    if (showManualWeighIn) {
        ManualWeighInDialog(
            lots = lots.filter { !it.recyclerConfirmed },
            onConfirm = { lotId, weight ->
                showManualWeighIn = false
                viewModel.confirmRecyclerHandover(lotId, verifiedWeight = weight, markPaid = false)
            },
            onDismiss = { showManualWeighIn = false }
        )
    }
    manifestLot?.let { lot -> EwasteManifestDialog(lot, { manifestLot = null }) { details ->
        viewModel.saveManifest(lot.lotId, details)
        manifestLot = null
    } }
}

// ---------------------------------------------------------------------------
// Recycler bottom-navigation tabs
// ---------------------------------------------------------------------------

@Composable
private fun RecyclerInboundLotsTab(
    lots: List<MaterialLot>,
    language: Language,
    onOpenWeighIn: () -> Unit,
    onOpenManifest: (MaterialLot) -> Unit
) {
    val pending = lots.count { !it.recyclerConfirmed }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = EcoGreenPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Inbound Lots",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "$pending awaiting weigh-in • ${lots.size} total inbound",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onOpenWeighIn,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Go to Weigh-In",
                            color = EcoGreenPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        if (lots.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No inbound lots yet. A record created by the informal collector appears here automatically after the next refresh.",
                        fontSize = 12.sp,
                        color = TextSecondaryMuted,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        items(lots) { lot ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("inbound_lot_${lot.lotId.lowercase()}")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = lot.lotId, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = EcoGreenPrimary)
                            Text(text = "${lot.category.getTitle(language)} (${lot.weightKg} kg)", fontSize = 12.sp, color = TextPrimaryDark)
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (lot.recyclerConfirmed) SuccessGreen.copy(alpha = 0.15f) else WarningAmber.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (lot.recyclerConfirmed) "VERIFIED" else "AWAITING WEIGH-IN",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (lot.recyclerConfirmed) SuccessGreen else WarningAmber,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Collection Point: ${lot.collectionLocation}", fontSize = 11.sp, color = TextSecondaryMuted)
                        Text(text = "Value: ₹${lot.estimatedValueInr.toInt()}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = EcoGreenPrimary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { onOpenManifest(lot) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Description, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (lot.manifestDetails.isNullOrBlank()) "Fill Form-6 Manifest" else "View / Update Form-6", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EwasteManifestDialog(lot: MaterialLot, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var sender by remember { mutableStateOf(lot.collectionLocation) }
    var transporter by remember { mutableStateOf("") }
    var vehicle by remember { mutableStateOf("") }
    var receiver by remember { mutableStateOf(lot.matchedRecyclerName ?: "") }
    var authorization by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf(lot.manifestDetails ?: "") }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = EcoWhite)) {
            Column(modifier = Modifier.padding(18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("E-Waste Manifest — Form 6", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = EcoGreenPrimary)
                Text("Lot ${lot.lotId} • ${lot.category.titleEn} • ${lot.weightKg} kg", fontSize = 12.sp, color = EcoTextSecondary)
                ManifestInput("Sender name & address", sender) { sender = it }
                ManifestInput("Transporter name & address", transporter) { transporter = it }
                ManifestInput("Vehicle type / registration number", vehicle) { vehicle = it }
                ManifestInput("Receiver / recycler name & address", receiver) { receiver = it }
                ManifestInput("Receiver authorization number", authorization) { authorization = it }
                ManifestInput("Handover notes", notes, false) { notes = it }
                Button(onClick = { onSave("Form-6 E-Waste Manifest\nSender: $sender\nTransporter: $transporter\nVehicle: $vehicle\nReceiver: $receiver\nReceiver authorization: $authorization\nE-waste: ${lot.category.titleEn}; ${lot.subCategory}; ${lot.weightKg} kg\nNotes: $notes") }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary)) { Text("Save & share with collector") }
            }
        }
    }
}

@Composable
private fun ManifestInput(label: String, value: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label, fontSize = 11.sp) }, singleLine = singleLine, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun RecyclerWeighInTab(
    lots: List<MaterialLot>,
    language: Language,
    onConfirm: (String, Double?) -> Unit
) {
    val pending = lots.filter { !it.recyclerConfirmed }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Scale Weigh-In & Verification",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextPrimaryDark
            )
            Text(
                text = "${pending.size} lots awaiting verified scale weight",
                fontSize = 12.sp,
                color = TextSecondaryMuted
            )
        }

        if (pending.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "All inbound lots are weight-verified. No pending weigh-ins.",
                        fontSize = 13.sp,
                        color = TextSecondaryMuted,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        items(pending) { lot ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("weighin_lot_${lot.lotId.lowercase()}")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = lot.lotId, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = EcoGreenPrimary)
                            Text(text = "${lot.category.getTitle(language)} (${lot.weightKg} kg)", fontSize = 12.sp, color = TextPrimaryDark)
                        }
                        Text(text = "Value: ₹${lot.estimatedValueInr.toInt()}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = EcoGreenPrimary)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    var verifiedWeight by remember(lot.lotId) { mutableStateOf(lot.weightKg.toString()) }
                    val parsedWeight = verifiedWeight.replace(",", ".").toDoubleOrNull()?.takeIf { it > 0.0 }
                    OutlinedTextField(
                        value = verifiedWeight,
                        onValueChange = { verifiedWeight = it },
                        label = { Text("Manually entered weight at scale (kg)", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onConfirm(lot.lotId, parsedWeight) },
                        enabled = parsedWeight != null,
                        colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Verify Weight at Scale",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecyclerReportsTab(
    lots: List<MaterialLot>,
    language: Language
) {
    val confirmed = lots.filter { it.recyclerConfirmed }
    val confirmedMt = confirmed.sumOf { it.weightKg } / 1000.0
    val pendingMt = lots.filter { !it.recyclerConfirmed }.sumOf { it.weightKg } / 1000.0
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "EPR & Weight Reports",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextPrimaryDark
            )
            Text(
                text = "Auto-generated from verified weigh-in records",
                fontSize = 12.sp,
                color = TextSecondaryMuted
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RecyclerStatCard(
                        modifier = Modifier.weight(1f),
                        label = "Total Inbound",
                        value = "${lots.size}",
                        subtitle = "Lots",
                        bgColor = EcoMint,
                        valueColor = EcoGreenPrimary
                    )
                    RecyclerStatCard(
                        modifier = Modifier.weight(1f),
                        label = "Weight Verified",
                        value = "${confirmed.size}",
                        subtitle = "Lots",
                        bgColor = EcoBlueBg,
                        valueColor = EcoBlueAccent
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RecyclerStatCard(
                        modifier = Modifier.weight(1f),
                        label = "EPR Credits Accrued",
                        value = String.format(Locale.US, "%.2f", confirmedMt) + " MT",
                        subtitle = "Verified weight",
                        bgColor = EcoGreenDeep,
                        valueColor = Color.White,
                        labelColor = Color.White.copy(alpha = 0.8f),
                        subtitleColor = EcoGreenBright
                    )
                    RecyclerStatCard(
                        modifier = Modifier.weight(1f),
                        label = "Awaiting Weigh-In",
                        value = String.format(Locale.US, "%.2f", pendingMt) + " MT",
                        subtitle = "Pending lots",
                        bgColor = EcoOrangeBg,
                        valueColor = EcoOrangeAccent
                    )
                }
            }
        }
        if (confirmed.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No EPR traceability certificates issued yet. Complete a weigh-in to generate the first certificate.",
                        fontSize = 12.sp,
                        color = TextSecondaryMuted,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
        items(confirmed) { lot ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("report_${lot.lotId.lowercase()}")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = lot.lotId, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = EcoGreenPrimary)
                            Text(text = "EPR Traceability Certificate (${lot.eprCertificateNo ?: "CPCB-EPR"})", fontSize = 11.sp, color = TextSecondaryMuted)
                        }
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecyclerProfileTab(
    facilityName: String,
    cpcbNumber: String,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = EcoGreenPrimary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🏭", fontSize = 26.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = facilityName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "CPCB Registered Recycler",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Facility Name", fontSize = 12.sp, color = TextSecondaryMuted)
                    Text(text = facilityName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "CPCB ID", fontSize = 12.sp, color = TextSecondaryMuted)
                    Text(text = cpcbNumber, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EcoGreenPrimary)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Role", fontSize = 12.sp, color = TextSecondaryMuted)
                    Text(text = "Formal Recycler", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                }
            }
        }
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "All inbound-queue, weigh-in and EPR records are stored offline-first on this device and optionally pushed to the CPCB platform.",
                fontSize = 12.sp,
                color = TextSecondaryMuted,
                modifier = Modifier.padding(16.dp)
            )
        }
        Button(
            onClick = onSignOut,
            colors = ButtonDefaults.buttonColors(
                containerColor = EcoOrangeAccent,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("recycler_sign_out")
        ) {
            Text(text = "Sign Out", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun RecyclerStatCard(
    modifier: Modifier,
    label: String,
    value: String,
    subtitle: String,
    bgColor: Color,
    valueColor: Color,
    labelColor: Color = EcoTextSecondary,
    subtitleColor: Color = EcoTextSecondary
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                color = labelColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                color = valueColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
            )
            Text(
                text = subtitle,
                color = subtitleColor,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Handover-QR flow. This offline-first build has no camera QR decoder, so the
 * card opens the in-app handover codes produced by the collector's digital
 * receipts; selecting one runs the identical verified weigh-in + EPR receipt
 * path. A camera QR reader (ML Kit / ZXing) is a follow-up enhancement.
 */
@Composable
private fun HandoverQrPickerDialog(
    lots: List<MaterialLot>,
    onWeighIn: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable { onDismiss() }
        )
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(24.dp),
            color = EcoWhite,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .testTag("handover_qr_picker")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Scan Collector Handover QR",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = EcoTextPrimary
                        )
                        Text(
                            text = "Handover codes from collector digital receipts",
                            fontSize = 11.sp,
                            color = EcoTextSecondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = EcoTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (lots.isEmpty()) {
                    Text(
                        text = "No lots awaiting handover. Records from informal collectors appear automatically after refresh.",
                        fontSize = 13.sp,
                        color = EcoTextSecondary,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(lots) { lot ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = EcoMint),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(EcoGreenPrimary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = lot.category.iconEmoji, fontSize = 18.sp)
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Code: ${lot.handoverReceiptNumber ?: "RC-${lot.lotId}"}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = EcoTextPrimary
                                        )
                                        Text(
                                            text = "${lot.lotId} • ${lot.weightKg} kg",
                                            fontSize = 11.sp,
                                            color = EcoTextSecondary
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = EcoGreenPrimary,
                                        modifier = Modifier.clickable { onWeighIn(lot.lotId) }
                                    ) {
                                        Text(
                                            text = "Scan & Pay",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualWeighInDialog(
    lots: List<MaterialLot>,
    onConfirm: (String, Double?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedLotId by remember { mutableStateOf<String?>(lots.firstOrNull()?.lotId) }
    var weightText by remember { mutableStateOf(lots.firstOrNull()?.weightKg?.toString() ?: "") }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable { onDismiss() }
        )
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(24.dp),
            color = EcoWhite,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(24.dp)
                .testTag("manual_weigh_in_dialog")
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Manual Weigh-In",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = EcoTextPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Lot picker
                var expanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = EcoMint,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = lots.firstOrNull { it.lotId == selectedLotId }?.lotId ?: "Select lot",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = EcoTextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = EcoTextSecondary
                            )
                        }
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        lots.forEach { lot ->
                            DropdownMenuItem(
                                text = { Text("${lot.lotId} • ${lot.category.getTitle(Language.ENGLISH)}", fontSize = 13.sp) },
                                onClick = {
                                    selectedLotId = lot.lotId
                                    weightText = lot.weightKg.toString()
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = { Text("Weight at scale (kg)", fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                val parsedWeight = weightText.replace(",", ".").toDoubleOrNull()?.takeIf { it > 0.0 }
                Button(
                    onClick = { selectedLotId?.let { onConfirm(it, parsedWeight) } },
                    enabled = selectedLotId != null && parsedWeight != null,
                    colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Verify Weight", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}
