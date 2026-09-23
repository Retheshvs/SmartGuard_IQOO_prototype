# SmartGuard

**On-device, identity-aware phone security for families — built for iQOO City Battles Hyderabad 2026**

SmartGuard is an Android app that recognizes who is using the phone and automatically applies the right mode — Child, Teen, or Adult — along with phone-wide content restrictions and screen-time limits. All identity verification runs **fully on-device**, with no cloud dependency.

---

## The Problem

Existing parental-control apps rely on manual mode-switching or a single shared login. They don't adapt automatically when the phone changes hands between a parent and a child, and most don't build in real anti-bypass protection for tech-savvy kids.

## The Idea

SmartGuard verifies identity at meaningful moments — unlock, screen resume, a handover, or a protected setting change — rather than continuously watching the user. When it detects the person using the phone has changed, it automatically applies that person's saved profile, mode, and restrictions.

### Core Features

- **Face + fingerprint recognition** — matches against enrolled profiles on-device; fingerprint (Android BiometricPrompt) is the fallback for low light
- **DOB-based accuracy** — known profiles store an actual date of birth instead of re-guessing age every time; age-bracket estimation only runs as a fallback for unrecognized faces
- **Parent-gated enrollment** — new profiles can only be created after the existing admin authenticates, preventing self-enrollment as a false "adult"
- **Adaptive handover detection** — requires two consistent detections before switching identities, avoiding mode-flickering from bad lighting or angles
- **Child PIN override** — a separate PIN that always forces Child mode, regardless of any other unlock method
- **Protected-action re-authentication** — changing screen-time limits, disabling restrictions, or editing profiles requires live admin re-auth
- **Phone-wide enforcement** — uses Android's Accessibility Service to detect and block restricted apps anywhere on the phone, plus Device Admin APIs and a default-launcher setup for near-system-level control without root
- **Screen-time budgeting** — per-profile daily limits, continues counting from where it left off across handovers, auto-locks at zero
- **Privacy-first** — raw camera frames are discarded immediately after generating a face embedding; nothing is uploaded or transmitted; only the embedding, DOB, fingerprint reference, and PIN hash are stored locally

---

## How It Works

```
Unlock / Screen Resume / Protected Action / Suspected Handover
                    │
                    ▼
           Face Detection (on-device)
                    │
                    ▼
        Face Embedding + Profile Match
                    │
          ┌─────────┴─────────┐
          ▼                   ▼
      KNOWN PROFILE        UNKNOWN FACE
          │                   │
          ▼                   ▼
   Load DOB → Role      Safe/Restricted Mode
          │              + Optional Admin Enrollment
          ▼
  2-Detection Confirmation
          │
          ▼
     Context Engine
   (time, app, screen time, bedtime rules)
          │
          ▼
      Policy Engine
          │
          ▼
      Mode Manager
   ┌──────┼──────┐
   ▼      ▼      ▼
 CHILD   TEEN   ADULT
```

Camera access is event-triggered only — it opens for a short verification, then releases immediately. There is no continuous or fixed-interval polling.

---

## Tech Stack

- **Platform:** Android (native)
- **On-device face recognition:** lightweight face embedding model (e.g. MobileFaceNet-class), running on the Snapdragon NPU
- **Biometric fallback:** Android `BiometricPrompt` API
- **Phone-wide enforcement:** `AccessibilityService`, `DeviceAdminReceiver`, `UsageStatsManager`
- **Storage:** local encrypted on-device storage — no backend, no cloud API

---

## Project Status

This is a hackathon prototype in active development for the iQOO City Battles Hyderabad event (Sept 26–27, 2026). Feature scope is prioritized as:

- **P0 (must-have):** face recognition, unlock/resume verification, 2-step identity confirmation, automatic Child ↔ Adult switching, camera released after every check
- **P1 (target):** protected-action re-auth, handover detection, screen-time integration, unknown-user safe mode, parent-gated enrollment
- **P2 (stretch):** fingerprint fallback, Teen mode, broader handover signals

---

## Getting Started

> Setup instructions to be added as the Android project structure is finalized.

```bash
git clone https://github.com/Retheshvs/SmartGuard_IQOO_prototype.git
cd SmartGuard_IQOO_prototype/SmartGuard
# Open in Android Studio
```

---

## Track

Submitted under **Smart Living** — iQOO City Battles Hyderabad 2026.

## Author

Built by [Retheshvs](https://github.com/Retheshvs).
