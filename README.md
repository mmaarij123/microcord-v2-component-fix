# V2 Component fix for Microcord

A Discord Components V2 plugin that improves card layout and animated media in Microcord on Android.

## What you can do

- Read short text cards and single-image cards at a width that fits their content in chat and search.
- Watch animated GIFs and WebP images in V2 thumbnails and galleries with Microcord's GIF autoplay setting enabled.
- View attachment-backed thumbnails, galleries, and files when Discord supplies the media details in the message's attachments.
- Recover text or buttons from incomplete Sections. The repair also handles forwarded messages with their own attachments.
- Long-press an affected message from older chat history and choose **Open repaired Components V2**. The preview supports links, files, media, and spoiler reveals.

## Install

1. Install [Microcord](https://github.com/microcord/microcord) on Android and sign in to Discord.
2. Open the [latest GitHub release](https://github.com/mmaarij123/microcord-v2-component-fix/releases/latest). Under **Assets**, download and install the file ending in `-debug.apk`.
3. In Microcord, go to **Settings → Plugins → V2 Component fix**. Approve the signer if prompted, then enable the plugin.
4. For animated media, enable GIF autoplay under **Settings → Accessibility**.

You can install the release over v1.0.0 or v1.1.0 from the same signer. The Android package is `io.github.mmaarij123.v2componentfix`.

## Build from source

V2 Component fix is written in Kotlin and built with Gradle against Microcord Plugin SDK 1.0.6. It targets Microcord build 307 and Plugin API level 3. Card layout and playback use Microcord's native renderer. Inline media and Section repairs apply to incoming messages and updates. Older history uses the message-menu preview.

Install JDK 21, Android SDK Platform 36.1, and Android Build Tools 36.0.0. Clone this repository and run the following from its root.

```sh
./gradlew testDebugUnitTest assembleDebug
```

Use the Windows wrapper in PowerShell.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

The debug APK is written to `build/outputs/apk/debug/V2ComponentFix-debug.apk`.

## License

The original plugin code is licensed under MIT. Copyright © 2026 Maarij (mmaarij123). You can use, modify, and redistribute it under the terms in [the MIT license](LICENSE).

See the notices for Microcord, the Gradle Wrapper, and the Kotlin runtime. [Third-party notices](THIRD_PARTY_NOTICES.md)
