// Frontend Backend Origin 결정
export function resolveBackendBaseUrl(
  configuredBaseUrl: string | undefined,
  fallbackBaseUrl = '',
): string {
  return (configuredBaseUrl ?? fallbackBaseUrl).replace(/\/$/, '')
}
