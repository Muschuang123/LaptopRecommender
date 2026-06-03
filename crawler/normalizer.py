from __future__ import annotations

import html
import re
from typing import Any

from .models import LaptopRawItem


BRAND_ALIASES = [
    ("ThinkPad", "ThinkPad"),
    ("Alienware", "Alienware"),
    ("MacBook", "Apple"),
    ("Apple", "Apple"),
    ("苹果", "Apple"),
    ("lenovo", "联想"),
    ("联想", "联想"),
    ("来酷", "来酷"),
    ("LeCool", "来酷"),
    ("惠普", "惠普"),
    ("HP", "惠普"),
    ("戴尔", "戴尔"),
    ("DELL", "戴尔"),
    ("华为", "华为"),
    ("HUAWEI", "华为"),
    ("荣耀", "荣耀"),
    ("HONOR", "荣耀"),
    ("ROG", "ROG"),
    ("华硕", "华硕"),
    ("ASUS", "华硕"),
    ("机械革命", "机械革命"),
    ("MECHREVO", "机械革命"),
    ("Acer", "Acer宏碁"),
    ("宏碁", "Acer宏碁"),
    ("微星", "微星"),
    ("MSI", "微星"),
    ("小米", "小米"),
    ("Redmi", "Redmi"),
    ("雷神", "雷神"),
    ("神舟", "神舟"),
    ("Microsoft", "微软"),
    ("微软", "微软"),
    ("LG", "LG"),
    ("Samsung", "三星"),
    ("三星", "三星"),
]


def strip_html(value: str | None) -> str:
    if not value:
        return ""
    text = re.sub(r"<script\b.*?</script>", " ", value, flags=re.I | re.S)
    text = re.sub(r"<style\b.*?</style>", " ", text, flags=re.I | re.S)
    text = re.sub(r"<[^>]+>", " ", text)
    text = html.unescape(text).replace("\xa0", " ")
    text = re.sub(r"纠错", " ", text)
    text = re.sub(r"更多\S*?>", " ", text)
    text = re.sub(r"\s+", " ", text)
    return text.strip(" >\t\r\n")


def clean_value(value: str | None) -> str | None:
    text = strip_html(value)
    return text or None


def parse_price(value: str | None) -> float | None:
    if not value:
        return None
    text = value.replace(",", "")
    if "暂无" in text or "概念产品" in text:
        return None
    wan = re.search(r"(\d+(?:\.\d+)?)\s*万", text)
    if wan:
        return round(float(wan.group(1)) * 10000, 2)
    numbers = re.findall(r"\d+(?:\.\d+)?", text)
    if not numbers:
        return None
    return float(numbers[0])


def parse_first_number(value: str | None) -> float | None:
    if not value:
        return None
    match = re.search(r"\d+(?:\.\d+)?", value)
    return float(match.group(0)) if match else None


def parse_int(value: str | None) -> int | None:
    number = parse_first_number(value)
    return int(number) if number is not None else None


def parse_memory_capacity_gb(value: str | None) -> int | None:
    if not value:
        return None
    match = re.search(r"(\d+)\s*GB", value, flags=re.I)
    if match:
        return int(match.group(1))
    match = re.search(r"(\d+)\s*G(?![A-Za-z])", value, flags=re.I)
    if match:
        return int(match.group(1))
    return None


def parse_storage_capacity_gb(value: str | None) -> int | None:
    if not value:
        return None
    text = value.replace("＋", "+")
    matches = re.findall(r"(\d+(?:\.\d+)?)\s*(TB|GB|T|G)", text, flags=re.I)
    if not matches:
        return None
    total = 0.0
    for amount, unit in matches:
        unit = unit.upper()
        number = float(amount)
        total += number * 1024 if unit.startswith("T") else number
    return int(total)


def parse_frequency_mhz(value: str | None) -> int | None:
    if not value:
        return None
    match = re.search(r"(\d{3,5})\s*MHz", value, flags=re.I)
    return int(match.group(1)) if match else None


def parse_release_date(value: str | None) -> str | None:
    if not value:
        return None
    match = re.search(r"(\d{4})年\s*(\d{1,2})月", value)
    if match:
        return f"{match.group(1)}-{int(match.group(2)):02d}-01"
    match = re.search(r"(\d{4})年", value)
    if match:
        return f"{match.group(1)}-01-01"
    return None


def parse_weight_kg(value: str | None) -> float | None:
    if not value:
        return None
    match = re.search(r"(\d+(?:\.\d+)?)\s*(?:Kg|KG|kg|千克)", value)
    if match:
        return float(match.group(1))
    gram = re.search(r"(\d{3,5})\s*g", value, flags=re.I)
    if gram:
        return round(float(gram.group(1)) / 1000, 3)
    return None


def parse_thickness_mm(value: str | None) -> float | None:
    if not value:
        return None
    match = re.search(r"(\d+(?:\.\d+)?)\s*mm", value, flags=re.I)
    return float(match.group(1)) if match else None


def parse_cpu_brand(model: str | None) -> str | None:
    if not model:
        return None
    text = model.lower()
    if "intel" in text or "酷睿" in model or "赛扬" in model:
        return "Intel"
    if "amd" in text or "ryzen" in text or "锐龙" in model:
        return "AMD"
    if "apple" in text or re.search(r"\bM[1-9]\b", model):
        return "Apple"
    if "qualcomm" in text or "snapdragon" in text or "骁龙" in model:
        return "Qualcomm"
    if "麒麟" in model or "kirin" in text:
        return "Huawei"
    return None


def parse_count_token(value: str | None) -> int | None:
    if not value:
        return None
    digit_match = re.search(r"\d+", value)
    if digit_match:
        return int(digit_match.group(0))
    numerals = {
        "一": 1,
        "二": 2,
        "两": 2,
        "三": 3,
        "四": 4,
        "五": 5,
        "六": 6,
        "七": 7,
        "八": 8,
        "九": 9,
    }
    text = value.strip()
    if text == "十":
        return 10
    if text.startswith("十"):
        return 10 + numerals.get(text[1:2], 0)
    if "十" in text:
        left, right = text.split("十", 1)
        return numerals.get(left[:1], 0) * 10 + numerals.get(right[:1], 0)
    return numerals.get(text[:1])


def parse_cpu_cores_threads(value: str | None) -> tuple[int | None, int | None]:
    if not value:
        return None, None
    match = re.search(r"(\d+)\s*核心\s*/\s*(\d+)\s*线程", value)
    if match:
        return int(match.group(1)), int(match.group(2))
    core = re.search(r"([一二两三四五六七八九十\d]+)\s*核心", value)
    thread = re.search(r"([一二两三四五六七八九十\d]+)\s*线程", value)
    return (
        parse_count_token(core.group(1)) if core else None,
        parse_count_token(thread.group(1)) if thread else None,
    )


def normalize_gpu_model(value: str | None) -> str | None:
    if not value:
        return None
    text = clean_value(value) or ""
    text = re.sub(r"RTX\s*(\d{4})", r"RTX \1", text, flags=re.I)
    text = re.sub(r"GTX\s*(\d{3,4})", r"GTX \1", text, flags=re.I)
    return text.strip() or None


def extract_gpu_from_title(title: str | None, gpu_type_text: str | None) -> str | None:
    source = f"{title or ''} {gpu_type_text or ''}"
    match = re.search(r"(?:NVIDIA\s+GeForce\s+)?(RTX|GTX)\s*-?(\d{3,4}\s*(?:Ti|SUPER)?)", source, flags=re.I)
    if match:
        suffix = re.sub(r"\s+", "", match.group(2))
        return f"NVIDIA GeForce {match.group(1).upper()} {suffix}"
    match = re.search(r"(Radeon\s+(?:RX\s*)?\d{3,4}\w*)", source, flags=re.I)
    if match:
        return match.group(1)
    if gpu_type_text and any(token in gpu_type_text for token in ["集成", "核芯", "核心显卡"]):
        return "Integrated Graphics"
    return None


def infer_gpu_type(model: str | None, gpu_type_text: str | None) -> str | None:
    text = f"{model or ''} {gpu_type_text or ''}".lower()
    if any(token in text for token in ["独立", "rtx", "gtx", "radeon rx", "firepro"]):
        return "discrete"
    if any(token in text for token in ["集成", "核芯", "核心显卡", "integrated", "iris", "uhd"]):
        return "integrated"
    return None


def parse_vram_gb(value: str | None) -> int | None:
    if not value:
        return None
    match = re.search(r"(\d+)\s*GB", value, flags=re.I)
    return int(match.group(1)) if match else None


def parse_screen_size(value: str | None) -> float | None:
    if not value:
        return None
    match = re.search(r"(\d+(?:\.\d+)?)\s*英寸", value)
    return float(match.group(1)) if match else None


def parse_resolution(value: str | None) -> str | None:
    if not value:
        return None
    match = re.search(r"(\d{3,4})\s*[xX×*]\s*(\d{3,4})", value)
    if match:
        return f"{match.group(1)}x{match.group(2)}"
    match = re.search(r"(\d(?:\.\d)?K)", value, flags=re.I)
    return match.group(1).upper() if match else None


def parse_refresh_rate(value: str | None) -> int | None:
    if not value:
        return None
    match = re.search(r"(\d{2,3})\s*Hz", value, flags=re.I)
    return int(match.group(1)) if match else None


def parse_percent(value: str | None) -> int | None:
    if not value:
        return None
    match = re.search(r"(\d{2,3})\s*%", value)
    return int(match.group(1)) if match else None


def parse_brightness(value: str | None) -> int | None:
    if not value:
        return None
    match = re.search(r"(\d{3,4})\s*(?:nits|nit|尼特)", value, flags=re.I)
    return int(match.group(1)) if match else None


def parse_touch(value: str | None) -> int | None:
    if not value:
        return None
    if "不支持" in value or "无" == value:
        return 0
    if "支持" in value or "触控" in value:
        return 1
    return None


def parse_battery_wh(value: str | None) -> int | None:
    if not value:
        return None
    match = re.search(r"(\d+(?:\.\d+)?)\s*(?:瓦时|Wh|WH)", value)
    return int(float(match.group(1))) if match else None


def extract_brand(title: str | None, explicit_brand: str | None = None) -> str | None:
    source = f"{explicit_brand or ''} {title or ''}"
    for alias, canonical in BRAND_ALIASES:
        if alias.lower() in source.lower():
            return canonical
    return explicit_brand or None


def clean_model(title: str | None, brand: str | None) -> str | None:
    text = clean_value(title)
    if not text:
        return None
    remove_words = [alias for alias, canonical in BRAND_ALIASES if canonical == brand]
    for word in remove_words:
        text = re.sub(re.escape(word), "", text, flags=re.I)
    text = re.sub(r"^[/\\（）()【】\s]+", "", text)
    text = re.sub(r"\s+", " ", text).strip(" -_/，,")
    return text or None


def first_present(*values: str | None) -> str | None:
    for value in values:
        cleaned = clean_value(value)
        if cleaned:
            return cleaned
    return None


def extract_ports(specs: dict[str, str]) -> list[dict[str, Any]]:
    port_text = "，".join(
        value
        for key, value in specs.items()
        if "接口" in key or key in {"读卡器", "数据接口", "视频接口", "音频接口"}
    )
    if not port_text:
        return []

    patterns = [
        ("HDMI", "video", r"(?:(\d+)\s*[×x]\s*)?(HDMI[\w\.\-]*)"),
        ("RJ45", "network", r"(?:(\d+)\s*[×x]\s*)?(RJ45|RJ-45|以太网口|网络接口)"),
        ("Thunderbolt", "data", r"(?:(\d+)\s*[×x]\s*)?(Thunderbolt\s*\d|雷电\s*\d)"),
        ("USB-C", "data", r"(?:(\d+)\s*[×x]\s*)?(USB\s*Type-?C|Type-?C|USB-C)"),
        ("USB-A", "data", r"(?:(\d+)\s*[×x]\s*)?(USB\s*Type-?A|USB-A|USB3\.\d|USB2\.0)"),
        ("SD", "card", r"(?:(\d+)\s*[×x]\s*)?(SD读卡器|SD卡槽|microSD)"),
        ("Audio", "audio", r"(?:(\d+)\s*[×x]\s*)?(3\.5mm|耳机/麦克风|耳麦)"),
        ("Power", "power", r"(?:(\d+)\s*[×x]\s*)?(电源接口|DC电源)"),
    ]
    result: dict[tuple[str, str], int] = {}
    for fallback_name, port_type, pattern in patterns:
        for count_text, matched_name in re.findall(pattern, port_text, flags=re.I):
            count = int(count_text) if count_text else 1
            name = matched_name.strip() if matched_name else fallback_name
            if fallback_name in {"USB-C", "USB-A"}:
                name = fallback_name
            key = (name, port_type)
            result[key] = max(result.get(key, 0), count)
    return [
        {"port_name": name, "port_type": port_type, "port_count": count}
        for (name, port_type), count in sorted(result.items())
    ]


class LaptopDataNormalizer:
    def normalize(self, raw: LaptopRawItem) -> dict[str, Any]:
        specs = {clean_value(k) or k: clean_value(v) or "" for k, v in raw.specs.items()}
        title = first_present(raw.title, specs.get("产品型号"))
        brand = extract_brand(title, raw.brand)
        model = first_present(raw.model, clean_model(title, brand), specs.get("产品型号"))

        memory_text = first_present(specs.get("内存容量"), title)
        storage_text = first_present(specs.get("硬盘容量"), title)
        cpu_model = first_present(specs.get("CPU型号"), specs.get("CPU系列"))
        cpu_cores, cpu_threads = parse_cpu_cores_threads(specs.get("核心/线程数"))
        gpu_type_text = specs.get("显卡类型")
        gpu_model = normalize_gpu_model(first_present(specs.get("显卡芯片"), specs.get("显卡型号")))
        gpu_model = gpu_model or extract_gpu_from_title(title, gpu_type_text)

        return {
            "source_name": raw.source_name,
            "source_url": raw.source_url,
            "title": title,
            "raw_title": raw.title,
            "brand": brand or "未知品牌",
            "model": model or title or raw.source_url,
            "price": raw.price if raw.price is not None else parse_price(first_present(raw.price_text, specs.get("电商报价"))),
            "price_text": first_present(raw.price_text, specs.get("电商报价")),
            "image_url": raw.image_url,
            "ranking": raw.ranking,
            "comment_count_text": raw.comment_count_text,
            "fetched_at": raw.fetched_at,
            "product_type": specs.get("产品类型"),
            "usage_positioning": specs.get("产品定位"),
            "os": specs.get("操作系统"),
            "color": specs.get("外壳描述"),
            "release_date": parse_release_date(specs.get("上市时间")),
            "weight_kg": parse_weight_kg(specs.get("笔记本重量")),
            "thickness_mm": parse_thickness_mm(specs.get("厚度")),
            "cpu": {
                "brand": parse_cpu_brand(cpu_model),
                "model": cpu_model,
                "core_count": cpu_cores,
                "thread_count": cpu_threads,
                "base_power_w": parse_first_number(
                    specs.get("CPU功耗") or specs.get("处理器功耗") or specs.get("功耗")
                ),
            },
            "gpu": {
                "brand": "NVIDIA" if gpu_model and "RTX" in gpu_model.upper() else None,
                "model": gpu_model,
                "gpu_type": infer_gpu_type(gpu_model, gpu_type_text),
                "vram_gb": parse_vram_gb(specs.get("显存容量")),
            },
            "memory": {
                "capacity_gb": parse_memory_capacity_gb(memory_text),
                "memory_type": first_present(specs.get("内存类型")),
                "frequency_mhz": parse_frequency_mhz(specs.get("内存类型")),
            },
            "storage": {
                "capacity_gb": parse_storage_capacity_gb(storage_text),
                "storage_type": "SSD" if "SSD" in first_present(specs.get("硬盘描述"), storage_text, "") else None,
                "interface_type": first_present(specs.get("硬盘描述")),
            },
            "screen": {
                "size_inch": parse_screen_size(specs.get("屏幕尺寸")),
                "resolution": parse_resolution(specs.get("屏幕分辨率")),
                "refresh_rate_hz": parse_refresh_rate(specs.get("屏幕刷新率")),
                "panel_type": first_present(specs.get("屏幕类型")),
                "color_gamut_percent": parse_percent(first_present(specs.get("色域"), specs.get("Adobe RGB色域"))),
                "brightness_nit": parse_brightness(specs.get("亮度")),
                "touch_support": parse_touch(specs.get("触控屏")),
            },
            "battery": {
                "capacity_wh": parse_battery_wh(specs.get("电池类型")),
                "charge_power": first_present(specs.get("电源适配器")),
            },
            "wireless": {
                "wifi_version": first_present(specs.get("无线网卡")),
                "bluetooth_version": first_present(specs.get("蓝牙")),
            },
            "ports": extract_ports(specs),
            "raw_specs": specs,
        }
