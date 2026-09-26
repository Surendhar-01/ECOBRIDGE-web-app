import type { TranslationKey } from './index'

/**
 * Display labels for the code-valued columns stored in Supabase.
 *
 * The codes are the stable contract; only the label is localized, so a row
 * written under one language renders correctly in all three. Each set mirrors a
 * CHECK constraint in `supabase/migrations/0008_localized_content.sql`, and an
 * unrecognised code degrades to a readable fallback rather than a blank.
 */
const TREND: Record<string, TranslationKey> = {
  UP: 'db.trend.up',
  DOWN: 'db.trend.down',
  STABLE: 'db.trend.stable',
}

const ALERT: Record<string, TranslationKey> = {
  CRITICAL: 'db.alert.critical',
  HIGH: 'db.alert.high',
  MEDIUM: 'db.alert.medium',
  LOW: 'db.alert.low',
}

const PAYMENT: Record<string, TranslationKey> = {
  CASH: 'db.payment.cash',
  UPI: 'db.payment.upi',
  BANK_TRANSFER: 'db.payment.bankTransfer',
}

const REQUEST: Record<string, TranslationKey> = {
  PENDING: 'db.request.pending',
  ACCEPTED: 'db.request.accepted',
  REJECTED: 'db.request.rejected',
  BLOCKED: 'db.request.blocked',
  REPORTED: 'db.request.reported',
}

const QUOTATION: Record<string, TranslationKey> = {
  PENDING: 'db.quotation.pending',
  SENT: 'db.quotation.sent',
  ACCEPTED: 'db.quotation.accepted',
  REJECTED: 'db.quotation.rejected',
  EXPIRED: 'db.quotation.expired',
  WITHDRAWN: 'db.quotation.withdrawn',
}

const AUTHORIZATION: Record<string, TranslationKey> = {
  active: 'db.authz.active',
  pending: 'db.authz.pending',
  expired: 'db.authz.expired',
  suspended: 'db.authz.suspended',
}

const UPLOAD: Record<string, TranslationKey> = {
  PENDING: 'db.upload.pending',
  UPLOADED: 'db.upload.uploaded',
  FAILED: 'db.upload.failed',
}

type Translate = (key: TranslationKey, values?: Record<string, string | number>) => string

export const dbTrend = (t: Translate, code?: string | null) =>
  t(TREND[code?.toUpperCase() ?? ''] ?? 'db.trend.stable')

export const dbAlertLevel = (t: Translate, code?: string | null) =>
  t(ALERT[code?.toUpperCase() ?? ''] ?? 'db.alert.medium')

export const dbPaymentMode = (t: Translate, code?: string | null) =>
  t(PAYMENT[code?.toUpperCase() ?? ''] ?? 'db.payment.cash')

export const dbRequestStatus = (t: Translate, code?: string | null) =>
  t(REQUEST[code?.toUpperCase() ?? ''] ?? 'db.request.pending')

export const dbQuotationStatus = (t: Translate, code?: string | null) =>
  t(QUOTATION[code?.toUpperCase() ?? ''] ?? 'db.quotation.pending')

export const dbAuthorizationStatus = (t: Translate, code?: string | null) =>
  t(AUTHORIZATION[code?.toLowerCase() ?? ''] ?? 'db.authz.pending')

export const dbUploadStatus = (t: Translate, code?: string | null) =>
  t(UPLOAD[code?.toUpperCase() ?? ''] ?? 'db.upload.pending')

/**
 * Renders an ISO-8601 instant as a localized relative time.
 *
 * Replaces the frozen English strings the `date_updated` column used to carry
 * ('Today', '2 days ago'), which went stale and could not be translated.
 */
export const dbRelativeTime = (
  t: Translate,
  isoInstant?: string | null,
  now: Date = new Date(),
): string => {
  if (!isoInstant) return ''
  const instant = new Date(isoInstant)
  if (Number.isNaN(instant.getTime())) return ''
  const seconds = Math.max(0, Math.floor((now.getTime() - instant.getTime()) / 1000))
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)
  if (minutes < 1) return t('db.relative.justNow')
  if (hours < 1) return t('db.relative.minutesAgo', { value: minutes })
  if (days < 1) return t('db.relative.hoursAgo', { value: hours })
  return t('db.relative.daysAgo', { value: days })
}
