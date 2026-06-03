from __future__ import annotations

import argparse
import json
import os
import subprocess
from datetime import datetime
from pathlib import Path

from .cli import _dedupe_by_url
from .http_client import HttpClient
from .normalizer import LaptopDataNormalizer
from .sources.zol import ZOL_NOTEBOOK_RANK_ROOT_URL, ZolHotRankCrawler, ZolNotebookRankCrawler
from .sql_writer import SafeOnlineMySqlSqlWriter


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Crawl laptop data and safely update MySQL without overwriting existing laptop attributes."
    )
    parser.add_argument("--max-details", type=int, default=None, help="Maximum parameter pages to crawl.")
    parser.add_argument("--delay", type=float, default=1.2, help="Delay between HTTP requests, seconds.")
    parser.add_argument("--output-dir", default="data/crawl_output")
    parser.add_argument("--zol-rank-root-url", default=ZOL_NOTEBOOK_RANK_ROOT_URL)
    parser.add_argument("--zol-hot-rank-url", default=None, help="Deprecated: crawl one rank page only.")
    parser.add_argument("--execute", action="store_true", help="Execute generated safe SQL with mysql client.")
    parser.add_argument("--mysql-bin", default="mysql", help="mysql executable path.")
    parser.add_argument(
        "--spring-config",
        default="laptop-rec-backend/application-local.yml",
        help="Spring local config path used to read spring.datasource settings.",
    )
    parser.add_argument("--db-host", default=os.getenv("MYSQL_HOST"))
    parser.add_argument("--db-port", default=os.getenv("MYSQL_PORT"))
    parser.add_argument("--db-user", default=os.getenv("MYSQL_USER"))
    parser.add_argument("--db-name", default=os.getenv("MYSQL_DATABASE"))
    return parser.parse_args()


def unquote_config_value(value: str) -> str:
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
        return value[1:-1]
    return value


def read_spring_datasource_config(path: Path) -> dict[str, str]:
    if not path.exists():
        return {}
    result: dict[str, str] = {}
    in_spring = False
    in_datasource = False
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line_without_comment = raw_line.split("#", 1)[0].rstrip()
        if not line_without_comment.strip():
            continue
        indent = len(line_without_comment) - len(line_without_comment.lstrip(" "))
        stripped = line_without_comment.strip()
        if indent == 0:
            in_spring = stripped == "spring:"
            in_datasource = False
            continue
        if in_spring and indent == 2:
            in_datasource = stripped == "datasource:"
            continue
        if in_spring and in_datasource and indent >= 4 and ":" in stripped:
            key, value = stripped.split(":", 1)
            result[key.strip()] = unquote_config_value(value)
    return result


def parse_jdbc_mysql_url(url: str | None) -> dict[str, str]:
    if not url:
        return {}
    prefix = "jdbc:mysql://"
    if not url.startswith(prefix) or "/" not in url[len(prefix):]:
        return {}
    authority, database_part = url[len(prefix):].split("/", 1)
    if ":" in authority:
        host, port = authority.rsplit(":", 1)
    else:
        host, port = authority, "3306"
    return {
        "host": host,
        "port": port,
        "database": database_part.split("?", 1)[0],
    }


def resolve_database_args(args: argparse.Namespace) -> None:
    datasource = read_spring_datasource_config(Path(args.spring_config))
    jdbc = parse_jdbc_mysql_url(datasource.get("url"))
    args.db_host = args.db_host or jdbc.get("host") or "127.0.0.1"
    args.db_port = args.db_port or jdbc.get("port") or "3306"
    args.db_name = args.db_name or jdbc.get("database")
    args.db_user = args.db_user or datasource.get("username")
    args.db_password = os.getenv("MYSQL_PWD") or datasource.get("password")


def crawl_normalized_items(
    max_details: int | None,
    delay: float,
    rank_root_url: str,
    hot_rank_url: str | None = None,
) -> tuple[list, list[dict]]:
    client = HttpClient(delay_seconds=delay)
    if hot_rank_url:
        crawler = ZolHotRankCrawler(client, hot_rank_url=hot_rank_url)
    else:
        crawler = ZolNotebookRankCrawler(client, rank_root_url=rank_root_url)
    raw_items = crawler.crawl(max_details=max_details)
    raw_items = _dedupe_by_url(raw_items)
    normalizer = LaptopDataNormalizer()
    return raw_items, [normalizer.normalize(item) for item in raw_items]


def execute_mysql(sql_path: Path, args: argparse.Namespace) -> None:
    if not args.db_user or not args.db_name:
        raise RuntimeError(
            "执行 SQL 需要数据库配置。请检查 application-local.yml，或设置 MYSQL_USER / MYSQL_DATABASE。"
        )
    command = [
        args.mysql_bin,
        "--default-character-set=utf8mb4",
        "-h",
        args.db_host,
        "-P",
        str(args.db_port),
        "-u",
        args.db_user,
        args.db_name,
    ]
    env = os.environ.copy()
    if args.db_password and "MYSQL_PWD" not in env:
        env["MYSQL_PWD"] = args.db_password
    with sql_path.open("rb") as sql_file:
        subprocess.run(command, stdin=sql_file, env=env, check=True)


def main() -> None:
    args = parse_args()
    resolve_database_args(args)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    raw_items, normalized_items = crawl_normalized_items(
        max_details=args.max_details,
        delay=args.delay,
        rank_root_url=args.zol_rank_root_url,
        hot_rank_url=args.zol_hot_rank_url,
    )

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    raw_path = output_dir / f"laptops_raw_{timestamp}.json"
    normalized_path = output_dir / f"laptops_normalized_{timestamp}.json"
    sql_path = output_dir / f"laptops_safe_online_update_{timestamp}.sql"

    raw_path.write_text(
        json.dumps([item.to_dict() for item in raw_items], ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    normalized_path.write_text(json.dumps(normalized_items, ensure_ascii=False, indent=2), encoding="utf-8")
    SafeOnlineMySqlSqlWriter().write(normalized_items, sql_path)

    if args.execute:
        execute_mysql(sql_path, args)
        status = "executed"
    else:
        status = "generated"

    print(f"status={status}")
    print(f"raw_items={len(raw_items)} laptops={len(normalized_items)}")
    print(f"raw_json={raw_path}")
    print(f"normalized_json={normalized_path}")
    print(f"safe_sql={sql_path}")


if __name__ == "__main__":
    main()
