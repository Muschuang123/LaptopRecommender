import argparse
import unittest

from crawler.online_update import base_mysql_command, parse_jdbc_mysql_url, quote_mysql_identifier


class OnlineUpdateConfigTest(unittest.TestCase):
    def test_parse_jdbc_mysql_url(self):
        result = parse_jdbc_mysql_url(
            "jdbc:mysql://127.0.0.1:3307/laptop_rec?useUnicode=true&characterEncoding=utf8"
        )

        self.assertEqual(result["host"], "127.0.0.1")
        self.assertEqual(result["port"], "3307")
        self.assertEqual(result["database"], "laptop_rec")

    def test_base_mysql_command_uses_resolved_args(self):
        args = argparse.Namespace(
            mysql_bin="mysql",
            db_host="127.0.0.1",
            db_port="3306",
            db_user="root",
            db_name="laptop_rec",
        )

        command = base_mysql_command(args)

        self.assertEqual(
            command,
            [
                "mysql",
                "--default-character-set=utf8mb4",
                "-h",
                "127.0.0.1",
                "-P",
                "3306",
                "-u",
                "root",
            ],
        )

    def test_quote_mysql_identifier(self):
        self.assertEqual(quote_mysql_identifier("laptop_rec"), "`laptop_rec`")
        self.assertEqual(quote_mysql_identifier("lap`top"), "`lap``top`")


if __name__ == "__main__":
    unittest.main()
