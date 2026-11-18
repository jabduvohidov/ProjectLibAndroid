# Universal Design System — v2.2.0

Single-file specification of the design system for Web (React + Tailwind), iOS & macOS (SwiftUI) and Android (Jetpack Compose). Give this file to any designer, developer or AI agent. Machine-readable tokens: `tokens.json` (source) and `tokens.dtcg.json` (W3C / Figma Tokens Studio). Ready-made code kits: `assets/kits/{web,apple,android}` in the skill.

---

## 1. Foundations

**Style in one line:** iOS / macOS 27 look (Liquid Glass) — lots of air, neutral grays with a faint green tint, exactly one accent color (emerald-teal `#0E8577`; dark mode bright `#2DD4BF` with dark text), capsule controls, rounder continuous corners (cards 20, sheets 28), a translucent glass layer for navigation floating above solid content, almost no shadows.

### Principles (the "why")

- **One accent, used sparingly.** Teal means "this is interactive / this is the main thing". If everything is teal, nothing stands out. Per screen: one primary (filled accent) button max; links and selected states use `accentText` / `accentSubtle`.
- **Two layers: glass navigation over solid content.** Bars, tab bars, sidebars, sheets, menus, floating buttons and toasts form a translucent "Liquid Glass" layer (native material on Apple, `.glass` class on web, tonal surfaces on Android). Content — cards, lists, forms, charts — stays solid and calm. Never put glass on content, never stack glass on glass.
- **Hierarchy through type & space, not decoration.** Titles bold, secondary text gray, generous spacing between groups. No gradients, no heavy borders, no colored section backgrounds.
- **Native shell, shared skin.** Colors, type scale, spacing, radii and component look are identical everywhere. Navigation and system patterns follow each platform (tab bar on iOS, bottom navigation bar + FAB on Android, sidebar on macOS / desktop web). Users trust apps that behave like their OS.
- **Light and dark are both first-class.** Every color is a semantic token with a light and dark value — use the token and dark mode works for free. In dark mode depth comes from lighter surfaces + 1px borders, not shadows.
- **Accessible by default.** All text pairs pass WCAG AA (checked by `scripts/build_tokens.py --check`). Touch targets ≥ 44pt (Apple) / 48dp (Android). Respect Dynamic Type / font scaling and Reduce Motion. Never convey meaning by color alone — pair with icon or text.
- **Every data screen has 4 states:** loading (skeleton), empty, error, content. Form/flow screens: initial-data loading, validation errors, submitting, success, failure.
- **One primary action per layer:** a screen and each sheet/dialog on top of it may each have one filled accent action.

### Semantic colors

Use these names, never palette names or raw hex in UI code.

| Token | Light | Dark | Use for |
|---|---|---|---|
| `bg` | #F2F5F4 | #050807 | app/window background (canvas) |
| `surface` | #FFFFFF | #121816 | cards, list groups, inputs |
| `surfaceRaised` | #FFFFFF | #1A2220 | menus, popovers, dialogs (solid fallback for glass) |
| `fill` | #E8EEEC | #1F2826 | secondary buttons, chips, hover rows, skeletons |
| `border` | #DDE4E2 | #26302E | hairlines, card outlines, dividers |
| `borderStrong` | #C4CFCC | #36423F | input outlines |
| `textPrimary` | #0B1513 | #F2F7F5 | titles, body text |
| `textSecondary` | #4A5754 | #A5B1AE | subtitles, descriptions, labels |
| `textTertiary` | #5F6D6A | #808C89 | placeholders, timestamps, captions |
| `textOnAccent` | #FFFFFF | #03241F | text/icons on accent fill (dark mode: bright accent + dark text) |
| `textOnDanger` | #FFFFFF | #FFFFFF | text/icons on dangerFill |
| `accent` | #0E8577 | #2DD4BF | primary buttons, selected states, active tab, toggles, progress |
| `accentHover` | #0F766E | #5EEAD4 | hover/pressed accent fill |
| `accentSubtle` | #E3F7F3 | #0B2A26 | selected row, accent badge, icon boxes, info banners |
| `accentText` | #0B6E64 | #5EEAD4 | links, accent text/icons on surfaces |
| `focusRing` | #14B8A6 | #2DD4BF | 2px focus outline with 2px offset |
| `success` | #15803D | #4ADE80 | positive amounts, success icons/text |
| `successSubtle` | #ECFDF3 | #06281A | success banner/badge bg |
| `warning` | #B54708 | #FDB022 | warning icons/text |
| `warningSubtle` | #FFFAEB | #3B1D04 | warning banner/badge bg |
| `danger` | #B42318 | #F97066 | errors, destructive text/icons |
| `dangerFill` | #D92D20 | #D92D20 | destructive button fill (white text) |
| `dangerSubtle` | #FEF3F2 | #3D0F0B | error banner/badge bg |
| `overlay` | #0B151366 | #00000099 | scrim behind modals/sheets |
| `glass` | #FFFFFF73 | #28323073 | Liquid Glass body tint (web) — see glass tokens |
| `glassStrong` | #FFFFFFC7 | #1A2220C7 | glass for large panels (dialogs, menus) — more opaque for legibility |
| `glassHighlight` | #FFFFFFE6 | #FFFFFF40 | specular rim highlight (top-left light) |
| `glassEdge` | #0B15131F | #00000080 | darkened outer edge (iOS 27) |

Charts: main series `accent`, comparison series grays (`textTertiary`, `borderStrong`), positive/negative `success`/`danger`, grid lines `border`.

### Typography

Same numbers in px (web) / pt (Apple) / sp (Android). Apple maps to native text styles so Dynamic Type keeps working (sizes may differ ±1pt — intentional).

| Token | Size / line | Weight | Apple | Material 3 |
|---|---|---|---|---|
| display | 34 / 40 | bold | largeTitle | displaySmall |
| title1 | 28 / 34 | bold | title | headlineMedium |
| title2 | 22 / 28 | semibold | title2 | headlineSmall |
| title3 | 20 / 26 | semibold | title3 | titleLarge |
| headline | 17 / 24 | semibold | headline | titleMedium |
| **body** | **16 / 24** | regular | body | bodyLarge |
| callout | 15 / 22 | regular | callout | bodyMedium |
| subheadline | 14 / 20 | medium | subheadline | labelLarge |
| footnote | 13 / 18 | regular | footnote | bodySmall |
| caption | 12 / 16 | medium | caption | labelMedium |

Fonts: **Inter** on web, **SF Pro** (system) on Apple, **Roboto** (system) on Android. Numbers and money use tabular figures. Titles get slight negative tracking (already in tokens).

### Spacing, radius, sizes

- **Spacing (4-pt grid):** xxs 2 · xs 4 · sm 8 · md 12 · lg 16 · xl 24 · 2xl 32 · 3xl 48 · 4xl 64.
  Screen edge padding 16 (phone) / 24 (tablet, desktop). Inside a group 8–12. Between groups/sections 24–32.
- **Radius:** xs 4 (tags) · sm 8 (icon boxes) · md 12 (inputs, selects, banners) · lg 20 (cards, list groups) · xl 28 (sheets, dialogs, popovers) · **full = capsule for every control** (buttons, segmented controls, chips, search fields, tab bar). Nested shapes are concentric: inner radius = outer radius − padding. Apple: continuous corners.
- **Controls:** height sm 32 / md 44 / lg 52 (sticky CTA); compact desktop 28–32. List row ≥ 56 (compact 36–40). Icons 16 / 20 / 24. Avatars 32 / 40 / 56. Icon box 36–40.
- **Layout:** web container max 1200, reading width 680, sidebar 248 (web) / 220 (macOS), header 64 (web desktop) / 56 (mobile web).
- **Breakpoints (web):** sm 640 · md 768 · lg 1024 · xl 1280.

### Glass, elevation & motion

- **Glass layer — what makes it read as glass (iOS 27):** a clear, thin body (not milky), the background behind it blurred lightly (14px) and *more* saturated, a top sheen, a bright specular rim on the top edge, a softer bright rim on the bottom (refraction), a darkened 0.5px outer edge, an inner glow (thickness), a soft depth shadow, and slight growth on press. Apple — real Liquid Glass (system bars, `.glassEffect`, `.glass` / `.glassProminent`). Web — `.glass` (regular), `.glass-strong` (dialogs/menus, more opaque), `.glass-tinted` (prominent = accent seen through glass), `.glass-interactive`. Android — Material tonal surfaces by default; optional glass mode (`designsystem/glass`, Haze `hazeGlass`) that switches itself on only on capable devices (Android 12+, blur enabled, no battery saver).
- **Where glass goes:** tab bar, floating top buttons (bell, avatar, back, search), floating action buttons, toasts, sheets, menus, popovers. **Where it never goes:** cards, list rows, inputs inside content, in-content buttons, whole page headers with text.
- **No header bars:** on phones there is no solid top bar. Glass buttons float top-right, the large title is part of the content, and content dissolves under the floating controls via the **scroll-edge effect** (`.scroll-edge-top` / `.scroll-edge-bottom` on web, system default + `.dsScrollEdges()` on Apple).
- **Tab bar (iOS 27):** floating glass capsule inset 16 from the edges; selected tab sits in a lighter "lens" pill with accent icon + label; search is a separate round glass button on the trailing side; scrolling down shrinks the bar to the selected icon, scrolling up restores it.
- **Glass needs color behind it.** Over flat gray it looks like nothing — content (cards with brand gradients, photos, charts) supplies the color; web pages may use the opt-in `ambient` canvas (faint accent wash).
- **Reduced transparency / increased contrast:** glass turns into solid `surfaceRaised` with a border, automatically.
- **Increase Contrast:** `textSecondary`, `textTertiary`, `border`, `borderStrong`, `accent`, `accentText` have high-contrast variants (`lightHC` / `darkHC` in tokens.json), applied automatically on Apple and web (`prefers-contrast: more`).

- Shadows (light only): sm `0 1 2 #1018280D` (inputs), md `0 4 12 #10182814` (hover cards, dropdowns), lg `0 12 32 #1018281F` (modals, toasts). Dark mode: `surfaceRaised` + 1px `border` instead.
- **Motion = springs, identical on every platform.** `standard` spring (response 0.35 s, damping 0.85 — barely overshoots) for anything that moves or resizes: tab lens, minimize, morphs, sheets. `bouncy` spring (response 0.4 s, damping 0.68 — visible overshoot) for glass reacting to touch and glass materializing. Apple: `DS.Motion.spring` / `.bouncy`; Android: `DSMotion.spring()` / `DSMotion.bouncy()` (stiffness = (2π/response)², dampingRatio = damping); web: `var(--easing-spring-standard)` + `var(--duration-spring-standard)` (CSS `linear()` curves generated from the same physics). Plain fades: fast 120 ms, base 200 ms.
- **The iOS 27 motion set** (implemented on all three platforms):
  1. *Selection lens* springs under the chosen tab.
  2. *Minimize on scroll*: tab bar shrinks to the active item while scrolling down, restores on scroll up.
  3. *Glass press*: glass grows to ×1.06 under the finger (bouncy) and a highlight blooms from the touch point; solid content buttons shrink to ×0.97 instead.
  4. *Glass morph*: a glass button flows into a menu of glass actions and melts back (Apple `GlassEffectContainer` + `glassEffectID`; Android Haze `hazeGlassContainer` + spring size; web `GlassMorphMenu`).
  5. *Container transform*: a button grows into its sheet and shrinks back (Apple `.matchedTransitionSource` + `.navigationTransition(.zoom)`; Android `SharedTransitionLayout`; web `morphFrom` / `Dialog origin`).
  6. *Materialize*: new glass appears with scale 0.6 → 1 + de-blur (bouncy), staggered 35 ms.
- **Reduce motion** (iOS Reduce Motion, Android "Remove animations", `prefers-reduced-motion`): no scaling, morphing or overshoot — every movement becomes a ≤ 160 ms crossfade. Animate only opacity / transform.

### Icons

Lucide on web (stroke 1.75), SF Symbols on Apple (hierarchical rendering), Material Symbols **Rounded** on Android. Same meaning → same metaphor across platforms (house = home, arrow.left.arrow.right = transfers, gear = settings).

### Density

- **Comfortable** (iOS, Android, mobile web): control 44–48, row 56, body 16.
- **Compact** (macOS, desktop web dashboards/admin): control 28–32, row 36–40, body 13–14 on macOS (native control sizes), radius sm 8 for controls. Colors and hierarchy stay the same.

### Content & fintech rules

- UI copy: short, sentence case ("Save changes"), verbs on buttons, language of the product (Russian / Tajik / English as needed).
- Money: tabular digits; currency after the amount for TJS/RUB ("1 250,00 TJS"); non-breaking space (U+00A0) for thousands; true minus "−".
- Positive amounts `success` with "+"; negative amounts stay `textPrimary` with "−" (don't paint every expense red). Offer a hide-balance (eye) toggle next to balances.

---

## 2. Layouts — what goes where

The skin (colors, type, spacing) is shared. The shell (navigation, where actions live) follows each platform.

### Contents
1. Universal screen anatomy
2. Navigation per platform
3. Where actions go
4. Common screen recipes (list, detail, form, settings, dashboard, auth, onboarding)
5. Responsive web grid

---

### 1. Universal screen anatomy (top → bottom)

```
┌─────────────────────────────────────┐
│ Header: title (left) · actions (right)│  ← navigation bar / top app bar / page header
├─────────────────────────────────────┤
│ Optional: search / segmented filter  │
│ Optional: summary (key number, hero) │  ← one focal point per screen
│ Content groups (cards / list groups) │  ← 24–32 between groups, 8–12 inside
│ …                                    │
├─────────────────────────────────────┤
│ Sticky primary action (mobile flows) │  ← full-width, 16 from edges, above safe area
│ Tab bar / bottom nav (top-level only)│
└─────────────────────────────────────┘
```

- Screen padding: 16 (phones), 24 (tablet, desktop). Content never touches the edge.
- Section header: `subheadline` (14 medium) in `textSecondary`, 8 above its group, optional trailing "See all" link in `accentText`.
- Group content in `surface` cards with radius `lg` (20) on `bg` background. In dark mode cards get a 1px `border`.
- Reading content max width 680; app content max width 1200 (web).

### 2. Navigation per platform

| | iOS | Android | macOS | Web desktop (≥1024) | Web mobile (<768) |
|---|---|---|---|---|---|
| Top-level sections (3–5) | Floating glass `TabView` tab bar (minimizes on scroll) + separate Search tab at trailing end | `NavigationBar` bottom (≤5); `NavigationRail` on tablets. Glass mode: `DSGlassNavigationBar` floating capsule + search button | Edge-to-edge `NavigationSplitView` sidebar (220), accent-tinted icons | Left sidebar 248, collapsible to 72 | Floating glass capsule tab bar (≤5), inset 16 from edges |
| Screen title | Large title, collapses on scroll | `LargeTopAppBar`/`MediumTopAppBar`, collapses. Glass mode: title + glass icon buttons over a scroll-edge fade | Uniform glass toolbar, title leading | Page header `title1` inside content | `title2` sticky header 56 |
| Back | `<` top-left, swipe-back | System back + top-left arrow | Sidebar selection / toolbar back for drill-down | Breadcrumbs above page title | `<` icon top-left |
| Search | Search tab / bottom search field (`.searchable`) | Search bar in top app bar | Toolbar search field (trailing) | Header search center/left, ⌘K | Icon in header → full-screen search |
| Account / profile | Last tab or avatar top-right | Avatar top-right in top app bar | Sidebar bottom or app menu | Avatar top-right of header 64 | Last bottom-nav tab |
| More detail | Push | Push | Third column / inspector (trailing 280) | Right drawer or new page | Push full page |

More than 5 sections → group them; never 6+ tabs.

### 3. Where actions go

- **Primary action of a screen**
  - iOS: toolbar trailing **icon** button (`+`, checkmark) for edit/create; for flows (checkout, transfer, onboarding) a full-width glass-prominent capsule in a bottom safe-area bar (`dsBottomCTA`).
  - Android: FAB bottom-right (create), or `TopAppBar` action; flows → full-width button at bottom.
  - macOS: icon button in the toolbar's primary-action group (grouped by function, `ToolbarSpacer` between groups) + keyboard shortcut (⌘N, ⌘S); in dialogs default button bottom-right.
  - Web: page header right side (`Button primary`); in forms bottom-right of the form (desktop), sticky full-width bottom (mobile).
- **Button order in a pair**
  - Web & macOS & Android dialogs: right-aligned, `[Secondary] [Primary]` — primary is rightmost.
  - iOS alerts/sheets: system ordering (Cancel bold-left or bottom in action sheets). Mobile stacked flows: primary on top, secondary (text button) below.
- **Destructive actions:** at the bottom of the screen/settings group, `danger` text; always confirm (alert/dialog) with the destructive verb repeated ("Delete account").
- **Row actions:** swipe actions (iOS/Android) + context menu (long-press / right-click); on web a trailing `⋯` menu visible on hover and always on touch.
- **Filters & sort:** segmented control (2–4 options) under the title, otherwise a "Filter" button that opens a sheet (mobile) or popover (desktop).

### 4. Screen recipes

#### List screen
Header (title + add action) → search → optional segmented filter → grouped list in `surface` card(s). Row 56: leading icon/avatar 40, title `body`, subtitle `footnote textSecondary`, trailing value/badge/chevron. Divider inset to text start. Empty state centered in the content area.

#### Detail screen
Hero block: key value large (`display` or `title1`), label above in `textSecondary`, status badge. Then grouped "key → value" rows (label left `textSecondary`, value right `textPrimary`). Actions: primary as sticky bottom button (mobile) or header-right (desktop); secondary actions in an `⋯` menu.

#### Form
Single column, max width 480 on desktop. Label above field (`subheadline`), helper text below (`footnote textTertiary`); error replaces helper in `danger` with an icon. Group related fields into sections with headers. Primary submit disabled until valid only if the reason is obvious; otherwise validate on submit and scroll to the first error. On iOS prefer native `Form` with sections.

#### Settings
Grouped list sections: Account → Preferences → Notifications → Security → About → (bottom) Sign out / Delete account in `danger`. Rows: icon in 28 rounded `fill` square, title, trailing value/toggle/chevron.

#### Dashboard (web/macOS/tablet)
Row of 3–4 KPI cards (label `footnote textSecondary`, value `title1` tabular, delta badge success/danger) → main chart card (span 8 of 12) + side list card (span 4) → table card. Gap 24. Charts use `accent` for the main series, grays for comparison.

#### Auth (sign in / OTP)
Centered column, max 400 wide; logo 48 top, `title1` heading, one-line `body textSecondary` subtext, fields, primary full-width button, secondary link below. OTP: 4–6 boxes 48×56 radius md, auto-advance, resend timer in `textTertiary`.

#### Onboarding
1–3 screens max: illustration/icon (accentSubtle circle with accent icon), `title1`, `body textSecondary` (≤2 lines), page dots, primary "Continue" sticky bottom, "Skip" top-right text button.

### 5. Responsive web grid

Breakpoints: sm 640 · md 768 · lg 1024 · xl 1280. 12 columns, gutter 24 (16 below md). Container max 1200, centered, side padding 16/24.
- `< md`: single column, bottom nav, sticky bottom primary actions, sheets instead of modals.
- `md–lg`: sidebar collapses to icon rail (72) or hidden behind menu button; two-column cards.
- `≥ lg`: full sidebar 248, header 64, modals centered (max 480/640).

---

## 3. Components

Values are token names from `assets/tokens.json`. Implementations per platform are in `web.md`, `apple.md`, `android.md`. Where Apple or Material has a native control, style the native control to match this spec rather than redrawing it.

### Contents
Buttons · Text field · Select / picker · Toggle, checkbox, radio · Segmented control · Card · List row · Badge / tag · Chips · Avatar · Banner / alert · Toast / snackbar · Modal / dialog · Bottom sheet · Tabs · Empty state · Skeleton · Progress · Table (web/macOS) · Amount display

---

### Buttons
| Variant | Background | Text | Border | Use |
|---|---|---|---|---|
| Primary | `accent` (hover/pressed `accentHover`) | `textOnAccent` | — | the main action; one per screen |
| Secondary | `fill` | `textPrimary` | — | other actions |
| Outline | transparent | `textPrimary` | 1px `borderStrong` | alt. secondary on busy surfaces |
| Ghost / text | transparent (hover `fill`) | `accentText` | — | tertiary, "See all", "Cancel" |
| Destructive | `dangerFill` | white | — | irreversible delete (inside confirm dialog) |
| Destructive soft | `dangerSubtle` | `danger` | — | delete entry point in settings |

- **Shape: capsule (`full`) on every platform** — iOS/macOS 27 geometry.
- Heights: sm 32 / md 44 (default) / lg 52 (sticky bottom CTA). Compact desktop 28–32.
- Horizontal padding: 12 (sm), 16 (md), 24 (lg).
- Where a button floats over content or sits in a bar (sticky CTA, FAB, toolbar), it belongs to the glass layer: Apple `.glassProminent` (primary, accent tint) / `.glass` (others); web primary stays solid accent, secondary floating buttons use `.glass`.
- Label: `headline`-weight 600 at 15–16 size (sm: 14). Icon 20 with 8 gap, leading.
- States: hover (web/mac) → `accentHover`/darker fill; pressed → scale 0.98 + `accentHover`; disabled → 40% opacity, no shadow; loading → spinner replaces label, width kept.
- Full-width on mobile flows; content-width on desktop.

### Text field
- Height 44 (compact 32), radius `md`, bg `surface`, 1px `borderStrong`, padding 12–14 horizontal, text `body`.
- Label above (`subheadline`, `textSecondary`), 6–8 gap. Placeholder `textTertiary` — never use placeholder as label.
- Focus: border `accent` + 3px ring `focusRing` at 30–40% (web); native focus on Apple.
- Error: border `danger`, message below in `footnote danger` with alert icon.
- Leading icon 20 `textTertiary`; trailing clear/eye button 44 hit area.
- Money input: large `title1` tabular digits, currency suffix in `textSecondary`.

### Select / picker
Looks like a text field with trailing chevron-down. Opens native picker on mobile (menu on iOS, `ExposedDropdownMenu` on Android), popover list on desktop (radius `md`, `surfaceRaised`, shadow `lg`, items 36 high, selected = check icon in `accent`).

### Toggle, checkbox, radio
Use native controls tinted with `accent`. Whole row is tappable. Toggle on the trailing side of a settings row; checkbox/radio on the leading side of a list.

### Segmented control
2–4 options. Capsule track `fill`, capsule selected segment `surfaceRaised` + 1px `border`, text `subheadline`. Height 44 (40 segments inside 2px padding; 32 compact). Apple: native `.segmented` picker.

### Card
Bg `surface`, radius `lg` (20), padding 16 (mobile) / 20–24 (desktop), no shadow in light by default (or `sm`); in dark mode 1px `border`. Clickable card: hover → shadow `md` (web), press → scale 0.99. Card title `headline`, meta `footnote textSecondary`.

### List row
Height ≥ 56 (compact 36–40). Leading: icon 20 inside 36–40 rounded square (`accentSubtle` bg + `accentText` icon, or `fill` + `textSecondary`) or avatar 40. Title `body textPrimary`, subtitle `footnote textSecondary` (1 line, truncate). Trailing: value `body textSecondary`, badge, toggle or chevron 16 `textTertiary`. Divider 1px `border` inset to title start. Selected: bg `accentSubtle`. Grouped inside a card with radius `lg`.

### Badge / tag
Height 20–24, padding 8 horizontal, radius `full` (status) or `xs` (tags), text `caption`. Status colors: neutral (`fill`/`textSecondary`), info (`accentSubtle`/`accentText`), success (`successSubtle`/`success`), warning (`warningSubtle`/`warning`), danger (`dangerSubtle`/`danger`). Optional 6px dot leading.

### Chips (filters)
Height 32, radius `full`, padding 12. Unselected: `fill` + `textPrimary`. Selected: `accent` + `textOnAccent` (or `accentSubtle` + `accentText` for multi-select). Horizontal scroll row with 8 gap, 16 edge padding.

### Avatar
Circle 32/40/56. Fallback: initials on `accentSubtle` with `accentText`, `subheadline` weight 600.

### Banner / inline alert
Radius `md`, padding 12–16, bg `<status>Subtle`, icon 20 in status color, title `subheadline` 600 `textPrimary`, text `footnote textSecondary`, optional close/action. Full width of its container.

### Toast / snackbar
Glass capsule (Apple `.glassEffect(.regular, in: .capsule)`, web `.glass rounded-full`), max width 400, bottom-center (mobile: above tab bar/nav, 16 from edges), auto-dismiss 4 s, optional one text action in `accentText`. Android: native `Snackbar`.

### Modal / dialog
Desktop: centered, width 400 (confirm) / 560 (form) / 720 (complex), radius `xl` (28; Apple: leave native sheet/alert chrome alone), padding 24, bg `surfaceRaised`, scrim `overlay`. Title `title3`, body `body textSecondary`, actions bottom-right. Mobile web/Android/iOS: prefer bottom sheet for anything bigger than a confirm.

### Bottom sheet
Top radius `xl` (28) — iOS 27 half sheets are inset from screen edges with glass, opaque at full height; grabber 36×5 `borderStrong` 8 from top, padding 16 + safe area, detents medium/large. iOS: `.sheet` + `.presentationDetents`; Android: `ModalBottomSheet`.

### Tabs (in-page)
Text `subheadline`; active `textPrimary` + 2px `accent` underline; inactive `textSecondary`. Height 44. Use segmented control instead when ≤ 3 options and it's a filter rather than navigation.

### Empty state
Centered, max width 320: icon 28–32 in a 64 circle (`accentSubtle` + `accentText`), `title3` heading, `body textSecondary` one-sentence explanation, optional primary or secondary button ("Add first …"). 48 top padding.

### Skeleton
Blocks in `fill`, radius matches the real element, shimmer opacity 0.6→1 over 1.2 s (disable with Reduce Motion). Mirror the real layout — never a lone spinner for content screens. Spinner only inside buttons or for < 1 s actions.

### Progress
Linear: 4px tall, track `fill`, bar `accent`, radius `full`. Circular: native, tinted `accent`. Steppers: dots 8 (`borderStrong`), active pill 24×8 `accent`.

### Table (web / macOS)
Header row 40, `caption` uppercase-free (sentence case), `textSecondary`, bg `bg`; rows 48 (compact 36), divider `border`, hover `fill`. Numbers right-aligned, tabular. Sticky header; pagination or infinite scroll at the bottom; row actions in trailing `⋯`. On mobile turn tables into list rows.

### Amount display (fintech)
Amount `display`/`title1` bold tabular; currency code or symbol smaller (`title3`, `textSecondary`) after the number. Format with locale grouping ("12 500,00"). Positive deltas `success` with "+", negative `textPrimary` with "−". Hide-balance toggle (eye icon) next to sensitive balances.

---

## 4. Platform implementation

| Platform | Token file | Components | Root setup |
|---|---|---|---|
| Web (React + Tailwind v3.3+) | `src/styles/tokens.css` + `tailwind.preset.cjs` | `src/ds/*` (Button, Card, Field, Select, Textarea, Switch, Checkbox, SegmentedControl, Chip, Badge, Banner, Toast, Dialog/Sheet, Tabs, Progress, ListGroup/ListRow, Amount, StatCard, Table, EmptyState, Skeleton, PageHeader, AppShell) | preset in tailwind config, import tokens.css first, Inter font, `<ToastProvider>` |
| iOS + macOS 27 style (SwiftUI, Liquid Glass; target iOS 26+ / macOS 26+, Xcode 27 SDK) | `DesignTokens.swift` (`DS.ColorToken`, `DS.FontToken`, `DS.Space`, `DS.Radius`, `DS.Size`, `DS.Motion`) | `DSComponents.swift` (`.buttonStyle(.ds(...))`, `.dsCard()`, `DSTextField`, `DSBadge`, `DSBanner`, `DSChip`, `DSListRow`, `DSAmount`, `DSStatCard`, `DSEmptyState`, `DSSkeleton`, `.dsToast`, …), `.dsBottomCTA`, `DSFloatingButton`, `DSGlassCluster`; `DSShowcase.swift` (iOS 27 tab shell, macOS 27 split-view shell) | `.tint(DS.ColorToken.accent)` at the root; no custom backgrounds behind system bars |
| Android (Compose, Material 3 1.3+) | `DesignTokens.kt` (`AppTheme`, `DS.colors`, `DSSpace`, `DSSize`, `DSMotion`) | `DSComponents.kt` (`DSButton`, `DSCard`, `DSTextField`, `DSSegmentedControl`, `DSChip`, `DSListRow`, `DSAmount`, `DSEmptyState`, `DSSkeleton`, …), `DSShowcase.kt` (app shell) | `setContent { AppTheme { … } }`, dynamic color OFF |

Rules for code: never hardcode a hex, font size or spacing value — reference tokens; prefer native controls (tinted) over custom replicas; build all four screen states; check light and dark.
