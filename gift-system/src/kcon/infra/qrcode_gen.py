"""二维码生成适配器。

现场签到已统一走主系统（按手机号），赠票侧车不再需要扫码场景，
二维码生成器退化为空实现：邮件模板对空 qr 不渲染二维码块。
保留接口与 Fake 实现以兼容既有测试与未来恢复扫码的可能。
"""
from abc import ABC, abstractmethod
from typing import Optional
class QrCodeGenerator(ABC):
    """二维码生成接口。"""
    @abstractmethod
    def generate_png(self, content: str, title: str,
                     subtitle: str = "", footer: str = "") -> bytes:
        """生成二维码 PNG 字节。"""
    def generate_datauri(self, content: str, title: str,
                         subtitle: str = "", footer: str = "") -> str:
        """生成 data URI（可直接嵌入 HTML）。"""
        png = self.generate_png(content, title, subtitle, footer)
        if not png:
            return ""
        import base64
        return "data:image/png;base64," + base64.b64encode(png).decode("ascii")
class NullQrCodeGenerator(QrCodeGenerator):
    """空实现：不生成二维码（现场签到走主系统，无扫码场景）。"""
    def generate_png(self, content: str, title: str,
                     subtitle: str = "", footer: str = "") -> bytes:
        return b""
class FakeQrCodeGenerator(QrCodeGenerator):
    """测试用假二维码生成器（不依赖 Pillow 渲染）。"""
    def __init__(self):
        self.calls = []
    def generate_png(self, content: str, title: str,
                     subtitle: str = "", footer: str = "") -> bytes:
        self.calls.append({"content": content, "title": title,
                           "subtitle": subtitle, "footer": footer})
        return b"FAKE_PNG_DATA"
