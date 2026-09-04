# Essential

GitHub Releasesを利用するアプリ更新と署名付き配信の手順は[APP_UPDATES.md](APP_UPDATES.md)を参照してください。

Material 3 Expressiveを基調にしたAndroidアプリです。ホーム、ダウンローダー、QRスキャナー、予定表ジェネレーターを実装しています。

## 技術構成

- Android固有処理・UI: Kotlin / Jetpack Compose
- コアロジック・重い処理: Rust
- KotlinとRustの接続: JNI
- 対応ABI: `arm64-v8a`、`armeabi-v7a`、`x86`、`x86_64`

## 機能

- ダウンローダー: yt-dlpとFFmpegを使い、権利のある公開動画・音声・画像を`Download/Essential`へ保存します。
- QRスキャナー: CameraX、端末のCamera HAL、端末内ML KitでQRコードを読み取ります。Quick Settings Tileにも対応します。
- 予定表ジェネレーター: 予定を入力し、PDF・UTF-8文章・PNG画像へ端末内で出力します。

制限事項は`ENGINE_LIMITS.md`、対象端末方針は`DEVICE_SUPPORT.md`、ライセンス上の注意は`THIRD_PARTY_NOTICES.md`を参照してください。

## ビルド

1. Rustライブラリを生成します。

   ```powershell
   .\scripts\build-rust.ps1
   ```

2. Androidアプリをビルドします。

   ```powershell
   .\gradlew.bat assembleDebug
   ```

Rustの生成済みJNIライブラリはAndroidプロジェクトへ含まれます。Rustコードを変更した場合は、Androidビルド前にスクリプトを再実行してください。
