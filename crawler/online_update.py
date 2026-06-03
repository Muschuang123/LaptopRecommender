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
from .sources.zol import ZolNotebookRankCrawler
from .sql_writer import SafeOnlineMySqlSqlWriter


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Crawl laptop data and safely update MySQL without overwriting existing laptop attributes."
    )
    parser.add_argument("--max-details", type=int, default=None, help="Maximum parameter pages to crawl.")
    parser.add_argument("--delay", type=float, default=1.2, help="Delay between HTTP requests, seconds.")
    parser.add_argument("--output-dir", default="data/crawl_output")
    parser.add_argument("--execute", action="store_true", help="Execute generated safe SQL with mysql client.")
    parser.add_argument("--init-schema", action="store_true", help="Create database and import sql/schema.sql before update.")
    parser.add_argument("--mysql-bin", default="mysql", help="mysql executable path.")
    parser.add_argument("--schema", default="sql/schema.sql", help="Schema SQL path used with --init-schema.")
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


def crawl_normalized_items(max_details: int | None, delay: float) -> tuple[list, list[dict]]:
    client = HttpClient(delay_seconds=delay)
    raw_items = ZolNotebookRankCrawler(client).crawl(max_details=max_details)
    raw_items = _dedupe_by_url(raw_items)
    normalizer = LaptopDataNormalizer()
    return raw_items, [normalizer.normalize(item) for item in raw_items]


def mysql_env(args: argparse.Namespace) -> dict[str, str]:
    env = os.environ.copy()
    if args.db_password and "MYSQL_PWD" not in env:
        env["MYSQL_PWD"] = args.db_password
    return env


def base_mysql_command(args: argparse.Namespace) -> list[str]:
    if not args.db_user or not args.db_name:
        raise RuntimeError(
            "执行 SQL 需要数据库配置。请检查 application-local.yml，或设置 MYSQL_USER / MYSQL_DATABASE。"
        )
    return [
        args.mysql_bin,
        "--default-character-set=utf8mb4",
        "-h",
        args.db_host,
        "-P",
        str(args.db_port),
        "-u",
        args.db_user,
    ]


def quote_mysql_identifier(name: str) -> str:
    return f"`{name.replace('`', '``')}`"


def init_database_schema(args: argparse.Namespace) -> None:
    schema_path = Path(args.schema)
    if not schema_path.exists():
        raise RuntimeError(f"找不到 schema 文件: {schema_path}")
    create_sql = (
        f"CREATE DATABASE IF NOT EXISTS {quote_mysql_identifier(args.db_name)} "
        "DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    )
    subprocess.run(base_mysql_command(args) + ["-e", create_sql], env=mysql_env(args), check=True)
    with schema_path.open("rb") as schema_file:
        subprocess.run(base_mysql_command(args) + [args.db_name], stdin=schema_file, env=mysql_env(args), check=True)


def execute_mysql(sql_path: Path, args: argparse.Namespace) -> None:
    with sql_path.open("rb") as sql_file:
        subprocess.run(base_mysql_command(args) + [args.db_name], stdin=sql_file, env=mysql_env(args), check=True)


def main() -> None:
    args = parse_args()
    resolve_database_args(args)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    if args.init_schema and not args.execute:
        raise RuntimeError("--init-schema 需要同时使用 --execute。")

    raw_items, normalized_items = crawl_normalized_items(max_details=args.max_details, delay=args.delay)

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
        if args.init_schema:
            init_database_schema(args)
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
