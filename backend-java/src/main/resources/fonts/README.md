# Noto Sans SC

- Source: [Noto Sans SC variable TrueType](https://github.com/notofonts/noto-cjk/blob/165c01b46ea533872e002e0785ff17e44f6d97d8/Sans/Variable/TTF/Subset/NotoSansSC-VF.ttf)
- Checked-in resources: static TrueType instances at `wght=400` (Regular) and `wght=700` (Bold), prepared offline with fontTools 4.63.0 `instantiateVariableFont(..., updateFontNames=True)`, preserving source timestamps. Font preparation is not part of the Java build or runtime.
- SHA-256:
  - Source variable TTF: `d68bafcb48a2707749396aa12bbbd833cb70401f3a9a689fd2902c7e0d295964`
  - Regular TTF: `c7763f454946833081cc90e73186615f8e1189de9c5e5a5a8752871fd79fddbc`
  - Bold TTF: `6b732eea2a61a58cbc2ce882d5f4c4f8f57576fba913edef72e3f1a007fcaeaa`
- License: SIL Open Font License 1.1; see `OFL.txt`

The Java DOCX exporter uses Apache FontBox to create document-specific Regular and Bold subsets, including automatic numbering characters, and embeds them using the OOXML obfuscated-font format. It retains Unicode mapping, font names, metrics, outlines and hinting; full-font GSUB/GPOS tables are not copied with invalid glyph references. Chinese/Latin horizontal text is the supported layout. Formula layout remains editable OMML using Cambria Math.

The Java service and its build use only the Java stack: no Python process, Python service or fontTools runtime dependency. Subsets omit unused characters; later edits in Word may use the viewer's installed fallback fonts for newly typed characters.
