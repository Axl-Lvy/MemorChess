"""A single chat-completion call with JSON output, for steps that run no tools (e.g. precheck).

It executes no model-written code, so it runs in the trusted process without a sandbox.
"""

import json
import time
import urllib.error
import urllib.request
from pathlib import Path

from agentrun.adapters.base import StepResult, output_path
from agentrun.config import StepConfig
from agentrun.sandbox import Sandbox


class ChatAdapter:
    """POSTs the prompt to an OpenAI-compatible endpoint and stores the reply as the output."""

    can_resume = False

    def __init__(self, base_url: str):
        self.base_url = base_url.rstrip("/")

    def start(self, prompt: str, step: StepConfig, sb: Sandbox, out_dir: Path) -> StepResult:
        out_dir.mkdir(parents=True, exist_ok=True)
        log = out_dir / f"{step.name}.log"
        body = json.dumps({
            "model": step.model,
            "messages": [{"role": "user", "content": prompt}],
            "response_format": {"type": "json_object"},
        }).encode()
        request = urllib.request.Request(
            f"{self.base_url}/v1/chat/completions",
            data=body,
            headers={"Content-Type": "application/json", "Authorization": "Bearer sk-local-proxy"},
        )
        started = time.monotonic()
        try:
            with urllib.request.urlopen(request, timeout=step.timeout_s) as response:
                reply = json.loads(response.read())
        except TimeoutError as e:
            log.write_text(str(e))
            return StepResult("timeout", None, None, None, None, None, time.monotonic() - started, log)
        except (urllib.error.URLError, json.JSONDecodeError) as e:
            log.write_text(str(e))
            return StepResult("error", None, None, None, None, None, time.monotonic() - started, log)
        wall = time.monotonic() - started
        log.write_text(json.dumps(reply))
        content = reply.get("choices", [{}])[0].get("message", {}).get("content")
        target = output_path(out_dir, step)
        if content:
            target.write_text(content)
        usage = reply.get("usage", {})
        return StepResult(
            "ok", None, target if content else None, 1,
            usage.get("prompt_tokens"), usage.get("completion_tokens"), wall, log,
        )

    def resume(self, session: str, feedback: str, step: StepConfig, sb: Sandbox, out_dir: Path) -> StepResult:
        raise NotImplementedError("chat adapter cannot resume; the loop restarts it instead")
