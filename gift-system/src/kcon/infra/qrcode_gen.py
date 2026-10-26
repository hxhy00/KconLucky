"""二维码生成适配器：带 KCon 主视觉边框。"""
import base64
import io
from abc import ABC, abstractmethod
from pathlib import Path
from typing import Optional
import qrcode
from PIL import Image, ImageDraw, ImageFont
from qrcode.constants import ERROR_CORRECT_H
# 配色
BG = (11, 14, 20)
FG = (0, 229, 255)
ACCENT = (255, 45, 149)
TEXT = (235, 240, 245)
MUTED = (150, 160, 175)
FONT_CANDIDATES = [
    "/System/Library/Fonts/PingFang.ttc",
    "/System/Library/Fonts/STHeiti Medium.ttc",
    "/System/Library/Fonts/Hiragino Sans GB.ttc",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
]
def _load_font(size: int):
    """加载中文字体，找不到时回退默认字体。"""
    for path in FONT_CANDIDATES:
        if Path(path).exists():
            try:
                return ImageFont.truetype(path, size)
            except (OSError, ValueError):
                continue
    return ImageFont.load_default()
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
        return "data:image/png;base64," + base64.b64encode(png).decode("ascii")
class PillowQrCodeGenerator(QrCodeGenerator):
    """基于 Pillow 的二维码生成器。"""
    def __init__(self, qr_box: int = 600, pad: int = 40, header_h: int = 120):
        self.qr_box = qr_box
        self.pad = pad
        self.header_h = header_h
    def _make_qr(self, content: str) -> Image.Image:
        """生成纯二维码图片。"""
        qr = qrcode.QRCode(version=None, error_correction=ERROR_CORRECT_H,
                           box_size=10, border=2)
        qr.add_data(content)
        qr.make(fit=True)
        img = qr.make_image(fill_color="black", back_color="white").convert("RGB")
        return img.resize((self.qr_box, self.qr_box), Image.LANCZOS)
    def generate_png(self, content: str, title: str,
                     subtitle: str = "", footer: str = "") -> bytes:
        """生成带边框的二维码 PNG。"""
        footer_h = 96 if footer else 40
        width = self.qr_box + self.pad * 2
        height = self.header_h + self.qr_box + self.pad * 2 + footer_h
        canvas = Image.new("RGB", (width, height), BG)
        draw = ImageDraw.Draw(canvas)
        # 顶部装饰条
        draw.rectangle([0, 0, width, 6], fill=FG)
        draw.rectangle([0, 6, width // 3, 10], fill=ACCENT)
        # 标题区
        font_logo = _load_font(52)
        font_title = _load_font(26)
        font_sub = _load_font(20)
        font_foot = _load_font(18)
        draw.text((self.pad, 28), "KCon", font=font_logo, fill=FG)
        draw.text((self.pad + 132, 44), "奕智", font=font_title, fill=ACCENT)
        draw.text((self.pad + 132, 78), "KCon2026", font=font_sub, fill=MUTED)
        # 二维码白底
        qr_img = self._make_qr(content)
        qr_y = self.header_h
        draw.rectangle([self.pad - 8, qr_y - 8,
                        self.pad + self.qr_box + 8, qr_y + self.qr_box + 8],
                       fill=(255, 255, 255))
        canvas.paste(qr_img, (self.pad, qr_y))
        # 底部文字
        text_y = qr_y + self.qr_box + 24
        draw.text((self.pad, text_y), title, font=font_title, fill=TEXT)
        if subtitle:
            draw.text((self.pad, text_y + 34), subtitle, font=font_sub, fill=MUTED)
        if footer:
            draw.text((self.pad, height - 44), footer, font=font_foot, fill=MUTED)
        buf = io.BytesIO()
        canvas.save(buf, format="PNG", optimize=True)
        return buf.getvalue()
class FakeQrCodeGenerator(QrCodeGenerator):
    """测试用假二维码生成器（不依赖 Pillow 渲染）。"""
    def __init__(self):
        self.calls = []
    def generate_png(self, content: str, title: str,
                     subtitle: str = "", footer: str = "") -> bytes:
        self.calls.append({"content": content, "title": title,
                           "subtitle": subtitle, "footer": footer})
        return b"FAKE_PNG_DATA"