const versionPattern = /^[a-f0-9]{40}$/

/** Read public release metadata only; detecting an update never navigates or submits a form. */
export function createVersionProbe(current: string, changed: (version: string) => void, read: typeof fetch = fetch) {
  let disposed = false
  let pending: Promise<void> | undefined
  let controller: AbortController | undefined
  let reported: string | undefined
  function check(): Promise<void> {
    if (disposed || !versionPattern.test(current)) return Promise.resolve()
    if (pending) return pending
    const own = new AbortController()
    controller = own
    const timeout = setTimeout(() => own.abort(), 5000)
    pending = (async () => {
      try {
        const response = await read('/frontend-version.json', {cache: 'no-store', credentials: 'omit', redirect: 'error', signal: own.signal, headers: {Accept: 'application/json'}})
        if (!response.ok || !response.headers.get('content-type')?.includes('application/json')) return
        const data: unknown = await response.json()
        if (disposed || own.signal.aborted || !data || typeof data !== 'object' || !('version' in data) || typeof data.version !== 'string' || !versionPattern.test(data.version)) return
        const next = data.version === current ? '' : data.version
        if (next !== reported) { reported = next; changed(next) }
      } catch { /* Offline/older servers are not evidence of a release change. */ }
      finally { clearTimeout(timeout); pending = undefined; if (controller === own) controller = undefined }
    })()
    return pending
  }
  return {check, stop() { disposed = true; controller?.abort() }}
}
