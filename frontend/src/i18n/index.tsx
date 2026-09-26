import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { en, type TranslationKey } from './locales/en'
import { hi } from './locales/hi'
import { mr } from './locales/mr'

export type { TranslationKey }

export type LanguageCode = 'en' | 'hi' | 'mr'

export type LanguageOption = {
  code: LanguageCode
  /** Name of the language in English, used for analytics and screen readers. */
  label: string
  /** Endonym shown on the switcher so each option is readable in its own script. */
  native: string
  /** BCP 47 tag used for Intl formatting. */
  locale: string
}

export const languages: readonly LanguageOption[] = [
  { code: 'en', label: 'English', native: 'English', locale: 'en-IN' },
  { code: 'hi', label: 'Hindi', native: 'हिन्दी', locale: 'hi-IN' },
  { code: 'mr', label: 'Marathi', native: 'मराठी', locale: 'mr-IN' },
]

const dictionaries: Record<LanguageCode, Record<TranslationKey, string>> = { en, hi, mr }

const STORAGE_KEY = 'eco-language'

type InterpolationValues = Record<string, string | number>

const isLanguageCode = (value: unknown): value is LanguageCode =>
  typeof value === 'string' && languages.some((language) => language.code === value)

/**
 * Resolves the language to start with: an explicit previous choice wins, then the
 * device preference, then English. Only the two base languages are matched, so a
 * device set to `hi-IN` or `mr-IN` variants still resolves to the right dictionary.
 */
const detectInitialLanguage = (): LanguageCode => {
  if (typeof window === 'undefined') return 'en'
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY)
    if (isLanguageCode(stored)) return stored
  } catch {
    // Private browsing or a blocked storage partition; fall through to detection.
  }
  for (const candidate of navigator.languages ?? [navigator.language]) {
    if (!candidate) continue
    const base = candidate.toLowerCase().split('-')[0]
    if (isLanguageCode(base)) return base
  }
  return 'en'
}

const translate = (
  language: LanguageCode,
  key: TranslationKey,
  values?: InterpolationValues,
): string => {
  const template = dictionaries[language][key] ?? en[key]
  if (!values) return template
  return template.replace(/\{(\w+)\}/g, (match, name: string) =>
    name in values ? String(values[name]) : match,
  )
}

/**
 * Pure lookup against one dictionary, bypassing React.
 *
 * Exported so non-component callers (and tests) interpolate exactly the way the
 * provider does, rather than re-implementing placeholder substitution.
 */
export const translateIn = (
  dictionary: Record<TranslationKey, string>,
  key: TranslationKey,
  values?: InterpolationValues,
): string => {
  const template = dictionary[key] ?? en[key]
  if (!values) return template
  return template.replace(/\{(\w+)\}/g, (match, name: string) =>
    name in values ? String(values[name]) : match,
  )
}

export type I18n = {
  language: LanguageCode
  locale: string
  setLanguage: (language: LanguageCode) => void
  t: (key: TranslationKey, values?: InterpolationValues) => string
  /** Formats a number using Indian digit grouping, always with Latin digits. */
  formatNumber: (value: number, options?: Intl.NumberFormatOptions) => string
  /** Formats an amount in Indian rupees without fractional paise. */
  formatCurrency: (value: number) => string
  /** Formats an ISO or `YYYY-MM-DD` string as a short date in the active locale. */
  formatDate: (value: string, options?: Intl.DateTimeFormatOptions) => string
  /** Renders a zero-based month index as a short month name in the active locale. */
  formatMonth: (monthIndex: number, options?: Intl.DateTimeFormatOptions) => string
}

const I18nContext = createContext<I18n>({
  language: 'en',
  locale: 'en-IN',
  setLanguage: () => undefined,
  t: (key) => en[key],
  formatNumber: (value) => String(value),
  formatCurrency: (value) => `₹${value}`,
  formatDate: (value) => value,
  formatMonth: (monthIndex) => String(monthIndex + 1),
})

export function I18nProvider({ children }: { children: ReactNode }) {
  const [language, setLanguageState] = useState<LanguageCode>(detectInitialLanguage)

  const option = useMemo(
    () => languages.find((item) => item.code === language) ?? languages[0],
    [language],
  )

  const setLanguage = useCallback((next: LanguageCode) => {
    setLanguageState(next)
    try {
      window.localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // Persistence is best effort; the choice still applies for this session.
    }
  }, [])

  useEffect(() => {
    document.documentElement.lang = option.locale.slice(0, 2)
  }, [option])

  const value = useMemo<I18n>(() => {
    const locale = option.locale
    return {
      language,
      locale,
      setLanguage,
      t: (key, values) => translate(language, key, values),
      formatNumber: (input, options) =>
        new Intl.NumberFormat(locale, { numberingSystem: 'latn', ...options }).format(input),
      formatCurrency: (input) =>
        new Intl.NumberFormat(locale, {
          style: 'currency',
          currency: 'INR',
          maximumFractionDigits: 0,
          numberingSystem: 'latn',
        }).format(input),
      formatDate: (input, options) => {
        // `new Date('2026-09-24')` is parsed as UTC midnight and can shift a day in
        // negative-offset zones, so plain calendar dates are read component-wise.
        const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(input)
        const date = match
          ? new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
          : new Date(input)
        if (Number.isNaN(date.getTime())) return input
        return new Intl.DateTimeFormat(locale, {
          day: 'numeric',
          month: 'short',
          year: 'numeric',
          numberingSystem: 'latn',
          ...options,
        }).format(date)
      },
      formatMonth: (monthIndex, options) =>
        new Intl.DateTimeFormat(locale, {
          month: 'short',
          numberingSystem: 'latn',
          ...options,
        }).format(new Date(2026, monthIndex, 1)),
    }
  }, [language, option, setLanguage])

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>
}

export const useI18n = () => useContext(I18nContext)
