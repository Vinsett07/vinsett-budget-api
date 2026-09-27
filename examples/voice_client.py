
#!/usr/bin/env python3
"""Send audio to the API and save the generated MP3. Python 3; no dependencies."""
import argparse
import base64
import json
import mimetypes
import os
from pathlib import Path
import sys
import urllib.error
import urllib.request
import uuid

def main():
    parser = argparse.ArgumentParser(description="Enviar um comando de voz para a API.")
    parser.add_argument("file", type=Path)
    parser.add_argument("--output", type=Path, default=Path("response.mp3"))
    parser.add_argument("--request-key", type=uuid.UUID, help="Reutilize esta chave ao repetir o mesmo envio.")
    args = parser.parse_args()
    token = os.environ.get("APP_API_TOKEN")
    if not token:
        parser.error("Defina APP_API_TOKEN no ambiente.")
    if not args.file.is_file():
        parser.error("Arquivo de áudio não encontrado.")
    if args.file.stat().st_size > 10 * 1024 * 1024:
        parser.error("Áudio maior que 10 MB.")
    request_key = args.request_key or uuid.uuid4()
    print(f"Idempotency-Key: {request_key}", file=sys.stderr)
    suffix = args.file.suffix.lower()
    filename = "recording" + suffix
    mime = mimetypes.guess_type(filename)[0] or "application/octet-stream"
    boundary = "budget-" + uuid.uuid4().hex
    prefix = (
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{filename}\"\r\n"
        f"Content-Type: {mime}\r\n\r\n"
    ).encode()
    body = prefix + args.file.read_bytes() + f"\r\n--{boundary}--\r\n".encode()
    base_url = os.environ.get("API_BASE_URL", "http://localhost:8080").rstrip("/")
    request = urllib.request.Request(base_url + "/api/v1/assistant/voice", data=body, method="POST", headers={
        "Authorization": "Bearer " + token,
        "Idempotency-Key": str(request_key),
        "Content-Type": "multipart/form-data; boundary=" + boundary,
    })
    try:
        with urllib.request.urlopen(request, timeout=480) as response:
            result = json.load(response)
    except urllib.error.HTTPError as error:
        print(error.read().decode("utf-8", errors="replace"))
        print("Confira as ações registradas. Para consultar o mesmo envio, reutilize a chave acima.", file=sys.stderr)
        return 1
    except urllib.error.URLError as error:
        print(f"Falha de conexão: {error.reason}. Reutilize a mesma chave ao tentar novamente.", file=sys.stderr)
        return 1
    audio = result.get("audio")
    if audio:
        args.output.write_bytes(base64.b64decode(audio["base64"], validate=True))
        audio["base64"] = f"[salvo em {args.output}]"
    print(json.dumps(result, ensure_ascii=False, indent=2))
    if audio:
        print("A voz do arquivo foi gerada por IA.", file=sys.stderr)
    return 0

if __name__ == "__main__":
    sys.exit(main())
