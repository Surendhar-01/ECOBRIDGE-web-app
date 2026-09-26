import { describe, expect, it } from 'vitest'
import { en } from './locales/en'
import { hi } from './locales/hi'
import { mr } from './locales/mr'
import { translateIn, type TranslationKey } from './index'
import {
  dbAlertLevel,
  dbAuthorizationStatus,
  dbPaymentMode,
  dbQuotationStatus,
  dbRelativeTime,
  dbRequestStatus,
  dbTrend,
  dbUploadStatus,
} from './dbCodes'

// Uses the real interpolation so placeholder handling is exercised, not stubbed.
// `en` is declared `as const`, so widen it to the plain string map the others use.
type Values = Parameters<typeof translateIn>[2]
const translator = (dictionary: Record<TranslationKey, string>) =>
  (key: TranslationKey, values?: Values) => translateIn(dictionary, key, values)
const tEn = translator(en)
const tHi = translator(hi)
const tMr = translator(mr)

describe('database code labels', () => {
  it('maps material_prices.trend codes in every language', () => {
    expect(dbTrend(tEn, 'UP')).toBe('Rising')
    expect(dbTrend(tHi, 'UP')).toBe('बढ़ रहा है')
    expect(dbTrend(tMr, 'STABLE')).toBe('स्थिर')
  })

  it('is case-insensitive so a differently cased code still resolves', () => {
    expect(dbTrend(tEn, 'up')).toBe(dbTrend(tEn, 'UP'))
    expect(dbAuthorizationStatus(tEn, 'ACTIVE')).toBe(dbAuthorizationStatus(tEn, 'active'))
  })

  it('maps safety_guidelines.alert_level codes', () => {
    expect(dbAlertLevel(tEn, 'CRITICAL')).toBe('Critical')
    expect(dbAlertLevel(tHi, 'CRITICAL')).toBe('अत्यधिक गंभीर')
    expect(dbAlertLevel(tMr, 'LOW')).toBe('कमी')
  })

  it('maps payment, request, quotation, authorization and upload codes', () => {
    expect(dbPaymentMode(tEn, 'BANK_TRANSFER')).toBe('Direct bank deposit')
    expect(dbPaymentMode(tHi, 'UPI')).toBe('सीधे UPI हस्तांतरण')
    expect(dbRequestStatus(tMr, 'BLOCKED')).toBe('अडवणी')
    expect(dbQuotationStatus(tHi, 'WITHDRAWN')).toBe('वापस लिया गया')
    expect(dbAuthorizationStatus(tMr, 'suspended')).toBe('प्राधिकरण स्थगित')
    expect(dbUploadStatus(tHi, 'UPLOADED')).toBe('अपलोड हो गया')
  })

  it('degrades to a readable label for a code outside the known set', () => {
    // A CHECK constraint should prevent this, but the UI must not go blank.
    expect(dbTrend(tEn, 'SOMETHING_NEW')).toBe('Stable')
    expect(dbQuotationStatus(tEn, undefined)).toBe('Pending')
  })

  it('renders relative timestamps in the active language', () => {
    const now = new Date('2026-09-25T12:00:00Z')
    expect(dbRelativeTime(tEn, '2026-09-25T11:59:30Z', now)).toBe('just now')
    expect(dbRelativeTime(tEn, '2026-09-25T11:30:00Z', now)).toBe('30 min ago')
    expect(dbRelativeTime(tEn, '2026-09-25T06:00:00Z', now)).toBe('6 hr ago')
    expect(dbRelativeTime(tEn, '2026-09-23T12:00:00Z', now)).toBe('2 days ago')
  })

  it('localizes the relative timestamp wording', () => {
    const now = new Date('2026-09-25T12:00:00Z')
    expect(dbRelativeTime(tHi, '2026-09-23T12:00:00Z', now)).toBe('2 दिन पहले')
    expect(dbRelativeTime(tMr, '2026-09-23T12:00:00Z', now)).toBe('2 दिवसांपूर्वी')
  })

  it('returns an empty string for a missing or unparseable timestamp', () => {
    expect(dbRelativeTime(tEn, null, new Date())).toBe('')
    expect(dbRelativeTime(tEn, 'not-a-date', new Date())).toBe('')
  })
})
