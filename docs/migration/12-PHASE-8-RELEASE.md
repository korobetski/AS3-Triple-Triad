# Phase 8: Release - Triple Triad Online Migration

## 📋 Document Information

- **Phase**: 8 - Release
- **Duration**: 2 weeks (Weeks 31-32)
- **Status**: NOT STARTED
- **Version**: 1.0
- **Last Updated**: 2026-07-21
- **Prerequisites**: Phases 1-7

---

## 🎯 Phase Overview

### 🔴 BLOCKED: this phase cannot proceed as written

Public distribution of this app would infringe Square Enix intellectual property.
Triple Triad, Final Fantasy VIII and Final Fantasy XIV are Square Enix trademarks;
all card art, character art, UI sprites and audio in `sources/assets/` and
`sources/bin/assets/` are extracted from shipped Square Enix titles, and
`application.xml` carries only a "(c) Moogle Works 2015" notice.

Submitting to Google Play and the App Store - especially with `final fantasy` as a
store keyword, as Task 8.3 below specifies - invites a DMCA takedown, developer
account penalties and legal exposure, after the entire project budget has been spent.

**Resolve BR-003 in [16-RISK-ASSESSMENT.md](./16-RISK-ASSESSMENT.md) before
executing any task in this phase.** The practical path is a full reskin with
original art, names and audio, which requires an artist and a sound designer -
neither is staffed in the current plan, and the asset-replacement work is not in
any estimate here.

Everything below assumes that blocker has been cleared.

---

### 🔴 BLOCKED: this phase cannot proceed as written

Public distribution of this app would infringe Square Enix intellectual property.
Triple Triad, Final Fantasy VIII and Final Fantasy XIV are Square Enix trademarks;
all card art, character art, UI sprites and audio in `sources/assets/` and
`sources/bin/assets/` are extracted from shipped Square Enix titles, and
`application.xml` carries only a "(c) Moogle Works 2015" notice.

Submitting to Google Play and the App Store - especially with `final fantasy` as a
store keyword, as Task 8.3 below specifies - invites a DMCA takedown, developer
account penalties and legal exposure, after the entire project budget has been spent.

**Resolve BR-003 in [16-RISK-ASSESSMENT.md](./16-RISK-ASSESSMENT.md) before
executing any task in this phase.** The practical path is a full reskin with
original art, names and audio, which requires an artist and a sound designer -
neither is staffed in the current plan, and the asset-replacement work is not in
any estimate here.

Everything below assumes that blocker has been cleared.

---

### Purpose
Prepare and execute the release of Triple Triad Online for Android and iOS app stores, including beta testing, final preparation, and deployment.

### Key Objectives
1. Prepare beta release
2. Conduct beta testing
3. Finalize app store listings
4. Submit to app stores
5. Deploy to production
6. Post-release monitoring

---

## 📅 Timeline

| Week | Focus | Owner |
|------|-------|-------|
| Week 31 | Beta preparation, Beta testing | DevOps + QA |
| Week 32 | App store submission, Release, Monitoring | DevOps + All |

---

## 📝 Tasks

### Week 31: Beta Release

#### Task 8.1: Beta Build Preparation
**Owner**: DevOps | **Duration**: 2 days | **Priority**: CRITICAL

**Beta Build Requirements**:
- Version: 1.0.0-beta.1
- All features complete
- All tests passing
- Performance validated
- No critical bugs
- Code freeze (except critical fixes)

**Build Configuration**:
```gradle
// androidApp/build.gradle.kts
android {
    defaultConfig {
        versionCode = 1
        versionName = "1.0.0-beta.1"
    }
    
    signingConfigs {
        // Referenced below as signingConfigs.getByName("beta"); it was never
        // declared in the previous revision, so the build would fail.
        create("beta") {
            storeFile = file(providers.gradleProperty("betaStoreFile").get())
            storePassword = providers.gradleProperty("betaStorePassword").get()
            keyAlias = providers.gradleProperty("betaKeyAlias").get()
            keyPassword = providers.gradleProperty("betaKeyPassword").get()
        }
    }

    buildTypes {
        // Kotlin DSL requires create() for a custom build type - a bare
        // `beta { }` block does not resolve.
        create("beta") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            
            // Beta-specific signing
            signingConfig = signingConfigs.getByName("beta")
            
            // Firebase App Distribution for beta
            firebaseAppDistribution {
                artifactType = "APK"
                groups = "beta-testers"
                releaseNotesFile = file("beta-release-notes.txt")
            }
        }
    }
}
```

> **Scope note**: Firebase App Distribution, Crashlytics, Analytics and
> Performance Monitoring are used throughout this phase, but Firebase appears
> nowhere in [03-TECHNICAL-STACK.md](./03-TECHNICAL-STACK.md), and
> [01-EXECUTIVE-SUMMARY.md](./01-EXECUTIVE-SUMMARY.md) lists analytics as
> explicitly **out of scope** ("to be added post-migration"). Either add Firebase
> to the technical stack and the budget, or use alternatives that need no SDK
> (Play Internal Testing + TestFlight for distribution; Play Console / Xcode
> Organizer for crash reports). Also note Crashlytics and Analytics collect user
> data, which triggers Data Safety / App Privacy declarations not covered here.

> **Scope note**: Firebase App Distribution, Crashlytics, Analytics and
> Performance Monitoring are used throughout this phase, but Firebase appears
> nowhere in [03-TECHNICAL-STACK.md](./03-TECHNICAL-STACK.md), and
> [01-EXECUTIVE-SUMMARY.md](./01-EXECUTIVE-SUMMARY.md) lists analytics as
> explicitly **out of scope** ("to be added post-migration"). Either add Firebase
> to the technical stack and the budget, or use alternatives that need no SDK
> (Play Internal Testing + TestFlight for distribution; Play Console / Xcode
> Organizer for crash reports). Also note Crashlytics and Analytics collect user
> data, which triggers Data Safety / App Privacy declarations not covered here.

**Beta Distribution**:
- **Android**: Firebase App Distribution, or Play Console internal testing, or Play Console internal testing
- **iOS**: TestFlight
- **Internal**: Direct APK/IPA distribution

**Acceptance Criteria**:
- [ ] Beta builds created for both platforms
- [ ] Builds pass all automated tests
- [ ] Builds installed on test devices

---

#### Task 8.2: Beta Testing
**Owner**: QA | **Duration**: 3 days | **Priority**: CRITICAL

**Beta Test Plan**:
- **Testers**: 50-100 external beta testers
- **Duration**: 5-7 days -- NOTE this contradicts the task's stated 3-day
  duration and the 1-week Week 31 slot. A 5-7 day beta plus triage does not fit;
  either extend Phase 8 to 3 weeks or shorten the beta to 3 days and say so. -- NOTE this contradicts the task's stated 3-day
  duration and the 1-week Week 31 slot. A 5-7 day beta plus triage does not fit;
  either extend Phase 8 to 3 weeks or shorten the beta to 3 days and say so.
- **Focus**: Full feature testing, edge cases, performance
- **Feedback**: Structured feedback collection

**Beta Test Areas**:
1. **Functionality**: All features work correctly
2. **Stability**: No crashes or freezes
3. **Performance**: Smooth on all devices
4. **Usability**: Intuitive and easy to use
5. **Compatibility**: Works on all supported devices
6. **Localization**: all 4 locales work (de_DE, en_US, fr_FR, ja_JA)

**Beta Test Builds**:
- Android: Universal APK and App Bundle
- iOS: TestFlight build

**Beta Test Feedback Collection**:
- In-app feedback form
- Crash reporting (Firebase Crashlytics)
- Analytics (Firebase Analytics)
- Direct communication channel

**Acceptance Criteria**:
- [ ] Beta testing complete
- [ ] Critical issues addressed
- [ ] Feedback analyzed
- [ ] Approval for release

---

### Week 32: Production Release

> WARNING: Week 32 assigns 8.3 (2 d) + 8.4 (2 d) + 8.5 (1 d) + 8.6 (2 d) = 7 days
> of work into a 5-day week, and Task 8.4 additionally waits 1-3 days for store
> review. Store review is wall-clock time that cannot be compressed. Phase 8
> needs 3 weeks, or submission must move into Week 31.

> WARNING: Week 32 assigns 8.3 (2 d) + 8.4 (2 d) + 8.5 (1 d) + 8.6 (2 d) = 7 days
> of work into a 5-day week, and Task 8.4 additionally waits 1-3 days for store
> review. Store review is wall-clock time that cannot be compressed. Phase 8
> needs 3 weeks, or submission must move into Week 31.

#### Task 8.3: App Store Preparation
**Owner**: DevOps + Marketing | **Duration**: 2 days | **Priority**: CRITICAL

**Google Play Store Preparation**:
- **App Listing**:
  - Title: Triple Triad Online
  - Short description: <80 characters
  - Full description: <4000 characters
  - Category: Games / Card
  - Tags: card, game, strategy, tactics  -- do NOT use "final fantasy",
    "triple triad" or any Square Enix mark unless a licence has been granted
    (see BR-003). Using them is both a trademark issue and grounds for store
    rejection under the impersonation/IP policies.
  - Feature graphic: 1024x500
  - Icon: 512x512
  - Screenshots: 6-8 images
  - Promo video: Optional

- **Store Listing Assets**:
  ```
  androidApp/
  ├── play/
  │   ├── listing/
  │   │   ├── short-description.txt
  │   │   ├── full-description.txt
  │   │   ├── whats-new.txt
  │   │   ├── feature-graphic.png
  │   │   ├── icon.png
  │   │   ├── screenshots/
  │   │   │   ├── screenshot-1.png
  │   │   │   ├── screenshot-2.png
  │   │   │   └── ...
  │   │   └── promo-video.mp4
  ```

**Apple App Store Preparation**:
- **App Store Connect**:
  - App name: Triple Triad Online
  - Subtitle: 32 characters max
  - Description: <4000 characters
  - Keywords: 100 characters max
  - Category: Games / Card
  - Age rating: 4+ or 9+
  - App icons: 1024x1024
  - Screenshots: 6.5" and 5.5" display sizes
  - App previews: 15-30 second videos

- **Info.plist Updates**:
  ```xml
  <key>CFBundleDisplayName</key>
  <string>Triple Triad Online</string>
  <key>CFBundleShortVersionString</key>
  <string>1.0.0</string>
  <key>CFBundleVersion</key>
  <string>1</string>
  ```

**Common Assets**:
- App icons (multiple sizes)
- Screenshots (multiple sizes)
- Promotional images
- App preview videos
- Privacy policy URL
- Support URL

**Acceptance Criteria**:
- [ ] All store assets prepared
- [ ] All metadata complete
- [ ] Assets meet store requirements

---

#### Task 8.4: App Store Submission
**Owner**: DevOps | **Duration**: 2 days | **Priority**: CRITICAL

**Google Play Submission**:
1. Create release in Play Console
2. Upload App Bundle and APK
3. Complete store listing
4. Set pricing (Free)
5. Set distribution (All countries)
6. Submit for review
7. Wait for approval (2-3 days)
8. Publish release

**Apple App Store Submission**:
1. Create app in App Store Connect
2. Upload build via Xcode
3. Complete store listing
4. Set pricing (Free)
5. Set availability (All territories)
6. Submit for review
7. Wait for approval (1-3 days)
8. Release to App Store

**Submission Checklist**:
- [ ] App builds uploaded
- [ ] Store listings complete
- [ ] Screenshots uploaded
- [ ] Icons uploaded
- [ ] Description and metadata complete
- [ ] Age rating completed
- [ ] Privacy policy linked
- [ ] Support contact information provided
- [ ] Pricing configured
- [ ] Distribution configured

**Acceptance Criteria**:
- [ ] Apps submitted to both stores
- [ ] All metadata complete
- [ ] Apps approved for release

---

#### Task 8.5: Release Deployment
**Owner**: DevOps | **Duration**: 1 day | **Priority**: CRITICAL

**Release Process**:
1. **Staged Rollout** (Android):
   - Start with 10% of users
   - Monitor for 24 hours
   - Gradually increase to 100%

2. **Full Release** (iOS):
   - Release to all users immediately
   - Monitor closely for first 24 hours

3. **Version Management**:
   - Update version codes
   - Tag release in Git
   - Create GitHub release

**Release Automation**:
```yaml
# .github/workflows/release.yml
name: Release

on:
  push:
    tags:
      - 'v*'

jobs:
  build-android:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: ./gradlew androidApp:bundleRelease
      - uses: actions/upload-artifact@v4
        with:
          name: app-release.aab
          path: androidApp/build/outputs/bundle/release/*.aab

  build-ios:
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v4
      # `iosApp` is an Xcode project, not a Gradle module - `./gradlew iosApp:build`
      # does not exist. Build the shared framework with Gradle, then use xcodebuild.
      - run: ./gradlew :shared:linkReleaseFrameworkIosArm64
      - run: |
          xcodebuild -workspace iosApp/iosApp.xcworkspace \
                     -scheme iosApp -configuration Release \
                     -archivePath build/iosApp.xcarchive archive
          xcodebuild -exportArchive -archivePath build/iosApp.xcarchive \
                     -exportOptionsPlist iosApp/ExportOptions.plist \
                     -exportPath build
      - uses: actions/upload-artifact@v4
        with:
          name: app-release.ipa
          path: iosApp/build/*.ipa

  publish:
    needs: [build-android, build-ios]
    runs-on: ubuntu-latest
    steps:
      - uses: actions/download-artifact@v4
      - run: ./scripts/deploy-release.sh
```

**Acceptance Criteria**:
- [ ] Apps published to stores
- [ ] Release version tagged in Git
- [ ] Release notes published

---

#### Task 8.6: Post-Release Monitoring
**Owner**: DevOps + QA | **Duration**: 2 days | **Priority**: HIGH

**Monitoring Setup**:
- **Crash Reporting**: Firebase Crashlytics
- **Analytics**: Firebase Analytics
- **Performance**: Firebase Performance Monitoring
- **Error Tracking**: Sentry or similar
- **Review Monitoring**: App store reviews

**Monitoring Dashboard**:
- Crash-free users percentage
- App startup time
- Memory usage
- Network request success rate
- User retention
- App store ratings
- Review sentiment

**Monitoring Alerts**:
- Crash rate > 1%
- FPS < 60 for >5% of sessions
- Memory usage > 100MB
- Launch time > 2s
- Negative review spike

**Post-Release Checklist**:
- [ ] Monitor crash reports
- [ ] Monitor app store reviews
- [ ] Monitor social media
- [ ] Monitor support channels
- [ ] Respond to issues promptly
- [ ] Plan hotfix if needed

**Acceptance Criteria**:
- [ ] Monitoring in place
- [ ] No critical issues
- [ ] Positive user feedback

---

## 📊 Phase 8 Deliverables

### Code Deliverables
- [ ] Beta builds (Android + iOS)
- [ ] Production builds (Android + iOS)
- [ ] Release scripts
- [ ] Deployment configuration

### Documentation Deliverables
- [ ] Release notes
- [ ] App store listings
- [ ] Deployment guide
- [ ] Monitoring guide

### Other Deliverables
- [ ] Published Android app
- [ ] Published iOS app
- [ ] Monitoring dashboard
- [ ] Release announcement

---

## ✅ Phase 8 Completion Criteria

### Technical
- [ ] Beta testing complete
- [ ] Apps published to stores
- [ ] Monitoring in place
- [ ] No critical issues

### Quality
- [ ] Positive user feedback
- [ ] Crash-free rate >99%
- [ ] App store rating >4.0

### Approvals
- [ ] Tech Lead approval
- [ ] DevOps approval
- [ ] Marketing approval

---

## 🎯 Migration Complete!

After Phase 8, the Triple Triad Online migration from ActionScript 3 to Kotlin Multiplatform will be complete!

**Next Steps After Release**:
- Monitor app performance
- Collect user feedback
- Plan post-release updates
- Start feature enhancements
- Continuous improvement

---

## 📞 Related Documents

- **Phase Overview**: [00-INDEX.md](./00-INDEX.md)
- **Executive Summary**: [01-EXECUTIVE-SUMMARY.md](./01-EXECUTIVE-SUMMARY.md)
- **Phase 7**: [11-PHASE-7-TESTING.md](./11-PHASE-7-TESTING.md)
- **Risk Assessment**: [16-RISK-ASSESSMENT.md](./16-RISK-ASSESSMENT.md)

---

*Generated: 2026-07-21*  
*Status: PLANNING COMPLETE*
