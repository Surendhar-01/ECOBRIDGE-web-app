import { createContext, useContext, useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { Navigate, NavLink, Route, Routes, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { isSupabaseConfigured, supabase } from './lib/supabase'
import { I18nProvider, useI18n, type I18n, type TranslationKey } from './i18n'
import { LanguageSwitcher } from './i18n/LanguageSwitcher'
import {
  ArrowRight, BarChart3, Bell, Building2, Camera, Check, ChevronRight, CircleDollarSign,
  ClipboardCheck, Clock3, Download, FileCheck2, Globe2, Home, Leaf, LogOut, MapPin,
  Menu, PackageCheck, Plus, Recycle, Search, ShieldCheck, Smartphone, Sparkles, Truck,
  UserRound, Users, Weight, WifiOff, X,
} from 'lucide-react'

type Role = 'collector' | 'recycler' | 'admin'
type MaterialKey = 'material.circuitBoards' | 'material.cablesWires' | 'material.batteries' | 'material.mixedElectronics'
type StatusKey =
  | 'status.draft'
  | 'status.quoteRequested'
  | 'status.quoteReceived'
  | 'status.pickupScheduled'
  | 'status.weighed'
  | 'status.paid'
  | 'status.processed'
type OrgStatusKey = 'org.statusActive' | 'org.statusPending' | 'org.statusSuspended'
type OrgTypeKey = 'org.typeRecycler' | 'org.typeNetwork'

type AuthProfile = {
  userId: string
  email: string
  displayName: string
  entityName: string
  role: Role
}

type Lot = {
  id: string
  material: MaterialKey
  weight: number
  estimatedValue: number
  finalWeight?: number
  finalValue?: number
  status: StatusKey
  recycler: string
  location: string
  /** ISO `YYYY-MM-DD` calendar date, rendered through the active locale. */
  createdAt: string
  synced: boolean
}

const ALL_STATUSES: StatusKey[] = [
  'status.draft',
  'status.quoteRequested',
  'status.quoteReceived',
  'status.pickupScheduled',
  'status.weighed',
  'status.paid',
  'status.processed',
]

/** CSS modifier classes stay in English so the stylesheet remains readable. */
const statusModifier: Record<StatusKey, string> = {
  'status.draft': 'draft',
  'status.quoteRequested': 'quote-requested',
  'status.quoteReceived': 'quote-received',
  'status.pickupScheduled': 'pickup-scheduled',
  'status.weighed': 'weighed',
  'status.paid': 'paid',
  'status.processed': 'processed',
}

/** Maps labels written by earlier builds onto translation keys. */
const legacyMaterial: Record<string, MaterialKey> = {
  'Circuit boards': 'material.circuitBoards',
  'Cables & wires': 'material.cablesWires',
  Batteries: 'material.batteries',
  'Mixed small electronics': 'material.mixedElectronics',
  'Mixed electronics': 'material.mixedElectronics',
}

const legacyStatus: Record<string, StatusKey> = {
  Draft: 'status.draft',
  'Quote requested': 'status.quoteRequested',
  'Quote received': 'status.quoteReceived',
  'Pickup scheduled': 'status.pickupScheduled',
  Weighed: 'status.weighed',
  Paid: 'status.paid',
  Processed: 'status.processed',
}

const MATERIAL_KEYS: MaterialKey[] = [
  'material.circuitBoards',
  'material.cablesWires',
  'material.batteries',
  'material.mixedElectronics',
]

const isMaterialKey = (value: unknown): value is MaterialKey =>
  typeof value === 'string' && MATERIAL_KEYS.includes(value as MaterialKey)

const isStatusKey = (value: unknown): value is StatusKey =>
  typeof value === 'string' && ALL_STATUSES.includes(value as StatusKey)

/** Lots persisted by an earlier build stored English labels; upgrade them in place. */
const normalizeLot = (lot: Lot): Lot => ({
  ...lot,
  material: isMaterialKey(lot.material) ? lot.material : legacyMaterial[lot.material as string] ?? 'material.mixedElectronics',
  status: isStatusKey(lot.status) ? lot.status : legacyStatus[lot.status as string] ?? 'status.draft',
})

const seedLots: Lot[] = [
  { id: 'EB-24091', material: 'material.circuitBoards', weight: 18.5, estimatedValue: 7030, finalWeight: 18.2, finalValue: 6916, status: 'status.paid', recycler: 'GreenCycle Industries', location: 'Pune, Maharashtra', createdAt: '2026-09-24', synced: true },
  { id: 'EB-24084', material: 'material.cablesWires', weight: 12, estimatedValue: 5520, status: 'status.pickupScheduled', recycler: 'Maha E-Recyclers', location: 'Mumbai, Maharashtra', createdAt: '2026-09-23', synced: true },
  { id: 'EB-24072', material: 'material.mixedElectronics', weight: 7.8, estimatedValue: 1560, status: 'status.quoteReceived', recycler: 'EcoLoop Solutions', location: 'Nashik, Maharashtra', createdAt: '2026-09-21', synced: true },
]

const AppContext = createContext<{
  role: Role | null
  profile: AuthProfile | null
  authLoading: boolean
  signIn: (email: string, password: string) => Promise<Role>
  signUp: (email: string, password: string, displayName: string, entityName: string, phone: string, role: Role) => Promise<void>
  signOut: () => Promise<void>
  lots: Lot[]
  addLot: (lot: Lot) => void
  updateLot: (id: string, patch: Partial<Lot>) => void
}>({ role: null, profile: null, authLoading: true, signIn: async () => { throw new Error('Authentication unavailable') }, signUp: async () => { throw new Error('Authentication unavailable') }, signOut: async () => undefined, lots: [], addLot: () => undefined, updateLot: () => undefined })

const mapDatabaseRole = (role: string): Role | null => {
  if (['informal_collector', 'collector'].includes(role)) return 'collector'
  if (['formal_recycler', 'recycler'].includes(role)) return 'recycler'
  if (['government_admin', 'admin'].includes(role)) return 'admin'
  return null
}

const dashboardPath = (role: Role) => `/${role}`

const accountStatusKey = (status: string): TranslationKey =>
  status === 'suspended' ? 'error.statusSuspended' : status === 'pending' ? 'error.statusPending' : 'error.statusInactive'

function AppProvider({ children }: { children: ReactNode }) {
  const { t } = useI18n()
  const [profile, setProfile] = useState<AuthProfile | null>(null)
  const [authLoading, setAuthLoading] = useState(true)
  const [lots, setLots] = useState<Lot[]>(() => {
    const stored = localStorage.getItem('eco-lots')
    if (!stored) return seedLots
    try {
      const parsed = JSON.parse(stored) as Lot[]
      return Array.isArray(parsed) ? parsed.map(normalizeLot) : seedLots
    } catch {
      return seedLots
    }
  })
  const loadProfile = async (userId: string, email = ''): Promise<AuthProfile> => {
    if (!supabase) throw new Error(t('error.supabaseMissing'))
    const { data, error } = await supabase.from('profiles').select('auth_user_id,role,account_status,display_name,entity_name,email').eq('auth_user_id', userId).single()
    if (error || !data) throw new Error(t('error.profileLoad'))
    if (data.account_status !== 'active') throw new Error(t('error.accountStatus', { status: t(accountStatusKey(data.account_status || '')) }))
    const role = mapDatabaseRole(data.role)
    if (!role) throw new Error(t('error.noRole'))
    const nextProfile = { userId, email: data.email || email, displayName: data.display_name || email.split('@')[0], entityName: data.entity_name || '', role }
    setProfile(nextProfile)
    return nextProfile
  }
  useEffect(() => {
    if (!supabase) { setAuthLoading(false); return }
    const authClient = supabase
    let active = true
    authClient.auth.getSession().then(async ({ data }) => {
      if (!active) return
      if (data.session?.user) {
        try { await loadProfile(data.session.user.id, data.session.user.email) } catch { await authClient.auth.signOut(); setProfile(null) }
      }
      if (active) setAuthLoading(false)
    })
    const { data: listener } = authClient.auth.onAuthStateChange((event, session) => {
      if (event === 'SIGNED_OUT' || !session?.user) { setProfile(null); setAuthLoading(false); return }
      window.setTimeout(() => loadProfile(session.user.id, session.user.email).catch(() => setProfile(null)).finally(() => setAuthLoading(false)), 0)
    })
    return () => { active = false; listener.subscription.unsubscribe() }
  }, [])
  const signIn = async (email: string, password: string) => {
    if (!supabase) throw new Error(t('error.supabaseMissing'))
    setAuthLoading(true)
    const { data, error } = await supabase.auth.signInWithPassword({ email: email.trim().toLowerCase(), password })
    if (error || !data.user) {
      setAuthLoading(false)
      const messageKey = error?.code === 'email_not_confirmed'
        ? 'error.emailNotConfirmed'
        : error?.code === 'over_request_rate_limit'
          ? 'error.authRateLimited'
          : 'error.invalidCredentials'
      throw new Error(t(messageKey))
    }
    try {
      const account = await loadProfile(data.user.id, data.user.email)
      setAuthLoading(false)
      return account.role
    } catch (error) {
      await supabase.auth.signOut()
      setAuthLoading(false)
      throw error
    }
  }
  const signUp = async (email: string, password: string, displayName: string, entityName: string, phone: string, role: Role) => {
    if (!supabase) throw new Error(t('error.supabaseMissing'))
    const phoneDigits = phone.replace(/\D/g, '').replace(/^91(?=\d{10}$)/, '')
    if (!/^[6-9]\d{9}$/.test(phoneDigits)) throw new Error(t('error.validPhoneRequired'))
    const dbRole = role === 'collector' ? 'informal_collector' : role === 'recycler' ? 'formal_recycler' : 'government_admin'
    const { data, error } = await supabase.auth.signUp({
      email: email.trim().toLowerCase(),
      password,
      options: { data: {
        display_name: displayName.trim(),
        entity_name: entityName.trim(),
        phone_number: `+91 ${phoneDigits}`,
        role: dbRole,
      } },
    })
    if (error || !data.user) {
      const messageKey = error?.status === 429 || error?.code === 'over_email_send_rate_limit' || error?.code === 'over_request_rate_limit'
        ? 'error.signupRateLimited'
        : error?.code === 'user_already_exists'
          ? 'error.accountAlreadyExists'
          : error?.code === 'weak_password'
            ? 'error.weakPassword'
            : 'error.signupFailed'
      throw new Error(t(messageKey))
    }
  }
  const signOut = async () => {
    if (supabase) await supabase.auth.signOut()
    setProfile(null)
  }
  const persist = (next: Lot[]) => { setLots(next); localStorage.setItem('eco-lots', JSON.stringify(next)) }
  const addLot = (lot: Lot) => persist([lot, ...lots])
  const updateLot = (id: string, patch: Partial<Lot>) => persist(lots.map((lot) => lot.id === id ? { ...lot, ...patch } : lot))
  return <AppContext.Provider value={{ role: profile?.role || null, profile, authLoading, signIn, signUp, signOut, lots, addLot, updateLot }}>{children}</AppContext.Provider>
}

const useApp = () => useContext(AppContext)

function Brand({ inverse = false }: { inverse?: boolean }) {
  const { t } = useI18n()
  return <div className={`brand ${inverse ? 'brand-inverse' : ''}`}><span className="brand-mark"><Recycle size={22} /></span><span><b>{t('brand.name')}</b><small>{t('brand.tagline')}</small></span></div>
}

function Landing() {
  const navigate = useNavigate()
  const { t, formatCurrency, formatNumber } = useI18n()
  const roleCards: { role: Role, icon: ReactNode, title: TranslationKey, text: TranslationKey }[] = [
    { role: 'collector', icon: <UserRound />, title: 'landing.roles.collector.title', text: 'landing.roles.collector.text' },
    { role: 'recycler', icon: <Building2 />, title: 'landing.roles.recycler.title', text: 'landing.roles.recycler.text' },
    { role: 'admin', icon: <BarChart3 />, title: 'landing.roles.admin.title', text: 'landing.roles.admin.text' },
  ]
  const steps: { n: string, icon: ReactNode, title: TranslationKey, text: TranslationKey }[] = [
    { n: '01', icon: <Camera />, title: 'landing.how.step1.title', text: 'landing.how.step1.text' },
    { n: '02', icon: <CircleDollarSign />, title: 'landing.how.step2.title', text: 'landing.how.step2.text' },
    { n: '03', icon: <Truck />, title: 'landing.how.step3.title', text: 'landing.how.step3.text' },
    { n: '04', icon: <PackageCheck />, title: 'landing.how.step4.title', text: 'landing.how.step4.text' },
  ]
  return <div className="landing">
    <header className="topbar container">
      <Brand />
      <nav className="desktop-nav"><a href="#how">{t('landing.nav.how')}</a><a href="#impact">{t('landing.nav.impact')}</a><a href="#trust">{t('landing.nav.safety')}</a></nav>
      <div className="top-actions"><LanguageSwitcher id="landing-language" /><button className="btn btn-dark" onClick={() => navigate('/select-role')}>{t('landing.nav.signIn')}</button></div>
    </header>
    <main>
      <section className="hero">
        <div className="container hero-grid">
          <div className="hero-copy">
            <span className="eyebrow"><Sparkles size={14} /> {t('landing.eyebrow')}</span>
            <h1>{t('landing.headingLine1')} <em>{t('landing.headingEmphasis')}</em><br />{t('landing.headingLine2')}</h1>
            <p>{t('landing.subtitle')}</p>
            <div className="hero-actions"><button className="btn btn-primary btn-lg" onClick={() => navigate('/select-role')}>{t('landing.getStarted')} <ArrowRight size={18} /></button><a className="text-link" href="#how">{t('landing.seeHow')} <ChevronRight size={17} /></a></div>
            <div className="trust-row"><span><ShieldCheck /> {t('landing.trust.verified')}</span><span><FileCheck2 /> {t('landing.trust.proof')}</span><span><Globe2 /> {t('landing.trust.languages')}</span></div>
          </div>
          <div className="hero-visual" aria-label={t('meta.description')}>
            <div className="orbit orbit-one" /><div className="orbit orbit-two" />
            <div className="hero-card main-card"><div className="mini-head"><span>{t('landing.card.lot', { id: 'EB-24106' })}</span><span className="status green">{t('landing.card.onTrack')}</span></div><div className="material-icon"><Smartphone /></div><strong>{t('material.mixedElectronics')}</strong><small>{t('landing.card.weightCity', { weight: formatNumber(14.5, { minimumFractionDigits: 1 }), city: 'Pune' })}</small><div className="progress-line"><i /><i /><i /><i className="muted" /></div><div className="card-foot"><span>{t('landing.card.estimatedValue')}</span><b>{formatCurrency(4350)}</b></div></div>
            <div className="float-card float-top"><span className="icon-bubble"><ShieldCheck /></span><div><b>{t('landing.card.verifiedTitle')}</b><small>{t('landing.card.verifiedSub')}</small></div></div>
            <div className="float-card float-bottom"><span className="icon-bubble lime"><CircleDollarSign /></span><div><b>{t('landing.card.paymentTitle')}</b><small>{t('landing.card.paymentSub', { value: formatCurrency(6916) })}</small></div><Check className="tick" /></div>
            <div className="leaf-decor"><Leaf /></div>
          </div>
        </div>
      </section>
      <section className="role-strip container" id="trust">
        <div><span className="section-kicker">{t('landing.roles.kicker')}</span><h2>{t('landing.roles.heading')}</h2></div>
        <div className="role-cards">
          {roleCards.map((card) => <RoleCard key={card.role} icon={card.icon} title={t(card.title)} text={t(card.text)} action={() => navigate(`/login?role=${card.role}`)} />)}
        </div>
      </section>
      <section id="how" className="steps-section">
        <div className="container"><span className="section-kicker">{t('landing.how.kicker')}</span><h2>{t('landing.how.heading')}</h2><p className="section-intro">{t('landing.how.intro')}</p>
          <div className="steps">{steps.map((step) => <Step key={step.n} {...step} title={t(step.title)} text={t(step.text)} />)}</div>
        </div>
      </section>
      <section id="impact" className="impact"><div className="container impact-inner"><div><span className="section-kicker light">{t('landing.impact.kicker')}</span><h2>{t('landing.impact.heading')}</h2><p>{t('landing.impact.text')}</p></div><div className="impact-stats"><b>{formatNumber(12.4, { minimumFractionDigits: 1 })}<span>{t('units.tonnesShort', { value: '' })}</span><small>{t('landing.impact.traced')}</small></b><b>{formatNumber(840)}<small>{t('landing.impact.collectors')}</small></b><b>₹{formatNumber(18.6, { minimumFractionDigits: 1 })}<span>{t('units.lakhSuffix')}</span><small>{t('landing.impact.paid')}</small></b></div></div></section>
    </main>
    <footer><div className="container footer-inner"><Brand inverse /><p>{t('landing.footer.text')}</p><span>{t('landing.footer.rights')}</span></div></footer>
  </div>
}

function RoleCard({ icon, title, text, action }: { icon: ReactNode, title: string, text: string, action: () => void }) {
  return <button className="role-card" onClick={action}><span className="role-icon">{icon}</span><div><h3>{title}</h3><p>{text}</p></div><ArrowRight /></button>
}
function Step({ n, icon, title, text }: { n: string, icon: ReactNode, title: string, text: string }) {
  return <article className="step"><span className="step-number">{n}</span><span className="step-icon">{icon}</span><h3>{title}</h3><p>{text}</p></article>
}

const roleDetails = (t: I18n['t']): Record<Role, { title: string, description: string, icon: ReactNode }> => ({
  collector: { title: t('role.collector.title'), description: t('role.collector.description'), icon: <UserRound /> },
  recycler: { title: t('role.recycler.title'), description: t('role.recycler.description'), icon: <Building2 /> },
  admin: { title: t('role.admin.title'), description: t('role.admin.description'), icon: <ShieldCheck /> },
})

function RoleSelection() {
  const navigate = useNavigate()
  const { t } = useI18n()
  const { role } = useApp()
  const details = roleDetails(t)
  useEffect(() => { if (role) navigate(dashboardPath(role), { replace: true }) }, [role, navigate])
  return <div className="role-select-page"><header><Brand /><div className="role-select-tools"><button className="back-link" onClick={() => navigate('/')}>{t('roleSelect.backHome')}</button></div></header><main><span className="eyebrow">{t('roleSelect.kicker')}</span><h1>{t('roleSelect.heading')}</h1><p>{t('roleSelect.text')}</p><div className="login-role-grid">{(Object.keys(details) as Role[]).map((item) => <button key={item} onClick={() => navigate(`/login?role=${item}`)}><span>{details[item].icon}</span><div><h2>{details[item].title}</h2><p>{details[item].description}</p><b>{t('role.continueToSignIn')} <ArrowRight /></b></div></button>)}</div><div className="role-security"><ShieldCheck /><span><b>{t('roleSelect.protectedTitle')}</b><small>{t('roleSelect.protectedText')}</small></span></div></main></div>
}

function Login() {
  const navigate = useNavigate()
  const { t } = useI18n()
  const [searchParams] = useSearchParams()
  const requestedRole = searchParams.get('role') as Role | null
  const details = roleDetails(t)
  const selectedRole = requestedRole && details[requestedRole] ? requestedRole : null
  const { signIn, signUp, signOut, role } = useApp()
  const [tab, setTab] = useState<'signin' | 'signup'>('signin')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [fullName, setFullName] = useState('')
  const [entityName, setEntityName] = useState('')
  const [phone, setPhone] = useState('')
  const [error, setError] = useState('')
  const [signUpSuccess, setSignUpSuccess] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  useEffect(() => { if (role) navigate(dashboardPath(role), { replace: true }) }, [role, navigate])
  if (!selectedRole) return <Navigate to="/select-role" replace />

  const switchTab = (next: 'signin' | 'signup') => {
    setTab(next)
    setError('')
    setSignUpSuccess(false)
  }

  const enter = async (event: FormEvent) => {
    event.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      const authenticatedRole = await signIn(email, password)
      if (authenticatedRole !== selectedRole) {
        await signOut()
        throw new Error(t('login.wrongRole', { role: details[selectedRole].title }))
      }
      navigate(dashboardPath(authenticatedRole), { replace: true })
    }
    catch (reason) { setError(reason instanceof Error ? reason.message : t('error.unableToSignIn')) }
    finally { setSubmitting(false) }
  }

  const register = async (event: FormEvent) => {
    event.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      await signUp(email, password, fullName, entityName, phone, selectedRole)
      setSignUpSuccess(true)
      setFullName(''); setEntityName(''); setPhone(''); setEmail(''); setPassword('')
      setTimeout(() => switchTab('signin'), 3500)
    }
    catch (reason) { setError(reason instanceof Error ? reason.message : t('error.unableToSignIn')) }
    finally { setSubmitting(false) }
  }

  const isAdmin = selectedRole === 'admin'

  return <div className="auth-page"><div className="auth-brand"><Brand inverse /><div><h1>{t('login.brandHeading')}</h1><p>{t('login.brandText')}</p></div><small>{t('login.brandFooter')}</small></div>
    <div className="auth-panel">
      <div className="auth-panel-tools"><button className="back-link" onClick={() => navigate('/select-role')}><span>←</span> {t('login.back')}</button></div>
      <div className="auth-box">
        <div className="selected-portal"><span>{details[selectedRole].icon}</span><div><small>{t('login.selectedPortal')}</small><b>{details[selectedRole].title}</b></div></div>
        <span className="eyebrow">{t('login.kicker')}</span>

        {!isAdmin && (
          <div className="auth-tabs" role="tablist">
            <button id="tab-signin" role="tab" aria-selected={tab === 'signin'} className={tab === 'signin' ? 'active' : ''} onClick={() => switchTab('signin')}>{t('login.tabSignIn')}</button>
            <button id="tab-signup" role="tab" aria-selected={tab === 'signup'} className={tab === 'signup' ? 'active' : ''} onClick={() => switchTab('signup')}>{t('login.tabSignUp')}</button>
          </div>
        )}

        {/* ── Sign-in form ── */}
        {tab === 'signin' && (
          <form onSubmit={enter}>
            <h2>{t('login.heading', { role: details[selectedRole].title })}</h2>
            <p>{t('login.subtitle')}</p>
            <div className="role-truth"><ShieldCheck /><span><b>{t('login.protectedTitle')}</b><small>{t('login.protectedText')}</small></span></div>
            <label htmlFor="email">{t('login.emailLabel')}</label>
            <div className="auth-input"><input id="email" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder={t('login.emailPlaceholder')} required /></div>
            <label htmlFor="password">{t('login.passwordLabel')}</label>
            <div className="auth-input"><input id="password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder={t('login.passwordPlaceholder')} minLength={6} required /></div>
            {error && <div className="auth-error" role="alert">{error}</div>}
            <button className="btn btn-primary btn-full" type="submit" disabled={submitting || !isSupabaseConfigured}>{submitting ? t('login.submitting') : t('login.submit')} {!submitting && <ArrowRight size={18} />}</button>
            {!isSupabaseConfigured && <div className="demo-note"><ShieldCheck size={15} /><span><b>{t('login.configRequired')}</b> {t('login.configHint')}</span></div>}
            {!isAdmin && <p className="auth-switch-hint">{t('login.noAccount')} <button type="button" className="text-btn" onClick={() => switchTab('signup')}>{t('login.tabSignUp')}</button></p>}
          </form>
        )}

        {/* ── Sign-up form (not for admin) ── */}
        {tab === 'signup' && !isAdmin && (
          <form onSubmit={register}>
            <h2>{t('login.signUpHeading', { role: details[selectedRole].title })}</h2>
            <p>{t('login.signUpSubtitle')}</p>
            {signUpSuccess && <div className="auth-success" role="status">{t('login.signUpSuccess')}</div>}
            <label htmlFor="su-fullname">{t('login.fullNameLabel')}</label>
            <div className="auth-input"><input id="su-fullname" type="text" autoComplete="name" value={fullName} onChange={(e) => setFullName(e.target.value)} placeholder={t('login.fullNamePlaceholder')} required /></div>
            {selectedRole === 'recycler' && <>
              <label htmlFor="su-entity">{t('login.entityNameLabel')}</label>
              <div className="auth-input"><input id="su-entity" type="text" value={entityName} onChange={(e) => setEntityName(e.target.value)} placeholder={t('login.entityNamePlaceholder')} /></div>
            </>}
            <label htmlFor="su-email">{t('login.emailLabel')}</label>
            <div className="auth-input"><input id="su-email" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder={t('login.emailPlaceholder')} required /></div>
            <label htmlFor="su-password">{t('login.passwordLabel')}</label>
            <div className="auth-input"><input id="su-password" type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder={t('login.passwordPlaceholder')} minLength={6} required /></div>
            <label htmlFor="su-phone">{t('login.phoneLabel')}</label>
            <div className="auth-input"><input id="su-phone" type="tel" autoComplete="tel" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder={t('login.phonePlaceholder')} pattern="(?:[+]91(?: |-)?)?[6-9][0-9]{9}" required /></div>
            {error && <div className="auth-error" role="alert">{error}</div>}
            <button className="btn btn-primary btn-full" type="submit" disabled={submitting || !isSupabaseConfigured}>{submitting ? t('login.signUpSubmitting') : t('login.signUpSubmit')} {!submitting && <ArrowRight size={18} />}</button>
            {!isSupabaseConfigured && <div className="demo-note"><ShieldCheck size={15} /><span><b>{t('login.configRequired')}</b> {t('login.configHint')}</span></div>}
            <p className="auth-switch-hint">{t('login.hasAccount')} <button type="button" className="text-btn" onClick={() => switchTab('signin')}>{t('login.tabSignIn')}</button></p>
          </form>
        )}

        {/* ── Admin: no public sign-up ── */}
        {isAdmin && (
          <div className="admin-signup-notice"><ShieldCheck /><span><b>{t('login.adminNotice')}</b></span></div>
        )}
      </div>
    </div>
  </div>
}

function Guard({ allow, children }: { allow: Role, children: ReactNode }) {
  const { role, authLoading } = useApp()
  const { t } = useI18n()
  if (authLoading) return <div className="auth-loading"><Recycle /><b>{t('guard.verifying')}</b></div>
  if (!role) return <Navigate to="/select-role" replace />
  return role === allow ? children : <Navigate to={dashboardPath(role)} replace />
}

const navByRole = (t: I18n['t']): Record<Role, { to: string, label: string, icon: ReactNode, end?: boolean }[]> => ({
  collector: [{ to: '/collector', label: t('nav.overview'), icon: <Home />, end: true }, { to: '/collector/new-lot', label: t('nav.createLot'), icon: <Plus /> }, { to: '/collector/history', label: t('nav.myLots'), icon: <PackageCheck /> }],
  recycler: [{ to: '/recycler', label: t('nav.inboundLots'), icon: <Home />, end: true }, { to: '/recycler/quotations', label: t('nav.quotations'), icon: <CircleDollarSign /> }, { to: '/recycler/manifests', label: t('nav.manifests'), icon: <ClipboardCheck /> }],
  admin: [{ to: '/admin', label: t('nav.overview'), icon: <BarChart3 />, end: true }, { to: '/admin/traceability', label: t('nav.traceability'), icon: <Search /> }, { to: '/admin/organizations', label: t('nav.organizations'), icon: <Building2 /> }, { to: '/admin/reports', label: t('nav.reports'), icon: <FileCheck2 /> }],
})

function Shell({ role, title, children }: { role: Role, title: string, children: ReactNode }) {
  const { profile, signOut } = useApp()
  const { t } = useI18n()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const accountName = profile?.entityName || profile?.displayName || t('common.account')
  const nav = navByRole(t)
  const badge = role === 'recycler' ? t('shell.badgeRecycler') : role === 'admin' ? t('shell.badgeAdmin') : t('shell.badgeCollector')
  const roleTitle: Record<Role, string> = { collector: t('role.collector.title'), recycler: t('role.recycler.title'), admin: t('role.admin.title') }
  return <div className="app-shell"><aside className={open ? 'sidebar open' : 'sidebar'}><div className="side-head"><Brand inverse /><button className="mobile-close" onClick={() => setOpen(false)} aria-label={t('shell.closeMenu')}><X /></button></div><div className="role-label">{role === 'admin' ? t('shell.portalAdmin') : t('shell.portal', { role: roleTitle[role] })}</div><nav>{nav[role].map((item) => <NavLink key={item.to} to={item.to} end={item.end} onClick={() => setOpen(false)}>{item.icon}<span>{item.label}</span></NavLink>)}</nav><div className="side-help"><Leaf /><b>{t('shell.needHelp')}</b><span>{t('shell.supportLine')}</span><strong>1800-123-2629</strong></div><button className="signout" onClick={async () => { await signOut(); navigate('/') }}><LogOut /> {t('shell.signOut')}</button></aside>
    <div className="app-main"><header className="app-header"><button className="menu-button" onClick={() => setOpen(true)} aria-label={t('shell.openMenu')}><Menu /></button><div><small>{t('shell.welcomeBack')}</small><h1>{title}</h1></div><div className="header-actions"><button className="icon-button" aria-label={t('common.notifications')}><Bell /></button><div className="profile"><span>{accountName.split(' ').map(x => x[0]).slice(0,2).join('').toUpperCase()}</span><div><b>{accountName}</b><small>{badge}</small></div></div></div></header><main className="content">{children}</main></div>{open && <button className="overlay" onClick={() => setOpen(false)} aria-label={t('shell.closeMenu')} />}</div>
}

function Metric({ icon, label, value, note, tone = 'green' }: { icon: ReactNode, label: string, value: string, note: string, tone?: string }) {
  return <article className="metric-card"><span className={`metric-icon ${tone}`}>{icon}</span><div><small>{label}</small><strong>{value}</strong><span>{note}</span></div></article>
}

function CollectorDashboard() {
  const { lots, profile } = useApp(); const navigate = useNavigate(); const { t, formatCurrency, formatNumber } = useI18n()
  const total = lots.reduce((sum, lot) => sum + lot.weight, 0)
  return <Shell role="collector" title={profile?.displayName || t('collector.title')}><div className="notice"><span><WifiOff /></span><div><b>{t('collector.offlineTitle')}</b><p>{t('collector.offlineText')}</p></div><button>{t('collector.gotIt')}</button></div>
    <section className="dashboard-hero"><div><span className="eyebrow">{t('collector.heroKicker')}</span><h2>{t('collector.heroHeading')}</h2><p>{t('collector.heroText')}</p><button className="btn btn-light" onClick={() => navigate('/collector/new-lot')}><Plus /> {t('collector.heroAction')}</button></div><div className="hero-recycle"><Recycle /></div></section>
    <div className="metrics"><Metric icon={<PackageCheck />} label={t('collector.metricActiveLots')} value={String(lots.filter(l => l.status !== 'status.processed').length)} note={t('collector.metricActiveLotsNote', { count: 2 })} /><Metric icon={<Weight />} label={t('collector.metricTotalCollected')} value={`${formatNumber(total, { minimumFractionDigits: 1 })} ${t('common.kg')}`} note={t('collector.metricTotalCollectedNote', { value: 12 })} tone="blue" /><Metric icon={<CircleDollarSign />} label={t('collector.metricTotalEarned')} value={formatCurrency(24680)} note={t('collector.metricTotalEarnedNote', { value: formatCurrency(6916) })} tone="amber" /></div>
    <SectionHead title={t('collector.recentLots')} link={t('common.viewAll')} onClick={() => navigate('/collector/history')} /><LotTable lots={lots.slice(0,4)} onRow={(id) => navigate(`/trace/${id}`)} />
  </Shell>
}

function SectionHead({ title, link, onClick }: { title: string, link?: string, onClick?: () => void }) { const { t } = useI18n(); return <div className="section-head"><h2>{title}</h2>{link && <button onClick={onClick}>{link} <ArrowRight /></button>}</div> }

function Status({ value }: { value: StatusKey }) { const { t } = useI18n(); return <span className={`status ${statusModifier[value]}`}><i />{t(value)}</span> }

function LotTable({ lots, onRow, recycler = false }: { lots: Lot[], onRow: (id: string) => void, recycler?: boolean }) {
  const { t, formatCurrency, formatNumber, formatDate } = useI18n()
  return <div className="table-card"><div className="table-scroll"><table><thead><tr><th>{t('table.lotId')}</th><th>{t('table.material')}</th><th>{t('table.weight')}</th><th>{recycler ? t('table.collectorLocation') : t('table.recycler')}</th><th>{t('table.status')}</th><th>{t('table.value')}</th><th /></tr></thead><tbody>{lots.map(lot => <tr key={lot.id} onClick={() => onRow(lot.id)} aria-label={t('table.open', { id: lot.id })}><td><b>{lot.id}</b><small>{formatDate(lot.createdAt)}</small></td><td>{t(lot.material)}</td><td>{formatNumber(lot.weight, { maximumFractionDigits: 1 })} {t('common.kg')}</td><td>{recycler ? lot.location : lot.recycler}</td><td><Status value={lot.status} /></td><td><b>{formatCurrency(lot.estimatedValue)}</b></td><td><ChevronRight /></td></tr>)}</tbody></table></div></div>
}

const categories: { key: MaterialKey, rate: number, icon: string }[] = [
  { key: 'material.circuitBoards', rate: 380, icon: '▦' },
  { key: 'material.cablesWires', rate: 460, icon: '⌁' },
  { key: 'material.batteries', rate: 160, icon: '▤' },
  { key: 'material.mixedElectronics', rate: 200, icon: '◫' },
]

function NewLot() {
  const navigate = useNavigate(); const { addLot } = useApp(); const { t, formatCurrency, formatNumber } = useI18n()
  const [step, setStep] = useState(1); const [material, setMaterial] = useState(''); const [weight, setWeight] = useState(''); const [photo, setPhoto] = useState('')
  const selected = categories.find(c => c.key === material); const value = selected ? Math.round(Number(weight || 0) * selected.rate) : 0
  const finish = () => { const id = `EB-${Math.floor(25000 + Math.random() * 70000)}`; addLot({ id, material: selected!.key, weight: Number(weight), estimatedValue: value, status: 'status.quoteRequested', recycler: 'Matching nearby recycler', location: 'Pune, Maharashtra', createdAt: new Date().toISOString().slice(0, 10), synced: navigator.onLine }); navigate(`/trace/${id}`) }
  const stepperLabels: TranslationKey[] = ['newLot.stepper.photo', 'newLot.stepper.material', 'newLot.stepper.weight']
  return <Shell role="collector" title={t('newLot.title')}><div className="wizard-wrap"><div className="wizard-title"><button onClick={() => step > 1 ? setStep(step - 1) : navigate('/collector')} aria-label={t('common.back')}>←</button><div><h2>{t('newLot.heading')}</h2><p>{t('newLot.stepOf', { current: step, total: 3 })}</p></div></div><div className="stepper">{stepperLabels.map((label, index) => <div className={step >= index + 1 ? 'active' : ''} key={label}><span>{step > index + 1 ? <Check /> : index + 1}</span><b>{t(label)}</b></div>)}</div>
    <div className="wizard-card">
      {step === 1 && <><span className="section-kicker">{t('newLot.step1.kicker')}</span><h2>{t('newLot.step1.heading')}</h2><p>{t('newLot.step1.text')}</p><label className={`photo-drop ${photo ? 'has-photo' : ''}`}>{photo ? <><Camera /><b>{t('newLot.photoAdded')}</b><span>{photo}</span></> : <><span className="camera-circle"><Camera /></span><b>{t('newLot.photoAdd')}</b><span>{t('newLot.photoFormats')}</span></>}<input type="file" accept="image/*" capture="environment" onChange={e => setPhoto(e.target.files?.[0]?.name || '')} /></label><div className="tip"><Sparkles /><span><b>{t('newLot.photoTipTitle')}</b> {t('newLot.photoTipText')}</span></div><button className="btn btn-primary btn-full" disabled={!photo} onClick={() => setStep(2)}>{t('newLot.identifyMaterial')} <ArrowRight /></button></>}
      {step === 2 && <><span className="section-kicker">{t('newLot.step2.kicker')}</span><h2>{t('newLot.step2.heading')}</h2><p>{t('newLot.step2.text')}</p><div className="ai-suggestion"><Sparkles /><div><small>{t('newLot.aiSuggestion', { value: 88 })}</small><b>{t('material.circuitBoards')}</b></div><span>{t('newLot.checkBeforeContinuing')}</span></div><div className="category-grid">{categories.map(item => <button key={item.key} className={material === item.key ? 'selected' : ''} onClick={() => setMaterial(item.key)}><span>{item.icon}</span><div><b>{t(item.key)}</b><small>{t('newLot.typicalRate', { rate: formatCurrency(item.rate) })}</small></div>{material === item.key && <Check />}</button>)}</div><button className="btn btn-primary btn-full" disabled={!material} onClick={() => setStep(3)}>{t('newLot.continue')} <ArrowRight /></button></>}
      {step === 3 && <><span className="section-kicker">{t('newLot.step3.kicker')}</span><h2>{t('newLot.step3.heading')}</h2><p>{t('newLot.step3.text')}</p><label>{t('newLot.weightLabel')}</label><div className="weight-input"><input type="number" min="0.1" step="0.1" value={weight} onChange={e => setWeight(e.target.value)} placeholder="0.0" /><span>{t('common.kg')}</span></div><div className="estimate-card"><div><small>{t('newLot.estimatedValue')}</small><strong>{formatCurrency(value)}</strong><span>{t('newLot.basedOnRate', { rate: formatCurrency(selected?.rate || 0) })}</span></div><CircleDollarSign /></div><div className="value-note"><ShieldCheck /><span>{t('newLot.estimateNote')}</span></div><button className="btn btn-primary btn-full" disabled={!weight || Number(weight) <= 0} onClick={finish}>{t('newLot.findRecyclers')} <ArrowRight /></button></>}
    </div></div></Shell>
}

function CollectorHistory() { const { lots } = useApp(); const navigate = useNavigate(); const { t } = useI18n(); return <Shell role="collector" title={t('collector.myLotsTitle')}><div className="page-tools"><div><h2>{t('collector.allLots')}</h2><p>{t('collector.allLotsText')}</p></div><button className="btn btn-primary" onClick={() => navigate('/collector/new-lot')}><Plus /> {t('nav.newLot')}</button></div><LotTable lots={lots} onRow={(id) => navigate(`/trace/${id}`)} /></Shell> }

function RecyclerDashboard() {
  const { lots, updateLot } = useApp(); const navigate = useNavigate(); const { t, formatNumber, formatMonth } = useI18n()
  const [filter, setFilter] = useState<StatusKey | 'all'>('all')
  const visible = filter === 'all' ? lots : lots.filter(l => l.status === filter)
  const chips: (StatusKey | 'all')[] = ['all', 'status.quoteRequested', 'status.pickupScheduled', 'status.weighed']
  return <Shell role="recycler" title={t('recycler.title')}><div className="page-tools"><div><span className="eyebrow">{t('recycler.kicker')}</span><h2>{t('recycler.heading')}</h2><p>{t('recycler.text')}</p></div><div className="verified-pill"><ShieldCheck /> {t('recycler.cpcbActive')}</div></div>
    <div className="metrics"><Metric icon={<PackageCheck />} label={t('recycler.metricRequests')} value="8" note={t('recycler.metricRequestsNote', { count: 3 })} /><Metric icon={<Truck />} label={t('recycler.metricPickups')} value="5" note={t('recycler.metricPickupsNote', { time: '11:30' })} tone="blue" /><Metric icon={<Weight />} label={t('recycler.metricWeight')} value={`${formatNumber(1248)} ${t('common.kg')}`} note={t('recycler.metricWeightNote', { value: 18, month: formatMonth(7) })} tone="amber" /></div>
    <div className="filter-row"><div className="search"><Search /><input placeholder={t('recycler.searchPlaceholder')} /></div><div className="chips">{chips.map(item => <button className={filter === item ? 'active' : ''} onClick={() => setFilter(item)} key={item}>{item === 'all' ? t('common.all') : t(item)}</button>)}</div></div>
    <LotTable recycler lots={visible} onRow={(id) => navigate(`/trace/${id}`)} />
    <div className="quick-action"><div><ClipboardCheck /><span><b>{t('recycler.quickActionTitle')}</b><small>{t('recycler.quickActionText')}</small></span></div><button className="btn btn-dark" onClick={() => lots[0] && updateLot(lots[0].id, { status: 'status.weighed', finalWeight: lots[0].weight - .3, finalValue: Math.round((lots[0].weight - .3) * 380) })}>{t('recycler.quickWeighIn')}</button></div>
  </Shell>
}

function RecyclerQuotations() {
  const { lots, updateLot } = useApp(); const { t, formatCurrency, formatDate } = useI18n()
  const [quotedId, setQuotedId] = useState<string | null>(null)
  const [rate, setRate] = useState('380')
  const requests = lots.filter((lot) => (['status.quoteRequested', 'status.quoteReceived'] as StatusKey[]).includes(lot.status))
  const sendQuote = (lot: Lot) => {
    updateLot(lot.id, { status: 'status.quoteReceived', estimatedValue: Math.round(lot.weight * Number(rate)) })
    setQuotedId(null)
  }
  return <Shell role="recycler" title={t('quotations.title')}>
    <div className="page-tools"><div><span className="eyebrow">{t('quotations.kicker')}</span><h2>{t('quotations.heading')}</h2><p>{t('quotations.text')}</p></div><div className="verified-pill"><ShieldCheck /> {t('quotations.ratesVisible')}</div></div>
    <div className="metrics"><Metric icon={<Clock3 />} label={t('quotations.metricAwaiting')} value={String(requests.filter(l => l.status === 'status.quoteRequested').length)} note={t('quotations.metricAwaitingNote', { time: t('units.hours', { value: 4 }) })} /><Metric icon={<CircleDollarSign />} label={t('quotations.metricSent')} value={String(requests.filter(l => l.status === 'status.quoteReceived').length)} note={t('quotations.metricSentNote', { count: 2 })} tone="blue" /><Metric icon={<BarChart3 />} label={t('quotations.metricAverage')} value={t('units.perKgRate', { value: 346 })} note={t('quotations.metricAverageNote')} tone="amber" /></div>
    <div className="quote-list">{requests.length ? requests.map((lot) => <article className="quote-card" key={lot.id}><div className="quote-main"><span className="metric-icon"><Recycle /></span><div><small>{lot.id} · {formatDate(lot.createdAt)}</small><h3>{t(lot.material)}</h3><p><MapPin /> {lot.location} <Weight /> {t('quotations.weightEstimated', { weight: lot.weight })}</p></div></div><div className="quote-value"><small>{t('quotations.indicativeValue')}</small><b>{formatCurrency(lot.estimatedValue)}</b><Status value={lot.status} /></div>{quotedId === lot.id ? <div className="quote-editor"><label>{t('quotations.rateLabel')}</label><div><span>₹</span><input type="number" min="1" value={rate} onChange={(event) => setRate(event.target.value)} /><span>{t('common.perKg')}</span></div><strong>{t('quotations.totalOffer', { value: formatCurrency(Math.round(lot.weight * Number(rate || 0))) })}</strong><button className="btn btn-primary" onClick={() => sendQuote(lot)}>{t('quotations.send')} <ArrowRight /></button><button className="btn btn-outline" onClick={() => setQuotedId(null)}>{t('common.cancel')}</button></div> : <button className="btn btn-primary" onClick={() => setQuotedId(lot.id)}>{lot.status === 'status.quoteReceived' ? t('quotations.revise') : t('quotations.create')} <ArrowRight /></button>}</article>) : <div className="empty-state"><CircleDollarSign /><h3>{t('quotations.emptyTitle')}</h3><p>{t('quotations.emptyText')}</p></div>}</div>
  </Shell>
}

function RecyclerManifests() {
  const { lots, updateLot } = useApp(); const { t, formatDate, formatNumber } = useI18n()
  const navigate = useNavigate()
  const [created, setCreated] = useState<string[]>([])
  const eligibleLots = lots.filter((lot) => (['status.pickupScheduled', 'status.weighed', 'status.paid', 'status.processed'] as StatusKey[]).includes(lot.status))
  const createManifest = (lot: Lot) => {
    if (!created.includes(lot.id)) setCreated([...created, lot.id])
    if (lot.status === 'status.pickupScheduled') updateLot(lot.id, { status: 'status.weighed', finalWeight: lot.weight })
  }
  return <Shell role="recycler" title={t('manifests.title')}>
    <div className="page-tools"><div><span className="eyebrow">{t('manifests.kicker')}</span><h2>{t('manifests.heading')}</h2><p>{t('manifests.text')}</p></div><button className="btn btn-outline"><Download /> {t('manifests.export')}</button></div>
    <div className="manifest-summary"><div><FileCheck2 /><span><b>{t('manifests.count', { count: eligibleLots.length + created.length })}</b><small>{t('manifests.period')}</small></span></div><div><ShieldCheck /><span><b>{t('manifests.verified')}</b><small>{t('manifests.noExceptions')}</small></span></div></div>
    <div className="table-card"><div className="table-scroll"><table><thead><tr><th>{t('table.manifest')}</th><th>{t('table.lotId')}</th><th>{t('table.material')}</th><th>{t('table.verifiedWeight')}</th><th>{t('table.processingStatus')}</th><th>{t('table.document')}</th></tr></thead><tbody>{eligibleLots.map((lot, index) => { const hasManifest = created.includes(lot.id) || (['status.weighed','status.paid','status.processed'] as StatusKey[]).includes(lot.status); return <tr key={lot.id} onClick={() => navigate(`/trace/${lot.id}`)}><td><b>{hasManifest ? `MF-26-${String(1042 + index).padStart(4, '0')}` : t('common.notCreated')}</b><small>{formatDate(lot.createdAt)}</small></td><td><b>{lot.id}</b></td><td>{t(lot.material)}</td><td>{formatNumber(lot.finalWeight || lot.weight, { maximumFractionDigits: 1 })} {t('common.kg')}</td><td><Status value={lot.status} /></td><td>{hasManifest ? <button className="table-action" onClick={(event) => { event.stopPropagation(); navigate(`/trace/${lot.id}`) }}><FileCheck2 /> {t('manifests.view')}</button> : <button className="table-action primary" onClick={(event) => { event.stopPropagation(); createManifest(lot) }}><Plus /> {t('manifests.create')}</button>}</td></tr>})}</tbody></table></div>{!eligibleLots.length && <div className="empty-state"><ClipboardCheck /><h3>{t('manifests.emptyTitle')}</h3><p>{t('manifests.emptyText')}</p></div>}</div>
  </Shell>
}

function AdminDashboard() {
  const { lots } = useApp(); const navigate = useNavigate(); const { t, formatCurrency, formatNumber, formatDate, formatMonth } = useI18n()
  return <Shell role="admin" title={t('admin.title')}><div className="page-tools"><div><span className="eyebrow">{t('admin.kicker')}</span><h2>{t('admin.heading')}</h2><p>{t('admin.reportingPeriod', { period: `${formatDate('2026-09-01', { day: 'numeric', month: 'short' })} – ${formatDate('2026-09-25', { day: 'numeric', month: 'short', year: 'numeric' })}` })}</p></div><button className="btn btn-outline"><Download /> {t('admin.exportReport')}</button></div>
    <div className="metrics admin-metrics"><Metric icon={<Weight />} label={t('admin.metricTraced')} value={t('units.tonnes', { value: formatNumber(12.4, { minimumFractionDigits: 1 }) })} note={t('admin.metricTracedNote', { value: 14.2 })} /><Metric icon={<Users />} label={t('admin.metricCollectors')} value={formatNumber(840)} note={t('admin.metricCollectorsNote', { count: 62 })} tone="blue" /><Metric icon={<Building2 />} label={t('admin.metricRecyclers')} value={formatNumber(28)} note={t('admin.metricRecyclersNote', { count: 2 })} tone="purple" /><Metric icon={<CircleDollarSign />} label={t('admin.metricSettled')} value={t('units.lakh', { value: `₹${formatNumber(18.6, { minimumFractionDigits: 1 })}` })} note={t('admin.metricSettledNote', { value: 96.8 })} tone="amber" /></div>
    <div className="admin-grid"><section className="panel"><SectionHead title={t('admin.materialFlow')} link={t('admin.lastMonths')} /><div className="chart"><div className="y-labels"><span>{t('units.tonnesShort', { value: 4 })}</span><span>{t('units.tonnesShort', { value: 3 })}</span><span>{t('units.tonnesShort', { value: 2 })}</span><span>{t('units.tonnesShort', { value: 1 })}</span><span>0</span></div><div className="bars">{[42,55,48,68,74,88].map((h,i) => <div key={i}><i style={{height:`${h}%`}} /><span>{formatMonth(3 + i)}</span></div>)}</div></div></section><section className="panel"><SectionHead title={t('admin.processingStatus')} /><div className="donut-wrap"><div className="donut"><span><b>{formatNumber(1286)}</b><small>{t('admin.totalLots')}</small></span></div><div className="legend"><span><i className="green-bg" />{t('admin.legendProcessed')} <b>68%</b></span><span><i className="blue-bg" />{t('admin.legendTransit')} <b>19%</b></span><span><i className="amber-bg" />{t('admin.legendAwaiting')} <b>10%</b></span><span><i className="gray-bg" />{t('admin.legendExceptions')} <b>3%</b></span></div></div></section></div>
    <SectionHead title={t('admin.recentRecords')} link={t('admin.searchAll')} /><LotTable lots={lots} recycler onRow={(id) => navigate(`/trace/${id}`)} />
  </Shell>
}

function AdminTraceability() {
  const { lots } = useApp(); const { t } = useI18n()
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState<StatusKey | 'all'>('all')
  const visible = lots.filter((lot) => {
    const haystack = `${lot.id} ${t(lot.material)} ${lot.location} ${lot.recycler}`.toLowerCase()
    return haystack.includes(query.toLowerCase()) && (status === 'all' || lot.status === status)
  })
  const chips: (StatusKey | 'all')[] = ['all', 'status.quoteReceived', 'status.pickupScheduled', 'status.weighed', 'status.paid', 'status.processed']
  return <Shell role="admin" title={t('traceSearch.title')}>
    <div className="page-tools"><div><span className="eyebrow">{t('traceSearch.kicker')}</span><h2>{t('traceSearch.heading')}</h2><p>{t('traceSearch.text')}</p></div><button className="btn btn-outline"><Download /> {t('traceSearch.export')}</button></div>
    <div className="audit-search"><div className="search"><Search /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder={t('traceSearch.placeholder')} /></div><div className="chips">{chips.map(item => <button key={item} className={status === item ? 'active' : ''} onClick={() => setStatus(item)}>{item === 'all' ? t('common.all') : t(item)}</button>)}</div></div>
    <div className="record-count"><ShieldCheck /><span><b>{t('traceSearch.count', { count: visible.length })}</b><small>{t('traceSearch.countNote')}</small></span></div>
    {visible.length ? <LotTable lots={visible} recycler onRow={(id) => navigate(`/trace/${id}`)} /> : <div className="table-card empty-state"><Search /><h3>{t('traceSearch.emptyTitle')}</h3><p>{t('traceSearch.emptyText')}</p></div>}
  </Shell>
}

type Organization = { id: string, name: string, type: OrgTypeKey, location: string, registration: string, status: OrgStatusKey }
const orgStatusModifier: Record<OrgStatusKey, string> = { 'org.statusActive': 'active', 'org.statusPending': 'pending-review', 'org.statusSuspended': 'suspended' }
const organizationSeed: Organization[] = [
  { id: 'ORG-001', name: 'GreenCycle Industries', type: 'org.typeRecycler', location: 'Pune, Maharashtra', registration: 'CPCB/EPR/2026/042', status: 'org.statusActive' },
  { id: 'ORG-002', name: 'Maha E-Recyclers', type: 'org.typeRecycler', location: 'Mumbai, Maharashtra', registration: 'CPCB/EPR/2025/118', status: 'org.statusActive' },
  { id: 'ORG-003', name: 'EcoLoop Solutions', type: 'org.typeRecycler', location: 'Nashik, Maharashtra', registration: 'CPCB/EPR/2026/091', status: 'org.statusPending' },
  { id: 'ORG-004', name: 'Pune Kabadi Collective', type: 'org.typeNetwork', location: 'Pune, Maharashtra', registration: 'ULB/PMC/COL/284', status: 'org.statusActive' },
]

function AdminOrganizations() {
  const { t, formatNumber, language } = useI18n()
  const [organizations, setOrganizations] = useState(organizationSeed)
  const [filter, setFilter] = useState<OrgStatusKey | 'all'>('all')
  // The directory is read through `authorized_recyclers_localized`, so the
  // descriptive `service_area` arrives already translated. Names, facility
  // addresses and CPCB numbers stay verbatim: they are proper nouns.
  type LocalizedRecyclerRow = {
    recycler_id: string
    name: string
    facility_location: string | null
    city: string | null
    service_area: string | null
    cpcb_reg_no: string
    authorization_status: string
  }
  const recyclerDirectory = useQuery({
    queryKey: ['authorized-recyclers', language],
    enabled: isSupabaseConfigured,
    queryFn: async () => {
      const { data, error } = await supabase!.rpc('authorized_recyclers_localized', { p_lang: language })
      if (error) throw error
      return (data ?? []) as LocalizedRecyclerRow[]
    },
  })
  useEffect(() => {
    if (!recyclerDirectory.data?.length) return
    setOrganizations(recyclerDirectory.data.map((row) => ({
      id: row.recycler_id,
      name: row.name,
      type: 'org.typeRecycler' as const,
      location: row.facility_location || row.city || t('org.locationMissing'),
      registration: row.cpcb_reg_no,
      status: row.authorization_status === 'active' ? 'org.statusActive' as const : row.authorization_status === 'suspended' ? 'org.statusSuspended' as const : 'org.statusPending' as const,
    })))
  }, [recyclerDirectory.data, t])
  const visible = filter === 'all' ? organizations : organizations.filter((item) => item.status === filter)
  const updateStatus = (id: string, status: OrgStatusKey) => setOrganizations(organizations.map((item) => item.id === id ? { ...item, status } : item))
  const chips: (OrgStatusKey | 'all')[] = ['all', 'org.statusActive', 'org.statusPending', 'org.statusSuspended']
  return <Shell role="admin" title={t('org.title')}>
    <div className="page-tools"><div><span className="eyebrow">{t('org.kicker')}</span><h2>{t('org.heading')}</h2><p>{t('org.text')}</p></div><div className={`data-source ${isSupabaseConfigured && !recyclerDirectory.isError ? 'connected' : ''}`}><i />{recyclerDirectory.isLoading ? t('org.connecting') : isSupabaseConfigured && !recyclerDirectory.isError ? t('org.connected') : t('org.previewData')}</div></div>
    <div className="metrics"><Metric icon={<Building2 />} label={t('org.metricRecyclers')} value={formatNumber(28)} note={t('org.metricRecyclersNote', { count: 26 })} /><Metric icon={<Users />} label={t('org.metricNetworks')} value={formatNumber(42)} note={t('org.metricNetworksNote', { count: 840 })} tone="blue" /><Metric icon={<Clock3 />} label={t('org.metricPending')} value={String(organizations.filter(o => o.status === 'org.statusPending').length)} note={t('org.metricPendingNote')} tone="amber" /></div>
    <div className="filter-row"><div className="search"><Search /><input placeholder={t('org.searchPlaceholder')} /></div><div className="chips">{chips.map(item => <button className={filter === item ? 'active' : ''} onClick={() => setFilter(item)} key={item}>{item === 'all' ? t('common.all') : t(item)}</button>)}</div></div>
    <div className="organization-list">{visible.map((organization) => <article className="organization-card" key={organization.id}><span className="org-logo">{organization.name.split(' ').map(word => word[0]).slice(0,2).join('')}</span><div className="org-details"><small>{t(organization.type).toUpperCase()} · {organization.id}</small><h3>{organization.name}</h3><p><MapPin /> {organization.location}<ShieldCheck /> {organization.registration}</p></div><span className={`org-status ${orgStatusModifier[organization.status]}`}>{t(organization.status)}</span><div className="org-actions">{organization.status === 'org.statusPending' ? <><button className="btn btn-primary" onClick={() => updateStatus(organization.id, 'org.statusActive')}><Check /> {t('org.approve')}</button><button className="btn btn-outline" onClick={() => updateStatus(organization.id, 'org.statusSuspended')}>{t('org.reject')}</button></> : <button className="btn btn-outline">{t('org.viewProfile')} <ChevronRight /></button>}</div></article>)}</div>
  </Shell>
}

const reportTypes: { title: TranslationKey, text: TranslationKey, icon: ReactNode, frequency: TranslationKey }[] = [
  { title: 'reports.summary.title', text: 'reports.summary.text', icon: <BarChart3 />, frequency: 'reports.frequencyMonthly' },
  { title: 'reports.epr.title', text: 'reports.epr.text', icon: <ShieldCheck />, frequency: 'reports.frequencyQuarterly' },
  { title: 'reports.payment.title', text: 'reports.payment.text', icon: <CircleDollarSign />, frequency: 'reports.frequencyMonthly' },
  { title: 'reports.authorization.title', text: 'reports.authorization.text', icon: <Building2 />, frequency: 'reports.frequencyLive' },
]

function AdminReports() {
  const { t } = useI18n()
  const [generated, setGenerated] = useState<TranslationKey[]>([])
  const generate = (title: TranslationKey) => setGenerated(generated.includes(title) ? generated : [...generated, title])
  return <Shell role="admin" title={t('reports.title')}>
    <div className="page-tools"><div><span className="eyebrow">{t('reports.kicker')}</span><h2>{t('reports.heading')}</h2><p>{t('reports.text')}</p></div><div className="report-period"><small>{t('reports.periodLabel')}</small><b>{t('reports.periodValue')}</b></div></div>
    <div className="report-grid">{reportTypes.map((report) => <article className="report-card" key={report.title}><span className="report-icon">{report.icon}</span><span className="report-frequency">{t(report.frequency)}</span><h3>{t(report.title)}</h3><p>{t(report.text)}</p><div>{generated.includes(report.title) ? <span className="generated"><Check /> {t('reports.ready')}</span> : <span>{t('reports.formats')}</span>}<button className="btn btn-outline" onClick={() => generate(report.title)}>{generated.includes(report.title) ? <><Download /> {t('reports.download')}</> : <><FileCheck2 /> {t('reports.generate')}</>}</button></div></article>)}</div>
    <section className="panel report-history"><SectionHead title={t('reports.recent')} /><div><span><FileCheck2 /><b>{t('reports.history1')}</b><small>{t('reports.history1Meta')}</small></span><button className="table-action"><Download /> {t('reports.download')}</button></div><div><span><ShieldCheck /><b>{t('reports.history2')}</b><small>{t('reports.history2Meta')}</small></span><button className="table-action"><Download /> {t('reports.download')}</button></div></section>
  </Shell>
}

const timelineStepCount = 6

function Traceability() {
  const { id } = useParams(); const { lots, role } = useApp(); const navigate = useNavigate(); const { t, formatCurrency, formatDate, formatNumber } = useI18n()
  const lot = lots.find(l => l.id === id) || lots[0]
  const progressByStatus: Record<StatusKey, number> = { 'status.draft': 1, 'status.quoteRequested': 1, 'status.quoteReceived': 2, 'status.pickupScheduled': 3, 'status.weighed': 4, 'status.paid': 5, 'status.processed': 6 }
  const progress = lot ? progressByStatus[lot.status] : 1
  if (!lot) return <Navigate to="/" />
  const steps: { title: TranslationKey, text: TranslationKey, icon: ReactNode }[] = [
    { title: 'trace.step1.title', text: 'trace.step1.text', icon: <Camera /> },
    { title: 'trace.step2.title', text: 'trace.step2.text', icon: <Building2 /> },
    { title: 'trace.step3.title', text: 'trace.step3.text', icon: <CircleDollarSign /> },
    { title: 'trace.step4.title', text: 'trace.step4.text', icon: <Weight /> },
    { title: 'trace.step5.title', text: 'trace.step5.text', icon: <Check /> },
    { title: 'trace.step6.title', text: 'trace.step6.text', icon: <Recycle /> },
  ]
  const city = lot.location.split(',')[0]
  const content = <div className="trace-page"><button className="back-link" onClick={() => navigate(-1)}>← {t('common.back')}</button><div className="trace-head"><div><span className="eyebrow">{t('trace.kicker')}</span><h2>{lot.id}</h2><p>{t('trace.created', { date: formatDate(lot.createdAt) })}</p></div><div><Status value={lot.status} /><button className="btn btn-outline"><Download /> {t('trace.downloadProof')}</button></div></div>
    <div className="trace-grid"><section className="panel lot-summary"><h3>{t('trace.lotSummary')}</h3><div className="summary-material"><span><Recycle /></span><div><b>{t(lot.material)}</b><small>{t('trace.confirmedByCollector')}</small></div></div><div className="summary-values"><div><small>{t('trace.estimatedWeight')}</small><b>{formatNumber(lot.weight, { maximumFractionDigits: 1 })} {t('common.kg')}</b></div><div><small>{t('trace.verifiedWeight')}</small><b>{lot.finalWeight ? `${formatNumber(lot.finalWeight, { maximumFractionDigits: 1 })} ${t('common.kg')}` : t('common.pending')}</b></div><div><small>{t('trace.estimatedValue')}</small><b>{formatCurrency(lot.estimatedValue)}</b></div><div><small>{t('trace.finalPaidValue')}</small><b>{lot.finalValue ? formatCurrency(lot.finalValue) : t('common.pending')}</b></div></div><div className="verified-org"><ShieldCheck /><div><small>{t('trace.authorizedRecycler')}</small><b>{lot.recycler}</b><span>{t('trace.cpcbVerified')}</span></div></div></section>
      <section className="panel timeline"><h3>{t('trace.timeline')}</h3>{steps.map((item,index) => <div className={index < progress ? 'complete' : index === progress ? 'current' : ''} key={item.title}><span className="timeline-icon">{index < progress ? <Check /> : item.icon}</span><i /><div><b>{t(item.title)}</b><p>{t(item.text, { city })}</p><small>{index < progress ? formatDate(`2026-09-${String(24 + index).padStart(2, '0')}`, { hour: '2-digit', minute: '2-digit' }) : t('common.pending')}</small></div></div>)}</section></div>
    {!lot.synced && <div className="sync-warning"><WifiOff /><span><b>{t('trace.savedOnDevice')}</b>{t('trace.syncWarning')}</span></div>}
  </div>
  return role ? <Shell role={role} title={t('trace.title')}>{content}</Shell> : <div className="public-trace"><header><Brand /></header>{content}</div>
}

function NotFound() { const { t } = useI18n(); return <div className="not-found"><Recycle /><h1>{t('notFound.title')}</h1><p>{t('notFound.text')}</p><a className="btn btn-primary" href="/">{t('notFound.action')}</a></div> }

function AppRoutes() {
  const { t } = useI18n()
  useEffect(() => { document.title = t('meta.title') }, [t])
  return <AppProvider><Routes>
    <Route path="/" element={<Landing />} />
    <Route path="/select-role" element={<RoleSelection />} />
    <Route path="/login" element={<Login />} />
    <Route path="/collector" element={<Guard allow="collector"><CollectorDashboard /></Guard>} />
    <Route path="/collector/new-lot" element={<Guard allow="collector"><NewLot /></Guard>} />
    <Route path="/collector/history" element={<Guard allow="collector"><CollectorHistory /></Guard>} />
    <Route path="/recycler" element={<Guard allow="recycler"><RecyclerDashboard /></Guard>} />
    <Route path="/recycler/quotations" element={<Guard allow="recycler"><RecyclerQuotations /></Guard>} />
    <Route path="/recycler/manifests" element={<Guard allow="recycler"><RecyclerManifests /></Guard>} />
    <Route path="/admin" element={<Guard allow="admin"><AdminDashboard /></Guard>} />
    <Route path="/admin/traceability" element={<Guard allow="admin"><AdminTraceability /></Guard>} />
    <Route path="/admin/organizations" element={<Guard allow="admin"><AdminOrganizations /></Guard>} />
    <Route path="/admin/reports" element={<Guard allow="admin"><AdminReports /></Guard>} />
    <Route path="/trace/:id" element={<Traceability />} />
    <Route path="*" element={<NotFound />} />
  </Routes></AppProvider>
}

export default function App() {
  return <I18nProvider><AppRoutes /></I18nProvider>
}
