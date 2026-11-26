"""Rebuild the web build's fallback fonts from the characters the app actually shows.

A browser canvas has no system fonts, so the web app ships two small fonts: the app's emoji, cut from
Noto Color Emoji, and a few punctuation marks, cut from DejaVu Sans. Run this after adding an emoji or
symbol to any string in app/src/commonMain:

    pip install fonttools
    python3 tools/subset_web_fonts.py /path/to/NotoColorEmoji.ttf /path/to/DejaVuSans.ttf

(On Debian/Ubuntu: fonts-noto-color-emoji and fonts-dejavu-core put them in /usr/share/fonts/truetype.)
"""
import glob
import sys

from fontTools import subset
from fontTools.ttLib import TTFont

OUT = "app/src/wasmJsMain/resources/fonts"


def used_characters():
    chars = set()
    for path in glob.glob("app/src/commonMain/kotlin/**/*.kt", recursive=True):
        if path.endswith(("Pieces.kt", "AppIcons.kt")):
            continue
        chars.update(ch for ch in open(path, encoding="utf-8").read() if ord(ch) > 0x7E)
    return sorted(ord(c) for c in chars)


def cut(source, codepoints, out):
    options = subset.Options()
    options.layout_features = ["*"]
    font = TTFont(source)
    subsetter = subset.Subsetter(options)
    subsetter.populate(unicodes=codepoints)
    subsetter.subset(font)
    font.save(out)
    return font.getBestCmap()


def main(emoji_font, text_font):
    wanted = used_characters()
    emoji_cmap = TTFont(emoji_font).getBestCmap()
    in_emoji = [c for c in wanted if c in emoji_cmap or c == 0xFE0F]
    cut(emoji_font, in_emoji, f"{OUT}/emoji.ttf")
    rest = [c for c in wanted if c not in emoji_cmap and c != 0xFE0F]
    got = cut(text_font, rest, f"{OUT}/symbols.ttf")
    missing = [chr(c) for c in rest if c not in got]
    print(f"emoji.ttf: {len(in_emoji)} characters; symbols.ttf: {len(rest)} characters")
    if missing:
        print("No font has:", " ".join(missing))
        sys.exit(1)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
