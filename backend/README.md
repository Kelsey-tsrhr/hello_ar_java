# Indoor navigation backend

This is a small, deployable compatibility backend for the Android and iOS
clients. It is useful for proving the full network path and AR route rendering:
the localization endpoint accepts a JPEG and returns the configured scene map.

It does **not** identify a room from image content. Replace the demo scene data
and implement a real visual-localization pipeline before using it for actual
indoor navigation.

## Run locally

Requires Python 3.10+ and FastAPI/uvicorn:

```powershell
cd backend
py -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn server:app --host 0.0.0.0 --port 8100
```

Check the service:

```powershell
curl.exe http://127.0.0.1:8100/health
curl.exe http://127.0.0.1:8100/api/v1/visualization/map
```

The phone cannot use `127.0.0.1` to reach a PC. For a local phone test, use
the PC's LAN address and keep the phone on the same Wi-Fi. For iOS production,
deploy behind a public HTTPS domain and set that origin in
`ios/Config/Backend.xcconfig`.

## API compatibility

Implemented endpoints:

```text
POST /api/v1/localization/query_relative/{scene_id}
POST /api/v1/localization/client_log
GET  /api/v1/visualization/map
GET  /health
```

The localization response has the same `code`, `data.relative_map`,
`image_id`, `name`, `position`, and `type` fields expected by both clients.

## Deploying to the internet

You need a cloud VM or container service with a public DNS name and TLS. A
simple production command is:

```bash
uvicorn server:app --host 0.0.0.0 --port 8100
```

Put an HTTPS reverse proxy such as Caddy or a managed platform in front of it.
Do not expose the demo server as a real navigation service until the image
localization implementation and authentication are added.
