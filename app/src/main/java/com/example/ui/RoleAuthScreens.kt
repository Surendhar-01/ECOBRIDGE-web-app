package com.example.ui

import android.app.Application
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.i18n.instructionsRes
import com.example.i18n.titleRes
import com.example.auth.AuthErrorCode
import com.example.auth.AuthMethod
import com.example.auth.AuthPipelineStep
import com.example.auth.GoogleAuthManager
import com.example.auth.OtpDeliveryDestination
import com.example.auth.SupabaseAuthConfig
import com.example.auth.SupabaseAuthService
import com.example.model.Language
import com.example.model.RoleType
import com.example.ui.admin.AdminViewModel
import com.example.ui.admin.GovernmentAdminPortalScreen
import com.example.ui.collector.CollectorDashboardScreen
import com.example.ui.collector.CollectorDashboardViewModel
import com.example.ui.components.ResendTimerView
import com.example.ui.components.SixDigitOtpInputField
import com.example.ui.recycler.FormalRecyclerPortalScreen
import com.example.ui.recycler.RecyclerViewModel
import com.example.ui.theme.BackgroundCream
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.ForestGreenDark
import com.example.ui.theme.ForestGreenPrimary
import com.example.ui.theme.MintBorder
import com.example.ui.theme.MintLight
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryMuted
import com.example.voice.AuthUiAction
import com.example.voice.VoiceEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Unified authentication screen for all three roles:
 * - Sign Up for collectors/recyclers (registration stored in Supabase;
 *   admin accounts are provisioned centrally — no self-registration)
 * - Email & Password sign-in with REGISTERED credentials only
 * - OTP second factor (demo on-device code shown in-app / real SMS hook)
 * - Continue with Google (Credential Manager + Supabase ID token exchange)
 *
 * Required flow: Sign Up -> Supabase -> Sign In -> OTP -> role check -> dashboard.
 * Unregistered credentials always fail; the dashboard opens only after the role
 * stored in `profiles` matches this portal.
 */
private enum class AuthStage {
    CREDENTIALS,
    SIGN_UP,
    OTP_CHALLENGE
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedRoleAuthScreen(
    role: RoleType,
    language: Language,
    voiceEngine: VoiceEngine? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authService = remember { SupabaseAuthService.getInstance(context) }
    val googleAuthManager = remember { GoogleAuthManager(context) }

    // Authentication States
    // All roles offer the Mobile OTP (phone-first) panel plus Email + Google.
    // Demo mode generates the OTP on-device and shows it in-app (no SMS);
    // sms mode sends a real provider-backed code.
    val demoMode = authService.isDemoOtpMode()
    var selectedAuthMethod by remember {
        mutableStateOf(AuthMethod.OTP_AUTHENTICATION)
    }
    var authStage by remember { mutableStateOf(AuthStage.CREDENTIALS) }
    var phoneNumber by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    // Sign-up form state (per-role registration).
    var signupName by remember { mutableStateOf("") }
    var signupMobile by remember { mutableStateOf("") }
    var signupEmail by remember { mutableStateOf("") }
    var signupPassword by remember { mutableStateOf("") }
    var signupConfirm by remember { mutableStateOf("") }
    var signupExtra1 by remember { mutableStateOf("") }
    var signupExtra2 by remember { mutableStateOf("") }

    var isOtpRequested by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    var resendCountdown by remember { mutableIntStateOf(0) }

    val currentStep by authService.currentPipelineStep.collectAsState()
    val authenticatedUser by authService.authenticatedUser.collectAsState()
    val devDisplayOtp by authService.devDisplayOtp.collectAsState()

    // Resend countdown timer loop (legacy phone tab + OTP challenge stage).
    LaunchedEffect(isOtpRequested, authStage) {
        if (isOtpRequested || authStage == AuthStage.OTP_CHALLENGE) {
            resendCountdown = authService.getResendCooldownRemaining().toInt().coerceAtLeast(45)
            while (resendCountdown > 0) {
                delay(1000L)
                resendCountdown--
            }
        }
    }

    // Role-specific Branding & Metadata
    val roleTitle = stringResource(role.titleRes())
    val roleIcon: ImageVector = when (role) {
        RoleType.INFORMAL_COLLECTOR -> Icons.Default.Recycling
        RoleType.FORMAL_RECYCLER -> Icons.Default.Factory
        RoleType.GOVERNMENT_ADMIN -> Icons.Default.Shield
    }

    val statutorySubtitle = when (role) {
        RoleType.INFORMAL_COLLECTOR -> stringResource(R.string.auth_portal_aggregator_gateway)
        RoleType.FORMAL_RECYCLER -> stringResource(R.string.auth_portal_treatment_facility)
        RoleType.GOVERNMENT_ADMIN -> stringResource(R.string.auth_portal_epr_oversight)
    }

    // Reusable OTP send action (button and voice both use it). Never stores or speaks the OTP.
    val sendOtpAction: () -> Unit = {
        coroutineScope.launch {
            if (phoneNumber.length != 10) {
                errorMessage = stringResource(R.string.err_valid_mobile)
                voiceEngine?.speak(errorMessage ?: "", language)
                return@launch
            }
            isVerifying = true
            errorMessage = null
            infoMessage = null
            val result = authService.generateAndSendOtp(
                destination = OtpDeliveryDestination.MOBILE_SMS,
                phoneNumber = phoneNumber,
                targetRole = role
            )
            isVerifying = false
            result.onSuccess {
                isOtpRequested = true
                otpCode = ""
                if (authService.isDemoOtpMode()) {
                    infoMessage = stringResource(R.string.msg_demo_otp_auto)
                    voiceEngine?.speak(
                        stringResource(R.string.msg_otp_on_screen),
                        language
                    )
                    // Auto sign-in: the demo code is already on screen. Wait 2s
                    // so the user sees it arrive, then fill it in — the OTP
                    // card watcher submits it automatically (smooth sign-in).
                    // Skipped if the user already typed a complete code.
                    coroutineScope.launch {
                        delay(2000L)
                        val liveCode = authService.devDisplayOtp.value
                        if (isOtpRequested && liveCode?.length == 6 && otpCode.length != 6) {
                            otpCode = liveCode
                        }
                    }
                } else {
                    infoMessage = stringResource(R.string.msg_code_sent_sms, phoneNumber)
                    voiceEngine?.speak(
                        stringResource(R.string.msg_otp_sms_prompt),
                        language
                    )
                }
            }.onFailure { err ->
                errorMessage = err.localizedMessage ?: stringResource(R.string.msg_code_send_failed)
                voiceEngine?.speak(errorMessage ?: "", language)
                isOtpRequested = false
            }
        }
    }

    // Reusable OTP verify action (button and voice both use it).
    val verifyOtpAction: () -> Unit = {
        if (otpCode.length != 6) {
            errorMessage = stringResource(R.string.err_otp_incomplete)
            voiceEngine?.speak(errorMessage ?: "", language)
        } else {
            isVerifying = true
            errorMessage = null
            coroutineScope.launch {
                val res = authService.verifyOtpAndLogin(
                    destination = OtpDeliveryDestination.MOBILE_SMS,
                    phoneNumber = phoneNumber,
                    enteredOtp = otpCode,
                    targetRole = role
                )
                isVerifying = false
                if (!res.isSuccess) {
                    errorMessage = res.errorMessage ?: stringResource(R.string.err_verification_failed)
                    voiceEngine?.speak(
                        "Verification failed. ${res.errorMessage ?: "Please try again."}",
                        language
                    )
                } else {
                    infoMessage = stringResource(R.string.msg_verified_granted)
                    voiceEngine?.speak(
                        stringResource(R.string.auth_access_granted, roleTitle),
                        language
                    )
                }
            }
        }
    }

    // Sign in with REGISTERED email + password. Supabase rejects unknown
    // accounts and wrong passwords; on success the OTP challenge stage opens.
    val credentialsLoginAction: () -> Unit = {
        if (!emailInput.contains("@") || emailInput.length < 5) {
            errorMessage = stringResource(R.string.err_valid_email)
            voiceEngine?.speak(errorMessage ?: "", language)
        } else if (passwordInput.length < 6) {
            errorMessage = stringResource(R.string.err_password_min_short)
            voiceEngine?.speak(errorMessage ?: "", language)
        } else {
            isVerifying = true
            errorMessage = null
            infoMessage = null
            coroutineScope.launch {
                val res = authService.loginWithEmail(
                    email = emailInput,
                    password = passwordInput,
                    targetRole = role
                )
                isVerifying = false
                if (res.awaitingOtp) {
                    authStage = AuthStage.OTP_CHALLENGE
                    otpCode = ""
                    resendCountdown = authService.getResendCooldownRemaining().toInt().coerceAtLeast(45)
                    infoMessage = res.infoMessage ?: stringResource(R.string.msg_credentials_verified)
                    voiceEngine?.speak(
                        stringResource(R.string.msg_credentials_demo),
                        language
                    )
                    if (authService.isDemoOtpMode()) {
                        // Auto sign-in: wait 2s so the code is seen arriving,
                        // then fill it — the challenge card watcher submits it.
                        coroutineScope.launch {
                            delay(2000L)
                            val liveCode = authService.devDisplayOtp.value
                            if (authStage == AuthStage.OTP_CHALLENGE &&
                                liveCode?.length == 6 && otpCode.length != 6
                            ) {
                                otpCode = liveCode
                            }
                        }
                    }
                } else if (!res.isSuccess) {
                    errorMessage = res.errorMessage ?: stringResource(R.string.err_authentication_failed)
                    voiceEngine?.speak(errorMessage ?: "", language)
                }
            }
        }
    }

    // Verify the OTP second factor for the pending login.
    val verifyChallengeAction: () -> Unit = {
        if (otpCode.length != 6) {
            errorMessage = stringResource(R.string.err_otp_incomplete)
            voiceEngine?.speak(errorMessage ?: "", language)
        } else {
            isVerifying = true
            errorMessage = null
            coroutineScope.launch {
                val res = authService.verifyLoginOtp(
                    enteredOtp = otpCode,
                    targetRole = role
                )
                isVerifying = false
                if (!res.isSuccess) {
                    errorMessage = res.errorMessage ?: stringResource(R.string.err_verification_failed)
                    voiceEngine?.speak(
                        "Verification failed. ${res.errorMessage ?: "Please try again."}",
                        language
                    )
                } else {
                    infoMessage = stringResource(R.string.msg_verified_granted)
                    voiceEngine?.speak(
                        stringResource(R.string.auth_access_granted, roleTitle),
                        language
                    )
                }
            }
        }
    }

    // Resend the OTP for the pending login.
    val resendChallengeAction: () -> Unit = {
        coroutineScope.launch {
            isVerifying = true
            errorMessage = null
            val result = authService.resendLoginOtp()
            isVerifying = false
            result.onSuccess {
                otpCode = ""
                resendCountdown = authService.getResendCooldownRemaining().toInt().coerceAtLeast(45)
                infoMessage = if (authService.isDemoOtpMode()) {
                    stringResource(R.string.msg_demo_otp_new_auto)
                } else {
                    stringResource(R.string.msg_code_resent)
                }
                if (authService.isDemoOtpMode()) {
                    // Auto sign-in on the fresh demo code, same as first send:
                    // wait 2s, fill, watcher submits.
                    coroutineScope.launch {
                        delay(2000L)
                        val liveCode = authService.devDisplayOtp.value
                        if (authStage == AuthStage.OTP_CHALLENGE &&
                            liveCode?.length == 6 && otpCode.length != 6 &&
                            !isVerifying
                        ) {
                            otpCode = liveCode
                        }
                    }
                }
            }.onFailure { err ->
                errorMessage = err.localizedMessage ?: stringResource(R.string.msg_resend_failed)
            }
        }
    }

    // Abandon the pending login and return to the credentials form.
    val cancelChallengeAction: () -> Unit = {
        authService.cancelPendingLogin()
        authStage = AuthStage.CREDENTIALS
        otpCode = ""
        passwordInput = ""
        errorMessage = null
        infoMessage = null
    }

    // Register a new account in Supabase for this portal's role.
    val signUpAction: () -> Unit = {
        if (signupName.trim().length < 3) {
            errorMessage = stringResource(R.string.err_full_name_required)
        } else if (signupMobile.filter { it.isDigit() }.length != 10) {
            errorMessage = stringResource(R.string.err_valid_mobile)
        } else if (!signupEmail.contains("@") || signupEmail.length < 5) {
            errorMessage = stringResource(R.string.err_valid_email)
        } else if (signupPassword.length < 6) {
            errorMessage = stringResource(R.string.err_password_min_short)
        } else if (signupPassword != signupConfirm) {
            errorMessage = stringResource(R.string.err_password_mismatch)
        } else if (role == RoleType.FORMAL_RECYCLER &&
            (signupExtra1.isBlank() || signupExtra2.isBlank())
        ) {
            errorMessage = stringResource(R.string.err_facility_required)
        } else if (role == RoleType.GOVERNMENT_ADMIN && signupExtra2.isBlank()) {
            errorMessage = stringResource(R.string.err_department_required)
        } else {
            isVerifying = true
            errorMessage = null
            infoMessage = null
            coroutineScope.launch {
                val res = authService.signUpWithEmail(
                    fullName = signupName,
                    phoneNumber = signupMobile,
                    email = signupEmail,
                    password = signupPassword,
                    targetRole = role,
                    entityName = if (role == RoleType.FORMAL_RECYCLER) signupExtra1
                    else if (role == RoleType.GOVERNMENT_ADMIN) "CPCB" else "",
                    statutoryIdentifier = signupExtra2,
                    areaLabel = if (role == RoleType.INFORMAL_COLLECTOR) signupExtra1 else ""
                )
                isVerifying = false
                if (res.isSuccess) {
                    authStage = AuthStage.CREDENTIALS
                    emailInput = signupEmail.trim()
                    passwordInput = ""
                    signupPassword = ""
                    signupConfirm = ""
                    infoMessage = res.infoMessage ?: stringResource(R.string.msg_account_created)
                    voiceEngine?.speak(
                        stringResource(R.string.msg_account_created_full),
                        language
                    )
                } else {
                    errorMessage = res.errorMessage ?: stringResource(R.string.err_registration_failed)
                    voiceEngine?.speak(errorMessage ?: "", language)
                }
            }
        }
    }

    // Execute voice-driven login-screen actions. SEND_OTP / VERIFY_OTP never carry the code value.
    LaunchedEffect(voiceEngine) {
        voiceEngine?.router?.authEvents?.collect { action ->
            when (action) {
                AuthUiAction.SelectMobileLogin -> {
                    selectedAuthMethod = AuthMethod.OTP_AUTHENTICATION
                    authStage = AuthStage.CREDENTIALS
                }
                AuthUiAction.SelectEmailLogin -> {
                    selectedAuthMethod = AuthMethod.EMAIL_PASSWORD
                    authStage = AuthStage.CREDENTIALS
                }
                AuthUiAction.SelectGoogleLogin -> selectedAuthMethod = AuthMethod.GOOGLE_OAUTH
                AuthUiAction.SendOtp -> if (authStage == AuthStage.OTP_CHALLENGE) {
                    resendChallengeAction()
                } else if (selectedAuthMethod == AuthMethod.OTP_AUTHENTICATION ||
                    selectedAuthMethod == AuthMethod.MOBILE_OTP
                ) {
                    sendOtpAction()
                } else {
                    voiceEngine?.speak(
                        stringResource(R.string.err_signin_before_otp),
                        language
                    )
                }
                AuthUiAction.VerifyOtp -> {
                    errorMessage = null
                    voiceEngine?.speak(
                        stringResource(R.string.voice_otp_security_note),
                        language
                    )
                }
                AuthUiAction.ChangePhone -> {
                    isOtpRequested = false
                    otpCode = ""
                    phoneNumber = ""
                    errorMessage = null
                    infoMessage = null
                }
                AuthUiAction.OpenRegister -> {
                    if (role == RoleType.GOVERNMENT_ADMIN) {
                        voiceEngine?.speak(
                            stringResource(R.string.voice_admin_registration),
                            language
                        )
                    } else {
                        authStage = AuthStage.SIGN_UP
                        errorMessage = null
                        infoMessage = null
                        voiceEngine?.speak(
                            stringResource(R.string.voice_opening_registration),
                            language
                        )
                    }
                }
                AuthUiAction.GoBack -> onBack()
            }
        }
    }

    // If already authenticated, display the matching role dashboard
    if (authenticatedUser != null) {
        val user = authenticatedUser!!
        if (user.role != role) {
            LaunchedEffect(user.role, role) {
                authService.signOut()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BackgroundCream),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.error_access_denied),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.error_role_mismatch, authService.roleTitle(user.role), authService.roleTitle(role)),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimaryDark,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            authService.signOut()
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
                    ) {
                        Text(stringResource(R.string.action_return_to_role_selection), color = Color.White)
                    }
                }
            }
            return
        }
        when (user.role) {
            RoleType.INFORMAL_COLLECTOR -> {
                val collectorVm = remember {
                    CollectorDashboardViewModel(
                        application = context.applicationContext as Application,
                        voiceEngine = voiceEngine
                    )
                }
                CollectorDashboardScreen(
                    viewModel = collectorVm,
                    collectorPhone = user.phoneNumber ?: user.statutoryIdentifier,
                    language = language,
                    onBack = {
                        authService.signOut()
                        onBack()
                    }
                )
            }
            RoleType.FORMAL_RECYCLER -> {
                val recyclerVm = remember {
                    RecyclerViewModel(context.applicationContext as Application)
                }
                FormalRecyclerPortalScreen(
                    viewModel = recyclerVm,
                    facilityName = user.entityName,
                    cpcbNumber = user.statutoryIdentifier,
                    language = language,
                    onBack = {
                        authService.signOut()
                        onBack()
                    }
                )
            }
            RoleType.GOVERNMENT_ADMIN -> {
                val adminVm = remember {
                    AdminViewModel(context.applicationContext as Application)
                }
                GovernmentAdminPortalScreen(
                    viewModel = adminVm,
                    adminId = user.statutoryIdentifier,
                    language = language,
                    onBack = {
                        authService.signOut()
                        onBack()
                    }
                )
            }
        }
        return
    }

    // Authentication Scaffold
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.app_name),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = ForestGreenPrimary,
                            letterSpacing = (-0.3).sp
                        )
                        Text(
                            text = stringResource(R.string.auth_title_suffix, roleTitle),
                            fontSize = 11.sp,
                            color = ForestGreenDark
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("auth_nav_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = ForestGreenPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val instructions = stringResource(role.instructionsRes())
                            voiceEngine?.speak(instructions, language)
                        },
                        modifier = Modifier.testTag("auth_voice_help_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = stringResource(R.string.cd_hear_auth_guidance),
                            tint = ForestGreenPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundCream)
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Role Badge & Statutory Designation Header
            Card(
                colors = CardDefaults.cardColors(containerColor = ForestGreenPrimary),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("role_auth_header_card")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(EmeraldAccent.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = roleIcon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = roleTitle,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Text(
                                text = statutorySubtitle,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = stringResource(role.instructionsRes()),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val authTabs = listOf(
                AuthMethod.OTP_AUTHENTICATION,
                AuthMethod.EMAIL_PASSWORD,
                AuthMethod.GOOGLE_OAUTH
            )
            val selectedTabIndex = authTabs.indexOf(selectedAuthMethod).coerceAtLeast(0)

            Surface(
                color = Color.White,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.Transparent,
                    contentColor = ForestGreenPrimary,
                    indicator = { tabPositions ->
                        if (selectedTabIndex < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                color = ForestGreenPrimary,
                                height = 3.dp
                            )
                        }
                    }
                ) {
                    authTabs.forEach { method ->
                        Tab(
                            selected = selectedAuthMethod == method,
                            onClick = {
                                selectedAuthMethod = method
                                if (authStage == AuthStage.SIGN_UP) {
                                    authStage = AuthStage.CREDENTIALS
                                }
                                errorMessage = null
                                infoMessage = null
                            },
                            text = {
                                Text(
                                    text = when (method) {
                                        AuthMethod.OTP_AUTHENTICATION -> stringResource(R.string.auth_otp_tab)
                                        AuthMethod.MOBILE_OTP -> stringResource(R.string.auth_tab_mobile)
                                        AuthMethod.EMAIL_PASSWORD -> stringResource(R.string.auth_email_tab)
                                        AuthMethod.GOOGLE_OAUTH -> stringResource(R.string.auth_google_tab)
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedAuthMethod == method) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = when (method) {
                                        AuthMethod.OTP_AUTHENTICATION -> Icons.Default.Security
                                        AuthMethod.MOBILE_OTP -> Icons.Default.Smartphone
                                        AuthMethod.EMAIL_PASSWORD -> Icons.Default.Email
                                        AuthMethod.GOOGLE_OAUTH -> Icons.Default.Language
                                    },
                                    contentDescription = method.title,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.testTag("auth_tab_${method.name.lowercase()}")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Error and Info Banners
            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE57373)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .testTag("auth_error_card")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = stringResource(R.string.cd_error),
                            tint = Color(0xFFC62828),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = Color(0xFFC62828),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (infoMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MintLight),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldAccent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .testTag("auth_info_card")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.cd_info),
                            tint = ForestGreenPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = infoMessage ?: "",
                            color = ForestGreenPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 4. Form: registration, credential sign-in, or OTP challenge.
            // Self-registration serves collectors and recyclers. Admin
            // accounts are provisioned centrally — no sign-up UI here.
            if (authStage == AuthStage.SIGN_UP && role != RoleType.GOVERNMENT_ADMIN) {
                SignUpCard(
                    role = role,
                    language = language,
                    isVerifying = isVerifying,
                    fullName = signupName,
                    onFullNameChange = { signupName = it },
                    mobile = signupMobile,
                    onMobileChange = { signupMobile = it.filter { ch -> ch.isDigit() }.take(10) },
                    email = signupEmail,
                    onEmailChange = { signupEmail = it },
                    password = signupPassword,
                    onPasswordChange = { signupPassword = it },
                    confirmPassword = signupConfirm,
                    onConfirmPasswordChange = { signupConfirm = it },
                    extra1 = signupExtra1,
                    onExtra1Change = { signupExtra1 = it },
                    extra2 = signupExtra2,
                    onExtra2Change = { signupExtra2 = it },
                    onSignUp = signUpAction
                )

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(
                    onClick = {
                        authStage = AuthStage.CREDENTIALS
                        errorMessage = null
                        infoMessage = null
                    },
                    enabled = !isVerifying,
                    modifier = Modifier.testTag("auth_switch_to_signin")
                ) {
                    Text(
                        text = stringResource(R.string.auth_have_account),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreenPrimary
                    )
                }
            } else when (selectedAuthMethod) {
                AuthMethod.OTP_AUTHENTICATION, AuthMethod.MOBILE_OTP -> {
                    PhoneOtpAuthCard(
                        phoneNumber = phoneNumber,
                        onPhoneChange = { phoneNumber = it.filter { ch -> ch.isDigit() }.take(10) },
                        otpCode = otpCode,
                        onOtpChange = { otpCode = it.filter { ch -> ch.isDigit() }.take(6) },
                        isOtpRequested = isOtpRequested,
                        resendCountdown = resendCountdown,
                        isVerifying = isVerifying,
                        devOtp = devDisplayOtp,
                        isDemoMode = authService.isDemoOtpMode(),
                        language = language,
                        onGenerateOtp = sendOtpAction,
                        onVerifyLogin = verifyOtpAction,
                        onChangeNumber = {
                            isOtpRequested = false
                            otpCode = ""
                            phoneNumber = ""
                            errorMessage = null
                            infoMessage = null
                        },
                        onSwitchToSignUp = if (role != RoleType.GOVERNMENT_ADMIN) {
                            {
                                authStage = AuthStage.SIGN_UP
                                errorMessage = null
                                infoMessage = null
                            }
                        } else null
                    )
                }

                AuthMethod.EMAIL_PASSWORD -> {
                    if (authStage == AuthStage.OTP_CHALLENGE) {
                        LoginOtpChallengeCard(
                            pendingEmail = authService.pendingLoginEmail() ?: emailInput,
                            otpCode = otpCode,
                            onOtpChange = { otpCode = it.filter { ch -> ch.isDigit() }.take(6) },
                            resendCountdown = resendCountdown,
                            isVerifying = isVerifying,
                            devOtp = devDisplayOtp,
                            isDemoMode = demoMode,
                            language = language,
                            onVerify = verifyChallengeAction,
                            onResend = resendChallengeAction,
                            onCancel = cancelChallengeAction
                        )
                    } else {
                        EmailPasswordAuthCard(
                            email = emailInput,
                            onEmailChange = { emailInput = it },
                            password = passwordInput,
                            onPasswordChange = { passwordInput = it },
                            isVerifying = isVerifying,
                            language = language,
                            onLogin = credentialsLoginAction,
                            onSwitchToSignUp = {
                                authStage = AuthStage.SIGN_UP
                                signupEmail = emailInput
                                errorMessage = null
                                infoMessage = null
                            },
                            showSignUp = role != RoleType.GOVERNMENT_ADMIN
                        )
                    }
                }

                AuthMethod.GOOGLE_OAUTH -> {
                    GoogleOAuthCard(
                        isVerifying = isVerifying,
                        onContinueWithGoogle = {
                            if (!SupabaseAuthConfig.googleIsConfigured()) {
                                errorMessage = stringResource(R.string.err_google_not_configured)
                                return@GoogleOAuthCard
                            }
                            isVerifying = true
                            errorMessage = null
                            infoMessage = null
                            coroutineScope.launch {
                                val googleResult = googleAuthManager.signInWithGoogle(
                                    activityContext = context,
                                    serverClientId = SupabaseAuthConfig.googleWebClientId
                                )
                                if (googleResult.isSuccess) {
                                    val res = authService.loginWithGoogleAuthResult(
                                        authResult = googleResult,
                                        targetRole = role
                                    )
                                    isVerifying = false
                                    if (!res.isSuccess) {
                                        errorMessage = res.errorMessage ?: stringResource(R.string.err_authorization_failed)
                                        voiceEngine?.speak(errorMessage ?: "", language)
                                    } else {
                                        infoMessage = stringResource(R.string.msg_google_granted)
                                        voiceEngine?.speak("Google authentication successful. Welcome, ${res.userProfile?.displayName}.", language)
                                    }
                                } else if (googleResult.isCancelled) {
                                    isVerifying = false
                                    errorMessage = stringResource(R.string.msg_google_cancelled)
                                } else {
                                    isVerifying = false
                                    errorMessage = googleResult.errorMessage ?: stringResource(R.string.msg_google_failed)
                                    voiceEngine?.speak(errorMessage ?: "", language)
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5. Statutory Verification Pipeline Card (Visual Multi-Step Progress)
            VerificationPipelineCard(
                currentStep = currentStep,
                language = language
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 6. Security Assurance & Statutory Notice
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = ForestGreenPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.auth_footer_legal),
                        fontSize = 10.sp,
                        color = TextSecondaryMuted,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------
// 1. MOBILE OTP AUTHENTICATION CARD (SMS, phone-first)
// -------------------------------------------------------------------------------------
@Composable
private fun PhoneOtpAuthCard(
    phoneNumber: String,
    onPhoneChange: (String) -> Unit,
    otpCode: String,
    onOtpChange: (String) -> Unit,
    isOtpRequested: Boolean,
    resendCountdown: Int,
    isVerifying: Boolean,
    devOtp: String?,
    isDemoMode: Boolean,
    language: Language,
    onGenerateOtp: () -> Unit,
    onVerifyLogin: () -> Unit,
    onChangeNumber: () -> Unit,
    onSwitchToSignUp: (() -> Unit)? = null
) {
    // Auto sign-in: submit the moment a complete 6-digit code is present —
    // typed, pasted, or auto-filled. The typed path also fires onCompleted
    // synchronously first (setting isVerifying), so this watcher strictly
    // observes afterwards and can never double-submit. A failed attempt keeps
    // the code without re-firing until it changes.
    LaunchedEffect(otpCode, isOtpRequested) {
        if (isOtpRequested && otpCode.length == 6 && !isVerifying) {
            onVerifyLogin()
        }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MintLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = ForestGreenPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.otp_card_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = ForestGreenPrimary
                    )
                    Text(
                        text = stringResource(R.string.otp_card_subtitle),
                        fontSize = 11.sp,
                        color = TextSecondaryMuted,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            AnimatedVisibility(visible = !isOtpRequested) {
                Column {
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = onPhoneChange,
                        label = { Text(stringResource(R.string.otp_label_mobile)) },
                        placeholder = { Text(stringResource(R.string.field_mobile_short)) },
                        prefix = { Text("+91 ", fontWeight = FontWeight.Bold, color = ForestGreenPrimary) },
                        leadingIcon = {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = ForestGreenPrimary)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        enabled = !isVerifying,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ForestGreenPrimary,
                            focusedLabelColor = ForestGreenPrimary,
                            // Keep typed text dark so it is never invisible on the
                            // white field background, regardless of system theme.
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark,
                            disabledTextColor = TextPrimaryDark,
                            cursorColor = ForestGreenPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_phone_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onGenerateOtp,
                        enabled = !isVerifying && phoneNumber.length == 10,
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("auth_send_otp_button")
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isDemoMode) stringResource(R.string.status_generating_demo_otp) else stringResource(R.string.status_sending_code),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.otp_generate_button),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isDemoMode) {
                            stringResource(R.string.status_demo_mode_explain)
                        } else {
                            stringResource(R.string.status_sms_explain)
                        },
                        fontSize = 10.sp,
                        color = TextSecondaryMuted,
                        lineHeight = 14.sp
                    )

                    // Self-registration entry for collectors/recyclers.
                    // Hidden on the admin portal (central provisioning only).
                    if (onSwitchToSignUp != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = onSwitchToSignUp,
                            enabled = !isVerifying,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_switch_to_signup")
                        ) {
                            Text(
                                text = stringResource(R.string.auth_need_account),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreenPrimary
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isOtpRequested) {
                Column {
                    Text(
                        text = if (isDemoMode) {
                            stringResource(R.string.otp_sent_demo)
                        } else {
                            stringResource(R.string.otp_sent_live)
                        },
                        fontSize = 11.sp,
                        color = TextSecondaryMuted,
                        modifier = Modifier.testTag("auth_otp_sent_to")
                    )
                    Text(
                        text = stringResource(R.string.phone_with_prefix, phoneNumber),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = ForestGreenPrimary
                    )
                    TextButton(
                        onClick = onChangeNumber,
                        enabled = !isVerifying,
                        modifier = Modifier.testTag("auth_change_number")
                    ) {
                        Text(
                            text = stringResource(R.string.action_change_number),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreenPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // DEV-ONLY: display the generated OTP prominently so testers
                    // can copy it without checking logcat or an SMS gateway.
                    if (devOtp != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFFF8E1) // warm amber background
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, Color(0xFFFFB300)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_dev_otp_banner")
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.BugReport,
                                        contentDescription = null,
                                        tint = Color(0xFFF57F17),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.demo_otp_badge),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color(0xFFF57F17)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.demo_otp_label),
                                    fontSize = 12.sp,
                                    color = TextSecondaryMuted
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = devOtp,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 32.sp,
                                    letterSpacing = 8.sp,
                                    color = Color(0xFFE65100),
                                    modifier = Modifier.testTag("auth_dev_otp_display")
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.demo_otp_body),
                                    fontSize = 10.sp,
                                    color = TextSecondaryMuted,
                                    lineHeight = 14.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { onOtpChange(devOtp) },
                                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("auth_autofill_otp_button")
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.demo_otp_autofill, devOtp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Text(
                        text = stringResource(R.string.otp_enter_code_header),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = ForestGreenPrimary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    SixDigitOtpInputField(
                        otpValue = otpCode,
                        onOtpChange = onOtpChange,
                        isEnabled = !isVerifying,
                        autoFocus = true,
                        onCompleted = {
                            if (it.length == 6 && !isVerifying) {
                                onVerifyLogin()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ResendTimerView(
                        countdownSeconds = resendCountdown,
                        isRequested = isOtpRequested,
                        onResendClick = onGenerateOtp,
                        language = language
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onVerifyLogin,
                        enabled = !isVerifying && otpCode.length == 6,
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("auth_verify_otp_button")
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.otp_verifying), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.otp_verify_and_login), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (devOtp != null) {
                        Text(
                            text = stringResource(R.string.demo_otp_dev_note),
                            fontSize = 10.sp,
                            color = TextSecondaryMuted,
                            lineHeight = 14.sp,
                            modifier = Modifier.testTag("auth_dev_otp_security_note")
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.otp_security_note),
                            fontSize = 10.sp,
                            color = TextSecondaryMuted,
                            lineHeight = 14.sp,
                            modifier = Modifier.testTag("auth_otp_security_note")
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------
// 1b. OTP SECOND-FACTOR CHALLENGE CARD (after credential sign-in)
// -------------------------------------------------------------------------------------
@Composable
private fun LoginOtpChallengeCard(
    pendingEmail: String,
    otpCode: String,
    onOtpChange: (String) -> Unit,
    resendCountdown: Int,
    isVerifying: Boolean,
    devOtp: String?,
    isDemoMode: Boolean,
    language: Language,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onCancel: () -> Unit
) {
    // Auto sign-in: submit the moment a complete 6-digit code is present —
    // typed, pasted, or auto-filled. Same single-flight guarantee as the
    // phone-first card (typed path sets isVerifying synchronously first).
    LaunchedEffect(otpCode) {
        if (otpCode.length == 6 && !isVerifying) {
            onVerify()
        }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.otp_step2_title),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = ForestGreenPrimary
            )
            Text(
                text = stringResource(R.string.otp_credentials_accepted, pendingEmail),
                fontSize = 11.sp,
                color = TextSecondaryMuted,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // DEV-ONLY: display the generated OTP prominently so testers can enter
            // it without an SMS gateway. Never shown when OTP_MODE=sms.
            if (devOtp != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFF8E1) // warm amber background
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, Color(0xFFFFB300)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_dev_otp_banner")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                tint = Color(0xFFF57F17),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.demo_otp_badge),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFFF57F17)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.demo_otp_label),
                            fontSize = 12.sp,
                            color = TextSecondaryMuted
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = devOtp,
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            letterSpacing = 8.sp,
                            color = Color(0xFFE65100),
                            modifier = Modifier.testTag("auth_dev_otp_display")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.demo_otp_body),
                            fontSize = 10.sp,
                            color = TextSecondaryMuted,
                            lineHeight = 14.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { onOtpChange(devOtp) },
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("auth_autofill_challenge_otp_button")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.demo_otp_autofill, devOtp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text(
                text = stringResource(R.string.otp_enter_code_header),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = ForestGreenPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            SixDigitOtpInputField(
                otpValue = otpCode,
                onOtpChange = onOtpChange,
                isEnabled = !isVerifying,
                autoFocus = true,
                onCompleted = {
                    if (it.length == 6 && !isVerifying) {
                        onVerify()
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ResendTimerView(
                countdownSeconds = resendCountdown,
                isRequested = true,
                onResendClick = onResend,
                language = language
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onVerify,
                enabled = !isVerifying && otpCode.length == 6,
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("auth_verify_otp_button")
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.otp_verifying), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.otp_verify_and_login), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            TextButton(
                onClick = onCancel,
                enabled = !isVerifying,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_challenge_cancel")
            ) {
                Text(
                    text = stringResource(R.string.auth_use_different_account),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ForestGreenPrimary
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------
// 1c. SIGN-UP / REGISTRATION CARD (per-role, stored in Supabase)
// -------------------------------------------------------------------------------------
@Composable
private fun SignUpCard(
    role: RoleType,
    language: Language,
    isVerifying: Boolean,
    fullName: String,
    onFullNameChange: (String) -> Unit,
    mobile: String,
    onMobileChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    extra1: String,
    onExtra1Change: (String) -> Unit,
    extra2: String,
    onExtra2Change: (String) -> Unit,
    onSignUp: () -> Unit
) {
    val roleLabel = stringResource(role.titleRes())
    val (extra1Label, extra1Optional, extra2Label) = when (role) {
        RoleType.INFORMAL_COLLECTOR -> Triple(stringResource(R.string.field_operating_area), true, null)
        RoleType.FORMAL_RECYCLER -> Triple(stringResource(R.string.field_facility_name), false, stringResource(R.string.field_cpcb_number))
        RoleType.GOVERNMENT_ADMIN -> Triple(null, true, stringResource(R.string.field_department_id))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.auth_create_account_title, roleLabel),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = ForestGreenPrimary
            )
            Text(
                text = stringResource(R.string.auth_create_account_body),
                fontSize = 11.sp,
                color = TextSecondaryMuted,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            SignupTextField(
                value = fullName,
                onValueChange = onFullNameChange,
                label = stringResource(R.string.field_full_name),
                placeholder = stringResource(R.string.field_full_name_hint),
                icon = Icons.Default.CheckCircle,
                keyboardType = KeyboardType.Text,
                isVerifying = isVerifying,
                testTag = "auth_signup_name"
            )

            Spacer(modifier = Modifier.height(10.dp))

            SignupTextField(
                value = mobile,
                onValueChange = onMobileChange,
                label = stringResource(R.string.field_mobile),
                placeholder = stringResource(R.string.field_mobile_hint),
                icon = Icons.Default.Phone,
                keyboardType = KeyboardType.Phone,
                isVerifying = isVerifying,
                prefix = "+91 ",
                testTag = "auth_signup_mobile"
            )

            Spacer(modifier = Modifier.height(10.dp))

            SignupTextField(
                value = email,
                onValueChange = onEmailChange,
                label = stringResource(R.string.field_email),
                placeholder = stringResource(R.string.field_email_hint),
                icon = Icons.Default.Email,
                keyboardType = KeyboardType.Email,
                isVerifying = isVerifying,
                testTag = "auth_signup_email"
            )

            Spacer(modifier = Modifier.height(10.dp))

            SignupTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = stringResource(R.string.field_password),
                placeholder = stringResource(R.string.field_password_hint),
                icon = Icons.Default.Lock,
                keyboardType = KeyboardType.Password,
                isVerifying = isVerifying,
                isPassword = true,
                testTag = "auth_signup_password"
            )

            Spacer(modifier = Modifier.height(10.dp))

            SignupTextField(
                value = confirmPassword,
                onValueChange = onConfirmPasswordChange,
                label = stringResource(R.string.field_confirm_password),
                placeholder = stringResource(R.string.field_confirm_password_hint),
                icon = Icons.Default.Lock,
                keyboardType = KeyboardType.Password,
                isVerifying = isVerifying,
                isPassword = true,
                testTag = "auth_signup_confirm"
            )

            if (extra1Label != null) {
                Spacer(modifier = Modifier.height(10.dp))
                SignupTextField(
                    value = extra1,
                    onValueChange = onExtra1Change,
                    label = if (extra1Optional) stringResource(R.string.field_optional_suffix, extra1Label) else extra1Label,
                    placeholder = extra1Label,
                    icon = Icons.Default.Factory,
                    keyboardType = KeyboardType.Text,
                    isVerifying = isVerifying,
                    testTag = "auth_signup_extra1"
                )
            }

            if (extra2Label != null) {
                Spacer(modifier = Modifier.height(10.dp))
                SignupTextField(
                    value = extra2,
                    onValueChange = onExtra2Change,
                    label = extra2Label,
                    placeholder = extra2Label,
                    icon = Icons.Default.Shield,
                    keyboardType = KeyboardType.Text,
                    isVerifying = isVerifying,
                    testTag = "auth_signup_extra2"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSignUp,
                enabled = !isVerifying && fullName.isNotBlank() && email.isNotBlank() &&
                    password.isNotBlank() && confirmPassword.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("auth_signup_button")
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_creating_account), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_create_account), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SignupTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    icon: ImageVector,
    keyboardType: KeyboardType,
    isVerifying: Boolean,
    isPassword: Boolean = false,
    prefix: String? = null,
    testTag: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        prefix = if (prefix != null) {
            { Text(prefix, fontWeight = FontWeight.Bold, color = ForestGreenPrimary) }
        } else null,
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = ForestGreenPrimary)
        },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        enabled = !isVerifying,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ForestGreenPrimary,
            focusedLabelColor = ForestGreenPrimary,
            focusedTextColor = TextPrimaryDark,
            unfocusedTextColor = TextPrimaryDark,
            disabledTextColor = TextPrimaryDark,
            cursorColor = ForestGreenPrimary
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    )
}
@Composable
private fun EmailPasswordAuthCard(
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    isVerifying: Boolean,
    language: Language,
    onLogin: () -> Unit,
    onSwitchToSignUp: () -> Unit,
    showSignUp: Boolean = true
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.auth_official_email_title),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = ForestGreenPrimary
            )
            Text(
                text = stringResource(R.string.auth_official_email_body),
                fontSize = 12.sp,
                color = TextSecondaryMuted,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                label = { Text(stringResource(R.string.field_official_email)) },
                placeholder = { Text(stringResource(R.string.field_email_hint)) },
                leadingIcon = {
                    Icon(Icons.Default.Email, contentDescription = null, tint = ForestGreenPrimary)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                enabled = !isVerifying,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ForestGreenPrimary,
                    focusedLabelColor = ForestGreenPrimary,
                    focusedTextColor = TextPrimaryDark,
                    unfocusedTextColor = TextPrimaryDark,
                    disabledTextColor = TextPrimaryDark,
                    cursorColor = ForestGreenPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_email_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(R.string.field_password_short)) },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = ForestGreenPrimary)
                },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                enabled = !isVerifying,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ForestGreenPrimary,
                    focusedLabelColor = ForestGreenPrimary,
                    focusedTextColor = TextPrimaryDark,
                    unfocusedTextColor = TextPrimaryDark,
                    disabledTextColor = TextPrimaryDark,
                    cursorColor = ForestGreenPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_password_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onLogin,
                enabled = !isVerifying && email.isNotBlank() && password.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("auth_email_login_button")
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Authenticating...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_sign_in_email), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Self-registration is offered to collectors and recyclers only —
            // admin accounts are provisioned centrally, so no entry point here.
            if (showSignUp) {
                TextButton(
                    onClick = onSwitchToSignUp,
                    enabled = !isVerifying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_switch_to_signup")
                ) {
                    Text(
                        text = stringResource(R.string.auth_need_account),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreenPrimary
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------
// 3. CONTINUE WITH GOOGLE AUTHENTICATION CARD
// -------------------------------------------------------------------------------------
@Composable
private fun GoogleOAuthCard(
    isVerifying: Boolean,
    onContinueWithGoogle: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE8F0FE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = stringResource(R.string.cd_google_sign_in),
                            tint = Color(0xFF1A73E8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.auth_google_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = ForestGreenPrimary
                        )
                        Text(
                            text = stringResource(R.string.auth_google_subtitle),
                            fontSize = 11.sp,
                            color = TextSecondaryMuted
                        )
                    }
                }

                Surface(
                    color = MintLight,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldAccent.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = stringResource(R.string.auth_oauth_label),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreenPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.auth_google_body),
                fontSize = 12.sp,
                color = TextSecondaryMuted,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onContinueWithGoogle,
                enabled = !isVerifying,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1A73E8),
                    disabledContainerColor = Color(0xFF1A73E8).copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("auth_continue_google_button")
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(stringResource(R.string.action_authenticating_google), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(stringResource(R.string.action_continue_google_account), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------
// 4. STATUTORY VERIFICATION PIPELINE PROGRESS CARD
// -------------------------------------------------------------------------------------
@Composable
private fun VerificationPipelineCard(
    currentStep: AuthPipelineStep,
    language: Language
) {
    val steps = listOf(
        AuthPipelineStep.AUTHENTICATING_USER,
        AuthPipelineStep.VERIFYING_ACCOUNT,
        AuthPipelineStep.VERIFYING_ROLE,
        AuthPipelineStep.VERIFYING_PERMISSIONS
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("auth_pipeline_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.otp_verification_pipeline_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = ForestGreenPrimary
                )
                Text(
                    text = when (currentStep) {
                        AuthPipelineStep.IDLE -> "Standby"
                        AuthPipelineStep.SUCCESS -> "Verified"
                        AuthPipelineStep.FAILED -> stringResource(R.string.status_verification_failed)
                        else -> "Processing Step ${currentStep.stepNumber}/4"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (currentStep) {
                        AuthPipelineStep.SUCCESS -> SuccessGreen
                        AuthPipelineStep.FAILED -> Color(0xFFC62828)
                        else -> EmeraldAccent
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            steps.forEachIndexed { index, step ->
                val isCompleted = currentStep.stepNumber > step.stepNumber || currentStep == AuthPipelineStep.SUCCESS
                val isCurrent = currentStep == step
                val isPending = currentStep.stepNumber < step.stepNumber && currentStep != AuthPipelineStep.SUCCESS

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isCompleted -> SuccessGreen
                                    isCurrent -> EmeraldAccent
                                    else -> Color(0xFFE0E0E0)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCompleted) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        } else if (isCurrent) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                        } else {
                            Text(
                                text = "${index + 1}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = when (language) {
                            Language.ENGLISH -> step.descriptionEn
                            Language.HINDI -> step.descriptionHi
                            Language.MARATHI -> step.descriptionMr
                        },
                        fontSize = 12.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isCompleted -> SuccessGreen
                            isCurrent -> ForestGreenPrimary
                            else -> TextSecondaryMuted
                        }
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------
// BACKWARD-COMPATIBLE PUBLIC SCREEN EXPORTS
// -------------------------------------------------------------------------------------
@Composable
fun InformalCollectorAuthScreen(
    language: Language,
    voiceEngine: VoiceEngine? = null,
    onBack: () -> Unit
) {
    UnifiedRoleAuthScreen(
        role = RoleType.INFORMAL_COLLECTOR,
        language = language,
        voiceEngine = voiceEngine,
        onBack = onBack
    )
}

@Composable
fun FormalRecyclerAuthScreen(
    language: Language,
    voiceEngine: VoiceEngine? = null,
    onBack: () -> Unit
) {
    UnifiedRoleAuthScreen(
        role = RoleType.FORMAL_RECYCLER,
        language = language,
        voiceEngine = voiceEngine,
        onBack = onBack
    )
}

@Composable
fun GovernmentAdminAuthScreen(
    language: Language,
    voiceEngine: VoiceEngine? = null,
    onBack: () -> Unit
) {
    UnifiedRoleAuthScreen(
        role = RoleType.GOVERNMENT_ADMIN,
        language = language,
        voiceEngine = voiceEngine,
        onBack = onBack
    )
}