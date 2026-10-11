from __future__ import annotations

import time
from pathlib import Path
from typing import Annotated

from fastapi import FastAPI, File, UploadFile
from fastapi.responses import JSONResponse


app = FastAPI(title="Indoor Navigation Backend", version="0.1.0")
SCENE_DIR = Path(__file__).with_name("scenes")


def scene_nodes(scene_id: int) -> list[dict]:
    path = SCENE_DIR / f"{scene_id}.json"
    if not path.exists():
        return []
    import json

    return json.loads(path.read_text(encoding="utf-8"))["relative_map"]


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "indoor-navigation-backend"}


@app.get("/api/v1/visualization/map")
def visualization_map() -> dict:
    return {"code": 200, "data": {"relative_map": scene_nodes(1)}}


@app.post("/api/v1/localization/query_relative/{scene_id}")
async def query_relative(
    scene_id: int,
    file: Annotated[UploadFile, File(description="Current camera JPEG")],
) -> JSONResponse:
    image_bytes = await file.read()
    if not image_bytes:
        return JSONResponse(status_code=400, content={"code": 400, "message": "empty image"})

    nodes = scene_nodes(max(1, scene_id))
    if not nodes:
        return JSONResponse(
            status_code=404,
            content={"code": 404, "message": f"scene {scene_id} not configured"},
        )

    return {
        "code": 200,
        "message": "demo scene map returned; image content was not localized",
        "server_time": time.time(),
        "data": {
            "image_name": file.filename or "query.jpg",
            "scale_factor_used": 1.0,
            "relative_map": nodes,
            "meta": {"num_inliers": 0, "camera_params": []},
        },
    }


@app.post("/api/v1/localization/client_log")
async def client_log(payload: dict) -> dict[str, object]:
    return {"code": 200, "message": "accepted", "tag": payload.get("tag")}
