FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    TZ=Asia/Shanghai

WORKDIR /app

# 无生产依赖（现场签到走主系统，侧车不再生成二维码，qrcode/Pillow 已移除）
# 测试依赖不进镜像：pytest 在宿主机/CI 用 gift-system/.venv 跑

COPY src ./src
COPY templates ./templates
COPY static ./static
COPY scripts ./scripts
COPY run.py ./

RUN mkdir -p /app/data

EXPOSE 8848

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD python -c "import urllib.request;urllib.request.urlopen('http://127.0.0.1:8848/healthz',timeout=2)" || exit 1

CMD ["python", "run.py"]
