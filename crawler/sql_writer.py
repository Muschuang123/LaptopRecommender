from __future__ import annotations

from collections import Counter
from datetime import datetime
from pathlib import Path
from typing import Any


def sql_value(value: Any) -> str:
    if value is None or value == "":
        return "NULL"
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, (int, float)):
        return str(value)
    text = str(value).replace("\\", "\\\\").replace("'", "''")
    return f"'{text}'"


def null_safe_equals(column: str, value: Any) -> str:
    return f"{column} <=> {sql_value(value)}"


class MySqlSqlWriter:
    def write(self, items: list[dict[str, Any]], output_path: Path) -> None:
        statements: list[str] = []
        now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        statements.extend(
            [
                f"-- Generated at {now}",
                "-- Run sql/schema.sql once before importing generated crawl SQL.",
                "SET NAMES utf8mb4;",
                "START TRANSACTION;",
                "",
            ]
        )

        self._write_sources(statements, items)
        for item in items:
            self._write_laptop(statements, item)
        self._write_logs(statements, items, now)
        statements.extend(["COMMIT;", ""])
        output_path.write_text("\n".join(statements), encoding="utf-8")

    def _write_sources(self, statements: list[str], items: list[dict[str, Any]]) -> None:
        for source_name in sorted({item["source_name"] for item in items}):
            base_url = {
                "ZOL_HOT_RANK": "https://wap.zol.com.cn/top/notebook/hot.html",
            }.get(source_name, "https://detail.zol.com.cn/")
            statements.extend(
                [
                    f"-- source: {source_name}",
                    (
                        "INSERT INTO crawl_source (source_name, base_url, enabled) "
                        f"VALUES ({sql_value(source_name)}, {sql_value(base_url)}, 1) "
                        "ON DUPLICATE KEY UPDATE base_url = VALUES(base_url), "
                        "enabled = VALUES(enabled), updated_at = CURRENT_TIMESTAMP;"
                    ),
                    "",
                ]
            )

    def _write_laptop(self, statements: list[str], item: dict[str, Any]) -> None:
        brand = item.get("brand") or "未知品牌"
        model = item.get("model")
        statements.extend(
            [
                f"-- laptop: {brand} {model}",
                (
                    "INSERT INTO brand (name) "
                    f"VALUES ({sql_value(brand)}) "
                    "ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;"
                ),
                f"SET @brand_id := (SELECT id FROM brand WHERE name = {sql_value(brand)} LIMIT 1);",
            ]
        )

        self._write_cpu(statements, item.get("cpu") or {})
        self._write_gpu(statements, item.get("gpu") or {})
        self._write_memory(statements, item.get("memory") or {})
        self._write_storage(statements, item.get("storage") or {})
        self._write_screen(statements, item.get("screen") or {})
        self._write_battery(statements, item.get("battery") or {})
        self._write_wireless(statements, item.get("wireless") or {})

        columns = [
            "brand_id",
            "cpu_id",
            "gpu_id",
            "memory_id",
            "storage_id",
            "screen_id",
            "battery_id",
            "wireless_id",
            "model",
            "product_type",
            "usage_positioning",
            "weight_kg",
            "thickness_mm",
            "os",
            "color",
            "image_url",
            "source_url",
            "source_name",
            "raw_title",
            "release_date",
        ]
        values = [
            "@brand_id",
            "@cpu_id",
            "@gpu_id",
            "@memory_id",
            "@storage_id",
            "@screen_id",
            "@battery_id",
            "@wireless_id",
            sql_value(model),
            sql_value(item.get("product_type")),
            sql_value(item.get("usage_positioning")),
            sql_value(item.get("weight_kg")),
            sql_value(item.get("thickness_mm")),
            sql_value(item.get("os")),
            sql_value(item.get("color")),
            sql_value(item.get("image_url")),
            sql_value(item.get("source_url")),
            sql_value(item.get("source_name")),
            sql_value(item.get("raw_title")),
            sql_value(item.get("release_date")),
        ]
        update_columns = [column for column in columns[1:] if column != "model"]
        statements.append(
            f"INSERT INTO laptop ({', '.join(columns)}) VALUES ({', '.join(values)}) "
            "ON DUPLICATE KEY UPDATE "
            + ", ".join(f"{column} = VALUES({column})" for column in update_columns)
            + ", updated_at = CURRENT_TIMESTAMP;"
        )
        statements.append(
            "SET @laptop_id := ("
            "SELECT id FROM laptop WHERE "
            f"source_url <=> {sql_value(item.get('source_url'))} "
            f"OR (brand_id = @brand_id AND model = {sql_value(model)}) "
            "ORDER BY id LIMIT 1);"
        )

        price = item.get("price")
        if price is not None:
            statements.append(
                "INSERT INTO price_record (laptop_id, price, source_name, source_url, crawled_at) "
                f"SELECT @laptop_id, {sql_value(price)}, {sql_value(item.get('source_name'))}, "
                f"{sql_value(item.get('source_url'))}, {sql_value(item.get('fetched_at'))} "
                "WHERE @laptop_id IS NOT NULL;"
            )
        self._write_ports(statements, item.get("ports") or [])
        statements.append("")

    def _write_cpu(self, statements: list[str], cpu: dict[str, Any]) -> None:
        model = cpu.get("model")
        if not model:
            statements.append("SET @cpu_id := NULL;")
            return
        statements.extend(
            [
                (
                    "INSERT INTO cpu_spec "
                    "(brand, model, core_count, thread_count, base_power_w) "
                    f"VALUES ({sql_value(cpu.get('brand'))}, {sql_value(model)}, {sql_value(cpu.get('core_count'))}, "
                    f"{sql_value(cpu.get('thread_count'))}, {sql_value(cpu.get('base_power_w'))}) "
                    "ON DUPLICATE KEY UPDATE brand = VALUES(brand), "
                    "core_count = COALESCE(VALUES(core_count), core_count), "
                    "thread_count = COALESCE(VALUES(thread_count), thread_count), "
                    "base_power_w = COALESCE(VALUES(base_power_w), base_power_w), "
                    "updated_at = CURRENT_TIMESTAMP;"
                ),
                f"SET @cpu_id := (SELECT id FROM cpu_spec WHERE model = {sql_value(model)} LIMIT 1);",
            ]
        )

    def _write_gpu(self, statements: list[str], gpu: dict[str, Any]) -> None:
        model = gpu.get("model")
        if not model:
            statements.append("SET @gpu_id := NULL;")
            return
        statements.extend(
            [
                (
                    "INSERT INTO gpu_spec "
                    "(brand, model, gpu_type, vram_gb) "
                    f"VALUES ({sql_value(gpu.get('brand'))}, {sql_value(model)}, {sql_value(gpu.get('gpu_type'))}, "
                    f"{sql_value(gpu.get('vram_gb'))}) "
                    "ON DUPLICATE KEY UPDATE brand = VALUES(brand), gpu_type = VALUES(gpu_type), "
                    "vram_gb = COALESCE(VALUES(vram_gb), vram_gb), "
                    "updated_at = CURRENT_TIMESTAMP;"
                ),
                f"SET @gpu_id := (SELECT id FROM gpu_spec WHERE model = {sql_value(model)} LIMIT 1);",
            ]
        )

    def _write_memory(self, statements: list[str], memory: dict[str, Any]) -> None:
        where = " AND ".join(
            [
                null_safe_equals("capacity_gb", memory.get("capacity_gb")),
                null_safe_equals("memory_type", memory.get("memory_type")),
                null_safe_equals("frequency_mhz", memory.get("frequency_mhz")),
            ]
        )
        statements.extend(
            [
                "INSERT INTO memory_spec (capacity_gb, memory_type, frequency_mhz) "
                f"SELECT {sql_value(memory.get('capacity_gb'))}, {sql_value(memory.get('memory_type'))}, "
                f"{sql_value(memory.get('frequency_mhz'))} "
                f"WHERE NOT EXISTS (SELECT 1 FROM memory_spec WHERE {where});",
                f"SET @memory_id := (SELECT id FROM memory_spec WHERE {where} LIMIT 1);",
            ]
        )

    def _write_storage(self, statements: list[str], storage: dict[str, Any]) -> None:
        where = " AND ".join(
            [
                null_safe_equals("capacity_gb", storage.get("capacity_gb")),
                null_safe_equals("storage_type", storage.get("storage_type")),
                null_safe_equals("interface_type", storage.get("interface_type")),
            ]
        )
        statements.extend(
            [
                "INSERT INTO storage_spec (capacity_gb, storage_type, interface_type) "
                f"SELECT {sql_value(storage.get('capacity_gb'))}, {sql_value(storage.get('storage_type'))}, "
                f"{sql_value(storage.get('interface_type'))} "
                f"WHERE NOT EXISTS (SELECT 1 FROM storage_spec WHERE {where});",
                f"SET @storage_id := (SELECT id FROM storage_spec WHERE {where} LIMIT 1);",
            ]
        )

    def _write_screen(self, statements: list[str], screen: dict[str, Any]) -> None:
        where = " AND ".join(
            [
                null_safe_equals("size_inch", screen.get("size_inch")),
                null_safe_equals("resolution", screen.get("resolution")),
                null_safe_equals("refresh_rate_hz", screen.get("refresh_rate_hz")),
                null_safe_equals("panel_type", screen.get("panel_type")),
                null_safe_equals("color_gamut_percent", screen.get("color_gamut_percent")),
                null_safe_equals("brightness_nit", screen.get("brightness_nit")),
                null_safe_equals("touch_support", screen.get("touch_support")),
            ]
        )
        statements.extend(
            [
                "INSERT INTO screen_spec "
                "(size_inch, resolution, refresh_rate_hz, panel_type, color_gamut_percent, brightness_nit, touch_support) "
                f"SELECT {sql_value(screen.get('size_inch'))}, {sql_value(screen.get('resolution'))}, "
                f"{sql_value(screen.get('refresh_rate_hz'))}, {sql_value(screen.get('panel_type'))}, "
                f"{sql_value(screen.get('color_gamut_percent'))}, {sql_value(screen.get('brightness_nit'))}, "
                f"{sql_value(screen.get('touch_support'))} "
                f"WHERE NOT EXISTS (SELECT 1 FROM screen_spec WHERE {where});",
                f"SET @screen_id := (SELECT id FROM screen_spec WHERE {where} LIMIT 1);",
            ]
        )

    def _write_battery(self, statements: list[str], battery: dict[str, Any]) -> None:
        where = " AND ".join(
            [
                null_safe_equals("capacity_wh", battery.get("capacity_wh")),
                null_safe_equals("charge_power", battery.get("charge_power")),
            ]
        )
        statements.extend(
            [
                "INSERT INTO battery_spec (capacity_wh, charge_power) "
                f"SELECT {sql_value(battery.get('capacity_wh'))}, {sql_value(battery.get('charge_power'))} "
                f"WHERE NOT EXISTS (SELECT 1 FROM battery_spec WHERE {where});",
                f"SET @battery_id := (SELECT id FROM battery_spec WHERE {where} LIMIT 1);",
            ]
        )

    def _write_wireless(self, statements: list[str], wireless: dict[str, Any]) -> None:
        where = " AND ".join(
            [
                null_safe_equals("wifi_version", wireless.get("wifi_version")),
                null_safe_equals("bluetooth_version", wireless.get("bluetooth_version")),
            ]
        )
        statements.extend(
            [
                "INSERT INTO wireless_spec (wifi_version, bluetooth_version) "
                f"SELECT {sql_value(wireless.get('wifi_version'))}, {sql_value(wireless.get('bluetooth_version'))} "
                f"WHERE NOT EXISTS (SELECT 1 FROM wireless_spec WHERE {where});",
                f"SET @wireless_id := (SELECT id FROM wireless_spec WHERE {where} LIMIT 1);",
            ]
        )

    def _write_ports(self, statements: list[str], ports: list[dict[str, Any]]) -> None:
        for port in ports:
            statements.extend(
                [
                    (
                        "INSERT INTO port_spec (port_name, port_type) "
                        f"VALUES ({sql_value(port.get('port_name'))}, {sql_value(port.get('port_type'))}) "
                        "ON DUPLICATE KEY UPDATE port_type = VALUES(port_type), updated_at = CURRENT_TIMESTAMP;"
                    ),
                    (
                        "SET @port_id := (SELECT id FROM port_spec "
                        f"WHERE port_name = {sql_value(port.get('port_name'))} LIMIT 1);"
                    ),
                    (
                        "INSERT INTO laptop_port (laptop_id, port_id, port_count) "
                        f"VALUES (@laptop_id, @port_id, {sql_value(port.get('port_count') or 1)}) "
                        "ON DUPLICATE KEY UPDATE port_count = VALUES(port_count);"
                    ),
                ]
            )

    def _write_logs(self, statements: list[str], items: list[dict[str, Any]], now: str) -> None:
        counts = Counter(item["source_name"] for item in items)
        for source_name, count in sorted(counts.items()):
            statements.append(
                "INSERT INTO crawl_log "
                "(source_id, fetched_count, inserted_count, updated_count, status, message, started_at, finished_at) "
                "SELECT id, "
                f"{count}, 0, 0, 'SQL_GENERATED', "
                f"{sql_value('SQL file generated; insert/update counts depend on MySQL execution result.')}, "
                f"{sql_value(now)}, {sql_value(now)} FROM crawl_source "
                f"WHERE source_name = {sql_value(source_name)} LIMIT 1;"
            )


class SafeOnlineMySqlSqlWriter(MySqlSqlWriter):
    """Generate SQL for online refresh without overwriting existing laptop attributes."""

    def write(self, items: list[dict[str, Any]], output_path: Path) -> None:
        statements: list[str] = []
        now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        statements.extend(
            [
                f"-- Generated at {now}",
                "-- Safe online update SQL: existing laptop rows are not updated.",
                "-- Existing laptops only receive new price_record rows.",
                "SET NAMES utf8mb4;",
                "START TRANSACTION;",
                "",
            ]
        )

        self._write_sources(statements, items)
        for item in items:
            self._write_laptop(statements, item)
        self._write_logs(statements, items, now)
        statements.extend(["COMMIT;", ""])
        output_path.write_text("\n".join(statements), encoding="utf-8")

    def _write_laptop(self, statements: list[str], item: dict[str, Any]) -> None:
        brand = item.get("brand") or "未知品牌"
        model = item.get("model")
        statements.extend(
            [
                f"-- safe online laptop: {brand} {model}",
                (
                    "INSERT INTO brand (name) "
                    f"VALUES ({sql_value(brand)}) "
                    "ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;"
                ),
                f"SET @brand_id := (SELECT id FROM brand WHERE name = {sql_value(brand)} LIMIT 1);",
                (
                    "SET @existing_laptop_id := ("
                    "SELECT id FROM laptop WHERE "
                    f"source_url <=> {sql_value(item.get('source_url'))} "
                    f"OR (brand_id = @brand_id AND model = {sql_value(model)}) "
                    "ORDER BY id LIMIT 1);"
                ),
            ]
        )

        self._write_cpu(statements, item.get("cpu") or {})
        self._write_gpu(statements, item.get("gpu") or {})
        self._write_memory(statements, item.get("memory") or {})
        self._write_storage(statements, item.get("storage") or {})
        self._write_screen(statements, item.get("screen") or {})
        self._write_battery(statements, item.get("battery") or {})
        self._write_wireless(statements, item.get("wireless") or {})

        columns = [
            "brand_id",
            "cpu_id",
            "gpu_id",
            "memory_id",
            "storage_id",
            "screen_id",
            "battery_id",
            "wireless_id",
            "model",
            "product_type",
            "usage_positioning",
            "weight_kg",
            "thickness_mm",
            "os",
            "color",
            "image_url",
            "source_url",
            "source_name",
            "raw_title",
            "release_date",
        ]
        values = [
            "@brand_id",
            "@cpu_id",
            "@gpu_id",
            "@memory_id",
            "@storage_id",
            "@screen_id",
            "@battery_id",
            "@wireless_id",
            sql_value(model),
            sql_value(item.get("product_type")),
            sql_value(item.get("usage_positioning")),
            sql_value(item.get("weight_kg")),
            sql_value(item.get("thickness_mm")),
            sql_value(item.get("os")),
            sql_value(item.get("color")),
            sql_value(item.get("image_url")),
            sql_value(item.get("source_url")),
            sql_value(item.get("source_name")),
            sql_value(item.get("raw_title")),
            sql_value(item.get("release_date")),
        ]
        statements.append(
            f"INSERT INTO laptop ({', '.join(columns)}) "
            f"SELECT {', '.join(values)} WHERE @existing_laptop_id IS NULL;"
        )
        statements.append(
            "SET @laptop_id := COALESCE(@existing_laptop_id, ("
            "SELECT id FROM laptop WHERE "
            f"source_url <=> {sql_value(item.get('source_url'))} "
            f"OR (brand_id = @brand_id AND model = {sql_value(model)}) "
            "ORDER BY id LIMIT 1));"
        )

        price = item.get("price")
        if price is not None:
            statements.append(
                "INSERT INTO price_record (laptop_id, price, source_name, source_url, crawled_at) "
                f"SELECT @laptop_id, {sql_value(price)}, {sql_value(item.get('source_name'))}, "
                f"{sql_value(item.get('source_url'))}, {sql_value(item.get('fetched_at'))} "
                "WHERE @laptop_id IS NOT NULL;"
            )
        self._write_ports(statements, item.get("ports") or [])
        statements.append("")

    def _write_cpu(self, statements: list[str], cpu: dict[str, Any]) -> None:
        model = cpu.get("model")
        if not model:
            statements.append("SET @cpu_id := NULL;")
            return
        statements.extend(
            [
                (
                    "INSERT INTO cpu_spec "
                    "(brand, model, core_count, thread_count, base_power_w) "
                    f"SELECT {sql_value(cpu.get('brand'))}, {sql_value(model)}, {sql_value(cpu.get('core_count'))}, "
                    f"{sql_value(cpu.get('thread_count'))}, {sql_value(cpu.get('base_power_w'))} "
                    f"WHERE @existing_laptop_id IS NULL AND NOT EXISTS "
                    f"(SELECT 1 FROM cpu_spec WHERE model = {sql_value(model)});"
                ),
                f"SET @cpu_id := (SELECT id FROM cpu_spec WHERE model = {sql_value(model)} LIMIT 1);",
            ]
        )

    def _write_gpu(self, statements: list[str], gpu: dict[str, Any]) -> None:
        model = gpu.get("model")
        if not model:
            statements.append("SET @gpu_id := NULL;")
            return
        statements.extend(
            [
                (
                    "INSERT INTO gpu_spec "
                    "(brand, model, gpu_type, vram_gb) "
                    f"SELECT {sql_value(gpu.get('brand'))}, {sql_value(model)}, {sql_value(gpu.get('gpu_type'))}, "
                    f"{sql_value(gpu.get('vram_gb'))} "
                    f"WHERE @existing_laptop_id IS NULL AND NOT EXISTS "
                    f"(SELECT 1 FROM gpu_spec WHERE model = {sql_value(model)});"
                ),
                f"SET @gpu_id := (SELECT id FROM gpu_spec WHERE model = {sql_value(model)} LIMIT 1);",
            ]
        )

    def _write_memory(self, statements: list[str], memory: dict[str, Any]) -> None:
        where = " AND ".join(
            [
                null_safe_equals("capacity_gb", memory.get("capacity_gb")),
                null_safe_equals("memory_type", memory.get("memory_type")),
                null_safe_equals("frequency_mhz", memory.get("frequency_mhz")),
            ]
        )
        statements.extend(
            [
                "INSERT INTO memory_spec (capacity_gb, memory_type, frequency_mhz) "
                f"SELECT {sql_value(memory.get('capacity_gb'))}, {sql_value(memory.get('memory_type'))}, "
                f"{sql_value(memory.get('frequency_mhz'))} "
                f"WHERE @existing_laptop_id IS NULL AND NOT EXISTS (SELECT 1 FROM memory_spec WHERE {where});",
                f"SET @memory_id := (SELECT id FROM memory_spec WHERE {where} LIMIT 1);",
            ]
        )

    def _write_storage(self, statements: list[str], storage: dict[str, Any]) -> None:
        where = " AND ".join(
            [
                null_safe_equals("capacity_gb", storage.get("capacity_gb")),
                null_safe_equals("storage_type", storage.get("storage_type")),
                null_safe_equals("interface_type", storage.get("interface_type")),
            ]
        )
        statements.extend(
            [
                "INSERT INTO storage_spec (capacity_gb, storage_type, interface_type) "
                f"SELECT {sql_value(storage.get('capacity_gb'))}, {sql_value(storage.get('storage_type'))}, "
                f"{sql_value(storage.get('interface_type'))} "
                f"WHERE @existing_laptop_id IS NULL AND NOT EXISTS (SELECT 1 FROM storage_spec WHERE {where});",
                f"SET @storage_id := (SELECT id FROM storage_spec WHERE {where} LIMIT 1);",
            ]
        )

    def _write_screen(self, statements: list[str], screen: dict[str, Any]) -> None:
        where = " AND ".join(
            [
                null_safe_equals("size_inch", screen.get("size_inch")),
                null_safe_equals("resolution", screen.get("resolution")),
                null_safe_equals("refresh_rate_hz", screen.get("refresh_rate_hz")),
                null_safe_equals("panel_type", screen.get("panel_type")),
                null_safe_equals("color_gamut_percent", screen.get("color_gamut_percent")),
                null_safe_equals("brightness_nit", screen.get("brightness_nit")),
                null_safe_equals("touch_support", screen.get("touch_support")),
            ]
        )
        statements.extend(
            [
                "INSERT INTO screen_spec "
                "(size_inch, resolution, refresh_rate_hz, panel_type, color_gamut_percent, brightness_nit, touch_support) "
                f"SELECT {sql_value(screen.get('size_inch'))}, {sql_value(screen.get('resolution'))}, "
                f"{sql_value(screen.get('refresh_rate_hz'))}, {sql_value(screen.get('panel_type'))}, "
                f"{sql_value(screen.get('color_gamut_percent'))}, {sql_value(screen.get('brightness_nit'))}, "
                f"{sql_value(screen.get('touch_support'))} "
                f"WHERE @existing_laptop_id IS NULL AND NOT EXISTS (SELECT 1 FROM screen_spec WHERE {where});",
                f"SET @screen_id := (SELECT id FROM screen_spec WHERE {where} LIMIT 1);",
            ]
        )

    def _write_battery(self, statements: list[str], battery: dict[str, Any]) -> None:
        where = " AND ".join(
            [
                null_safe_equals("capacity_wh", battery.get("capacity_wh")),
                null_safe_equals("charge_power", battery.get("charge_power")),
            ]
        )
        statements.extend(
            [
                "INSERT INTO battery_spec (capacity_wh, charge_power) "
                f"SELECT {sql_value(battery.get('capacity_wh'))}, {sql_value(battery.get('charge_power'))} "
                f"WHERE @existing_laptop_id IS NULL AND NOT EXISTS (SELECT 1 FROM battery_spec WHERE {where});",
                f"SET @battery_id := (SELECT id FROM battery_spec WHERE {where} LIMIT 1);",
            ]
        )

    def _write_wireless(self, statements: list[str], wireless: dict[str, Any]) -> None:
        where = " AND ".join(
            [
                null_safe_equals("wifi_version", wireless.get("wifi_version")),
                null_safe_equals("bluetooth_version", wireless.get("bluetooth_version")),
            ]
        )
        statements.extend(
            [
                "INSERT INTO wireless_spec (wifi_version, bluetooth_version) "
                f"SELECT {sql_value(wireless.get('wifi_version'))}, {sql_value(wireless.get('bluetooth_version'))} "
                f"WHERE @existing_laptop_id IS NULL AND NOT EXISTS (SELECT 1 FROM wireless_spec WHERE {where});",
                f"SET @wireless_id := (SELECT id FROM wireless_spec WHERE {where} LIMIT 1);",
            ]
        )

    def _write_ports(self, statements: list[str], ports: list[dict[str, Any]]) -> None:
        for port in ports:
            statements.extend(
                [
                    (
                        "INSERT INTO port_spec (port_name, port_type) "
                        f"SELECT {sql_value(port.get('port_name'))}, {sql_value(port.get('port_type'))} "
                        "WHERE @existing_laptop_id IS NULL AND NOT EXISTS "
                        f"(SELECT 1 FROM port_spec WHERE port_name = {sql_value(port.get('port_name'))});"
                    ),
                    (
                        "SET @port_id := (SELECT id FROM port_spec "
                        f"WHERE port_name = {sql_value(port.get('port_name'))} LIMIT 1);"
                    ),
                    (
                        "INSERT INTO laptop_port (laptop_id, port_id, port_count) "
                        f"SELECT @laptop_id, @port_id, {sql_value(port.get('port_count') or 1)} "
                        "WHERE @existing_laptop_id IS NULL AND @laptop_id IS NOT NULL AND @port_id IS NOT NULL "
                        "ON DUPLICATE KEY UPDATE port_count = port_count;"
                    ),
                ]
            )

    def _write_logs(self, statements: list[str], items: list[dict[str, Any]], now: str) -> None:
        counts = Counter(item["source_name"] for item in items)
        for source_name, count in sorted(counts.items()):
            statements.append(
                "INSERT INTO crawl_log "
                "(source_id, fetched_count, inserted_count, updated_count, status, message, started_at, finished_at) "
                "SELECT id, "
                f"{count}, 0, 0, 'SAFE_ONLINE_SQL_GENERATED', "
                f"{sql_value('Existing laptop attributes are preserved; prices are appended.')}, "
                f"{sql_value(now)}, {sql_value(now)} FROM crawl_source "
                f"WHERE source_name = {sql_value(source_name)} LIMIT 1;"
            )
