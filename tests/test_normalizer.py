import unittest

from crawler.models import LaptopRawItem
from crawler.normalizer import LaptopDataNormalizer, extract_brand


class LaptopDataNormalizerTest(unittest.TestCase):
    def test_normalizes_core_fields(self):
        item = LaptopRawItem(
            source_name="ZOL",
            source_url="https://detail.zol.com.cn/2168/2167146/param.shtml",
            title="联想拯救者Y7000P 2026 酷睿Ultra 7 251HX/16GB/1TB/RTX5060",
            price_text="￥9999",
            specs={
                "CPU型号": "Intel 酷睿 Ultra 7 251HX",
                "核心/线程数": "18核心/18线程",
                "功耗": "45W",
                "内存容量": "16GB（16GB×1）",
                "内存类型": "DDR5 6400MHz",
                "硬盘容量": "1TB",
                "硬盘描述": "SSD固态硬盘（PCIe5.0）",
                "屏幕尺寸": "16英寸",
                "屏幕分辨率": "2560x1600",
                "屏幕刷新率": "240Hz",
                "显卡类型": "发烧级独立显卡",
                "显卡芯片": "NVIDIA GeForce RTX5060",
                "显存容量": "8GB",
                "电池类型": "锂电池，80瓦时",
                "笔记本重量": "2.35Kg",
            },
        )

        normalized = LaptopDataNormalizer().normalize(item)

        self.assertEqual(normalized["brand"], "联想")
        self.assertEqual(normalized["price"], 9999)
        self.assertEqual(normalized["cpu"]["core_count"], 18)
        self.assertEqual(normalized["cpu"]["base_power_w"], 45)
        self.assertEqual(normalized["memory"]["capacity_gb"], 16)
        self.assertEqual(normalized["storage"]["capacity_gb"], 1024)
        self.assertEqual(normalized["screen"]["refresh_rate_hz"], 240)
        self.assertEqual(normalized["gpu"]["gpu_type"], "discrete")
        self.assertEqual(normalized["battery"]["capacity_wh"], 80)
        self.assertEqual(normalized["weight_kg"], 2.35)

    def test_parses_chinese_core_thread_count(self):
        item = LaptopRawItem(
            source_name="ZOL",
            source_url="https://detail.zol.com.cn/2168/2167540/param.shtml",
            title="联想拯救者R7000X 2026",
            specs={
                "CPU型号": "AMD Ryzen 7 H 255",
                "核心/线程数": "八核心/十六线程",
            },
        )

        normalized = LaptopDataNormalizer().normalize(item)

        self.assertEqual(normalized["cpu"]["core_count"], 8)
        self.assertEqual(normalized["cpu"]["thread_count"], 16)

    def test_extracts_long_tail_brands_from_title(self):
        cases = [
            ("ThinkBook 16+(21CY0001CD)", "ThinkBook"),
            ("七彩虹将星 X17 AT(i5/RTX4050)", "七彩虹"),
            ("Colorful 隐星 P16", "七彩虹"),
            ("技嘉AERO 15-Classic", "GIGABYTE（技嘉）"),
            ("GIGABYTE AERO 16", "GIGABYTE（技嘉）"),
            ("AORUS 17 2022", "GIGABYTE（技嘉）"),
            ("H3C Book Ultra 14T", "H3C"),
            ("努比亚红魔游戏本 16 PRO", "努比亚"),
            ("VAIO SX12 2023", "VAIO"),
            ("松下CF-SZ6", "Panasonic（松下）"),
            ("真我笔记本 Air", "realme"),
        ]

        for title, brand in cases:
            with self.subTest(title=title):
                self.assertEqual(extract_brand(title), brand)

    def test_explicit_brand_is_canonicalized(self):
        item = LaptopRawItem(
            source_name="ZOL",
            source_url="https://detail.zol.com.cn/1/1/param.shtml",
            title="AERO 15-Classic-YA",
            brand="gigabyte",
        )

        normalized = LaptopDataNormalizer().normalize(item)

        self.assertEqual(normalized["brand"], "GIGABYTE（技嘉）")


if __name__ == "__main__":
    unittest.main()
