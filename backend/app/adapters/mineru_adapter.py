import html
import re
from pathlib import Path, PurePosixPath
from typing import Any

from bs4 import BeautifulSoup, Tag

from app.models.lesson_document import (
    FormulaBlock,
    HeadingBlock,
    ImageBlock,
    LessonBlock,
    LessonDocument,
    LessonMetadata,
    ListBlock,
    ParagraphBlock,
    TableBlock,
)


class MinerUAdapter:
    def convert(
        self,
        mineru_result: dict[str, Any],
        *,
        document_id: str,
        source_file_name: str,
        asset_urls: dict[str, str] | None = None,
    ) -> LessonDocument:
        asset_urls = asset_urls or {}
        blocks: list[LessonBlock] = []
        title = Path(source_file_name).stem
        ocr_layout = mineru_result.get("ocr_layout", [])

        content_list = self._merge_layout_table_prefixes(mineru_result.get("content_list", []))
        for index, item in enumerate(content_list, start=1):
            block = self._convert_item(item, index, asset_urls, ocr_layout)
            if block is None:
                continue
            blocks.append(block)
            if isinstance(block, HeadingBlock) and block.level == 1 and title == Path(source_file_name).stem:
                title = block.text

        return LessonDocument(
            document_id=document_id,
            title=title,
            metadata=LessonMetadata(
                source_type=Path(source_file_name).suffix.lstrip(".").lower(),
                source_file_name=source_file_name,
            ),
            blocks=blocks,
        )

    def _convert_item(
        self,
        item: dict[str, Any],
        index: int,
        asset_urls: dict[str, str],
        ocr_layout: Any,
    ) -> LessonBlock | None:
        item_type = str(item.get("type", "")).lower()
        block_id = f"block-{index:04d}"
        text = self._text(item)

        if item_type in {"title", "heading"} or item.get("text_level"):
            if not text:
                return None
            level = int(item.get("text_level") or item.get("level") or 1)
            return HeadingBlock(
                id=block_id,
                type="heading",
                level=min(max(level, 1), 6),
                text=self._restore_heading_spacing(self._plain_text(text), item, ocr_layout),
                alignment=self._heading_alignment(item),
            )

        if item_type in {"list", "list_item"}:
            items = item.get("items") or item.get("list_items") or []
            if not items and text:
                items = [line for line in text.splitlines() if line.strip()]
            normalized = [self._text(value) if isinstance(value, dict) else str(value).strip() for value in items]
            normalized = [value for value in normalized if value]
            return ListBlock(
                id=block_id,
                type="list",
                items=normalized,
                ordered=bool(item.get("ordered") or item.get("list_type") == "ordered"),
            ) if normalized else None

        if item_type == "table":
            html = str(item.get("table_body") or item.get("html") or "").strip()
            html = self._rewrite_table_assets(html, asset_urls)
            html = self._rewrite_table_formulas(html)
            return TableBlock(id=block_id, type="table", html=html) if html else None

        if item_type == "image":
            raw_path = str(item.get("img_path") or item.get("image_path") or item.get("src") or "")
            src = self._asset_url(raw_path, asset_urls)
            captions = item.get("image_caption") or item.get("caption") or []
            alt = " ".join(captions) if isinstance(captions, list) else str(captions)
            return ImageBlock(id=block_id, type="image", src=src, alt=alt or None) if src else None

        if item_type in {"equation", "formula", "interline_equation", "inline_equation"}:
            latex = self._normalize_latex(str(item.get("latex") or text))
            return FormulaBlock(id=block_id, type="formula", latex=latex) if latex else None

        if item_type in {"text", "paragraph", ""} and text:
            list_lines = [line.strip() for line in text.splitlines() if line.strip()]
            if len(list_lines) > 1 and all(re.match(r"^(?:[-*•]|\d+[.)])\s+", line) for line in list_lines):
                ordered = all(re.match(r"^\d+[.)]\s+", line) for line in list_lines)
                items = [re.sub(r"^(?:[-*•]|\d+[.)])\s+", "", line) for line in list_lines]
                return ListBlock(id=block_id, type="list", items=items, ordered=ordered)
            return ParagraphBlock(id=block_id, type="paragraph", text=text)
        return None

    @classmethod
    def _merge_layout_table_prefixes(cls, raw_items: Any) -> list[dict[str, Any]]:
        """Restore rows that MinerU extracted as text from a bordered layout table.

        MinerU can recognize the lower, multi-column part of a page as a table while
        returning the preceding single-column rows as independent text blocks.  The
        bounding boxes still show that those rows share the table's left edge and
        are vertically continuous, so they can be safely folded back into the HTML
        table.  Centered document titles do not meet this geometry test and remain
        regular heading blocks.
        """

        if not isinstance(raw_items, list):
            return []
        items = [dict(item) for item in raw_items if isinstance(item, dict)]
        table_index = 0
        while table_index < len(items):
            table_item = items[table_index]
            table_html = str(table_item.get("table_body") or table_item.get("html") or "").strip()
            table_box = cls._bbox(table_item)
            if table_item.get("type") != "table" or not table_html or table_box is None:
                table_index += 1
                continue

            page_index = table_item.get("page_idx")
            prefix_start = table_index
            while prefix_start > 0:
                candidate = items[prefix_start - 1]
                candidate_box = cls._bbox(candidate)
                if (
                    str(candidate.get("type", "")).lower() != "text"
                    or candidate.get("page_idx") != page_index
                    or candidate_box is None
                    or abs(candidate_box[0] - table_box[0]) > 40
                    or candidate_box[2] > table_box[2] + 20
                ):
                    break
                prefix_start -= 1

            prefix = items[prefix_start:table_index]
            last_box = cls._bbox(prefix[-1]) if prefix else None
            has_heading = any(item.get("text_level") for item in prefix)
            is_wide_table = table_box[0] <= 250 and table_box[2] >= 700
            is_continuous = last_box is not None and -10 <= table_box[1] - last_box[3] <= 40
            if len(prefix) < 3 or not has_heading or not is_wide_table or not is_continuous:
                table_index += 1
                continue

            merged_html = cls._prepend_layout_rows(table_html, prefix, table_box)
            if not merged_html:
                table_index += 1
                continue
            table_item["table_body"] = merged_html
            table_item.pop("html", None)
            items[prefix_start:table_index] = []
            table_index = prefix_start + 1
        return items

    @staticmethod
    def _bbox(item: dict[str, Any]) -> tuple[float, float, float, float] | None:
        bbox = item.get("bbox")
        if not isinstance(bbox, list) or len(bbox) != 4:
            return None
        try:
            left, top, right, bottom = (float(value) for value in bbox)
        except (TypeError, ValueError):
            return None
        if right <= left or bottom <= top:
            return None
        return left, top, right, bottom

    @classmethod
    def _heading_alignment(cls, item: dict[str, Any]) -> str:
        """Infer semantic alignment from MinerU geometry, independent of text.

        MinerU emits page-relative boxes on either a 0..1 or 0..1000 scale.
        Keeping the provider-specific coordinate handling here prevents raw
        geometry from leaking into LessonDocument or the frontend.
        """

        bbox = cls._bbox(item)
        if bbox is None:
            return "left"

        left, _, right, _ = bbox
        page_width = 1.0 if max(abs(left), abs(right)) <= 1.5 else 1000.0
        block_center = (left + right) / 2
        block_width = right - left
        center_tolerance = max(
            page_width * 0.025,
            min(page_width * 0.08, block_width * 0.2),
        )
        if abs(block_center - page_width / 2) <= center_tolerance:
            return "center"
        if block_center >= page_width * 0.67:
            return "right"
        return "left"

    @classmethod
    def _restore_heading_spacing(
        cls,
        text: str,
        item: dict[str, Any],
        ocr_layout: Any,
    ) -> str:
        """Recover visually meaningful gaps between heading text runs.

        MinerU's content list collapses run gaps to one ASCII space, while its
        OCR layout retains each run's box.  Reconstruct spacing only when text
        tokens and OCR boxes match unambiguously; otherwise preserve the text.
        """

        tokens = re.split(r"\s+", text.strip())
        if len(tokens) < 2 or not isinstance(ocr_layout, list):
            return text
        try:
            page_index = int(item.get("page_idx", 0))
            page_boxes = ocr_layout[page_index]
        except (TypeError, ValueError, IndexError):
            return text
        heading_box = cls._normalized_bbox(item)
        if heading_box is None or not isinstance(page_boxes, list):
            return text

        left, top, right, bottom = heading_box
        line_height = bottom - top
        matching_boxes: list[tuple[float, float, float, float]] = []
        for candidate in page_boxes:
            if not isinstance(candidate, dict):
                continue
            box = cls._normalized_bbox(candidate)
            if box is None:
                continue
            box_left, box_top, box_right, box_bottom = box
            vertical_overlap = max(0.0, min(bottom, box_bottom) - max(top, box_top))
            if (
                vertical_overlap >= min(line_height, box_bottom - box_top) * 0.5
                and box_right >= left - 0.01
                and box_left <= right + 0.01
            ):
                matching_boxes.append(box)

        matching_boxes.sort(key=lambda box: box[0])
        if len(matching_boxes) != len(tokens):
            return text

        restored = [tokens[0]]
        for index, token in enumerate(tokens[1:], start=1):
            gap_ratio = max(0.0, matching_boxes[index][0] - matching_boxes[index - 1][2]) / line_height
            if gap_ratio >= 0.75:
                separator = "　" * min(6, max(1, round(gap_ratio)))
            else:
                separator = " "
            restored.extend((separator, token))
        return "".join(restored)

    @classmethod
    def _normalized_bbox(
        cls, item: dict[str, Any]
    ) -> tuple[float, float, float, float] | None:
        bbox = cls._bbox(item)
        if bbox is None:
            return None
        scale = 1.0 if max(abs(value) for value in bbox) <= 1.5 else 1000.0
        return tuple(value / scale for value in bbox)

    @staticmethod
    def _positive_int(value: Any, default: int) -> int:
        try:
            parsed = int(value)
        except (TypeError, ValueError):
            return default
        return parsed if parsed > 0 else default

    @classmethod
    def _prepend_layout_rows(
        cls,
        table_html: str,
        prefix: list[dict[str, Any]],
        table_box: tuple[float, float, float, float],
    ) -> str:
        soup = BeautifulSoup(table_html, "html.parser")
        table = soup.find("table")
        if not isinstance(table, Tag):
            return ""
        first_row = table.find("tr")
        if not isinstance(first_row, Tag):
            return ""
        column_count = sum(cls._positive_int(cell.get("colspan"), 1) for cell in first_row.find_all(["th", "td"], recursive=False))
        if column_count < 1:
            return ""

        classes = list(table.get("class") or [])
        if "lesson-layout-table" not in classes:
            classes.append("lesson-layout-table")
        table["class"] = classes
        table["data-repeat-header"] = "false"

        for item in prefix:
            text = cls._text(item)
            if not text:
                continue
            row = soup.new_tag("tr")
            cell = soup.new_tag("td", colspan=str(column_count))
            cell_classes = ["lesson-layout-cell"]
            bbox = cls._bbox(item)
            if item.get("text_level"):
                cell_classes.append("lesson-layout-heading-cell")
                strong = soup.new_tag("strong")
                strong.string = cls._plain_text(text)
                cell.append(strong)
            else:
                if bbox and bbox[0] >= table_box[0] + 20 and bbox[2] <= table_box[2] - 20:
                    cell_classes.append("lesson-layout-centered-cell")
                cell.string = cls._plain_text(text)
            cell["class"] = cell_classes
            row.append(cell)
            first_row.insert_before(row)
        return str(table)

    @staticmethod
    def _text(value: Any) -> str:
        if isinstance(value, dict):
            value = value.get("text") or value.get("content") or ""
        return str(value or "").strip()

    @staticmethod
    def _plain_text(value: str) -> str:
        return html.unescape(re.sub(r"<[^>]+>", "", value)).strip()

    @staticmethod
    def _normalize_latex(value: str) -> str:
        latex = value.strip().strip("$").strip()
        return latex.replace("°", r"^{\circ}")

    @staticmethod
    def _asset_url(raw_path: str, asset_urls: dict[str, str]) -> str:
        if not raw_path:
            return ""
        normalized = str(PurePosixPath(raw_path.replace("\\", "/")))
        return asset_urls.get(normalized) or asset_urls.get(PurePosixPath(normalized).name, "")

    @classmethod
    def _rewrite_table_assets(cls, html: str, asset_urls: dict[str, str]) -> str:
        def replace_src(match: re.Match[str]) -> str:
            source = match.group(2)
            resolved = cls._asset_url(source, asset_urls)
            return f"{match.group(1)}{resolved or source}{match.group(3)}"

        return re.sub(
            r"(<img\b[^>]*?\bsrc\s*=\s*[\"'])([^\"']+)([\"'])",
            replace_src,
            html,
            flags=re.IGNORECASE,
        )

    @classmethod
    def _rewrite_table_formulas(cls, table_html: str) -> str:
        pattern = re.compile(
            r"<eq>(?P<eq>.*?)</eq>|(?<!\\)\$(?P<dollar>.+?)(?<!\\)\$",
            flags=re.IGNORECASE | re.DOTALL,
        )

        def replace_formula(match: re.Match[str]) -> str:
            raw_latex = html.unescape(match.group("eq") or match.group("dollar") or "")
            latex = cls._normalize_latex(raw_latex)
            escaped = html.escape(latex, quote=True)
            return (
                '<span class="lesson-inline-formula" '
                f'data-latex="{escaped}" role="button" tabindex="0"></span>'
            )

        return pattern.sub(replace_formula, table_html)
