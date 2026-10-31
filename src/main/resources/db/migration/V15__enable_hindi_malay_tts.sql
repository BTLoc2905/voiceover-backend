-- Edge-TTS voices hi-IN-SwaraNeural and ms-MY-YasminNeural are available.
-- Existing translations stay intact; admins can regenerate audio per language.
UPDATE language SET tts_supported = TRUE WHERE code IN ('hi', 'ms');
