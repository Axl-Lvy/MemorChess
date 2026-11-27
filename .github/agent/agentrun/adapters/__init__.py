"""Harness adapters by name; `pipeline.toml` picks one per step."""

from typing import Callable

from agentrun.adapters.base import Adapter
from agentrun.adapters.chat import ChatAdapter
from agentrun.adapters.claude_code import ClaudeCodeAdapter
from agentrun.adapters.fake import FakeAdapter

HARNESSES: dict[str, Callable[[str], Adapter]] = {
    "chat": ChatAdapter,
    "claude-code": ClaudeCodeAdapter,
    "fake": lambda _base_url: FakeAdapter([]),
}
