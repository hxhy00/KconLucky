FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    TZ=Asia/Shanghai

WORKDIR /app

# 依赖很少（qrcode + Pillow），使用国内源加速
COPY requirements.txt ./
RUN pip install --no-cache-dir -i https://pypi.tuna.tsinghua.edu.cn/simple -r requirements.txt

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
