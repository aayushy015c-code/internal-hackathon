FROM python:3.12-slim
WORKDIR /app

# install libraries first, so Docker can reuse this step when only our code changes
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY *.py ./

# don't run as root. /models keeps the downloaded whisper model between restarts.
RUN useradd --create-home app && mkdir /models && chown app /models
USER app
ENV HF_HOME=/models \
    PYTHONUNBUFFERED=1
EXPOSE 8000
HEALTHCHECK --interval=10s --timeout=3s --start-period=120s --retries=10 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health')"
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
