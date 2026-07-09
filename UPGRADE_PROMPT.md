# Doc Scanner — Full Application Upgrade Specification

Use this document as the single source of truth for upgrading the Android document scanner app.

---

## 1. Design System (Light Theme)

### Color palette (70 / 20 / 10 rule)

| Role | Usage | Suggested hex |
|------|--------|----------------|
| **Primary** | 70% — backgrounds, main surfaces, nav | `#E8F0FE`, `#4F6BED` (indigo) |
| **Secondary** | 20% — cards, panels, toolbars | `#FFFFFF`, `#F5F7FA` |
| **Accent** | 10% — CTAs, highlights, progress | `#FF6B35`, `#00BCD4` |

### UI principles

- Light theme only (no dark-first screens)
- Floating cards with soft elevation (8–16dp)
- Rounded corners 16–24dp
- Premium typography (sans-serif-medium titles)
- Micro-interactions on tap (scale 0.96 → 1.0)
- Smooth fragment transitions (fade + slide)
- Reference quality: Adobe Scan, Dropbox, modern Material 3

---

## 2. Dashboard

### Layout

- Header: app title + subtitle
- **Action grid** (floating cards):
  - Scan (primary FAB or hero card)
  - Import from gallery
  - Convert files
  - Compress files
  - **Merge PDFs** (new)
  - **Encrypt file** (new)
- **Recent scans** — horizontal or vertical list with thumbnails

### Merge PDFs

1. Tap **Merge PDFs** → merge screen
2. Multi-select PDF picker (`application/pdf`)
3. List selected files with name + size
4. Drag-and-drop reorder
5. **Merge** button (requires ≥2 PDFs)
6. Progress while merging
7. Success state → Download, Rename, Share

### Encrypt file

1. Tap **Encrypt file** → encrypt screen
2. Single PDF picker
3. Password + confirm password (show/hide toggles)
4. Validate: match, min length (e.g. 6)
5. Optional strength indicator
6. Encrypt (PDF standard via PdfBox) — **do not persist passwords**
7. Download / Share encrypted PDF

### Recent scans — long press

Menu: **Rename** | **Edit** | **Delete** | **Share**

- **Rename**: dialog → update title in index
- **Edit**: load pages → Editor
- **Delete**: remove files + index; optional Snackbar undo
- **Share**: share exported PDF if exists, else images

---

## 3. Editor page

### Remove

- **Crop** toolbar button (cropping only in scan flow)

### Add — Adjust

- Toolbar button **Adjust** opens bottom panel
- Horizontal chips/tabs: Brightness, Contrast, Sharpen, Saturation, Warmth, Exposure, Highlights, Shadows
- Per option: slider centered at **0**, range **-100 … +100**
- Real-time preview on main image
- Apply on top of current filter; persist per page

### Bug fixes (mandatory)

| ID | Issue | Fix |
|----|--------|-----|
| B1 | Rotate resets filter to Original | After rotate, re-apply `page.filter` to display bitmap |
| B2 | Retake adds duplicate page | Retake replaces `currentPageIndex` page; set `retakeReplaceIndex` before camera |
| B3 | First single page Export stays on camera | `exportAfterPreview` callback then navigate; Editor waits for pages |

---

## 4. Technical modules

```
pdf/
  PdfMerger.kt      — merge multiple PDFs (PdfBox)
  PdfEncryptor.kt   — password protect PDF (PdfBox StandardProtectionPolicy)

image/
  ImageAdjustments.kt — brightness, contrast, etc. (ColorMatrix + OpenCV sharpen)

ui/
  merge/FileMergeFragment.kt
  encrypt/FileEncryptFragment.kt
  editor/AdjustBottomSheet.kt
```

---

## 5. Acceptance criteria

- [ ] Light theme on Dashboard, Editor, Merge, Encrypt, Convert, Compress
- [ ] Merge ≥2 PDFs with reorder and download
- [ ] Encrypt PDF with password validation
- [ ] Adjust panel with 8 sliders, live preview
- [ ] Recent long-press menu fully working
- [ ] B1, B2, B3 verified manually
- [ ] No regression in scan → crop → preview → editor flow

---

## 6. Out of scope (future)

- Cloud sync
- OCR
- Batch encrypt multiple files
