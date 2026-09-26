import { useEffect, useRef, useState } from 'react'
import { Check, ChevronDown, Globe2 } from 'lucide-react'
import { languages, useI18n, type LanguageCode } from './index'

type LanguageSwitcherProps = {
  /** `ghost` for the marketing header, `solid` for the signed-in app header. */
  variant?: 'ghost' | 'solid'
  id?: string
}

export function LanguageSwitcher({ variant = 'ghost', id }: LanguageSwitcherProps) {
  const { language, setLanguage, t } = useI18n()
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const current = languages.find((item) => item.code === language) ?? languages[0]

  useEffect(() => {
    if (!open) return
    const onPointerDown = (event: MouseEvent | TouchEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) setOpen(false)
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onPointerDown)
    document.addEventListener('touchstart', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('mousedown', onPointerDown)
      document.removeEventListener('touchstart', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  const choose = (code: LanguageCode) => {
    setLanguage(code)
    setOpen(false)
  }

  return (
    <div className={`language-switch ${variant}`} ref={containerRef}>
      <button
        type="button"
        id={id}
        className="language-trigger"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-label={t('common.changeLanguage')}
        title={t('common.changeLanguage')}
        onClick={() => setOpen((value) => !value)}
      >
        <Globe2 size={16} aria-hidden="true" />
        <span>{current.native}</span>
        <ChevronDown className={open ? 'chevron open' : 'chevron'} size={14} aria-hidden="true" />
      </button>
      {open && (
        <ul className="language-menu" role="listbox" aria-labelledby={id} aria-label={t('common.language')}>
          {languages.map((option) => (
            <li key={option.code} role="none">
              <button
                type="button"
                role="option"
                aria-selected={option.code === language}
                lang={option.locale}
                className={option.code === language ? 'language-option active' : 'language-option'}
                onClick={() => choose(option.code)}
              >
                <span className="language-native">{option.native}</span>
                <span className="language-label">{option.label}</span>
                {option.code === language && <Check size={15} aria-hidden="true" />}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
