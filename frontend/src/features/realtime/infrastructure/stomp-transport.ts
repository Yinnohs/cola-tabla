import { Client } from '@stomp/stompjs'

/** Callbacks del transporte hacia el adapter. */
export interface StompTransportHandlers {
  /** CONNECT aceptado por el servidor. */
  onConnect(): void
  /** El socket se cerro (por nosotros, por el servidor o por la red). */
  onClose(): void
  /** Frame STOMP ERROR (`message` es la cabecera `message`, p. ej. "Access denied"). */
  onStompError(message: string): void
}

/** Superficie minima de STOMP que usa el adapter; permite probarlo sin red ni `@stomp/stompjs`. */
export interface StompTransport {
  activate(): void
  deactivate(): void
  /** Devuelve la baja. Solo valido mientras la conexion este abierta. */
  subscribe(destination: string, onMessage: (body: string) => void): () => void
}

export type StompTransportFactory = (handlers: StompTransportHandlers) => StompTransport

export interface StompTransportOptions {
  /** `ws://host/ws` o `wss://host/ws` (WebSocket nativo, sin SockJS). */
  brokerURL: string
  /** JWT vigente o null; se lee en cada CONNECT. Va en la cabecera STOMP, no en el upgrade HTTP. */
  getToken: () => string | null
}

/**
 * Transporte real sobre `@stomp/stompjs`. Sin reconexion automatica (`reconnectDelay: 0`): backoff y
 * renovacion del token en el socket llegan en la Fase 4 (ADR-0006). `stompjs` crea el WebSocket por
 * `brokerURL`, por eso no se usa `new WebSocket` (regla ESLint).
 */
export function createStompTransport(options: StompTransportOptions): StompTransportFactory {
  return (handlers) => {
    const client = new Client({
      brokerURL: options.brokerURL,
      reconnectDelay: 0,
      beforeConnect: (c) => {
        const token = options.getToken()
        c.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {}
      },
      onConnect: () => handlers.onConnect(),
      onStompError: (frame) => handlers.onStompError(frame.headers['message'] ?? frame.body ?? ''),
      onWebSocketClose: () => handlers.onClose(),
    })
    return {
      activate: () => client.activate(),
      deactivate: () => {
        void client.deactivate()
      },
      subscribe: (destination, onMessage) => {
        const sub = client.subscribe(destination, (message) => onMessage(message.body))
        return () => {
          try {
            sub.unsubscribe()
          } catch {
            // conexion ya cerrada: nada que dar de baja
          }
        }
      },
    }
  }
}
