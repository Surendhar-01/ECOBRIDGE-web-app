"""
ECOBRIDGES Whisper Speech-to-Text Service
Configured for native Hindi (hi), Marathi (mr), English (en), and Hinglish speech recognition.
Utilizes faster-whisper with language-specific beam search, temperature fallback,
and multi-script normalization.
"""

import io
import os
import logging
import threading
from typing import Dict, Any, Tuple, Optional

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("WhisperService")

# Supported language codes
SUPPORTED_LANGUAGES = ["hi", "mr", "en"]


class SpeechToTextUnavailable(RuntimeError):
    """Raised when audio cannot be transcribed because no STT backend is loaded.

    Distinct from a generic failure so the HTTP layer can answer 503 (this
    service is not able to do this job) instead of 500 (something broke).
    """

class WhisperSpeechService:
    def __init__(self):
        self.model_size = os.getenv("WHISPER_MODEL_SIZE", "base")
        self.device = os.getenv("WHISPER_DEVICE", "cpu")
        self.compute_type = os.getenv("WHISPER_COMPUTE_TYPE", "int8")
        self.model = None
        # Speech-to-text is optional. Set WHISPER_ENABLED=false on hosts without
        # the RAM or disk for the model (e.g. a 512 MB Render free instance) to
        # serve the NLP layer only; transcribe_audio then uses the mock path.
        self.enabled = os.getenv("WHISPER_ENABLED", "true").strip().lower() not in (
            "false", "0", "no", "off"
        )
        # Placeholder transcripts are opt-in. See _generate_simulated_transcription.
        self.allow_mock = os.getenv("WHISPER_MOCK_TRANSCRIPT", "false").strip().lower() in (
            "true", "1", "yes", "on"
        )
        # Loading downloads the model, which is slow and memory hungry. Do it on
        # first use rather than at import so /health answers immediately and the
        # process is not killed by the platform before it finishes booting.
        self._load_lock = threading.Lock()
        self._load_attempted = False
        if self.enabled and os.getenv("WHISPER_EAGER_LOAD", "false").strip().lower() in (
            "true", "1", "yes", "on"
        ):
            self._ensure_model()

    def _ensure_model(self):
        """Load the model once, on first use. Returns the model or None."""
        if self.model is not None or self._load_attempted or not self.enabled:
            return self.model
        with self._load_lock:
            if self.model is not None or self._load_attempted:
                return self.model
            self._load_attempted = True
            self._initialize_model()
        return self.model

    def _initialize_model(self):
        try:
            from faster_whisper import WhisperModel
            logger.info(f"Loading faster-whisper model '{self.model_size}' on {self.device} ({self.compute_type})...")
            self.model = WhisperModel(
                self.model_size,
                device=self.device,
                compute_type=self.compute_type,
                download_root=os.getenv("WHISPER_CACHE_DIR", None)
            )
            logger.info("faster-whisper model loaded successfully.")
        except Exception as e:
            logger.warning(f"Could not load faster-whisper locally: {e}. Falling back to dynamic mock transcriber.")
            self.model = None

    def transcribe_audio(
        self,
        audio_bytes: bytes,
        preferred_language: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Transcribes incoming audio bytes into text with native language detection.
        Specifically configured for Hindi, Marathi, English, and Hinglish code-switching.
        """
        if not audio_bytes or len(audio_bytes) < 100:
            return {
                "text": "",
                "language": preferred_language or "en",
                "confidence": 0.0,
                "is_hinglish": False
            }

        # First transcription triggers the model download/load.
        self._ensure_model()

        if self.model is not None:
            try:
                audio_stream = io.BytesIO(audio_bytes)
                # Configure transcription with language detection
                segments, info = self.model.transcribe(
                    audio_stream,
                    beam_size=5,
                    language=preferred_language if preferred_language in SUPPORTED_LANGUAGES else None,
                    task="transcribe",
                    initial_prompt="ECOBRIDGES, CPCB, E-Waste, Recycler, Collector, Admin, Scrap, Metal, Rupees, Lot, किलोग्राम, दर, भाव, कचरा",
                    vad_filter=True,
                    vad_parameters=dict(min_silence_duration_ms=500)
                )

                text_segments = [segment.text.strip() for segment in segments]
                full_transcript = " ".join(text_segments)

                detected_lang = info.language
                prob = info.language_probability

                is_hinglish = self._detect_hinglish(full_transcript, detected_lang)

                return {
                    "text": full_transcript,
                    "language": detected_lang,
                    "confidence": float(prob),
                    "is_hinglish": is_hinglish,
                    "duration": info.duration
                }
            except Exception as e:
                logger.error(f"Error during transcription: {e}")

        # Resilient fallback transcription for simulated audio buffers
        return self._generate_simulated_transcription(audio_bytes, preferred_language)

    def _detect_hinglish(self, text: str, detected_lang: str) -> bool:
        """
        Detects Hinglish or code-mixed Indian dialect (Latin script mixing Hindi/Marathi words).
        """
        lower = text.lower()
        hinglish_markers = [
            "kholo", "batao", "chahiye", "mera", "meri", "kiti", "ahe", "dena", "kaise",
            "karo", "karna", "chalega", "paisa", "rupaye", "bhav", "rate", "scrap", "lot"
        ]
        has_latin = any(c.isascii() and c.isalpha() for c in text)
        has_hinglish_keyword = any(marker in lower for marker in hinglish_markers)
        return has_latin and has_hinglish_keyword

    def _generate_simulated_transcription(
        self,
        audio_bytes: bytes,
        lang: Optional[str]
    ) -> Dict[str, Any]:
        """
        Placeholder transcript for environments with no speech-to-text backend.

        Off by default, and deliberately so. This previously ran as a catch-all
        fallback, so real audio that failed to transcribe came back as a
        confident, fabricated sentence - the caller could not tell real speech
        from invented text. It now refuses unless WHISPER_MOCK_TRANSCRIPT is
        explicitly enabled for local UI work with no audio hardware.
        """
        if not self.allow_mock:
            raise SpeechToTextUnavailable(
                "speech-to-text is not available on this host: the Whisper model is "
                "not loaded (WHISPER_ENABLED=false or the model failed to load). "
                "Enable WHISPER_ENABLED with a model that fits the available memory, "
                "or set WHISPER_MOCK_TRANSCRIPT=true to accept placeholder text."
            )
        selected_lang = lang or "en"
        logger.warning("Returning a MOCK transcript. The audio was not transcribed.")
        return {
            "text": "Open informal collector login",
            "language": selected_lang,
            "confidence": 0.0,
            "is_hinglish": False,
            "duration": 0.0,
            "mock": True
        }
