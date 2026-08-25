from __future__ import annotations

import hashlib
import logging
import uuid
from functools import lru_cache
from io import BytesIO
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

from fontTools import subset
from fontTools.ttLib import TTFont
from lxml import etree


FONT_FAMILY = "Noto Sans SC"
FONT_DIRECTORY = Path(__file__).resolve().parents[1] / "assets" / "fonts"
FONT_SOURCES = {
    400: FONT_DIRECTORY / "NotoSansSC-Regular.otf",
    700: FONT_DIRECTORY / "NotoSansSC-Bold.otf",
}

CONTENT_TYPES_NS = "http://schemas.openxmlformats.org/package/2006/content-types"
PACKAGE_RELATIONSHIPS_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
OFFICE_RELATIONSHIPS_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
WORDPROCESSING_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
EMBEDDED_FONT_RELATIONSHIP = f"{OFFICE_RELATIONSHIPS_NS}/font"
OBFUSCATED_FONT_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.obfuscatedFont"

logging.getLogger("fontTools.subset").setLevel(logging.WARNING)

# Numbering markers and common Chinese punctuation may not occur in w:t nodes.
ALWAYS_INCLUDED_CHARACTERS = "•·，。！？；：、‘’“”（）【】《》〈〉—–…-+−=×÷≤≥<>%°0123456789"


def embed_default_fonts(package: BytesIO, characters: str) -> BytesIO:
    """Embed document-specific Regular and Bold font subsets in a DOCX package."""
    glyphs = "".join(sorted(set(characters + ALWAYS_INCLUDED_CHARACTERS)))
    regular = _build_font_subset(glyphs, 400)
    bold = _build_font_subset(glyphs, 700)
    embedded_fonts = {
        "regular": _prepare_embedded_font(regular, "regular"),
        "bold": _prepare_embedded_font(bold, "bold"),
    }

    package.seek(0)
    output = BytesIO()
    with ZipFile(package, "r") as source, ZipFile(output, "w", ZIP_DEFLATED) as target:
        replacements = {
            "[Content_Types].xml": _patch_content_types(source.read("[Content_Types].xml")),
            "word/fontTable.xml": _patch_font_table(source.read("word/fontTable.xml"), embedded_fonts),
            "word/settings.xml": _patch_settings(source.read("word/settings.xml")),
        }
        font_rels_path = "word/_rels/fontTable.xml.rels"
        replacements[font_rels_path] = _patch_font_relationships(
            source.read(font_rels_path) if font_rels_path in source.namelist() else None,
            embedded_fonts,
        )

        for item in source.infolist():
            if item.filename == font_rels_path:
                continue
            target.writestr(item, replacements.pop(item.filename, source.read(item.filename)))
        for path, data in replacements.items():
            target.writestr(path, data)
        for embedded in embedded_fonts.values():
            target.writestr(embedded["path"], embedded["data"])

    output.seek(0)
    return output


@lru_cache(maxsize=16)
def _build_font_subset(characters: str, weight: int) -> bytes:
    font_source = FONT_SOURCES[weight]
    font = TTFont(font_source, recalcTimestamp=False)
    if font["OS/2"].fsType & 0x0002:
        raise ValueError(f"Font does not permit embedding: {font_source}")

    options = subset.Options()
    options.name_IDs = [0, 1, 2, 3, 4, 5, 6, 16, 17]
    options.name_languages = [0x0409, 0x0804]
    options.notdef_glyph = True
    options.recommended_glyphs = True
    options.layout_features = ["*"]
    subsetter = subset.Subsetter(options=options)
    subsetter.populate(text=characters)
    subsetter.subset(font)

    output = BytesIO()
    font.save(output)
    return output.getvalue()


def _prepare_embedded_font(font_data: bytes, style: str) -> dict[str, str | bytes]:
    digest = hashlib.sha256(style.encode("ascii") + font_data).digest()
    font_key = uuid.UUID(bytes=digest[:16])
    key = font_key.bytes[::-1]
    obfuscated = bytearray(font_data)
    for index in range(min(32, len(obfuscated))):
        obfuscated[index] ^= key[index % len(key)]
    return {
        "data": bytes(obfuscated),
        "font_key": f"{{{str(font_key).upper()}}}",
        "path": f"word/fonts/{FONT_FAMILY.replace(' ', '')}-{style}.odttf",
        "relationship_id": f"rIdEmbeddedFont{style.title()}",
    }


def _patch_content_types(xml: bytes) -> bytes:
    root = etree.fromstring(xml)
    exists = root.xpath(
        "./ct:Default[@Extension='odttf']",
        namespaces={"ct": CONTENT_TYPES_NS},
    )
    if not exists:
        default = etree.Element(f"{{{CONTENT_TYPES_NS}}}Default")
        default.set("Extension", "odttf")
        default.set("ContentType", OBFUSCATED_FONT_CONTENT_TYPE)
        root.insert(0, default)
    return _serialize(root)


def _patch_font_table(xml: bytes, embedded_fonts: dict[str, dict[str, str | bytes]]) -> bytes:
    root = etree.fromstring(xml)
    namespaces = {"w": WORDPROCESSING_NS}
    for existing in root.xpath("./w:font[@w:name=$name]", namespaces=namespaces, name=FONT_FAMILY):
        root.remove(existing)

    font = etree.SubElement(root, f"{{{WORDPROCESSING_NS}}}font")
    font.set(f"{{{WORDPROCESSING_NS}}}name", FONT_FAMILY)
    alternate_name = etree.SubElement(font, f"{{{WORDPROCESSING_NS}}}altName")
    alternate_name.set(f"{{{WORDPROCESSING_NS}}}val", "Microsoft YaHei")
    panose = etree.SubElement(font, f"{{{WORDPROCESSING_NS}}}panose1")
    panose.set(f"{{{WORDPROCESSING_NS}}}val", "020B0500000000000000")
    charset = etree.SubElement(font, f"{{{WORDPROCESSING_NS}}}charset")
    charset.set(f"{{{WORDPROCESSING_NS}}}val", "86")
    family = etree.SubElement(font, f"{{{WORDPROCESSING_NS}}}family")
    family.set(f"{{{WORDPROCESSING_NS}}}val", "swiss")
    pitch = etree.SubElement(font, f"{{{WORDPROCESSING_NS}}}pitch")
    pitch.set(f"{{{WORDPROCESSING_NS}}}val", "variable")
    signature = etree.SubElement(font, f"{{{WORDPROCESSING_NS}}}sig")
    for name, value in {
        "usb0": "20000083",
        "usb1": "2ADF3C10",
        "usb2": "00000016",
        "usb3": "00000000",
        "csb0": "60060107",
        "csb1": "00000000",
    }.items():
        signature.set(f"{{{WORDPROCESSING_NS}}}{name}", value)
    for style, element_name in (("regular", "embedRegular"), ("bold", "embedBold")):
        embedded = embedded_fonts[style]
        element = etree.SubElement(font, f"{{{WORDPROCESSING_NS}}}{element_name}")
        element.set(f"{{{OFFICE_RELATIONSHIPS_NS}}}id", str(embedded["relationship_id"]))
        element.set(f"{{{WORDPROCESSING_NS}}}fontKey", str(embedded["font_key"]))
        element.set(f"{{{WORDPROCESSING_NS}}}subsetted", "true")
    return _serialize(root)


def _patch_font_relationships(
    xml: bytes | None,
    embedded_fonts: dict[str, dict[str, str | bytes]],
) -> bytes:
    root = (
        etree.fromstring(xml)
        if xml is not None
        else etree.Element(f"{{{PACKAGE_RELATIONSHIPS_NS}}}Relationships")
    )
    for embedded in embedded_fonts.values():
        relationship_id = str(embedded["relationship_id"])
        for existing in root.xpath(
            "./pr:Relationship[@Id=$rid]",
            namespaces={"pr": PACKAGE_RELATIONSHIPS_NS},
            rid=relationship_id,
        ):
            root.remove(existing)
        relationship = etree.SubElement(root, f"{{{PACKAGE_RELATIONSHIPS_NS}}}Relationship")
        relationship.set("Id", relationship_id)
        relationship.set("Type", EMBEDDED_FONT_RELATIONSHIP)
        relationship.set("Target", f"fonts/{Path(str(embedded['path'])).name}")
    return _serialize(root)


def _patch_settings(xml: bytes) -> bytes:
    root = etree.fromstring(xml)
    for name in ("embedTrueTypeFonts", "saveSubsetFonts"):
        if root.find(f"{{{WORDPROCESSING_NS}}}{name}") is None:
            root.insert(0, etree.Element(f"{{{WORDPROCESSING_NS}}}{name}"))
    return _serialize(root)


def _serialize(root: etree._Element) -> bytes:
    return etree.tostring(root, xml_declaration=True, encoding="UTF-8", standalone=True)
