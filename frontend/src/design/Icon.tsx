import type { ReactNode } from 'react'

// Authored on a 20px grid with one 1.5px stroke, so every icon shares a hand.
const shapes = {
  plus: <path d="M10 4.5v11M4.5 10h11" />,
  copy: (
    <>
      <rect x="7" y="7" width="9" height="10" rx="1" />
      <path d="M4 13.5V4h9" />
    </>
  ),
  phone: (
    <path d="M6.5 3.5H4.75a1 1 0 0 0-1 1.06C4.3 11 9 15.7 15.44 16.25a1 1 0 0 0 1.06-1V13.5l-3.1-1.3-1.55 1.55a8.1 8.1 0 0 1-4.6-4.6L8.8 7.6 7.5 4.5z" />
  ),
  mail: (
    <>
      <rect x="3" y="5" width="14" height="10.5" rx="1" />
      <path d="m3.5 5.5 6.5 5 6.5-5" />
    </>
  ),
  door: (
    <>
      <path d="M5.5 16.5V3.5h8v13" />
      <path d="M3 16.5h14" />
      <circle cx="11" cy="10.25" r="0.9" fill="currentColor" stroke="none" />
    </>
  ),
  check: <path d="m4.5 10.5 3.5 3.5 7.5-8" />,
} satisfies Record<string, ReactNode>

export type IconName = keyof typeof shapes

/** Decorative unless given a label. */
export function Icon({ name, label, size = 20 }: { name: IconName; label?: string; size?: number }) {
  return (
    <svg
      viewBox="0 0 20 20"
      width={size}
      height={size}
      fill="none"
      stroke="currentColor"
      strokeWidth={1.5}
      strokeLinecap="round"
      strokeLinejoin="round"
      role={label ? 'img' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
      focusable="false"
    >
      {shapes[name]}
    </svg>
  )
}
