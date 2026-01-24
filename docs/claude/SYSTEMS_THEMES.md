# SYSTEMS_THEMES

## 8. Theme Support Integration

### 8.1 Warmth Slider

**Location**: Top toolbar or Appearance settings

**Implementation:**
- Slider from -100 (cool) to +100 (warm)
- 0 = neutral (original theme colors)
- Adjusts HSL hue values programmatically

```javascript
function applyWarmth(warmthValue) {
  // warmthValue: -100 to +100
  const hueShift = warmthValue * 0.5; // max ±50° shift

  document.documentElement.style.setProperty(
    '--warmth-hue-shift',
    `${hueShift}deg`
  );
}

// In CSS
:root {
  --base-hue: 210; /* blue */
  --warmth-hue-shift: 0deg;
  --active-hue: calc(var(--base-hue) + var(--warmth-hue-shift));
  --accent-color: hsl(var(--active-hue), 60%, 50%);
}
```

### 8.2 Day/Night Mode

- Already exists in settings
- Widgets respect current theme
- Smooth transition animation (300ms)

### 8.3 Custom Themes

Future: Theme import/export system
- Themes define color palettes
- Warmth slider works across all themes
- Widget styling adapts automatically


### 6. CSS Variables for Theming

```css
:root {
  /* Color system */
  --warmth-hue-shift: 0deg;
  --base-hue: 210;
  --active-hue: calc(var(--base-hue) + var(--warmth-hue-shift));

  /* Widget colors */
  --widget-bg: var(--bg-light);
  --widget-bg-hover: var(--bg-lighter);
  --widget-border: var(--border-color);
  --widget-border-hover: var(--accent-color);
  --widget-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  --widget-shadow-hover: 0 8px 24px rgba(0, 0, 0, 0.2);

  /* Widget sizes */
  --widget-padding: 16px;
  --widget-gap: 16px;
  --widget-border-radius: 12px;

  /* Animations */
  --widget-transition: 0.2s ease-out;
  --widget-mount-duration: 0.3s;
}
```


## Post-Implementation: Future Enhancements

**Not in scope for v1, but documented for later:**

### 3. Widget Themes (v2)
- Users can theme widgets independently
- Widget theme packs (e.g., "Dark Forest," "Sunset Beach," "Cyberpunk Neon")
- Theme sharing/import (like agents)
- Widgets adapt to theme automatically

### 4. Complete UI Theme System (v3 - BIG FEATURE)

**Vision:** Visual theme builder for the ENTIRE app (workbench + editor + modals).

**Theme Builder Interface:**
```
┌─────────────────────────────────────────────────────────┐
│  Theme Builder                              [Export]    │
├─────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌──────────────────────────────────┐ │
│  │ Live Preview│  │ Color Palette                    │ │
│  │             │  │ ┌───┐ Primary   #0e639c          │ │
│  │  [Button]   │  │ ┌───┐ Accent    #1177bb          │ │
│  │  [Input]    │  │ ┌───┐ BG Dark   #1e1e1e          │ │
│  │  [Card]     │  │ ┌───┐ BG Light  #2a2a2e          │ │
│  │             │  │ ┌───┐ Text      #cccccc          │ │
│  └─────────────┘  └──────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Typography                                       │  │
│  │ Font Family: [Dropdown: Inter, Roboto, etc.]    │  │
│  │ Base Size:   [Slider: 12px - 18px]              │  │
│  │ Heading Weight: [400 / 500 / 600 / 700]         │  │
│  └──────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Spacing & Borders                                │  │
│  │ Border Radius: [Slider: 0px - 20px]             │  │
│  │ Padding Scale:  [Slider: 0.8x - 1.5x]           │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

**How it works:**
1. Click any UI element in preview → selects that component type
2. Adjust color/font/spacing → all instances update live
3. Preview shows: buttons, inputs, cards, modals, editor, widgets
4. Name your theme, save, export as `.crtheme` file
5. Import/share themes with community

**Theme File Format:**
```json
{
  "name": "Moonlit Writer",
  "author": "YourName",
  "version": "1.0.0",
  "colors": {
    "primary": "#4a5f7a",
    "accent": "#7395ae",
    "bg-dark": "#1a1a2e",
    "bg-light": "#16213e",
    "text": "#e0e0e0"
  },
  "typography": {
    "fontFamily": "Inter",
    "baseFontSize": 14,
    "headingWeight": 600
  },
  "spacing": {
    "borderRadius": 12,
    "paddingScale": 1.0
  },
  "advanced": {
    "customCSS": "/* Optional CSS overrides */"
  }
}
```

**Implementation approach:**
- CSS variables for everything (already started with warmth slider!)
- Theme loader applies theme variables to `:root`
- Component styles reference variables
- Visual editor updates variables in real-time
- Export theme = JSON file with all variable values
