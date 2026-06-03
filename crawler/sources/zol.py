from __future__ import annotations

from dataclasses import dataclass
from fnmatch import fnmatch
import re
from urllib.parse import urlparse

from ..http_client import HttpClient
from ..models import LaptopRawItem
from ..normalizer import clean_value, extract_brand, parse_price, strip_html

try:
    from tqdm import tqdm
except ImportError:
    tqdm = None


ZOL_NOTEBOOK_RANK_ROOT_URL = "https://wap.zol.com.cn/top/notebook/"
ZOL_BRAND_RANK_URL = "https://wap.zol.com.cn/top/notebook/brand/"
ZOL_DETAIL_ROBOTS_URL = "https://detail.zol.com.cn/robots.txt"
ZOL_WAP_ROBOTS_URL = "https://wap.zol.com.cn/robots.txt"


@dataclass
class RankPage:
    url: str
    title: str
    brand: str | None = None


@dataclass
class RobotsRules:
    disallow: list[str]

    def can_fetch(self, url: str) -> bool:
        parsed = urlparse(url)
        target = parsed.path
        if parsed.query:
            target += "?" + parsed.query
        for rule in self.disallow:
            if not rule:
                continue
            exact = rule.endswith("$")
            pattern = rule[:-1] if exact else rule
            if rule.startswith("http"):
                candidate = url
            else:
                candidate = target
            if "*" in pattern:
                if fnmatch(candidate, pattern):
                    return False
            elif exact and candidate == pattern:
                return False
            elif not exact and candidate.startswith(pattern):
                return False
        return True


class RobotsGuard:
    def __init__(self, client: HttpClient) -> None:
        self.client = client
        self._cache: dict[str, RobotsRules] = {}

    def assert_can_fetch(self, url: str) -> None:
        rules = self._rules_for_url(url)
        if not rules.can_fetch(url):
            raise RuntimeError(f"robots.txt disallows crawling: {url}")

    def _rules_for_url(self, url: str) -> RobotsRules:
        host = urlparse(url).netloc.lower()
        if host not in self._cache:
            robots_url = {
                "detail.zol.com.cn": ZOL_DETAIL_ROBOTS_URL,
                "wap.zol.com.cn": ZOL_WAP_ROBOTS_URL,
            }.get(host)
            if robots_url is None:
                self._cache[host] = RobotsRules([])
            else:
                self._cache[host] = self._parse_robots(self.client.get_text(robots_url))
        return self._cache[host]

    def _parse_robots(self, text: str) -> RobotsRules:
        disallow: list[str] = []
        active_for_all = False
        for raw_line in text.splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#") or ":" not in line:
                continue
            key, value = line.split(":", 1)
            key = key.strip().lower()
            value = value.strip()
            if key == "user-agent":
                active_for_all = value == "*"
            elif key == "disallow" and active_for_all:
                disallow.append(value)
        return RobotsRules(disallow)


class ZolParamCrawler:
    def __init__(self, client: HttpClient) -> None:
        self.client = client
        self.robots = RobotsGuard(client)

    def fetch_param(self, item: LaptopRawItem) -> LaptopRawItem:
        self.robots.assert_can_fetch(item.source_url)
        param_html = self.client.get_text(item.source_url)
        title = self._first_match(
            [
                r"<h1[^>]*>(.*?)</h1>",
                r"<title>(.*?)</title>",
            ],
            param_html,
        )
        item.title = clean_value(item.title) or self._clean_title(title)
        item.specs.update(self.parse_param_page(param_html))
        if not item.price_text:
            item.price_text = self._first_match([r'<b class="price-type">(.*?)</b>'], param_html)
            item.price = parse_price(item.price_text)
        return item

    def parse_param_page(self, html_text: str) -> dict[str, str]:
        specs: dict[str, str] = {}
        for th_html, td_html in re.findall(
            r"<tr>\s*<th[^>]*>(.*?)</th>\s*<td[^>]*>(.*?)</td>",
            html_text,
            flags=re.I | re.S,
        ):
            key = strip_html(th_html)
            value = strip_html(td_html)
            if key and value:
                specs[key] = value
        return specs

    def _first_match(self, patterns: list[str], text: str) -> str | None:
        for pattern in patterns:
            match = re.search(pattern, text, flags=re.I | re.S)
            if match:
                return match.group(1)
        return None

    def _clean_title(self, title: str | None) -> str:
        cleaned = clean_value(title) or ""
        cleaned = re.sub(r"】.*$", "", cleaned)
        cleaned = cleaned.replace("【", "")
        return cleaned.strip()


class ZolNotebookRankCrawler(ZolParamCrawler):
    source_name = "ZOL_HOT_RANK"

    def __init__(
        self,
        client: HttpClient,
        rank_root_url: str = ZOL_NOTEBOOK_RANK_ROOT_URL,
        brand_rank_url: str = ZOL_BRAND_RANK_URL,
    ) -> None:
        super().__init__(client)
        self.rank_root_url = rank_root_url
        self.brand_rank_url = brand_rank_url

    def crawl(self, max_details: int | None = None) -> list[LaptopRawItem]:
        rank_pages = self.discover_rank_pages()
        candidates = self.collect_rank_items(rank_pages, max_details=max_details)
        iterator = self._progress(candidates, "抓取 ZOL 参数页", "台")
        return [self.fetch_param(item) for item in iterator]

    def discover_rank_pages(self) -> list[RankPage]:
        self.robots.assert_can_fetch(self.rank_root_url)
        root_html = self.client.get_text(self.rank_root_url)
        rank_pages = self.parse_rank_directory_page(root_html, self.rank_root_url)

        self.robots.assert_can_fetch(self.brand_rank_url)
        brand_html = self.client.get_text(self.brand_rank_url)
        rank_pages.extend(self.parse_rank_directory_page(brand_html, self.brand_rank_url))

        return self._dedupe_rank_pages(rank_pages)

    def collect_rank_items(self, rank_pages: list[RankPage], max_details: int | None = None) -> list[LaptopRawItem]:
        items: list[LaptopRawItem] = []
        seen: dict[str, LaptopRawItem] = {}
        iterator = self._progress(rank_pages, "解析 ZOL 榜单页", "榜")
        for rank_page in iterator:
            self.robots.assert_can_fetch(rank_page.url)
            html = self.client.get_text(rank_page.url)
            for item in self.parse_rank_page(html, rank_page.url, rank_brand=rank_page.brand):
                existing = seen.get(item.source_url)
                if existing is not None:
                    if not existing.brand and item.brand:
                        existing.brand = item.brand
                    continue
                seen[item.source_url] = item
                items.append(item)
                if max_details is not None and len(items) >= max_details:
                    return items
        return items

    def _progress(self, items: list, desc: str, unit: str):
        if tqdm is not None:
            return self._tqdm_progress(items, desc, unit)
        total = len(items)
        return self._fallback_progress(items, total, desc)

    def _tqdm_progress(self, items: list, desc: str, unit: str):
        with tqdm(total=len(items), desc=desc, unit=unit, dynamic_ncols=True) as progress:
            for item in items:
                progress.update(1)
                yield item

    def _fallback_progress(self, items: list, total: int, desc: str):
        for index, item in enumerate(items, start=1):
            title = getattr(item, "title", str(item))
            print(f"[{index}/{total}] {desc}: {title}")
            yield item

    def parse_rank_directory_page(self, html_text: str, page_url: str) -> list[RankPage]:
        rank_pages: list[RankPage] = []
        for href, text in re.findall(r'<a[^>]+href="([^"]+)"[^>]*>(.*?)</a>', html_text, flags=re.I | re.S):
            url = self.client.absolute_url(page_url, href)
            if not url or not self._is_notebook_rank_url(url):
                continue
            title = clean_value(strip_html(text)) or url
            brand = self._extract_rank_brand(title, url, page_url)
            rank_pages.append(RankPage(url=url, title=title, brand=brand))
        return self._dedupe_rank_pages(rank_pages)

    def parse_rank_page(
        self,
        html_text: str,
        page_url: str,
        rank_brand: str | None = None,
    ) -> list[LaptopRawItem]:
        blocks = re.findall(r'<li class="showLi\b.*?</li>', html_text, flags=re.I | re.S)
        items: list[LaptopRawItem] = []
        seen: set[str] = set()
        for block in blocks:
            href = self._first_match([r'<a class="tab-box__link" href="([^"]+)"'], block)
            param_url = self._rank_href_to_param_url(href)
            if not param_url or param_url in seen:
                continue
            seen.add(param_url)
            rank = self._first_match([r"TOP\s*<br/?>\s*(\d+)"], block)
            title = self._first_match([r'<p class="pro-info-name[^"]*">(.*?)</p>'], block)
            price_text = self._first_match([r'<p class="pro-info-money">￥<span class="pro-info-price[^"]*">(.*?)</span>'], block)
            image = self._first_match([r'<img[^>]+data-src="([^"]+)"', r'<img[^>]+src="([^"]+)"'], block)
            heat = self._first_match([r'<p class="pro-page-view[^"]*">(.*?)</p>'], block)
            items.append(
                LaptopRawItem(
                    source_name=self.source_name,
                    source_url=param_url,
                    title=clean_value(title) or "",
                    brand=rank_brand,
                    price_text=clean_value(price_text),
                    price=parse_price(price_text),
                    image_url=self.client.absolute_url(page_url, image),
                    ranking=int(rank) if rank else None,
                    comment_count_text=clean_value(heat),
                )
            )
        return items

    def _dedupe_rank_pages(self, rank_pages: list[RankPage]) -> list[RankPage]:
        deduped: list[RankPage] = []
        seen: set[str] = set()
        for rank_page in rank_pages:
            if rank_page.url in seen:
                continue
            seen.add(rank_page.url)
            deduped.append(rank_page)
        return deduped

    def _extract_rank_brand(self, title: str, url: str, directory_url: str) -> str | None:
        if urlparse(directory_url).path != "/top/notebook/brand/":
            return None
        slug = urlparse(url).path.strip("/").split("/")[-1]
        return extract_brand(title, slug)

    def _is_notebook_rank_url(self, url: str) -> bool:
        parsed = urlparse(url)
        if parsed.netloc.lower() != "wap.zol.com.cn":
            return False
        path = parsed.path
        if path in {"/top/notebook/", "/top/notebook/brand/"}:
            return False
        return bool(
            re.fullmatch(
                r"/top/notebook/(?:hot\.html|param_\d+\.html|[A-Za-z0-9_-]+/)",
                path,
            )
        )

    def _rank_href_to_param_url(self, href: str | None) -> str | None:
        if not href:
            return None
        match = re.search(r"/(\d+)/(\d+)/index\.html", href)
        if match:
            return f"https://detail.zol.com.cn/{match.group(1)}/{match.group(2)}/param.shtml"
        return None
