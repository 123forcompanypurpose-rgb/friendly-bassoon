# UI.md — Midnight Library (Liquid Glass v2)

Book reading web app. Dark, mystical, premium. Everything is frosted glass floating over a glowing violet night sky.

## 1. Design Direction
- **Liquid glassmorphism**: translucent panels, heavy blur, bright edge highlights, soft deep shadows.
- **Depth**: floating 3D glass orbs and bubbles overlapping panels.
- **Glow**: violet glowing pill buttons, gold glowing progress.
- **Mood**: calm, cozy, late-night reading. One gold accent only.
- Inspiration: liquid glass UI kits, PinkMelo glass layouts, lavender glass portfolio, tarot/gold ornament, gamified streaks.

## 2. Tokens

### Color
| Token | Value |
|---|---|
| bg-deep | `#0D0722` |
| blob-violet | `#7C4DFF` |
| blob-magenta | `#C46BFF` |
| blob-indigo | `#3D2BB0` |
| primary | `#A47BFF` → `#6A3DF0` (gradient) |
| gold accent | `#FFD98A` |
| text-primary | `#FFFFFF` |
| text-soft | `#EFE8FF` |
| text-muted | `#D9CEFF` |
| glass-border | `rgba(255,255,255,.22)` |

Light mode ("Lavender Paper"): bg `#F2F0FF`, glass `rgba(255,255,255,.55)`, text `#1B1236`.
Reader themes: Midnight Violet, Lavender Paper, Sepia Hearth (`#F4ECD8` / `#3B2F1E`).

### Glass (core recipe)
```css
.glass {
  background: linear-gradient(135deg, rgba(255,255,255,.17), rgba(255,255,255,.04));
  backdrop-filter: blur(26px) saturate(150%);
  border: 1px solid rgba(255,255,255,.22);
  box-shadow: inset 0 1px 0 rgba(255,255,255,.38), 0 24px 60px rgba(8,2,30,.45);
  border-radius: 28px;
}
```

### Background
```css
background:
  radial-gradient(800px 560px at 12% 8%, #7C4DFF, transparent 60%),
  radial-gradient(640px 560px at 92% 28%, #C46BFF99, transparent 60%),
  radial-gradient(800px 640px at 50% 105%, #3D2BB0, transparent 60%),
  #0D0722;
```

### Glass orb
```css
.orb {
  position: absolute; border-radius: 50%;
  background: radial-gradient(circle at 30% 28%, rgba(255,255,255,.95), rgba(200,170,255,.4) 35%, rgba(120,80,230,.25) 65%, rgba(255,255,255,.06));
  box-shadow: inset 0 0 30px rgba(255,255,255,.4), 0 20px 40px rgba(60,20,160,.45);
}
```

### Typography
- Titles, book text: **Playfair Display** (500/600, italic for quotes)
- UI: **Inter** (400/500/600)
- Labels: 11px, uppercase, letter-spacing .2em
- Reader body: serif 18px, line-height 1.75 (adjustable 14-26px)

### Shape & motion
- Cards 28px, nav and buttons fully round, covers 14px
- Transitions 200-300ms ease-out; hover = stronger glow + slight lift
- Progress bar: gradient `#8A5CFF → #FFD98A` with glow

## 3. Components
- **Nav pill**: glass, logo left, links center (active = glowing violet chip), streak chip right.
- **Primary button**: violet gradient pill, white 40% border, glow `0 0 32px rgba(140,90,255,.65)`.
- **Chip**: glass pill, 13px Inter.
- **Book cover**: gradient + gold 1px border + violet glow.
- **Book card**: glass, cover, serif title, rating + author.
- **Progress ring**: conic-gradient gold on a dark center.
- **Quote block**: gold-tinted glass (`rgba(255,217,138,.1)`, gold border).
- **Bottom reader bar**: floating glass pill with prev/next, progress, Aa, theme.
- **Side cards (reader)**: Almanac, Bookmark, Whispers.

## 4. Screens
1. **Home**: nav, hero "Resume reading", goal ring, atmosphere, curations row
2. **Reading Room**: glass reading panel, side cards, floating progress bar
3. **Library**: tabs (Reading / Want / Finished), grid of book cards
4. **Book Detail**: large cover with glow, synopsis glass card, Start Reading
5. **Audio Player**: circular cover, glass controls
6. **Stats**: streak heatmap, XP ring, badges with gold frames
7. **Login**: centered glass card over aurora background

## 5. Stitch Prompts

### Master style
```
Design a desktop web app called "Midnight Library", a nocturnal book reading app, in a liquid glassmorphism style. Background: deep violet-black (#0D0722) with large soft glowing blobs in violet (#7C4DFF), magenta (#C46BFF), indigo (#3D2BB0). Every panel is frosted translucent glass: white fill 5-15%, heavy blur, 1px light border, bright inner top highlight, soft deep shadow, 28px radius. Floating 3D glass orbs overlapping panels for depth. Pill-shaped glass nav, glowing violet pill buttons. Gold (#FFD98A) is the only accent (progress, streak, labels). Playfair Display for titles and book text, Inter for UI, small uppercase letter-spaced labels. Gold-edged book covers with violet glow. High-contrast text. Calm, premium, mystical. No emoji.
```

### Home
```
Using the style above, design HOME: glass pill nav (logo, Explore / Reading Room / Chronicles / Curators, streak chip). Large glass hero card with book cover, "The Starless Sea", author, glowing progress bar (64%), a quote, "Resume reading" and "Whisper audio" buttons. Right: daily goal ring (24/40 pages) and atmosphere card (Vinyl & Rain, Midnight Thunder, Library Hearth). Bottom: "Midnight Curations" with 5 glass book cards.
```

### Reading Room
```
Using the same style, design READING ROOM: large glass reading panel with serif chapter title, comfortable body text, highlighted quote block. Right: three glass cards (Almanac, Bookmark, Whispers). Bottom: floating glass pill with previous/next chapter, glowing progress bar (page 284 of 512), font size and theme switch (Midnight, Lavender, Sepia).
```

### Library
```
Same style, design LIBRARY: glass segmented tabs (Reading, Want to Read, Finished), search field, filter chips, grid of glass book cards with gold-edged covers and thin glowing progress bars, grid/list toggle, floating glowing add button.
```

### Book Detail
```
Same style, design BOOK DETAIL: large cover with violet glow and glass reflection on the left; serif title, author, rating, genre chips, reading time on the right. Glass synopsis card, glowing "Start Reading" button, glass "Add to shelf" and "Listen" buttons. Tabs (About, Reviews, Similar) and a row of similar books.
```

### Audio Player
```
Same style, design AUDIO PLAYER: large circular cover inside concentric glass rings with a gold progress arc, serif title, spaced uppercase author, glass progress slider, large glowing violet play button, speed and sleep timer chips.
```

### Stats
```
Same style, design STATS: level and XP ring, streak calendar heatmap in violet shades, weekly pages chart, genre donut, goals, and a grid of glowing badges (locked and unlocked, some with thin gold ornate frames).
```

### Login
```
Same style, design LOGIN: centered large frosted glass card over aurora violet background with glass orbs. Serif "Welcome back", glass pill inputs with icons, show/hide password, Remember me, glowing "Log in" button, "Continue with Google" glass button, sign-up link.
```

## 6. Tweak Prompts
- Too dark: "lighter lavender glass, pastel pink-violet sky background"
- Too busy: "fewer decorations, more whitespace, one accent per section"
- More 3D: "add dripping glass shapes and crystal formations like a surreal beauty brand layout"
- Readability: "increase text contrast on glass, stronger blur"

## 7. Dev Notes
- Use `backdrop-filter` with a `-webkit-` fallback; provide a solid `rgba` fallback if unsupported.
- Keep the reader panel plain and fast; heavy effects only on dashboard, login and stats.
- Check WCAG AA contrast on every glass card.
- Stack: React/Next.js + Tailwind; map tokens above into the Tailwind config.
