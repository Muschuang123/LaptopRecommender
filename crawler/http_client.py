from __future__ import annotations

import time
from urllib.parse import urljoin

import requests


DEFAULT_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/126.0 Safari/537.36"
    ),
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.6",
}


class HttpClient:
    def __init__(self, delay_seconds: float = 1.2, timeout_seconds: int = 20) -> None:
        self.delay_seconds = max(0.0, delay_seconds)
        self.timeout_seconds = timeout_seconds
        self.session = requests.Session()
        self.session.headers.update(DEFAULT_HEADERS)
        self._last_request_at = 0.0

    def absolute_url(self, base_url: str, href: str | None) -> str | None:
        if not href:
            return None
        if href.startswith("//"):
            return "https:" + href
        return urljoin(base_url, href)

    def get_text(self, url: str) -> str:
        wait_seconds = self.delay_seconds - (time.monotonic() - self._last_request_at)
        if wait_seconds > 0:
            time.sleep(wait_seconds)

        response = self.session.get(url, timeout=self.timeout_seconds)
        self._last_request_at = time.monotonic()
        response.raise_for_status()
        if not response.encoding or response.encoding.lower() == "iso-8859-1":
            response.encoding = response.apparent_encoding
        return response.text

