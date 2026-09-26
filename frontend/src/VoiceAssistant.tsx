import { useEffect, useMemo, useRef, useState } from 'react'
import { Keyboard, Mic, Send, Volume2, X } from 'lucide-react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useI18n, type LanguageCode } from './i18n'

type RecognitionEvent = { results: ArrayLike<{ 0: { transcript: string } }> }
type RecognitionErrorEvent = { error: string }
type Recognition = {
  lang: string
  continuous: boolean
  interimResults: boolean
  start: () => void
  stop: () => void
  onresult: ((event: RecognitionEvent) => void) | null
  onerror: ((event: RecognitionErrorEvent) => void) | null
  onend: (() => void) | null
}

type RecognitionConstructor = new () => Recognition
type VoiceWindow = Window & typeof globalThis & {
  SpeechRecognition?: RecognitionConstructor
  webkitSpeechRecognition?: RecognitionConstructor
}

type IntentResponse = {
  intent?: string
  navigationTarget?: string
  isAuthorized?: boolean
  authorizationReason?: string
  spokenFeedback?: Partial<Record<LanguageCode, string>>
}

const languageTags: Record<LanguageCode, string> = { en: 'en-IN', hi: 'hi-IN', mr: 'mr-IN' }

const copy: Record<LanguageCode, Record<string, string>> = {
  en: {
    title: 'AI voice assistant', prompt: 'What do you want to do?', listen: 'Speak to continue',
    listening: 'Listening…', processing: 'Understanding your request…', type: 'Type a voice command',
    placeholder: 'Example: Open collector login', unsupported: 'Voice recognition is unavailable. Type your command instead.',
    micDenied: 'Microphone access was denied. Allow microphone permission and try again.',
    transcribeFailed: 'I could not understand the recording. Please try again or type your command.',
    ready: 'Ask me to open a portal, check prices, create a lot, or explain safe handling.',
  },
  hi: {
    title: 'AI आवाज़ सहायक', prompt: 'आप क्या करना चाहते हैं?', listen: 'बोलकर आगे बढ़ें',
    listening: 'सुन रहा हूँ…', processing: 'आपका अनुरोध समझ रहा हूँ…', type: 'वॉइस कमांड लिखें',
    placeholder: 'उदाहरण: कलेक्टर लॉगिन खोलो', unsupported: 'वॉइस पहचान उपलब्ध नहीं है। कृपया कमांड लिखें।',
    micDenied: 'माइक्रोफ़ोन अनुमति नहीं मिली। अनुमति देकर फिर से प्रयास करें।',
    transcribeFailed: 'रिकॉर्डिंग समझ में नहीं आई। फिर से प्रयास करें या कमांड लिखें।',
    ready: 'पोर्टल खोलने, कीमत देखने, लॉट बनाने या सुरक्षित संचालन के बारे में पूछें।',
  },
  mr: {
    title: 'AI आवाज सहाय्यक', prompt: 'तुम्हाला काय करायचे आहे?', listen: 'बोलून पुढे जा',
    listening: 'ऐकत आहे…', processing: 'तुमची विनंती समजून घेत आहे…', type: 'व्हॉइस कमांड लिहा',
    placeholder: 'उदाहरण: कलेक्टर लॉगिन उघडा', unsupported: 'आवाज ओळख उपलब्ध नाही. कृपया कमांड लिहा.',
    micDenied: 'मायक्रोफोनची परवानगी मिळाली नाही. परवानगी देऊन पुन्हा प्रयत्न करा.',
    transcribeFailed: 'रेकॉर्डिंग समजले नाही. पुन्हा प्रयत्न करा किंवा कमांड लिहा.',
    ready: 'पोर्टल उघडणे, किंमत पाहणे, लॉट तयार करणे किंवा सुरक्षित हाताळणीबद्दल विचारा.',
  },
}

export function speakText(text: string, language: LanguageCode) {
  if (!('speechSynthesis' in window) || !text) return
  window.speechSynthesis.cancel()
  const utterance = new SpeechSynthesisUtterance(text)
  utterance.lang = languageTags[language]
  utterance.rate = 0.92
  const voices = window.speechSynthesis.getVoices()
  utterance.voice = voices.find((voice) => voice.lang.toLowerCase().startsWith(language)) ?? null
  window.speechSynthesis.speak(utterance)
}

function localIntent(transcript: string, language: LanguageCode): IntentResponse {
  const text = transcript.toLowerCase()
  const role = text.match(/recycl|factory|facility|रीसायक|पुनर्प्रक्रिया/) ? 'recycler'
    : text.match(/admin|government|officer|cpcb|सरकार|प्रशासन|शासन/) ? 'admin'
      : text.match(/collector|scrap|kabadi|कलेक्टर|संकलन|कबाड़ी/) ? 'collector' : null
  if (role) {
    const label = role === 'collector' ? 'collector' : role === 'recycler' ? 'recycler' : 'government administrator'
    return { navigationTarget: `/login?role=${role}`, isAuthorized: true, spokenFeedback: { [language]: `Opening ${label} login.` } }
  }
  if (text.match(/new lot|create lot|नया लॉट|लॉट बन|नवीन लॉट|लॉट तयार/)) {
    return { navigationTarget: '/collector/new-lot', isAuthorized: true, spokenFeedback: { [language]: copy[language].ready } }
  }
  if (text.match(/home|dashboard|मुख्य|होम/)) {
    return { navigationTarget: '/', isAuthorized: true, spokenFeedback: { [language]: copy[language].ready } }
  }
  return { isAuthorized: true, spokenFeedback: { [language]: copy[language].ready } }
}

function mapBackendRoute(target?: string) {
  if (!target) return undefined
  if (target.includes('/auth/collector')) return '/login?role=collector'
  if (target.includes('/auth/recycler')) return '/login?role=recycler'
  if (target.includes('/auth/government')) return '/login?role=admin'
  if (target.includes('/auth/selection')) return '/select-role'
  if (target.includes('/collector/dashboard')) return '/collector'
  if (target.includes('/recycler/portal')) return '/recycler'
  if (target.includes('/government/oversight')) return '/admin'
  return target
}

export function VoiceAssistant() {
  const { language } = useI18n()
  const navigate = useNavigate()
  const location = useLocation()
  const recognition = useRef<Recognition | null>(null)
  const recorder = useRef<MediaRecorder | null>(null)
  const microphoneStream = useRef<MediaStream | null>(null)
  const recordingChunks = useRef<Blob[]>([])
  const recordingTimer = useRef<number | null>(null)
  const [open, setOpen] = useState(location.pathname === '/')
  const [listening, setListening] = useState(false)
  const [processing, setProcessing] = useState(false)
  const [typing, setTyping] = useState(false)
  const [input, setInput] = useState('')
  const [transcript, setTranscript] = useState('')
  const [message, setMessage] = useState(copy[language].ready)
  const text = copy[language]
  const apiUrl = useMemo(() => (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/+$/, ''), [])
  const voiceApiUrl = useMemo(() => (import.meta.env.VITE_VOICE_API_URL as string | undefined)?.replace(/\/+$/, ''), [])

  useEffect(() => { setMessage(copy[language].ready) }, [language])
  useEffect(() => {
    const start = () => { setOpen(true); window.setTimeout(() => startListening(), 0) }
    window.addEventListener('ecobridge:start-voice', start)
    return () => window.removeEventListener('ecobridge:start-voice', start)
  })
  useEffect(() => () => {
    recognition.current?.stop()
    if (recorder.current?.state === 'recording') recorder.current.stop()
    microphoneStream.current?.getTracks().forEach((track) => track.stop())
    if (recordingTimer.current) window.clearTimeout(recordingTimer.current)
  }, [])

  async function processCommand(command: string) {
    const clean = command.trim()
    if (!clean) return
    setTranscript(clean)
    setProcessing(true)
    let result: IntentResponse
    try {
      if (!apiUrl) throw new Error('NLP API not configured')
      const currentRole = location.pathname.startsWith('/collector') ? 'INFORMAL_COLLECTOR'
        : location.pathname.startsWith('/recycler') ? 'FORMAL_RECYCLER'
          : location.pathname.startsWith('/admin') ? 'GOVERNMENT_ADMIN' : undefined
      const response = await fetch(`${apiUrl}/voice/intent`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ transcript: clean, context: { currentScreen: location.pathname, currentRole, isAuthenticated: Boolean(currentRole), activeLanguage: language }, userRole: currentRole }),
      })
      if (!response.ok) throw new Error(`NLP request failed: ${response.status}`)
      result = await response.json() as IntentResponse
    } catch {
      result = localIntent(clean, language)
    }
    const feedback = result.isAuthorized === false
      ? result.authorizationReason || text.ready
      : result.spokenFeedback?.[language] || result.spokenFeedback?.en || text.ready
    setMessage(feedback)
    speakText(feedback, language)
    const target = result.isAuthorized === false ? undefined : mapBackendRoute(result.navigationTarget)
    if (target) window.setTimeout(() => navigate(target), 650)
    setProcessing(false)
    setTyping(false)
    setInput('')
  }

  async function startMediaRecording() {
    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined' || !voiceApiUrl) {
      setTyping(true); setMessage(text.unsupported); speakText(text.unsupported, language); return
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      microphoneStream.current = stream
      const preferredType = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4'].find((type) => MediaRecorder.isTypeSupported(type))
      const instance = preferredType ? new MediaRecorder(stream, { mimeType: preferredType }) : new MediaRecorder(stream)
      recordingChunks.current = []
      instance.ondataavailable = (event) => { if (event.data.size) recordingChunks.current.push(event.data) }
      instance.onstop = async () => {
        if (recordingTimer.current) window.clearTimeout(recordingTimer.current)
        stream.getTracks().forEach((track) => track.stop())
        microphoneStream.current = null
        setListening(false)
        const blob = new Blob(recordingChunks.current, { type: instance.mimeType || 'audio/webm' })
        if (!blob.size) { setTyping(true); setMessage(text.transcribeFailed); return }
        setProcessing(true)
        try {
          const form = new FormData()
          form.append('file', blob, instance.mimeType.includes('mp4') ? 'voice.m4a' : 'voice.webm')
          form.append('language', language)
          const response = await fetch(`${voiceApiUrl}/api/v1/voice/transcribe`, { method: 'POST', body: form })
          if (!response.ok) throw new Error(`Transcription failed: ${response.status}`)
          const result = await response.json() as { text?: string }
          if (!result.text?.trim()) throw new Error('Empty transcription')
          await processCommand(result.text)
        } catch {
          setProcessing(false); setTyping(true); setMessage(text.transcribeFailed); speakText(text.transcribeFailed, language)
        }
      }
      recorder.current = instance
      setListening(true)
      setMessage(text.listening)
      instance.start()
      recordingTimer.current = window.setTimeout(() => { if (instance.state === 'recording') instance.stop() }, 7000)
    } catch {
      setListening(false); setTyping(true); setMessage(text.micDenied); speakText(text.micDenied, language)
    }
  }

  async function startListening() {
    const voiceWindow = window as VoiceWindow
    const Constructor = voiceWindow.SpeechRecognition ?? voiceWindow.webkitSpeechRecognition
    if (!Constructor) { await startMediaRecording(); return }
    window.speechSynthesis.cancel()
    recognition.current?.stop()
    const instance = new Constructor()
    instance.lang = languageTags[language]
    instance.continuous = false
    instance.interimResults = false
    instance.onresult = (event) => void processCommand(event.results[0][0].transcript)
    instance.onerror = (event) => {
      setListening(false)
      if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
        setTyping(true); setMessage(text.micDenied); speakText(text.micDenied, language)
      } else {
        void startMediaRecording()
      }
    }
    instance.onend = () => setListening(false)
    recognition.current = instance
    setListening(true)
    setMessage(text.listening)
    instance.start()
  }

  function stopListening() {
    recognition.current?.stop()
    if (recorder.current?.state === 'recording') recorder.current.stop()
    setListening(false)
  }

  return <aside className={`voice-assistant ${open ? 'open' : ''}`} aria-label={text.title}>
    {!open && <button className="voice-fab" onClick={() => setOpen(true)} aria-label={text.title}><Mic /></button>}
    {open && <div className="voice-panel">
      <button className="voice-close" onClick={() => setOpen(false)} aria-label="Close voice assistant"><X /></button>
      <span className="voice-kicker"><Volume2 /> {text.title}</span>
      <button className={`voice-main-mic ${listening ? 'listening' : ''}`} onClick={listening ? stopListening : () => void startListening()} aria-label={listening ? 'Stop listening' : text.listen}><Mic /></button>
      <h3>{listening ? text.listening : processing ? text.processing : text.listen}</h3>
      <p>{transcript ? `“${transcript}”` : text.prompt}</p>
      <div className="voice-message" aria-live="polite">{message}</div>
      {typing ? <form className="voice-type-form" onSubmit={(event) => { event.preventDefault(); void processCommand(input) }}>
        <input value={input} onChange={(event) => setInput(event.target.value)} placeholder={text.placeholder} autoFocus />
        <button type="submit" aria-label="Send command"><Send /></button>
      </form> : <button className="voice-type-toggle" onClick={() => setTyping(true)}><Keyboard /> {text.type}</button>}
    </div>}
  </aside>
}
