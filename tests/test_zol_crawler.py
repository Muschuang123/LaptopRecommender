import unittest

from crawler.models import LaptopRawItem
from crawler.sources.zol import RankPage, ZolNotebookRankCrawler


class FakeClient:
    def absolute_url(self, base_url: str, href: str | None) -> str | None:
        if href is None:
            return None
        if href.startswith("http"):
            return href
        return "https://wap.zol.com.cn" + href

    def get_text(self, url: str) -> str:
        return "<html></html>"


class ZolNotebookRankCrawlerTest(unittest.TestCase):
    def test_parses_brand_from_brand_directory(self):
        crawler = ZolNotebookRankCrawler(FakeClient())
        html = """
        <a href="/top/notebook/gigabyte/">47 GIGABYTE（技嘉） ￥7999-￥33399(共69款)</a>
        <a href="/top/notebook/H3C/">48 H3C ￥6599-￥13999(共14款)</a>
        <a href="/top/notebook/">笔记本电脑排行榜</a>
        """

        pages = crawler.parse_rank_directory_page(html, "https://wap.zol.com.cn/top/notebook/brand/")

        self.assertEqual([page.brand for page in pages], ["GIGABYTE（技嘉）", "H3C"])

    def test_collect_rank_items_merges_duplicate_brand_context(self):
        crawler = ZolNotebookRankCrawler(FakeClient())
        crawler.robots.assert_can_fetch = lambda url: None
        calls = []

        def parse_rank_page(html_text, page_url, rank_brand=None):
            calls.append((page_url, rank_brand))
            return [
                LaptopRawItem(
                    source_name="ZOL",
                    source_url="https://detail.zol.com.cn/1/1/param.shtml",
                    title="AERO 15",
                    brand=rank_brand,
                )
            ]

        crawler.parse_rank_page = parse_rank_page
        pages = [
            RankPage(url="https://wap.zol.com.cn/top/notebook/hot.html", title="热门"),
            RankPage(url="https://wap.zol.com.cn/top/notebook/gigabyte/", title="技嘉", brand="GIGABYTE（技嘉）"),
        ]

        items = crawler.collect_rank_items(pages)

        self.assertEqual(len(items), 1)
        self.assertEqual(items[0].brand, "GIGABYTE（技嘉）")
        self.assertEqual(calls[1][1], "GIGABYTE（技嘉）")


if __name__ == "__main__":
    unittest.main()
