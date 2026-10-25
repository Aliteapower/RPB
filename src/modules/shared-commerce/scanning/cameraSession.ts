export type CameraIssue = 'insecure' | 'unsupported' | 'denied' | 'missing' | 'busy' | 'playback' | 'interrupted' | 'decode' | 'wrongCode' | 'torch' | null
export interface CameraState {
  phase: 'idle' | 'opening' | 'scanning' | 'paused' | 'recognized' | 'error'
  issue: CameraIssue
  facing: 'environment' | 'user'
  torchAvailable: boolean
  torch: boolean
}
interface CameraOptions {
  getVideo: () => HTMLVideoElement | null
  mediaDevices?: Pick<MediaDevices, 'getUserMedia'>
  secure: boolean
  requestFrame: (callback: FrameRequestCallback) => number
  cancelFrame: (handle: number) => void
  decode: (video: HTMLVideoElement, fullFrame: boolean) => string | null
  accept: (value: string) => boolean
  onCode: (value: string) => void
  onChange: (state: CameraState) => void
}

/** Owns one camera lease. Late permissions/playback can only dispose their own lease. */
export function createCameraSession(options: CameraOptions) {
  let state: CameraState = { phase: 'idle', issue: null, facing: 'environment', torchAvailable: false, torch: false }
  let generation = 0, stream: MediaStream | null = null, attachedVideo: HTMLVideoElement | null = null
  let frame: number | null = null, ended: (() => void) | null = null, disposed = false, torchBusy = false
  function publish(change: Partial<CameraState>) { state = { ...state, ...change }; options.onChange({ ...state }) }
  function release() {
    if (frame !== null) options.cancelFrame(frame)
    frame = null
    if (stream) {
      const current = stream
      stream = null
      for (const track of current.getTracks()) {
        if (ended) track.removeEventListener('ended', ended)
        track.stop()
      }
      if (attachedVideo?.srcObject === current) { attachedVideo.pause(); attachedVideo.srcObject = null }
    }
    attachedVideo = null; ended = null; torchBusy = false
  }
  function stop(phase: CameraState['phase'] = 'idle', issue: CameraIssue = null) {
    generation++; release(); publish({ phase, issue, torchAvailable: false, torch: false })
  }
  function cameraIssue(error: unknown): CameraIssue {
    const name = (error as { name?: string })?.name
    if (name === 'NotAllowedError' || name === 'SecurityError') return 'denied'
    if (name === 'NotFoundError' || name === 'OverconstrainedError') return 'missing'
    if (name === 'NotReadableError' || name === 'AbortError') return 'busy'
    return 'unsupported'
  }
  async function start(facing = state.facing) {
    if (disposed || state.phase === 'opening') return
    stop(); const own = generation
    publish({ phase: 'opening', facing })
    if (!options.secure) { stop('error', 'insecure'); return }
    if (!options.mediaDevices?.getUserMedia) { stop('error', 'unsupported'); return }
    let playing = false
    try {
      let candidate: MediaStream
      try {
        candidate = await options.mediaDevices.getUserMedia({ audio: false, video: {
          facingMode: { ideal: facing }, width: { ideal: 1280 }, height: { ideal: 720 }, frameRate: { ideal: 15, max: 24 }
        } })
      } catch (e) {
        if (own !== generation || disposed) return
        if ((e as { name?: string })?.name !== 'OverconstrainedError') throw e
        candidate = await options.mediaDevices.getUserMedia({ audio: false, video: { facingMode: { ideal: facing } } })
      }
      if (own !== generation || disposed) { candidate.getTracks().forEach(t => t.stop()); return }
      const video = options.getVideo()
      if (!video) { candidate.getTracks().forEach(t => t.stop()); stop('error', 'playback'); return }
      stream = candidate; attachedVideo = video
      ended = () => { if (own === generation) stop('error', 'interrupted') }
      candidate.getTracks().forEach(t => t.addEventListener('ended', ended!))
      video.muted = true; video.playsInline = true; video.srcObject = candidate
      playing = true; await video.play()
      if (own !== generation || disposed) return
      const track = candidate.getVideoTracks()[0]
      let torchAvailable = false
      try { torchAvailable = (track?.getCapabilities?.() as MediaTrackCapabilities & { torch?: boolean })?.torch === true } catch { /* Optional on iOS. */ }
      const actualFacing = track?.getSettings?.().facingMode
      publish({ phase: 'scanning', issue: null, torchAvailable, facing: actualFacing === 'user' || actualFacing === 'environment' ? actualFacing : facing })
      let lastRead = -Infinity, reads = 0
      const tick: FrameRequestCallback = time => {
        if (own !== generation || state.phase !== 'scanning') return
        if (time - lastRead >= 180 && video.readyState >= 2) {
          lastRead = time
          try {
            const value = options.decode(video, ++reads % 3 === 0)
            if (value) {
              if (options.accept(value)) { stop('recognized'); options.onCode(value); return }
              if (state.issue !== 'wrongCode') publish({ issue: 'wrongCode' })
            }
          } catch { stop('error', 'decode'); return }
        }
        frame = options.requestFrame(tick)
      }
      frame = options.requestFrame(tick)
    } catch (e) {
      if (own === generation && !disposed) stop('error', playing ? 'playback' : cameraIssue(e))
    }
  }
  async function switchFacing() {
    const facing = state.facing === 'environment' ? 'user' : 'environment'
    stop(); await start(facing)
  }
  async function toggleTorch() {
    if (!state.torchAvailable || torchBusy || !stream || state.phase !== 'scanning') return
    const own = generation, enabled = !state.torch, track = stream.getVideoTracks()[0]
    if (!track) return
    torchBusy = true
    try {
      const constraint: MediaTrackConstraintSet & { torch: boolean } = { torch: enabled }
      await track.applyConstraints({ advanced: [constraint] })
      if (own === generation) publish({ torch: enabled, issue: null })
    } catch { if (own === generation) publish({ issue: 'torch' }) }
    finally { if (own === generation) torchBusy = false }
  }
  return {
    start, stop: () => stop(), switchFacing, toggleTorch,
    pause: () => { if (state.phase === 'opening' || state.phase === 'scanning') stop('paused') },
    dispose: () => { disposed = true; stop() }
  }
}
