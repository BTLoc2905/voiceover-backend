"""Offline checks: python -m unittest discover -s scripts -p test_edge_tts_worker.py."""

import asyncio
import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from edge_tts.exceptions import NoAudioReceived
import edge_tts_worker as worker


class EdgeTtsWorkerTest(unittest.TestCase):
    def test_java_owned_attempt_is_not_retried_inside_python(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "one.mp3"

            async def save(filename):
                Path(filename).write_bytes(b"partial")
                raise NoAudioReceived("Thai temporarily unavailable")

            with patch.object(worker.edge_tts, "Communicate", return_value=SimpleNamespace(save=save)) as communicate, \
                    patch.object(worker.asyncio, "sleep", new_callable=AsyncMock) as sleep:
                with self.assertRaises(NoAudioReceived):
                    asyncio.run(worker.synthesize("ทดสอบ", "th-TH-PremwadeeNeural", output, 1))
            self.assertEqual(communicate.call_count, 1)
            sleep.assert_not_awaited()
            self.assertFalse(output.exists())

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.output = Path(self.temp.name) / "audio.mp3"

    def test_transient_failures_restart_complete_text_with_same_voice(self):
        requests = []

        async def save(filename):
            path = Path(filename)
            self.assertFalse(path.exists(), "A failed partial file must be removed before retry")
            if len(requests) < 3:
                path.write_bytes(b"incomplete audio")
                raise NoAudioReceived("No audio in a later text chunk")
            path.write_bytes(b"valid audio fixture" * 100)

        def communicate(text, voice):
            requests.append((text, voice))
            return SimpleNamespace(save=save)

        with patch.object(worker.edge_tts, "Communicate", side_effect=communicate), \
                patch.object(worker, "MP3", return_value=SimpleNamespace(info=SimpleNamespace(length=4.2))), \
                patch.object(worker.asyncio, "sleep", new_callable=AsyncMock) as sleep:
            result = asyncio.run(worker.synthesize("Complete narration", "vi-VN-HoaiMyNeural", self.output))
        self.assertEqual(requests, [("Complete narration", "vi-VN-HoaiMyNeural")] * 3)
        self.assertEqual(result, {"durationSec": 5, "bytes": self.output.stat().st_size, "attempts": 3})
        self.assertEqual([call.args[0] for call in sleep.await_args_list], [1, 2])

    def test_exhaustion_is_bounded_and_removes_partial_file(self):
        async def save(filename):
            Path(filename).write_bytes(b"partial audio")
            raise NoAudioReceived("No audio")

        with patch.object(worker.edge_tts, "Communicate", return_value=SimpleNamespace(save=save)) as communicate, \
                patch.object(worker.asyncio, "sleep", new_callable=AsyncMock):
            with self.assertRaises(worker.AudioRetriesExhausted):
                asyncio.run(worker.synthesize("Text", "vi-VN-HoaiMyNeural", self.output))
        self.assertEqual(communicate.call_count, 3)
        self.assertFalse(self.output.exists())

    def test_invalid_mp3_is_not_reported_as_success(self):
        async def save(filename):
            Path(filename).write_bytes(b"Not an MP3" * 100)

        with patch.object(worker.edge_tts, "Communicate", return_value=SimpleNamespace(save=save)) as communicate:
            with self.assertRaises(Exception):
                asyncio.run(worker.synthesize("Text", "vi-VN-HoaiMyNeural", self.output))
        self.assertEqual(communicate.call_count, 1)
        self.assertFalse(self.output.exists())

    def test_configuration_errors_are_not_retried(self):
        with patch.object(worker.edge_tts, "Communicate", side_effect=ValueError("Invalid voice")) as communicate:
            with self.assertRaisesRegex(ValueError, "Invalid voice"):
                asyncio.run(worker.synthesize("Text", "invalid", self.output))
        self.assertEqual(communicate.call_count, 1)

    def test_cli_exhaustion_has_distinct_exit_code_and_no_success_json(self):
        source = Path(self.temp.name) / "input.txt"
        source.write_text("Text", encoding="utf-8")
        stdout, stderr = io.StringIO(), io.StringIO()
        with patch.object(worker.sys, "argv", ["worker", "--input", str(source), "--output", str(self.output),
                                              "--voice", "vi-VN-HoaiMyNeural"]), \
                patch.object(worker, "synthesize", new=AsyncMock(side_effect=worker.AudioRetriesExhausted("No audio"))), \
                contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            result = worker.main()
        self.assertEqual(result, 75)
        self.assertEqual(stdout.getvalue(), "")
        self.assertIn("AudioRetriesExhausted", stderr.getvalue())

    def test_cli_stdout_contains_only_success_metadata(self):
        source = Path(self.temp.name) / "input.txt"
        source.write_text("Text", encoding="utf-8")
        stdout = io.StringIO()
        metadata = {"durationSec": 5, "bytes": 24000, "attempts": 2}
        with patch.object(worker.sys, "argv", ["worker", "--input", str(source), "--output", str(self.output),
                                              "--voice", "vi-VN-HoaiMyNeural"]), \
                patch.object(worker, "synthesize", new=AsyncMock(return_value=metadata)), \
                contextlib.redirect_stdout(stdout):
            result = worker.main()
        self.assertEqual(result, 0)
        self.assertEqual(json.loads(stdout.getvalue()), metadata)


if __name__ == "__main__":
    unittest.main()
