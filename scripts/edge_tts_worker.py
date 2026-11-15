"""One-shot Edge-TTS helper: read UTF-8 text, save MP3, validate it, report duration."""

import argparse
import asyncio
import json
import math
import sys
from pathlib import Path

import edge_tts
from edge_tts.exceptions import NoAudioReceived
from mutagen.mp3 import MP3


MAX_ATTEMPTS = 3
# The Java caller recognizes this exit code and must not repeat exhausted retries.
RETRIES_EXHAUSTED_EXIT_CODE = 75


class AudioRetriesExhausted(Exception):
    """The service returned no audio on every bounded attempt."""


async def synthesize(text: str, voice: str, output: Path, max_attempts: int = MAX_ATTEMPTS) -> dict:
    if not 1 <= max_attempts <= MAX_ATTEMPTS:
        raise ValueError("attempts must be between 1 and 3")
    for attempt in range(1, max_attempts + 1):
        try:
            # A Communicate instance can only stream once. Restart the full text
            # into a new file, including when a later text chunk failed.
            await edge_tts.Communicate(text, voice).save(str(output))
            size = output.stat().st_size
            duration = MP3(output).info.length
            if size <= 128 or not math.isfinite(duration) or duration <= 0:
                raise ValueError("Edge-TTS không tạo được MP3 có frame âm thanh hợp lệ")
            return {"durationSec": max(1, math.ceil(duration)), "bytes": size, "attempts": attempt}
        except NoAudioReceived as error:
            output.unlink(missing_ok=True)
            if max_attempts == 1:
                # Java persists the attempt budget and owns retries across worker restarts.
                raise
            if attempt == max_attempts:
                raise AudioRetriesExhausted(
                    f"Edge-TTS không trả âm thanh sau {max_attempts} lần với giọng {voice}. "
                    "Dịch vụ có thể tạm thời không khả dụng; hãy thử lại sau."
                ) from error
            await asyncio.sleep(attempt)
        except BaseException:
            # Do not leave partial/invalid audio behind on errors or cancellation.
            output.unlink(missing_ok=True)
            raise


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--voice", required=True)
    parser.add_argument("--attempts", type=int, choices=range(1, MAX_ATTEMPTS + 1), default=MAX_ATTEMPTS)
    args = parser.parse_args()
    try:
        text = args.input.read_text(encoding="utf-8").strip()
        if not text:
            raise ValueError("Nội dung TTS rỗng")
        metadata = asyncio.run(synthesize(text, args.voice, args.output, args.attempts))
        print(json.dumps(metadata))
        return 0
    except AudioRetriesExhausted as error:
        print(f"{type(error).__name__}: {error}", file=sys.stderr)
        return RETRIES_EXHAUSTED_EXIT_CODE
    except Exception as error:
        print(f"{type(error).__name__}: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
