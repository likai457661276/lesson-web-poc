# Design QA

- Source visual truth: `/var/folders/1d/sr_5n_2j4877xxt715wlyn9r0000gn/T/codex-clipboard-d3455024-f958-4b97-845e-91022c66adb7.png`
- Browser-rendered implementation: `/Users/likai/Documents/workspace/lesson-web-poc/design-qa-implementation.jpg`
- Full browser view: `/Users/likai/Documents/workspace/lesson-web-poc/design-qa-full.jpg`
- Same-scale focused comparison: `/Users/likai/Documents/workspace/lesson-web-poc/design-qa-comparison.jpg`
- Browser viewport: 1280 × 720 CSS px; device pixel ratio 1
- Source pixels: 715 × 557
- Implementation crop: 701 × 557; displayed at 715 px width in the focused comparison to normalize the page crop
- State: PDF parse completed, first-page title area, default non-editing state
- Test data: the repository's stored MinerU result for the same PDF was converted by the current Parser/Adapter code; the browser parse response was intercepted locally to avoid another external MinerU request

## Findings

No actionable P0/P1/P2 differences remain in the requested title region.

- Fonts and typography: both title lines use the existing Songti-style serif stack. The source and Web implementation have equivalent optical size, weight hierarchy, and line spacing at the normalized scale. The second title's two text runs retain the source gap.
- Spacing and layout rhythm: both title lines are horizontally centered. Their vertical offsets differ by no more than roughly 4 CSS px in the focused comparison; the title-to-table transition is also within roughly 4 px.
- Colors and visual tokens: the Web paper uses the existing warm `#fffdf7` token while the PDF capture is white. This is a P3 app-surface difference outside the requested title-position correction.
- Image quality and assets: no raster image asset appears in the compared title region; text and table borders remain code-rendered and sharp.
- Copy and content: `第一课时`, `5.1.1`, `任意角`, and the first table rows match. Significant spacing between `5.1.1` and `任意角` is restored from OCR run geometry.

## Comparison History

1. Initial implementation evidence showed three issues: titles were left-aligned, the second title's internal run gap had collapsed, and generic heading margins pushed the following content too low.
2. Fixes applied: added semantic heading alignment to LessonDocument v1, inferred alignment from normalized Provider boxes, retained OCR run gaps only when token/box matching is unambiguous, and added centered-title rhythm styles.
3. Post-fix evidence: `/Users/likai/Documents/workspace/lesson-web-poc/design-qa-comparison.jpg` shows matching center axes, title-line spacing, internal run gap, and title-to-table rhythm. No P0/P1/P2 issue remains.

## Browser Verification

- Tested the upload chooser and completed-document rendering path.
- Confirmed the real converted document produces three top-level blocks: centered level-1 heading, centered level-2 heading, and restored layout table.
- Confirmed no browser console errors or warnings during the final render.
- Responsive fallback remains semantic: centered/right/left alignment is CSS-based and no PDF absolute coordinate is used in the frontend.

## Follow-up Polish

- P3: change the document paper token from warm white to pure white only if full PDF color fidelity becomes a product requirement.

final result: passed
