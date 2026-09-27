const wholeRupees = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 })
const exactRupees = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 })
const dayMonthYear = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
const dayMonth = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short' })
const monthOnly = new Intl.DateTimeFormat('en-IN', { month: 'long' })

/** 1250000 paise as "₹12,500"; paise only when there are some. */
export function rupees(paise: number): string {
  return paise % 100 === 0 ? wholeRupees.format(paise / 100) : exactRupees.format(paise / 100)
}

/** "12,500" or "12500.50" as paise; null when it isn't an amount. */
export function toPaise(input: string): number | null {
  const cleaned = input.replace(/[₹,\s]/g, '')
  if (!/^\d+(\.\d{1,2})?$/.test(cleaned)) {
    return null
  }
  return Math.round(Number(cleaned) * 100)
}

/** Reads "2026-10-05" as that calendar day, without a timezone shift. */
export function parseDate(iso: string): Date {
  const [year, month, day] = iso.slice(0, 10).split('-').map(Number)
  return new Date(year, month - 1, day)
}

export function formatDate(iso: string): string {
  return dayMonthYear.format(parseDate(iso))
}

export function formatDayMonth(iso: string): string {
  return dayMonth.format(parseDate(iso))
}

export function monthName(iso: string): string {
  return monthOnly.format(parseDate(iso))
}

export function formatInstant(iso: string): string {
  return dayMonthYear.format(new Date(iso))
}

export function formatInstantShort(iso: string): string {
  return dayMonth.format(new Date(iso))
}

const indianDate = new Intl.DateTimeFormat('en-IN', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  timeZone: 'Asia/Kolkata',
})

/** A moment as the date it was in India, wherever the reader is: a receipt issued at 1 am IST keeps its day. */
export function formatInstantDate(iso: string): string {
  return indianDate.format(new Date(iso))
}

const indianMoment = new Intl.DateTimeFormat('en-IN', {
  day: 'numeric',
  month: 'short',
  hour: 'numeric',
  minute: '2-digit',
  timeZone: 'Asia/Kolkata',
})

/** 2048 bytes as "2 KB", 1468006 as "1.4 MB". */
export function formatSize(bytes: number): string {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1).replace(/\.0$/, '')} MB`
}

/** When a line was written, in India's time: "11 Sept, 2:05 pm". */
export function formatMoment(iso: string): string {
  return indianMoment.format(new Date(iso))
}

/** "Bed A", "Bed B", … for a room with this many beds. */
export function bedLabels(count: number): string[] {
  return Array.from({ length: count }, (_, index) => `Bed ${String.fromCharCode(65 + index)}`)
}

export function ordinal(day: number): string {
  const tens = day % 100
  if (tens >= 11 && tens <= 13) return `${day}th`
  return `${day}${{ 1: 'st', 2: 'nd', 3: 'rd' }[day % 10] ?? 'th'}`
}

export function todayIso(): string {
  return isoDaysFromToday(0)
}

export function isoDaysFromToday(days: number): string {
  const day = new Date()
  day.setDate(day.getDate() + days)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${day.getFullYear()}-${pad(day.getMonth() + 1)}-${pad(day.getDate())}`
}

/** "2026-09" as "September". */
export function monthLabel(yearMonth: string): string {
  return monthName(`${yearMonth}-01`)
}

/** How a payment arrived, as it reads in a sentence: "Paid in cash". */
export function methodPhrase(method: string): string {
  switch (method) {
    case 'CASH':
      return 'in cash'
    case 'UPI':
      return 'by UPI'
    case 'BANK_TRANSFER':
      return 'by bank transfer'
    case 'CHEQUE':
      return 'by cheque'
    case 'DEPOSIT':
      return 'from the deposit'
    default:
      return 'online'
  }
}
