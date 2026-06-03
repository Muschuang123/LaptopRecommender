from __future__ import annotations

from dataclasses import asdict, dataclass, field
from datetime import datetime
from typing import Any


def now_text() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


@dataclass
class LaptopRawItem:
    source_name: str
    source_url: str
    title: str = ""
    brand: str | None = None
    model: str | None = None
    price_text: str | None = None
    price: float | None = None
    image_url: str | None = None
    ranking: int | None = None
    comment_count_text: str | None = None
    fetched_at: str = field(default_factory=now_text)
    specs: dict[str, str] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)
