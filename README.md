# Salah Times

Prayer-times Android app — English default, Indonesian translated. Targets a massive Muslim audience worldwide (240M+ in Indonesia alone) with daily-open retention that is ideal for banner-ad monetization.

## Stack
- Java 17, Material Components, ViewBinding
- Min SDK 23, Target SDK 34
- AdMob SDK 23, UMP SDK 2.2 (GDPR/IAB consent-ready)

## Play Store compliance checklist
- [x] `android:allowBackup="false"` + `data_extraction_rules` (no sensitive cloud backups)
- [x] Privacy Policy screen accessible from toolbar overflow — link to a hosted URL in the Play Console listing
- [x] Only `INTERNET` + `ACCESS_NETWORK_STATE` permissions (AdMob required) — nothing unjustified
- [x] AdMob App ID in manifest
- [x] Test unit IDs committed — replace with real `ca-app-pub-XXX` before release
- [x] English default strings + `values-id` Indonesian translation
- [ ] Add real privacy-policy URL to Play Console Data Safety form before publish
- [ ] Replace test ad IDs
- [ ] Upload adaptive launcher icon

## UX principles
- Banner placed in a distinct `#F1F5F9` container with an "AD" label — visually separated, no accidental taps.
- No interstitials in MVP — prayer times are a fast-glance use case, fullscreen ads would annoy users.

## Roadmap
- v1.1: location-aware times (`adhan-java`), Qibla compass
- v1.2: Adhan notifications, tasbih counter (rewarded ad to remove ads for 24h)
