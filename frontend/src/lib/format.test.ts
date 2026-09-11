import { describe, expect, it } from 'vitest'
import { bedLabels, formatDate, formatInstantDate, formatSize, monthName, ordinal, rupees, toPaise } from './format'

describe('rupees', () => {
  it('groups digits the Indian way', () => {
    expect(rupees(18_400_000)).toBe('₹1,84,000')
  })

  it('shows paise only when there are some', () => {
    expect(rupees(1_250_000)).toBe('₹12,500')
    expect(rupees(1_250_050)).toBe('₹12,500.50')
  })
})

describe('toPaise', () => {
  it('reads amounts as typed', () => {
    expect(toPaise('12,500')).toBe(1_250_000)
    expect(toPaise('₹ 12500.5')).toBe(1_250_050)
  })

  it('refuses things that are not amounts', () => {
    expect(toPaise('twelve')).toBeNull()
    expect(toPaise('12.345')).toBeNull()
    expect(toPaise('')).toBeNull()
  })
})

describe('dates', () => {
  it('reads an ISO day without shifting it across timezones', () => {
    expect(formatDate('2026-10-05')).toBe('5 Oct 2026')
    expect(monthName('2026-10-05')).toBe('October')
  })

  it("dates a moment by India's calendar, wherever the reader is", () => {
    // 20:00 UTC on 4 October is already 1:30 am on 5 October in India.
    expect(formatInstantDate('2026-10-04T20:00:00Z')).toBe('5 Oct 2026')
  })
})

describe('formatSize', () => {
  it('says kilobytes under a megabyte and trims a round megabyte', () => {
    expect(formatSize(2048)).toBe('2 KB')
    expect(formatSize(200)).toBe('1 KB')
    expect(formatSize(1_468_006)).toBe('1.4 MB')
    expect(formatSize(10 * 1024 * 1024)).toBe('10 MB')
  })
})

describe('ordinal', () => {
  it('handles the teens', () => {
    expect([1, 2, 3, 4, 11, 12, 13, 21, 22, 28].map(ordinal)).toEqual([
      '1st', '2nd', '3rd', '4th', '11th', '12th', '13th', '21st', '22nd', '28th',
    ])
  })
})

describe('bedLabels', () => {
  it('letters beds from A', () => {
    expect(bedLabels(3)).toEqual(['Bed A', 'Bed B', 'Bed C'])
    expect(bedLabels(0)).toEqual([])
  })
})
