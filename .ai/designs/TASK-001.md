# TASK-001 — Location-permission onboarding design specification

## Scope and intent

This specification replaces the Compose sample greeting with the first Smart Flight entry experience. It obtains the authorization prerequisite for later flight-information cards; it does not imply that location, GNSS, map, route, satellite, or telemetry data are active or available.

The screen must present only a useful permission state before authorization, and a deliberately minimal confirmation state after authorization. It must not render placeholder dashboard cards.

## Reference and decisions

### Observed legacy behavior and styling

- The legacy dashboard is a vertically scrolling dark-purple page with a centered `Smart Flight` toolbar title. It adds a permission card when fine location is absent and does not add location-dependent cards until access is granted.
- `permission_view.xml` is a minimum-160 dp tall card. A packed, vertically centered chain contains centered explanatory text followed by a centered, borderless cyan text action. Both use the legacy 18 sp label style.
- Legacy tokens are: page `#484685`, card `#5B5999`, muted text `#A1A0C4`, light text `#D9D9ED`, cyan action `#25E5FE`, 12 dp card margin, 10 dp radius, and 4 dp elevation.
- When the legacy app determines that the permission can no longer be requested, its action changes from `Grant Permission` to `Open settings`. When access is granted it removes the permission card and creates the dashboard cards.

### Required behavior from TASK-001

- Fine/precise location is the only authorization that satisfies onboarding. On Android 12+ (API 31+), request `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` together where Android requires the paired request to present its precise/approximate location choice. The coarse companion is a system-prompt requirement only: approximate/coarse-only access is not sufficient.
- The system runtime prompt, including its precise/approximate controls and version-specific wording, is Android-owned and is not redesigned by this document.
- The action is `Grant permission` while access is requestable. An ordinary denial leaves the permission screen usable; permanent denial offers `Open settings` for this app’s details page.
- The view derives its state from the current fine-location grant and refreshes on resume, including on return from settings. A coarse-only platform result remains in the requestable onboarding state.
- Only a fine-location grant immediately replaces onboarding with the minimal granted entry state. No functional flight cards or data collection are introduced.

### Design decisions introduced here

- Use a compact app shell and one primary surface rather than recreating the full legacy dashboard. This preserves the recognizable purple/cyan language while accurately representing the rewrite’s current feature set.
- Retain a text-action visual treatment, but make its entire row a 48 dp minimum touch target. This is the accessible Compose equivalent of the legacy borderless clickable text.
- Use a short title plus plain-language body rather than the legacy two-line imperative copy. The title supplies hierarchy without adding an unsupported feature claim.
- The granted state is an informational confirmation card with no call to action. This makes the successful prerequisite visible without suggesting live flight data exists.

## Screen structure

All app-owned content is drawn edge-to-edge. Apply safe drawing insets so no content is obscured by display cutouts, status bars, gesture navigation, or the IME. Do not place an app-owned background behind or attempt to emulate the Android permission dialog.

```text
Window (page background: Dark purple)
├── top safe inset
├── App header
│   └── "Smart Flight" (centered)
└── Content region (scrollable when constrained)
    └── State card (one card only)
        ├── Permission requestable / denied state
        │   ├── title: "Location permission"
        │   ├── explanatory body
        │   └── cyan text action: "Grant permission"
        ├── Permanent-denial state
        │   ├── title: "Location permission needed"
        │   ├── explanatory body
        │   └── cyan text action: "Open settings"
        └── Granted state
            ├── title: "Location permission granted"
            └── explanatory body (no action)
```

### App shell

- The root fills the available window with `#484685`; this color continues under system bars, with light system-bar icons for sufficient contrast.
- Header: horizontally centered app name `Smart Flight`; 56 dp visual height below the top inset, matching the legacy action-bar rhythm. It has the same page color, no divider, no navigation icon, and no overflow menu in this iteration.
- Header typography: 20 sp, medium weight, `#D9D9ED`, single line. It is a static label, not a button.
- Body is a vertically scrollable single-column region using the remaining height. Use `fillViewport` behavior: on a normally sized phone the card sits near the top of the content region rather than vertically centered in the entire screen. This retains the legacy dashboard’s “first card” placement.
- Content horizontal padding is 12 dp. Card top margin is 12 dp; retain 12 dp bottom padding so the card clears navigation insets. On a very short viewport, scrolling exposes the full card and action.

### Shared card geometry and surface

- Width: full available content width (screen width less 24 dp outer margins).
- Minimum height: 160 dp for all onboarding states, mirroring the legacy permission card. Height may grow with larger font scaling; never clip, overlap, or reduce touch targets.
- Shape: 10 dp uniformly rounded corners.
- Container: `#5B5999`; 4 dp tonal/shadow elevation or the closest Material 3 elevation that visibly separates it from `#484685` without changing the color family. Do not use dynamic colors for this screen.
- Internal horizontal padding: 24 dp. Internal vertical padding: 20 dp, with expansion as required by text scaling.
- Keep title, body, and action in one centered vertical group. With the card at its minimum height, center the group vertically; with expanded text, preserve the stated padding and allow the card to grow.

## State content and hierarchy

Copy is sentence case exactly as shown below in the default English resource. Localize all user-facing strings; do not hard-code them in composables.

| Platform-derived state | Title | Body | Action | Result |
| --- | --- | --- | --- | --- |
| Fine location not granted; request is available (including initial launch, ordinary denial, and approximate-only authorization) | `Location permission` | `Smart Flight needs precise location permission to enable flight information cards.` | `Grant permission` | Launch Android’s location request. On Android 12+, request fine and coarse together so the system can offer its precise/approximate choice; only fine is success. |
| Fine location not granted; platform indicates the user must be directed to settings after an actual request/denial history | `Location permission needed` | `Location permission is turned off. Open settings to allow Smart Flight to enable flight information cards.` | `Open settings` | Open this app’s application-details settings page. |
| Fine location granted | `Location permission granted` | `Smart Flight is ready for flight information cards. Flight data will be available in a future update.` | None | Remain on the minimal granted entry state. |

The granted body is intentionally forward-looking but must not say the app is collecting location, connected to GPS, or displaying flight information.

### Typography

- Card title: 22 sp / 28 sp line height, medium (or semibold if the project typography makes medium indistinct), `#D9D9ED`, centered. Limit is two lines; if localized copy needs more, allow wrapping rather than ellipsis.
- Title-to-body spacing: 12 dp.
- Body: 18 sp / 25 sp line height, regular, `#A1A0C4`, centered. This preserves the legacy 18 sp text scale. Use natural wrapping and no forced newline.
- Body-to-action spacing: 12 dp. In the granted state omit this spacing and action entirely.
- Action label: 18 sp / 24 sp line height, medium, `#25E5FE`, centered, sentence case. It is visually text-only: no filled button, outline, icon, or all-caps transformation.

## Controls, interaction, and feedback

### Grant permission

- The complete action row is a semantic button with a 48 dp minimum height and 48 dp minimum width. Its visible label remains centered and text-only, recalling the legacy control.
- Use a borderless Material indication/ripple clipped to the card’s content area. Pressed state may darken/overlay the row with 12% black; focused state uses a visible cyan 2 dp outline or equivalent high-contrast focus indicator with 4 dp separation from the card edge.
- On tap, invoke the Android Activity Result location permission request. On Android 12+, request fine and coarse together so Android can show its precise/approximate choice; on earlier versions request fine location. While the platform dialog is on screen, the app content remains behind it and no second request can be launched. No custom loading spinner or duplicate in-app dialog is necessary.
- Resolve the next screen from the actual current fine-location grant after the result. Only fine permission transitions immediately to the granted card. Ordinary denial and an approximate-only selection both return to the same requestable state, with the precise-location explanation and `Grant permission` action still available. Do not display an error snackbar solely for denial or approximate-only selection—the unchanged actionable explanation is the feedback.
### Open settings

- Reuse the same text-button geometry and interaction treatment, changing only the label and state copy.
- Tap opens Android’s application-details settings for the Smart Flight package. The user may return with the permission granted, still denied, or unchanged.
- On every activity resume, recheck the platform fine-location permission before drawing/retaining the state. If fine location is granted, show the granted state immediately; if only coarse is granted, keep or rederive the actionable onboarding state.
### State transition motion

- Card changes use a brief 150–200 ms crossfade/size transition respecting the system “remove animations” setting. Do not animate the Android runtime dialog or settings handoff.
- Preserve the card’s top position and scroll position when switching between denial states. When changing to the granted card, keeping the same placement is preferred over a dashboard-style reflow because no dashboard exists yet.

## Responsive behavior

### Portrait phones

- Standard layout is a single full-width card directly below the header, 12 dp from each page edge and 12 dp from the content top.
- On common phone widths the explanatory body naturally wraps to approximately two to three centered lines. Do not constrain it to an artificially narrow column.

### Landscape phones and wider windows

- Keep the header at the top and the state card as the single content item; do not introduce two columns, side navigation, or empty dashboard panes.
- Cap the card width at 600 dp and horizontally center it once the available width exceeds 624 dp (600 dp card plus the preferred 12 dp outer clearance each side). Below that threshold, retain the full available width.
- Maintain a 12 dp top margin. The body remains scrollable so reduced landscape height never hides the action.

### Font scale and localization

- Support at least 200% font scale: card height grows, the action stays at least 48 dp tall, and scrolling is available. Do not use fixed-height text containers.
- Keep action labels unbroken where possible; localized labels may wrap to two centered lines while retaining the target size. Body and title must use locale-aware line breaking and support RTL. In RTL, centered geometry is unchanged.

## Accessibility

- Card text order and accessibility traversal: title, body, then action. The card itself is not separately clickable or announced as a button.
- The action’s semantic label exactly matches its visible label. Add a concise action hint only if the platform does not already announce the result (for example, “Requests precise location permission” / “Opens app settings”).
- Body text is selectable only if that is standard project behavior; selection is not required. It must remain readable at the specified muted color: `#A1A0C4` on `#5B5999` is a legacy-faithful visual reference, but implementation must verify sufficient accessible contrast. If it fails contrast testing at the actual rendered sizes, increase the body color toward `#D9D9ED` while retaining hierarchy. Cyan action text must similarly be contrast-verified against the card.
- Never rely on color alone: title and wording distinguish requestable, settings-required, and granted states. Avoid status icons because no established icon system is needed for this minimal entry screen.
- Use live-region/polite announcement for a state change caused by a permission result, announcing the new title once. Do not announce repeatedly on ordinary recomposition or resume.
- The design works with TalkBack, switch access, keyboard focus, large text, display cutouts, and gesture navigation.

## Empty, error, and non-goals

- There is no loading state for platform permission checking; resolve synchronously from the platform before presenting the stable state, avoiding a visible flash of the wrong card.
- There is no “GPS unavailable,” “no satellites,” map, telemetry, route, or generic empty-dashboard state. Such states need later tasks and must not be suggested by disabled cards.
- Settings may be unavailable or its activity may fail to launch on an unusual device. Keep the settings action enabled under normal Android behavior; if launching fails, retain the permanent-denial card and provide a short accessible transient message such as `Unable to open app settings.` This is a defensive error condition, not a separate screen.

## Implementation handoff notes

- Declare `android.permission.ACCESS_FINE_LOCATION`. Also declare `android.permission.ACCESS_COARSE_LOCATION` as the Android 12+ companion needed for the paired runtime request; it must never be used as the success gate. Do not add storage permissions or a location/map dependency.
- The app currently enables edge-to-edge and has a sample Material 3 theme with dynamic colors. This screen needs fixed legacy-inspired tokens so Android wallpaper/dark-mode choices cannot replace the required purple/cyan identity.
- Use the Activity Result permission API and platform checks to model requestable versus settings-required states. Do not infer permanent denial merely from the first denied callback; it depends on request history plus the platform rationale result.
- The supplied Android runtime prompt text, its precise controls, and any Android version-specific approximate-location options are platform UI and outside application visual control. On Android 12+, the app requests fine and coarse together solely to allow this platform UI; application success still requires fine location.
## Design verification checklist

- Fresh install: header, one permission card, and `Grant permission`; no `Hello Android!` or inactive flight cards.
- Granting precise location: the permission card becomes the minimal granted card in the same session.
- Ordinary denial or Android 12+ approximate-only selection: requestable card remains, explains that precise location is needed, and can request again.
- Return from settings after granting precise location: the granted card is shown on resume; coarse-only access remains onboarding.
- Permanent denial: the cyan action says `Open settings` and handoff opens app details.

- Portrait, landscape, RTL, keyboard focus, TalkBack, and 200% font scale retain readable text, a reachable action, safe inset clearance, and no clipping.

## Inspected source references

- Task: `.ai/tasks/TASK-001.md`.
- Legacy permission UI and behavior: `app/src/main/res/layout/permission_view.xml`, `app/src/main/java/kniezrec/com/flightinfo/cards/permission/PermissionCardView.kt`, `PermissionManager.kt`, `cards/adapter/CardViewContainer.kt`, and legacy `MainActivity.kt`.
- Legacy shell/tokens: `res/layout/activity_main.xml`, `res/layout/content_main.xml`, `res/values/colors.xml`, `dimens.xml`, `styles.xml`, `strings.xml`, plus the plane/menu assets.
- Legacy visual media: local `promo/promo.png` (1024 × 500) and the README’s three linked app screenshots. The local image viewer was unavailable in the design environment, so exact promo-image composition is not used as a prescriptive layout measurement; source UI resources above provide the authoritative permission-screen details.
- Rewrite baseline: `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt`, `ui/theme/Color.kt`, `Theme.kt`, `Type.kt`, `res/values/themes.xml`, `res/values/strings.xml`, and `AndroidManifest.xml`.
