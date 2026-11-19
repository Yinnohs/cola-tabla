/**
 * Estado de la conexion en tiempo real de un tablero.
 * - connecting: abriendo el socket / suscribiendo.
 * - connected: suscrito.
 * - disconnected: socket cerrado (sin reconexion automatica hasta la Fase 4).
 * - access-denied: el servidor rechazo la suscripcion (frame ERROR "Access denied"): no se reintenta.
 * - error: otro frame ERROR o fallo del transporte.
 */
export type ConnectionStatus =
  'connecting' | 'connected' | 'disconnected' | 'access-denied' | 'error'
