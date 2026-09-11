import { useQuery } from '@tanstack/react-query'
import { Navigate } from 'react-router'
import { SessionLoading } from '../app/guards'
import { boardQuery } from './queries'

/** Opens the book at the first property, or at "add a property" when the book is empty. */
export function BookIndex() {
  const board = useQuery(boardQuery)
  if (board.isPending) return <SessionLoading />
  if (board.isError) return <p role="alert">Couldn't load your properties. Reload the page to try again.</p>
  const first = board.data.properties[0]
  return <Navigate to={first ? `/l/p/${first.id}` : '/l/properties/new'} replace />
}
