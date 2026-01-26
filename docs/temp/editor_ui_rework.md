# Editor UI Rework

> Multi-session UI/UX cleanup for the Editor view.
> Goal: Streamline the editor to be a focused "VSCode for writers" experience.

---

## Completed (Session 1)

### Phase 1: Cleanup Dead UI ✓
- [x] Removed `FILE | NAVIGATION | OPEN FILES | TABS` labels from toolbar
- [x] Removed Reveal File, Open Folder buttons from toolbar
- [x] Removed Terminal button from sidebar
- [x] Removed Open File System button from sidebar
- [x] Removed "Ready" text from status bar
- [x] Removed Bell button from status bar
- [x] Removed entire status bar element

### Phase 2: Toolbar Simplification ✓
- [x] Removed Find, Search buttons from toolbar (Ctrl+F, Ctrl+Shift+F still work)
- [x] Moved +New Issue to sidebar
- [x] Moved File History to sidebar
- [x] Tabs now have full width

### Phase 3: Right Panel - Project Badge ✓
- [x] Moved project name pill from tab strip to chat panel header
- [x] Replaced "Chat with AI" label with project badge
- [x] Badge no longer wanders when explorer panel opens/closes

---

## Completed (Session 2)

### Right Panel Polish ✓
- [x] Agent selection: clickable avatar that opens dropdown
- [x] Show selected agent's avatar prominently (with fallback to initials/emoji)
- [x] Convert AI Tools buttons to icon-only with tooltips (Summarize, Explain, Suggest, Propose Edit)
- [x] Move Memory controls (Bind, R3, More evidence) below Send button
- [x] Compact AI tools bar with grouped icon buttons
- [x] Memory bar uses icon buttons (link, arrow-up-circle)

---

## Remaining Work

### Bottom Panel (Future)
- [ ] Fix scrolling issue in Console/AI Actions/Search area
- [ ] Default to AI Actions tab (most useful for writers)
- [ ] Consider hiding Console by default (dev feature)
- [ ] Search tab could become full Find & Replace

---

## Reference

- Original spec: [docs/reference/cr_editor.md](../reference/cr_editor.md)
- Boot context: [docs/claude/boot.md](../claude/boot.md)
