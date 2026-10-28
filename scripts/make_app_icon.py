"""Android vector drawables for the KaPosts icon - the iOS design/app-icon/make_feed_icon.py
light artwork, shape for shape. Run from the repo root: python3 scripts/make_app_icon.py app/src/main/res
Writes drawable/ic_launcher_foreground.xml (the adaptive icon's foreground; the background is
@color/ic_launcher_background, white), drawable/kaposts_app_mark.xml and
drawable/kaposts_app_mark_dark.xml (the setup screen's logo, light and dark - iOS KaPostsLogo and
KaPostsLogo-dark) and drawable/ic_notification.xml (the notification small icon)."""
import sys, os
res = sys.argv[1]

def rrect(x, y, w, h, r):
    return (f"M{x+r},{y} h{w-2*r} a{r},{r} 0 0 1 {r},{r} v{h-2*r} a{r},{r} 0 0 1 -{r},{r} "
            f"h-{w-2*r} a{r},{r} 0 0 1 -{r},-{r} v-{h-2*r} a{r},{r} 0 0 1 {r},-{r} z")

def circle(cx, cy, r):
    return f"M{cx-r},{cy} a{r},{r} 0 1,0 {2*r},0 a{r},{r} 0 1,0 -{2*r},0 z"

# The backward K exactly as KaChat's app icon draws it, traced from KaChat's 1024 px icon: the
# bent bar and the thick chevron, in that icon's pixels (iOS KaPosts 499275c `K_BAR`/`K_CHEVRON`).
# Its bounding box is 360..665 x 254..690, centred on (512.9, 472).
K_BAR = [(556, 268), (632, 254), (665.4, 472), (632, 690), (556, 676), (588.1, 472)]
K_CHEVRON = [(407.9, 293.6), (636.2, 472), (407.9, 650.4), (360.4, 587.9), (508.8, 472), (360.4, 356.1)]
K_CENTRE, K_HEIGHT = (512.9, 472), 436

def k_mark(cx, cy, height, color):
    # the backward K, `height` px tall, its bounding box centred on (cx, cy)
    s = height / K_HEIGHT
    def path(shape):
        return "M" + " L".join(f"{cx + s*(x - K_CENTRE[0]):.1f},{cy + s*(y - K_CENTRE[1]):.1f}" for x, y in shape) + " Z"
    return (f'<path android:fillColor="{color}" android:pathData="{path(K_BAR)}"/>\n        '
            f'<path android:fillColor="{color}" android:pathData="{path(K_CHEVRON)}"/>')

HEART = "M712,690 c-26,-34 -78,-14 -64,26 c8,24 40,44 64,62 c24,-18 56,-38 64,-62 c14,-40 -38,-60 -64,-26 Z"

# iOS make_feed_icon.py's PALETTES, as Android colours (#AARRGGBB where iOS has an rgba()):
# background, back cards (far, near), avatar, K, lines (title, body), heart, and the drop shadow's
# two layers.
PALETTES = {
    "light": dict(bg="#FFFFFF", far="#DDF3EE", near="#B5E5DC", avatar="#FFFFFF", k="#5BBDAC",
                  title="#EBFFFFFF", body="#94FFFFFF", heart="#FFFFFF", shadow=("#0D000000", "#12000000")),
    # rgba(27,32,41,0.85) / 0.45; the shadow at iOS's 45% rather than 16%.
    "dark": dict(bg="#1B2029", far="#2A4B47", near="#3F7F75", avatar="#1B2029", k="#8AD6C9",
                 title="#D91B2029", body="#731B2029", heart="#1B2029", shadow=("#24000000", "#33000000")),
}

def art(include_bg, variant="light"):
    c = PALETTES[variant]
    p = []
    if include_bg:
        p.append(f'<path android:fillColor="{c["bg"]}" android:pathData="M0,0 h1024 v1024 h-1024 z"/>')
    p.append(f'<path android:fillColor="{c["far"]}" android:pathData="{rrect(304,168,416,220,64)}"/>')
    p.append(f'<path android:fillColor="{c["near"]}" android:pathData="{rrect(244,238,536,220,72)}"/>')
    # The SVG's soft drop shadow (dy 12, 16 blur), as two faint offset layers: vector drawables
    # have no blur.
    p.append(f'<path android:fillColor="{c["shadow"][0]}" android:pathData="{rrect(166,328,692,504,92)}"/>')
    p.append(f'<path android:fillColor="{c["shadow"][1]}" android:pathData="{rrect(170,324,684,500,90)}"/>')
    p.append(f'''<path android:pathData="{rrect(172,318,680,500,88)}">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="0" android:startY="318" android:endX="0" android:endY="818">
                <item android:offset="0" android:color="#FF8AD6C9"/>
                <item android:offset="1" android:color="#FF5BBDAC"/>
            </gradient>
        </aapt:attr>
    </path>''')
    p.append(f'<path android:fillColor="{c["avatar"]}" android:pathData="{circle(316,462,86)}"/>')
    p.append(k_mark(316, 462, 132, c["k"]))
    p.append(f'<path android:fillColor="{c["title"]}" android:pathData="{rrect(440,420,320,36,18)}"/>')
    for (x, y, w) in ((440, 482, 200), (246, 598, 532), (246, 660, 380)):
        p.append(f'<path android:fillColor="{c["body"]}" android:pathData="{rrect(x,y,w,36,18)}"/>')
    p.append(f'<path android:fillColor="{c["heart"]}" android:pathData="{HEART}"/>')
    return "\n        ".join(p)

HEADER = '''<?xml version="1.0" encoding="utf-8"?>
<!-- {comment} -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="{size}dp"
    android:height="{size}dp"
    android:viewportWidth="{vp}"
    android:viewportHeight="{vp}">
    <group android:translateX="{off}" android:translateY="{off}">
        {body}
    </group>
</vector>
'''

# Adaptive foreground: a 108dp layer whose middle 72dp shows, so the 1024 artwork sits in the
# middle two thirds of a 1536 viewport.
open(os.path.join(res, "drawable", "ic_launcher_foreground.xml"), "w").write(HEADER.format(
    comment="KaPosts launcher icon, foreground layer: the iOS app icon (design/app-icon in KaPosts-iPhone) - a stack of post cards, the top one a post whose avatar is the Kaspa K. Generated by scripts/make_app_icon.py.",
    size=108, vp=1536, off=256, body=art(False)))
# The setup screen's logo (iOS KaPostsLogo): the whole square icon, background included.
open(os.path.join(res, "drawable", "kaposts_app_mark.xml"), "w").write(HEADER.format(
    comment="KaPosts icon as one square picture, for the setup screen (iOS KaPostsLogo). Generated by scripts/make_app_icon.py.",
    size=120, vp=1024, off=0, body=art(True)))
# Its dark appearance (iOS KaPostsLogo-dark), picked by the setup screen in the dark theme.
open(os.path.join(res, "drawable", "kaposts_app_mark_dark.xml"), "w").write(HEADER.format(
    comment="KaPosts icon as one square picture, dark appearance, for the setup screen (iOS KaPostsLogo-dark). Generated by scripts/make_app_icon.py.",
    size=120, vp=1024, off=0, body=art(True, "dark")))

# Notification small icon: the cards and the post's avatar as a white silhouette (Android tints it).
cards = " ".join([rrect(304,168,416,220,64), rrect(244,238,536,220,72)])
open(os.path.join(res, "drawable", "ic_notification.xml"), "w").write(f'''<?xml version="1.0" encoding="utf-8"?>
<!-- KaPosts notification icon: the app icon's card stack as a silhouette, the top card's avatar
     and heart cut out. Generated by scripts/make_app_icon.py. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="1024"
    android:viewportHeight="1024">
    <path android:fillColor="#80FFFFFF" android:pathData="{cards}"/>
    <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="{rrect(172,318,680,500,88)} {circle(316,462,86)} {HEART}"/>
</vector>
''')
print("written")
