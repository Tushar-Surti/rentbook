import { isRouteErrorResponse, Link, useRouteError } from 'react-router'
import { Brand } from '../design/Brand'
import styles from './errors.module.css'

function Page({ heading, body }: { heading: string; body: string }) {
  return (
    <main className={styles.page}>
      <Link to="/" className={styles.home} aria-label="Rentbook home">
        <Brand />
      </Link>
      <h1 className={styles.heading}>{heading}</h1>
      <p className={styles.body}>{body}</p>
      <Link to="/">Go to your rent book</Link>
    </main>
  )
}

export function NotFound() {
  return <Page heading="This page isn't in the book" body="The link may be old or mistyped." />
}

export function RouteError() {
  const error = useRouteError()
  if (isRouteErrorResponse(error) && error.status === 404) {
    return <NotFound />
  }
  return <Page heading="Something went wrong" body="Reload the page. If it keeps happening, sign out and back in." />
}
