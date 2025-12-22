# Application Icons

This directory should contain application icons for different platforms.

## Required Icon Files

| File | Platform | Format | Minimum Size |
|------|----------|--------|--------------|
| `app-icon.ico` | Windows | ICO | 256x256 (multi-resolution recommended) |
| `app-icon.icns` | macOS | ICNS | 512x512 or 1024x1024 |
| `app-icon.png` | Linux | PNG | 256x256 or 512x512 |

## Creating Icons

### From a source image (PNG/SVG)

1. Create a high-resolution source image (at least 1024x1024 PNG or SVG)
2. Use one of these tools to generate platform-specific icons:

**Online Tools:**
- https://icoconvert.com/ (for ICO)
- https://cloudconvert.com/png-to-icns (for ICNS)

**Command Line (ImageMagick):**
```bash
# Windows ICO (multi-resolution)
convert source.png -define icon:auto-resize=256,128,64,48,32,16 app-icon.ico

# macOS ICNS (requires iconutil on Mac)
mkdir icon.iconset
sips -z 16 16 source.png --out icon.iconset/icon_16x16.png
sips -z 32 32 source.png --out icon.iconset/icon_16x16@2x.png
sips -z 32 32 source.png --out icon.iconset/icon_32x32.png
sips -z 64 64 source.png --out icon.iconset/icon_32x32@2x.png
sips -z 128 128 source.png --out icon.iconset/icon_128x128.png
sips -z 256 256 source.png --out icon.iconset/icon_128x128@2x.png
sips -z 256 256 source.png --out icon.iconset/icon_256x256.png
sips -z 512 512 source.png --out icon.iconset/icon_256x256@2x.png
sips -z 512 512 source.png --out icon.iconset/icon_512x512.png
sips -z 1024 1024 source.png --out icon.iconset/icon_512x512@2x.png
iconutil -c icns icon.iconset -o app-icon.icns

# Linux PNG
convert source.png -resize 256x256 app-icon.png
```

## Suggested Icon Design

For Mini-IDE, consider an icon that represents:
- A document/text editor (page with lines)
- Writing/creativity (pen, quill)
- Code/IDE aesthetic (brackets, terminal)
- A combination showing it's a writing tool for creative projects

## No Icon Available?

If no icon files are present, jpackage will use a default Java icon.
The application will still work normally.
