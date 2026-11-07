"""One-shot Edge-TTS helper: read UTF-8 text, save MP3, validate it, report duration."""

import argparse
import asyncio
import json
import math
import sys
from pathlib import Path

import edge_tts
from mutagen.mp3 import MP3


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--voice", required=True)
    args = parser.parse_args()
    try:
        text = args.input.read_text(encoding="utf-8").strip()
        if not text:
            raise ValueError("Nội dung TTS rỗng")
        asyncio.run(edge_tts.Communicate(text, args.voice).save(str(args.output)))
        size = args.output.stat().st_size
        duration = MP3(args.output).info.length
        if size <= 128 or not math.isfinite(duration) or duration <= 0:
            raise ValueError("Edge-TTS không tạo được MP3 có frame âm thanh hợp lệ")
        print(json.dumps({"durationSec": max(1, math.ceil(duration)), "bytes": size}))
        return 0
    except Exception as error:
        print(f"{type(error).__name__}: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
