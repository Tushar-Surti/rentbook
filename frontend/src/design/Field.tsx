import {
  useId,
  type InputHTMLAttributes,
  type ReactNode,
  type Ref,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from 'react'
import styles from './Field.module.css'

type Common = { label: string; hint?: ReactNode; error?: string }

function describedBy(id: string, hint?: ReactNode, error?: string) {
  return [hint ? `${id}-hint` : '', error ? `${id}-error` : ''].filter(Boolean).join(' ') || undefined
}

function Notes({ id, hint, error }: { id: string; hint?: ReactNode; error?: string }) {
  return (
    <>
      {hint && (
        <p id={`${id}-hint`} className={styles.hint}>
          {hint}
        </p>
      )}
      {error && (
        <p id={`${id}-error`} className={styles.error}>
          {error}
        </p>
      )}
    </>
  )
}

type TextFieldProps = Common &
  InputHTMLAttributes<HTMLInputElement> & { ref?: Ref<HTMLInputElement>; prefix?: string }

/** Typed values are entries in the record, so they render in Carbon. */
export function TextField({ label, hint, error, id, prefix, ref, className, ...input }: TextFieldProps) {
  const generated = useId()
  const inputId = id ?? generated
  return (
    <div className={[styles.field, className].filter(Boolean).join(' ')}>
      <label htmlFor={inputId} className={styles.label}>
        {label}
      </label>
      <div className={styles.control} data-invalid={error ? true : undefined}>
        {prefix && (
          <span className={styles.prefix} aria-hidden="true">
            {prefix}
          </span>
        )}
        <input
          {...input}
          ref={ref}
          id={inputId}
          className={styles.input}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy(inputId, hint, error)}
        />
      </div>
      <Notes id={inputId} hint={hint} error={error} />
    </div>
  )
}

type SelectFieldProps = Common &
  SelectHTMLAttributes<HTMLSelectElement> & {
    ref?: Ref<HTMLSelectElement>
    options: { value: string; label: string }[]
  }

export function SelectField({ label, hint, error, id, options, ref, className, ...select }: SelectFieldProps) {
  const generated = useId()
  const selectId = id ?? generated
  return (
    <div className={[styles.field, className].filter(Boolean).join(' ')}>
      <label htmlFor={selectId} className={styles.label}>
        {label}
      </label>
      <div className={styles.control} data-invalid={error ? true : undefined}>
        <select
          {...select}
          ref={ref}
          id={selectId}
          className={`${styles.input} ${styles.select}`}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy(selectId, hint, error)}
        >
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>
      <Notes id={selectId} hint={hint} error={error} />
    </div>
  )
}

type TextAreaFieldProps = Common & TextareaHTMLAttributes<HTMLTextAreaElement> & { ref?: Ref<HTMLTextAreaElement> }

/** Several lines of the reader's own words, written into the record in Carbon like any other entry. */
export function TextAreaField({ label, hint, error, id, ref, className, rows = 4, ...textarea }: TextAreaFieldProps) {
  const generated = useId()
  const fieldId = id ?? generated
  return (
    <div className={[styles.field, className].filter(Boolean).join(' ')}>
      <label htmlFor={fieldId} className={styles.label}>
        {label}
      </label>
      <div className={styles.control} data-invalid={error ? true : undefined}>
        <textarea
          {...textarea}
          ref={ref}
          id={fieldId}
          rows={rows}
          className={`${styles.input} ${styles.textarea}`}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy(fieldId, hint, error)}
        />
      </div>
      <Notes id={fieldId} hint={hint} error={error} />
    </div>
  )
}

/** A form-level failure that is not tied to one field. */
export function FormError({ children }: { children?: ReactNode }) {
  if (!children) return null
  return (
    <p role="alert" className={styles.formError}>
      {children}
    </p>
  )
}
