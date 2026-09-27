import { QueryClient, QueryClientProvider, useQueryClient } from '@tanstack/react-query'
import { StrictMode, useEffect, useRef } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router/dom'
import { ApiError } from './api/client'
import { router } from './app/router'
import { AuthProvider, useAuth } from './auth/AuthProvider'
import './design/global.css'
import { LiveProvider } from './realtime/LiveProvider'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      // Client errors (not found, forbidden) will not fix themselves on retry.
      retry: (failures, error) => !(error instanceof ApiError && error.status < 500) && failures < 2,
    },
  },
})

/**
 * Nothing cached for one person may be shown to the next person who signs in on this device. Only an
 * actual sign-out clears the cache: a visitor who was never signed in keeps what public pages have loaded.
 */
function ForgetOnSignOut() {
  const { state } = useAuth()
  const client = useQueryClient()
  const wasSignedIn = useRef(false)
  useEffect(() => {
    if (state.status === 'signed-in') wasSignedIn.current = true
    if (state.status === 'signed-out' && wasSignedIn.current) {
      wasSignedIn.current = false
      client.clear()
    }
  }, [state.status, client])
  return null
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <ForgetOnSignOut />
        <LiveProvider>
          <RouterProvider router={router} />
        </LiveProvider>
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
)
