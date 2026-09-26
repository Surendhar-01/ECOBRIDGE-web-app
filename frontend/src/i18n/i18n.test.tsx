import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import App from '../App'
import { en } from './locales/en'
import { hi } from './locales/hi'
import { mr } from './locales/mr'

const landingLanguageButton = () => screen.getByRole('button', { name: /change language/i })

const chooseLanguage = async (nativeName: RegExp) => {
  fireEvent.click(landingLanguageButton())
  const menu = await screen.findByRole('listbox')
  fireEvent.click(within(menu).getByRole('option', { name: new RegExp(nativeName, 'i') }))
}

describe('translation resources', () => {
  const keys = Object.keys(en)

  it('ships Hindi and Marathi entries for every English key', () => {
    const missingInHindi = keys.filter((key) => !(key in hi))
    const missingInMarathi = keys.filter((key) => !(key in mr))
    expect(missingInHindi).toEqual([])
    expect(missingInMarathi).toEqual([])
  })

  it('keeps the same interpolation placeholders across languages', () => {
    const placeholders = (value: string) => (value.match(/\{\w+\}/g) ?? []).sort()
    const mismatched = keys.filter((key) => {
      const expected = placeholders(en[key as keyof typeof en])
      return (
        placeholders(hi[key as keyof typeof hi]).join() !== expected.join() ||
        placeholders(mr[key as keyof typeof mr]).join() !== expected.join()
      )
    })
    expect(mismatched).toEqual([])
  })

  it('does not leave untranslated English values in the Hindi or Marathi dictionary', () => {
    // Brand names, legal notices and typed placeholders are intentionally identical.
    const intentionallyUntranslated = new Set(['brand.name', 'landing.footer.rights', 'login.emailPlaceholder'])
    const untranslated = keys.filter(
      (key) =>
        !intentionallyUntranslated.has(key) &&
        hi[key as keyof typeof hi] === en[key as keyof typeof en] &&
        /[A-Za-z]{4,}/.test(en[key as keyof typeof en]),
    )
    expect(untranslated).toEqual([])
  })
})

describe('language switching', () => {
  beforeEach(() => localStorage.clear())
  afterEach(cleanup)

  it('renders the landing page in the device language when nothing is stored', () => {
    render(<MemoryRouter><App /></MemoryRouter>)
    expect(screen.getByRole('heading', { name: /turn e-waste into value/i })).toBeInTheDocument()
  })

  it('switches the whole interface to Hindi', async () => {
    render(<MemoryRouter><App /></MemoryRouter>)
    await chooseLanguage(/हिन्दी/)
    expect(screen.getByRole('heading', { name: /ई-कचरे को मूल्य में बदलें/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^साइन इन$/ })).toBeInTheDocument()
    expect(screen.getByText(/स्थानीय कचरा संग्राहकों/)).toBeInTheDocument()
  })

  it('switches the whole interface to Marathi', async () => {
    render(<MemoryRouter><App /></MemoryRouter>)
    await chooseLanguage(/मराठी/)
    expect(screen.getByRole('heading', { name: /ई-कचरा मूल्यात बदला/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^साइन इन$/ })).toBeInTheDocument()
  })

  it('localises guarded routes and their data in the active language', async () => {
    localStorage.setItem('eco-language', 'mr')
    render(<MemoryRouter initialEntries={['/collector']}><App /></MemoryRouter>)
    // Unauthenticated collector routes redirect to role selection, in Marathi.
    expect(await screen.findByRole('heading', { name: /तुम्हाला कसे पुढे जायचे आहे/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /कचरा संचक/ })).toBeInTheDocument()
  })

  it('persists the chosen language across mounts', async () => {
    const first = render(<MemoryRouter><App /></MemoryRouter>)
    await chooseLanguage(/मराठी/)
    expect(localStorage.getItem('eco-language')).toBe('mr')
    first.unmount()

    render(<MemoryRouter><App /></MemoryRouter>)
    expect(screen.getByRole('heading', { name: /ई-कचरा मूल्यात बदला/ })).toBeInTheDocument()
  })

  it('marks the active option in the switcher list', async () => {
    render(<MemoryRouter><App /></MemoryRouter>)
    fireEvent.click(landingLanguageButton())
    const menu = await screen.findByRole('listbox')
    expect(within(menu).getByRole('option', { name: /English/ })).toHaveAttribute('aria-selected', 'true')
    fireEvent.click(within(menu).getByRole('option', { name: /हिन्दी/ }))
    // The trigger relabels itself in the newly selected language.
    fireEvent.click(screen.getByRole('button', { name: /भाषा बदलें/ }))
    const next = await screen.findByRole('listbox')
    expect(within(next).getByRole('option', { name: /हिन्दी/ })).toHaveAttribute('aria-selected', 'true')
  })
})
