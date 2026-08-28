import html
import re
from pathlib import Path, PurePosixPath
from typing import Any

from bs4 import BeautifulSoup, NavigableString, Tag

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

        content_list = self._restore_page_tables(
            mineru_result.get("content_list", []),
            mineru_result.get("page_tables", []),
        )
        content_list = self._mark_suspicious_multicolumn_text(content_list)
        content_list = self._merge_layout_table_prefixes(content_list)
        for index, item in enumerate(content_list, start=1):
            block = self._convert_item(item, index, asset_urls, ocr_layout)
            if block is None:
                continue
            blocks.append(block)
            if isinstance(block, HeadingBlock) and block.level == 1 and title == Path(source_file_name).stem:
                title = block.text

        if title == Path(source_file_name).stem:
            title = self._title_from_first_table(blocks) or title

        return LessonDocument(
            document_id=document_id,
            title=title,
            metadata=LessonMetadata(
                source_type=Path(source_file_name).suffix.lstrip(".").lower(),
                source_file_name=source_file_name,
                source_url=f"/api/documents/{document_id}/source",
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
        context = self._block_context(item)

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
                **context,
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
                **context,
            ) if normalized else None

        if item_type == "table":
            table_html = str(item.get("table_body") or item.get("html") or "").strip()
            table_html = self._rewrite_table_assets(table_html, asset_urls)
            table_html = self._rewrite_table_formulas(table_html)
            table_html = self._rewrite_plain_text_formulas(table_html)
            table_html = self._enhance_table_structure(table_html)
            return TableBlock(id=block_id, type="table", html=table_html, **context) if table_html else None

        if item_type == "image":
            if self._is_decorative_image(item):
                return None
            raw_path = str(item.get("img_path") or item.get("image_path") or item.get("src") or "")
            src = self._asset_url(raw_path, asset_urls)
            captions = item.get("image_caption") or item.get("caption") or []
            alt = " ".join(captions) if isinstance(captions, list) else str(captions)
            return ImageBlock(id=block_id, type="image", src=src, alt=alt or None, **context) if src else None

        if item_type in {"equation", "formula", "interline_equation", "inline_equation"}:
            latex = self._normalize_latex(str(item.get("latex") or text))
            return FormulaBlock(id=block_id, type="formula", latex=latex, **context) if latex else None

        if item_type in {"text", "paragraph", ""} and text:
            list_lines = [line.strip() for line in text.splitlines() if line.strip()]
            if len(list_lines) > 1 and all(re.match(r"^(?:[-*•]|\d+[.)])\s+", line) for line in list_lines):
                ordered = all(re.match(r"^\d+[.)]\s+", line) for line in list_lines)
                items = [re.sub(r"^(?:[-*•]|\d+[.)])\s+", "", line) for line in list_lines]
                return ListBlock(id=block_id, type="list", items=items, ordered=ordered, **context)
            return ParagraphBlock(id=block_id, type="paragraph", text=text, **context)
        return None

    @classmethod
    def _restore_page_tables(cls, raw_items: Any, page_tables: Any) -> list[dict[str, Any]]:
        """Replace merged table HTML with the model's page-local fragments."""

        if not isinstance(raw_items, list):
            return []
        items = [dict(item) for item in raw_items if isinstance(item, dict)]
        if not isinstance(page_tables, list):
            return items

        candidates: dict[int, list[dict[str, Any]]] = {
            page_index: [dict(item) for item in page if isinstance(item, dict)]
            for page_index, page in enumerate(page_tables)
            if isinstance(page, list)
        }
        used: set[tuple[int, int]] = set()
        for item in items:
            if str(item.get("type", "")).lower() != "table":
                continue
            try:
                page_index = int(item.get("page_idx"))
            except (TypeError, ValueError):
                continue
            item_box = cls._normalized_bbox(item)
            best_index: int | None = None
            best_score = -1.0
            for candidate_index, candidate in enumerate(candidates.get(page_index, [])):
                if (page_index, candidate_index) in used:
                    continue
                candidate_box = cls._normalized_bbox(candidate)
                score = cls._bbox_overlap_score(item_box, candidate_box)
                if score > best_score:
                    best_index = candidate_index
                    best_score = score
            if best_index is None or best_score < 0.15:
                continue
            candidate = candidates[page_index][best_index]
            page_html = str(candidate.get("table_body") or "").strip()
            if page_html:
                item["table_body"] = page_html
                item.pop("html", None)
                used.add((page_index, best_index))
        return items

    @staticmethod
    def _bbox_overlap_score(
        first: tuple[float, float, float, float] | None,
        second: tuple[float, float, float, float] | None,
    ) -> float:
        if first is None or second is None:
            return -1.0
        left = max(first[0], second[0])
        top = max(first[1], second[1])
        right = min(first[2], second[2])
        bottom = min(first[3], second[3])
        intersection = max(0.0, right - left) * max(0.0, bottom - top)
        first_area = (first[2] - first[0]) * (first[3] - first[1])
        second_area = (second[2] - second[0]) * (second[3] - second[1])
        return intersection / max(first_area, second_area, 1e-9)

    @classmethod
    def _mark_suspicious_multicolumn_text(cls, raw_items: Any) -> list[dict[str, Any]]:
        """Flag provider text whose geometry crosses visual columns.

        Once OCR has interleaved two columns inside one string there is no safe,
        provider-independent way to invent the original order. The adapter fixes
        duplicated suffixes when they are provable and otherwise exposes an
        explicit review state instead of silently presenting corrupted text.
        """

        if not isinstance(raw_items, list):
            return []
        items = [dict(item) for item in raw_items if isinstance(item, dict)]
        by_page: dict[int, list[dict[str, Any]]] = {}
        for item in items:
            try:
                page_index = int(item.get("page_idx"))
            except (TypeError, ValueError):
                continue
            by_page.setdefault(page_index, []).append(item)

        for page_items in by_page.values():
            text_items = [
                item for item in page_items
                if str(item.get("type", "")).lower() in {"text", "paragraph", ""}
                and cls._text(item)
                and cls._normalized_bbox(item) is not None
            ]
            right_items = [
                item for item in text_items
                if (box := cls._normalized_bbox(item)) is not None and box[0] >= 0.68
            ]
            left_items = [
                item for item in text_items
                if (box := cls._normalized_bbox(item)) is not None and box[0] <= 0.18
            ]
            has_columns = bool(right_items) and len(left_items) >= 2
            for item in text_items:
                box = cls._normalized_bbox(item)
                if box is None:
                    continue
                width = box[2] - box[0]
                height = box[3] - box[1]
                crosses_column = has_columns and any(
                    (right_box := cls._normalized_bbox(right_item)) is not None
                    and box[0] < 0.6
                    and box[2] > right_box[0] + 0.08
                    and min(box[3], right_box[3]) > max(box[1], right_box[1])
                    for right_item in right_items
                )
                suspicious = crosses_column or (width >= 0.55 and height >= 0.12) or width >= 0.75
                if not suspicious:
                    continue
                original = cls._text(item)
                corrected = cls._remove_provable_duplicate_suffix(original, item, right_items)
                if corrected != original:
                    item["text"] = corrected
                item["review_required"] = True
                item["review_reason"] = "该文本块横跨多个版面列，阅读顺序需结合原页复核。"
        return items

    @classmethod
    def _remove_provable_duplicate_suffix(
        cls,
        text: str,
        item: dict[str, Any],
        right_items: list[dict[str, Any]],
    ) -> str:
        item_box = cls._normalized_bbox(item)
        if item_box is None:
            return text
        for candidate in right_items:
            if candidate is item:
                continue
            candidate_box = cls._normalized_bbox(candidate)
            candidate_text = cls._text(candidate)
            if candidate_box is None or candidate_box[0] - item_box[0] < 0.4:
                continue
            max_length = min(24, len(text), len(candidate_text))
            for length in range(max_length, 3, -1):
                suffix = text[-length:]
                if candidate_text.endswith(suffix):
                    return text[:-length].rstrip()
        return text

    @staticmethod
    def _block_context(item: dict[str, Any]) -> dict[str, Any]:
        try:
            source_page = int(item.get("page_idx")) + 1
        except (TypeError, ValueError):
            source_page = None
        return {
            "source_page": source_page,
            "group_id": f"page-{source_page}" if source_page is not None else None,
            "review_required": bool(item.get("review_required")),
            "review_reason": str(item.get("review_reason") or "") or None,
        }

    @classmethod
    def _is_decorative_image(cls, item: dict[str, Any]) -> bool:
        bbox = cls._normalized_bbox(item)
        captions = item.get("image_caption") or item.get("caption") or []
        has_caption = bool(captions)
        if bbox is None or not has_caption:
            return False
        width = bbox[2] - bbox[0]
        height = bbox[3] - bbox[1]
        is_tiny_standalone_asset = width <= 0.08 and height <= 0.08
        is_small_header_asset = bbox[1] <= 0.22 and width <= 0.14 and height <= 0.12
        return is_tiny_standalone_asset or is_small_header_asset

    @staticmethod
    def _title_from_first_table(blocks: list[LessonBlock]) -> str | None:
        table_block = next((block for block in blocks if isinstance(block, TableBlock)), None)
        if table_block is None:
            return None
        soup = BeautifulSoup(table_block.html, "html.parser")
        first_row = soup.find("tr")
        if not isinstance(first_row, Tag):
            return None
        cells = first_row.find_all(["td", "th"], recursive=False)
        if len(cells) != 1:
            return None
        value = cells[0].get_text(" ", strip=True)
        if not value or len(value) > 120:
            return None
        match = re.fullmatch(r"[^:：]{1,16}[:：]\s*(.+)", value)
        return match.group(1).strip() if match else value

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

    @classmethod
    def _rewrite_plain_text_formulas(cls, table_html: str) -> str:
        """Recover compact equations that MinerU left as plain table text."""

        soup = BeautifulSoup(table_html, "html.parser")
        pattern = re.compile(
            r"(?<![\w.])"
            r"(?P<expr>(?:[A-Za-z]|\d+(?:\.\d+)?)"
            r"(?:\s*(?:/|:|=|×|÷|\+|-)\s*(?:[A-Za-z]|\d+(?:\.\d+)?)){2,})"
            r"(?![\w.])"
        )

        for text_node in list(soup.find_all(string=True)):
            parent = text_node.parent
            if not isinstance(parent, Tag) or parent.find_parent(attrs={"data-latex": True}):
                continue
            if parent.name in {"script", "style"}:
                continue
            value = str(text_node)
            if not pattern.search(value):
                continue

            cursor = 0
            replacements: list[Tag | NavigableString] = []
            for match in pattern.finditer(value):
                expression = match.group("expr")
                if not any(operator in expression for operator in ("=", ":", "/", "×", "÷")):
                    continue
                if match.start() > cursor:
                    replacements.append(NavigableString(value[cursor:match.start()]))
                latex = cls._normalize_plain_math(expression)
                span = soup.new_tag("span")
                span["class"] = "lesson-inline-formula"
                span["data-latex"] = latex
                span["role"] = "button"
                span["tabindex"] = "0"
                replacements.append(span)
                cursor = match.end()
            if not replacements:
                continue
            if cursor < len(value):
                replacements.append(NavigableString(value[cursor:]))
            for replacement in replacements:
                text_node.insert_before(replacement)
            text_node.extract()
        return str(soup)

    @staticmethod
    def _normalize_plain_math(value: str) -> str:
        normalized = value.strip().replace("×", r"\times ").replace("÷", r"\div ")
        return re.sub(
            r"(?<![A-Za-z0-9.])([A-Za-z0-9.]+)/([A-Za-z0-9.]+)",
            r"\\frac{\1}{\2}",
            normalized,
        )

    @staticmethod
    def _enhance_table_structure(table_html: str) -> str:
        soup = BeautifulSoup(table_html, "html.parser")
        table = soup.find("table")
        if not isinstance(table, Tag):
            return ""
        classes = list(table.get("class") or [])
        if "lesson-source-table" not in classes:
            classes.append("lesson-source-table")
        table["class"] = classes
        rows = table.find_all("tr")
        column_count = max(
            (
                sum(
                    MinerUAdapter._positive_int(cell.get("colspan"), 1)
                    for cell in row.find_all(["th", "td"], recursive=False)
                )
                for row in rows
            ),
            default=1,
        )
        table["data-column-count"] = str(column_count)

        boundary = re.compile(
            r"(?<=[。！？；：）)】])"
            r"(?=(?:[一二三四五六七八九十]+、|\d+[.、](?!\d)))"
        )
        for text_node in list(table.find_all(string=True)):
            parent = text_node.parent
            if not isinstance(parent, Tag) or parent.find_parent(attrs={"data-latex": True}):
                continue
            value = str(text_node)
            enhanced = boundary.sub("\n", value)
            if enhanced != value:
                text_node.replace_with(NavigableString(enhanced))
        return str(soup)
