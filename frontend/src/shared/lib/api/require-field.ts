/** Los esquemas generados marcan los campos de respuesta como opcionales: los mappers los exigen aqui. */
export function requireField<T>(value: T | null | undefined, field: string): T {
  if (value === undefined || value === null) {
    throw new Error(`Respuesta de la API malformada: falta '${field}'`)
  }
  return value
}
