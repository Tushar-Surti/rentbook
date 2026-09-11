import type { ComponentPropsWithRef } from 'react'
import { Link, type LinkProps } from 'react-router'
import styles from './Button.module.css'

type Variant = 'primary' | 'secondary' | 'quiet'

function classes(variant: Variant, block: boolean, extra?: string) {
  return [styles.button, styles[variant], block ? styles.block : '', extra ?? ''].filter(Boolean).join(' ')
}

type ButtonProps = ComponentPropsWithRef<'button'> & { variant?: Variant; busy?: boolean; block?: boolean }

export function Button({ variant = 'primary', busy = false, block = false, className, disabled, type, ...rest }: ButtonProps) {
  return (
    <button
      {...rest}
      type={type ?? 'button'}
      className={classes(variant, block, className)}
      disabled={disabled || busy}
      aria-busy={busy || undefined}
    />
  )
}

type LinkButtonProps = LinkProps & { variant?: Variant; block?: boolean }

export function LinkButton({ variant = 'secondary', block = false, className, ...rest }: LinkButtonProps) {
  return <Link {...rest} className={classes(variant, block, className)} />
}
