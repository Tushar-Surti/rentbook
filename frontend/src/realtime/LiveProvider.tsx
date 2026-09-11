import { Client, type StompSubscription } from '@stomp/stompjs'
import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { freshAccessToken } from '../api/client'
import type { LiveEnvelope } from '../api/types'
import { useAuth } from '../auth/AuthProvider'

type Listener = { destination: string; handler: (event: LiveEnvelope) => void; subscription?: StompSubscription }

/** One STOMP connection per signed-in tab; listeners are re-attached after every reconnect. */
class LiveChannel {
  private readonly client: Client
  private readonly listeners = new Set<Listener>()

  constructor() {
    this.client = new Client({
      brokerURL: liveUrl(),
      reconnectDelay: 3000,
      heartbeatIncoming: 20_000,
      heartbeatOutgoing: 20_000,
      beforeConnect: async (client) => {
        const token = await freshAccessToken()
        client.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {}
      },
      onConnect: () => this.listeners.forEach((listener) => this.attach(listener)),
    })
  }

  start() {
    this.client.activate()
  }

  stop() {
    return this.client.deactivate()
  }

  listen(destination: string, handler: (event: LiveEnvelope) => void): () => void {
    const listener: Listener = { destination, handler }
    this.listeners.add(listener)
    if (this.client.connected) {
      this.attach(listener)
    }
    return () => {
      this.listeners.delete(listener)
      if (this.client.connected) {
        listener.subscription?.unsubscribe()
      }
    }
  }

  private attach(listener: Listener) {
    listener.subscription = this.client.subscribe(listener.destination, (message) =>
      listener.handler(JSON.parse(message.body) as LiveEnvelope),
    )
  }
}

function liveUrl(): string {
  const configured = import.meta.env.VITE_WS_URL as string | undefined
  if (configured) return configured
  return `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws`
}

const LiveContext = createContext<LiveChannel | null>(null)

export function LiveProvider({ children }: { children: ReactNode }) {
  const { state } = useAuth()
  const signedIn = state.status === 'signed-in'
  const [channel, setChannel] = useState<LiveChannel | null>(null)

  useEffect(() => {
    if (!signedIn) return
    const live = new LiveChannel()
    live.start()
    setChannel(live)
    return () => {
      void live.stop()
      setChannel(null)
    }
  }, [signedIn])

  return <LiveContext.Provider value={channel}>{children}</LiveContext.Provider>
}

/** Calls {@code onEvent} for every event on {@code destination} while the component is mounted. */
export function useLive<T>(destination: string | null, onEvent: (event: LiveEnvelope<T>) => void) {
  const channel = useContext(LiveContext)
  const handler = useRef(onEvent)

  useEffect(() => {
    handler.current = onEvent
  })

  useEffect(() => {
    if (!channel || !destination) return
    return channel.listen(destination, (event) => handler.current(event as LiveEnvelope<T>))
  }, [channel, destination])
}
