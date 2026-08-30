import json
import asyncio
from pathlib import Path

import httpx
import pytest

from app.core.config import Settings
from app.core.exceptions import MinerUError
from app.parsers.mineru_parser import MinerUDocumentParser


def test_parser_keeps_only_ocr_boxes_from_model_file(tmp_path: Path) -> None:
    model = [[
        {"type": "paragraph_title", "bbox": [0.1, 0.1, 0.4, 0.2], "content": None},
        {"type": "ocr_text", "bbox": [0.1, 0.1, 0.2, 0.2], "text": "", "score": 1.0},
        {"type": "ocr_text", "bbox": [0.3, 0.1, 0.4, 0.2], "text": "", "score": 1.0},
    ]]
    (tmp_path / "document_model.json").write_text(
        json.dumps(model),
        encoding="utf-8",
    )

    result = MinerUDocumentParser._load_ocr_layout(tmp_path)

    assert result == [[
        {"bbox": [0.1, 0.1, 0.2, 0.2]},
        {"bbox": [0.3, 0.1, 0.4, 0.2]},
    ]]


def test_parser_ocr_layout_is_optional(tmp_path: Path) -> None:
    assert MinerUDocumentParser._load_ocr_layout(tmp_path) == []


@pytest.mark.parametrize("stage,context", [
    ("create", "申请上传地址失败"), ("upload", "文件上传失败"),
    ("poll", "查询解析状态失败"), ("download", "结果下载失败"),
])
@pytest.mark.parametrize("failure", [httpx.ConnectError, httpx.ReadTimeout, httpx.RemoteProtocolError, None])
def test_network_failures_are_provider_errors(tmp_path: Path, stage, context, failure) -> None:
    parser = MinerUDocumentParser(Settings(_env_file=None, mineru_api_key="test-token"))
    source = tmp_path / "source.pdf"
    source.write_bytes(b"pdf")

    def respond(request):
        if failure:
            raise failure("signature=private-value", request=request)
        return httpx.Response(503, json={"code": 503})

    async def run():
        async with httpx.AsyncClient(transport=httpx.MockTransport(respond)) as client:
            if stage == "create":
                await parser._create_upload(client, source.name)
            elif stage == "upload":
                await parser._upload_file(client, "https://upload.test?signature=private-value", source)
            elif stage == "poll":
                await parser._wait_for_result(client, "batch", source.name)
            else:
                await parser._download_and_extract(client, "https://download.test?signature=private-value", tmp_path / "mineru")

    with pytest.raises(MinerUError) as raised:
        asyncio.run(run())
    assert raised.value.code == "MINERU_PARSE_FAILED"
    assert raised.value.status_code == 502
    assert context in str(raised.value)
    assert "private-value" not in str(raised.value)
