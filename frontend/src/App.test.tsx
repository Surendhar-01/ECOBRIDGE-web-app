import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import App from './App'

describe('ECO BRIDGES website', () => {
  beforeEach(() => localStorage.clear())
  afterEach(cleanup)

  it('explains the service and offers a clear start action', () => {
    render(<MemoryRouter><App /></MemoryRouter>)
    expect(screen.getByRole('heading', { name: /turn e-waste into value/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /get started/i })).toBeInTheDocument()
  })

  it('opens role selection before sign in', async () => {
    render(<MemoryRouter><App /></MemoryRouter>)
    fireEvent.click(screen.getByRole('button', { name: /^sign in$/i }))
    expect(await screen.findByRole('heading', { name: /how would you like to continue/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /waste collector/i })).toBeInTheDocument()
  })

  it('protects collector pages until a portal is selected and authenticated', async () => {
    render(<MemoryRouter initialEntries={['/collector']}><App /></MemoryRouter>)
    expect(await screen.findByRole('heading', { name: /how would you like to continue/i })).toBeInTheDocument()
  })

  it('shows a role-specific login page', () => {
    render(<MemoryRouter initialEntries={['/login?role=recycler']}><App /></MemoryRouter>)
    expect(screen.getByRole('heading', { name: /authorized recycler sign in/i })).toBeInTheDocument()
    expect(screen.getByText(/selected portal/i)).toBeInTheDocument()
  })
})
