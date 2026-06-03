from __future__ import annotations

import argparse
import json
from datetime import datetime
from pathlib import Path

from .http_client import HttpClient
from .normalizer import LaptopDataNormalizer
from .sources.zol import ZOL_NOTEBOOK_RANK_ROOT_URL, ZolHotRankCrawler, ZolNotebookRankCrawler
from .sql_writer import MySqlSqlWriter


def _dedupe_by_url(raw_items: list):
    deduped = []
    seen = set()
    for item in raw_items:
        key = item.source_url
        if key in seen:
            continue
        seen.add(key)
        deduped.append(item)
    return deduped


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Crawl laptop data and generate MySQL upsert SQL.")
    parser.add_argument("--max-details", type=int, default=None, help="Maximum parameter pages to crawl.")
    parser.add_argument("--delay", type=float, default=1.2, help="Delay between HTTP requests, seconds.")
    parser.add_argument("--output-dir", default="data/crawl_output")
    parser.add_argument("--zol-rank-root-url", default=ZOL_NOTEBOOK_RANK_ROOT_URL)
    parser.add_argument("--zol-hot-rank-url", default=None, help="Deprecated: crawl one rank page only.")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    client = HttpClient(delay_seconds=args.delay)
    if args.zol_hot_rank_url:
        crawler = ZolHotRankCrawler(client, hot_rank_url=args.zol_hot_rank_url)
    else:
        crawler = ZolNotebookRankCrawler(client, rank_root_url=args.zol_rank_root_url)
    raw_items = crawler.crawl(max_details=args.max_details)
    raw_items = _dedupe_by_url(raw_items)

    normalizer = LaptopDataNormalizer()
    normalized_items = [normalizer.normalize(item) for item in raw_items]

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    raw_path = output_dir / f"laptops_raw_{timestamp}.json"
    normalized_path = output_dir / f"laptops_normalized_{timestamp}.json"
    sql_path = output_dir / f"laptops_upsert_{timestamp}.sql"

    raw_path.write_text(
        json.dumps([item.to_dict() for item in raw_items], ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    normalized_path.write_text(
        json.dumps(normalized_items, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    MySqlSqlWriter().write(normalized_items, sql_path)

    print(f"raw_items={len(raw_items)} laptops={len(normalized_items)}")
    print(f"raw_json={raw_path}")
    print(f"normalized_json={normalized_path}")
    print(f"sql={sql_path}")


if __name__ == "__main__":
    main()
