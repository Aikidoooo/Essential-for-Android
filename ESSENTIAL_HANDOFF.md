# Essential 開発引き継ぎメモ

このファイルは、次のチャットでEssentialの開発を再開するための現行仕様・作業ルール・検証手順をまとめたものです。古い作業履歴と現行ソースが矛盾する場合は、現行ソースとこのメモを優先し、変更前に`Work history.txt`を確認してください。

## 次のチャットで最初に行うこと

1. 作業ディレクトリを`D:\#AI開発\Android\Essential`にする。
2. `Work history.txt`とこの`ESSENTIAL_HANDOFF.md`を読む。
3. `git status --short`、`git log -5 --oneline`、`git remote -v`を確認する。
4. ユーザーの新しい依頼を現行仕様へ反映する。明示されない限り既存機能を削除・リセット・公開しない。
5. 編集後は`Work history.txt`へ日付付きで詳細を追記し、ビルド結果・端末確認・未確認事項を分けて記録する。

## プロジェクトとGit

- プロジェクト: `D:\#AI開発\Android\Essential`
- Git remote: `https://github.com/Aikidoooo/Essential-for-Android.git`
- 現在のブランチ: `main`
- 現在の公開バージョン: `v0.5.7`（versionCode 18）。Pro Filmの比較・現像・モーション改善、外部Picker、マインスイーパー12×24、全画面のスクロール追従改善を含む。
- 公開コミット: `72920ef Essential 0.5.7を公開`。タグは`v0.5.0`〜`v0.5.7`。
- GitHub Release `Essential v0.5.7`は正式公開済み。Release URLは`https://github.com/Aikidoooo/Essential-for-Android/releases/tag/v0.5.7`。ローカル検証用スクリーンショットとUIツリーは公開対象外。
- ユーザーが明示的に公開を依頼するまで、GitHubへのpush、タグ作成、Release公開、外部アップロードを行わない。

## 開発ルール

- ユーザーとの対話・計画・説明は日本語。
- コード内コメントとドキュメンテーション文字列も日本語。変数名は英語で分かりやすくする。
- `Work history.txt`を必ず変更前に読み、変更後に事細かに追記する。
- ファイル編集は`apply_patch`を使用し、既存のユーザー変更を上書きしない。
- Material 3 Expressive、liquid glass、Progressive Motion UI、マイクロインタラクションを基本デザインとする。
- 画面操作・ネットワーク・実機確認の結果と、ソース／ビルドだけで確認した結果を区別して報告する。
- 物理端末で未確認の動作を「対応済み」と断定しない。
- ダウンローダーは本人が保存権限を持つ公開コンテンツのみ。ログイン、Cookie、DRM、地域制限、暗号化、アクセス制御の回避や任意コマンド実行は実装しない。
- AIを使う機能では、可視テキストの重複除去と送信量の上限を設け、token使用量を可能な限り少なくする。

## Androidビルド構成

- `applicationId` / namespace: `jp.essential.app`
- `versionCode = 18`
- `versionName = "0.5.7"`
- `compileSdk = 36`、`targetSdk = 35`、`minSdk = 26`
- Android Gradle Plugin `8.9.1`
- Gradle Wrapper `8.11.1`
- Kotlin `2.2.10`
- Java/Kotlin JVM target `17`
- Jetpack Compose UI/Foundation `1.7.6`
- Material 3 `1.4.0`
- 対応ABI: `arm64-v8a`、`armeabi-v7a`、`x86`、`x86_64`、universal APK
- Kotlin/Composeのビルドは日本語パスで問題が出ることがあるため、ASCII Junction経由で実行する。

### 標準ビルド（PowerShell）

```powershell
$junction = 'C:\Users\waki1\Desktop\essential-build'
$env:GRADLE_USER_HOME = 'C:\Users\waki1\Desktop\essential-gradle-home'
$env:GRADLE_OPTS = '-Dkotlin.compiler.execution.strategy=in-process'
$env:ANDROID_USER_HOME = 'C:\Users\waki1\Desktop\essential-android-home'
Set-Location -LiteralPath $junction
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --max-workers=1
```

通常のCI相当検査は`clean test lint assembleDebug --no-daemon --max-workers=1`。Kotlin daemonのアクセス拒否警告後にfallback compilerへ切り替わることがあるが、`BUILD SUCCESSFUL`なら既知の環境警告である。

### エミュレーター確認

- adb: `C:\Users\waki1\AppData\Local\Android\Sdk\platform-tools\adb.exe`
- 接続先: `emulator-5554`（主にPixel_10a相当のAndroid Emulator）
- x86_64 APK: `D:\#AI開発\Android\Essential\app\build\outputs\apk\debug\app-x86_64-debug.apk`
- インストール: `adb install -r -d app/build/outputs/apk/debug/app-x86_64-debug.apk`
- UI確認: `adb shell uiautomator dump /sdcard/window.xml`、`adb exec-out screencap -p > screenshot.png`
- 現在の最新x86_64 Debug APKは約95MB。universalは全ABIを含むため約262MBで、端末にはABI別APKを優先する。
- 物理端末、カメラ、実ネットワーク、Firebase実アカウント、120Hzの体感は必要に応じて別途確認する。

## アプリの入口・ナビゲーション

- `MainActivity.kt`: Intent処理、共有URL、Quick Settings QR入口、テーマ、アイコン、fps、初回設定、廃止Classiデータ回収。
- `LauncherActivity.kt`: activity-aliasから実画面を起動する非表示ランチャー入口。
- `ui/EssentialApp.kt`: Home、機能一覧、設定、FeatureRoute、背景、ボトムナビ、画面遷移。
- 初回起動時、`home_shortcut_configured`が未設定なら設定画面を開く。設定したホームショートカットは`appearance` SharedPreferencesへ保存する。
- 現在のFeatureRouteは`Downloader`、`QrScanner`、`Schedule`、`Files`、`MiniGame`、`Routine`、`ProFilm`。
- Homeの機能枠はダウンローダー、QRスキャナー、行程表、ファイル参照、ミニゲーム、日課、Pro Film。Classi枠は現行版から削除済み。
- `ui/StartupMotion.kt`の`progressiveItem`などで、各まとまりを下から順にフィードインする。

## 現在の主な機能

## 過去の重要要件（今後も壊さない）

- ダウンローダーの共有URL経路はX/Twitter、Instagram、TikTokなどの公開コンテンツを対象にする。TikTok画像ではロゴ画像と真っ黒なダミー画像を候補から除外し、公開されている画像を一覧化する。
- X/Twitter動画は先頭1〜2秒で停止しないよう、直接MP4・映像音声の同期を優先する。サイト仕様変更時は成功を偽装しない。
- 画像候補は読み込み後に横並びで表示し、タップで選択、長押しでプレビューする。選択した画像だけを保存し、保存日時はダウンロード時刻にする。
- Android StudioのエミュレーターへDebug APKを反映して確認する。Google Pixelを含む物理端末の処理速度、カメラ、120Hz描画は別途実測が必要。
- アップデートは起動時／手動確認とし、古いAPK・`.part`・更新用一時ファイルを自動回収してアプリデータが増え続けないようにする。ユーザー操作なしの自動インストールは行わない。
- 初回起動は設定画面へ移動し、ホームのショートカット機能を設定できる。設定値は`appearance`へ保存する。

### ダウンローダー

- `feature/downloader/DownloaderScreen.kt`、`YtDlpDownloadEngine.kt`、`ImageDiscovery.kt`、`InstagramImageParser.kt`、`VideoDownloadPolicy.kt`、`DownloadTimestamp.kt`。
- yt-dlp、FFmpeg、Camera等ではなく、公開URLの動画・音声・画像を固定オプションで処理する。
- X/Twitterの動画は直接MP4を優先して、先頭1〜2秒で止まるHLS依存を避ける方針。
- X、Instagram、TikTok等の画像URLでは公開画像を複数検出し、画像を横並び表示。画像タップで選択、長押しでプレビューし、選択画像だけ保存する。
- Instagramは投稿内の公開画像を可能な限り全件表示する。ログイン必須・非公開・埋め込み禁止・サイト仕様変更時は失敗を成功表示しない。
- 保存先は`Download/Essential`。`DownloadTimestamp`とMediaStoreの`DATE_TAKEN`で、ファイル／写真の日時をダウンロード時刻にする。
- yt-dlp本体の更新UI、更新専用一時領域、失敗時の`.part`回収を実装済み。

### QRスキャナー

- `feature/qr/QrScannerScreen.kt`、`QrDetectionTracker.kt`、`QrScannerTileService.kt`。
- CameraX Camera2バックエンド＋端末内ML Kit Barcode Scanning。Quick Settings Tileあり。
- 検出矩形をPreviewViewのクロップに合わせて画面座標へ変換。認識したQRの位置を実際にタップすると、HTTP/HTTPSだけ外部ブラウザーで開く。
- 戻る／ライトボタンは円形Surfaceと同じ形状・押下モーション。検出外タップはフォーカス操作。
- 物理カメラでの位置精度・端末別の露出や倍率は未確認。

### 行程表・ファイル参照

- 行程表: `feature/schedule/ScheduleGeneratorScreen.kt`、`ScheduleDocumentGenerator.kt`。入力を端末内でPDF、UTF-8文章、PNGへ出力。保存先はStorage Access Framework。
- ファイル参照: `feature/files/FileReferenceScreen.kt`、`MediaFileEngine.kt`、`FfmpegRunner.kt`。元ファイルを変更せず、圧縮・変換・背景透過の生成物を保存。ML Kit Subject Segmentationは初回モデル準備が必要な場合がある。
- 動画のフレーム切り取りは専用画面で動画プレビュー、時間スライダー、前後1フレーム移動、PNG保存を行う。実フレーム寸法を優先して縦横比へ追従し、外枠、プレイヤー、フォールバック画像をすべて角丸にする。
- 標準デコーダーが再生を拒否した場合、システムエラーダイアログを出さず`MediaMetadataRetriever`の実フレームプレビューへ切り替える。

### Pro Film

- `feature/profilm/ProFilmScreen.kt`。OpenDocumentで写真を参照し、EXIF回転を補正して長辺最大2560pxで端末内処理する。
- Leica M9、Leica M3、Leica (Xiaomi Original)、Hasselblad (OPPO Original)、Huawei (Huaweiスマートフォン)、ZEISS (Vivo Original)、Sony (Xperia Original)、FUJIFILM (PROVIA)の8種類。
- 色チャンネル、選択色、露出、階調、黒、ハイライト、粒状感、周辺減光を組み合わせ、100%の加工結果と元画像を強度0〜100%で合成する。公式LUTや実機ISPの完全再現とは表現しない。

### ミニゲーム

- `feature/minigame/MiniGameScreen.kt`に、どすこい、マインスイーパー、Block Blastがある。
- メニューから各画面へ戻る階層を持ち、Backボタンを維持する。

#### どすこい

- `app/src/main/assets/dosukoi.html`をWebViewAssetLoaderで同梱表示。現在のHTMLは約205KB。
- HTML内でFirebase compat SDK（Auth、Realtime Database）を読み込み、Firebase匿名認証とルーム作成／参加／同期を行う。既存のFirebaseプロジェクト設定はHTML内にあるため、必要性なく差し替えない。
- 起動演出はDOSUKOI文字、中央ユーザーアイコン、左右アイコン、遊ぶボタンの順。遊ぶ後にCreate Room／Join Roomを表示。Join Roomには貼り付けアイコン。
- 待機場、ゲーム画面、プロフィール、退出、ゲーム中UIをHTML側で構成。暗いDOSUKOI背景はアプリ全体のシステムバーまで連続させる。
- 中央カードを前面、左右カードを奥側へ回転配置し、立体感を出している。
- CSS／WebViewへ30、60、120fps設定を伝え、Android 15以降は`requestedFrameRate`を指定する。端末が実際に120Hzを持たない場合、120fpsを保証しない。
- WebView描画レイヤー、Renderer priority、不要な再描画抑制を設定済み。物理端末での滑らかさは未確認。

#### マインスイーパー

- `MiniGameScreen.kt`内に実装。先に「マス目を選択」画面を表示し、8×8（地雷10）、9×9（地雷10）、12×12（地雷22）から選択する。
- 通常タップで開く、長押しで旗、旗立てモードのON/OFF、クリア／ゲームオーバー、再挑戦を実装。

#### Block Blast（現行最新仕様）

- 実装ファイル: `feature/minigame/BlockBlastScreen.kt`
- 8×8盤面、3個の手札、縦横の完成列消去、配置数＋同時消去ボーナス。盤面、手札、現在スコア、ベストスコア、倍率状態をSharedPreferencesへ保存する。
- 操作は手札から盤面へドラッグするだけ。手札タップによる選択、盤面タップ配置は削除済み。
- 右上の設定ボタン、ゲームリセットボタン、リセット確認ダイアログを削除済み。
- 下部の「タップで選択して盤面をタップ…」説明文を削除済み。中央文言は「ブロックをドラッグして盤面へ置こう」、配置後もドラッグ操作を案内する。
- 手札は枠、背景、状態文言を描画せず、ブロック形状だけを表示。外接矩形だけを描画し、空の4×4余白は表示しない。
- 手札行高は132dp、プレビューセルは25dp。不可視のドラッグ受付領域は残している。
- `BlockShapeMetrics`で形状中心を計算し、持った場所に依存せずブロック中央を指位置へ合わせる。ドラッグ中の浮遊プレビュー、候補セルの有効／無効色、盤面外・重複配置拒否は維持。
- 直接候補へ置けない場合は近傍の配置可能な隙間へ吸着する。全手札を配置できない場合は現在スコアと「次へ」を表示し、ベストスコアを残して新しいゲームへ進む。
- 配置ごとの列消去が連続すると、2連続で10秒間2倍、3連続で10秒間3倍のように倍率を上げる。倍率中は配置点と消去点へ適用する。
- `BlockBlastRulesTest.kt`で交差列消去、境界・重複拒否、配置可能性、吸着、連続消去倍率を検証。
- 最新確認画像: `build-blockblast-drag-only-latest.png`、`build-blockblast-drag-only-latest-drag.png`、比較QA: `design-qa-comparison.png`。

### 日課

- `feature/routine/RoutineScreen.kt`。デイリー1〜5pt、ウィークリー5〜15ptを追加・編集・削除。絵文字を設定でき、期間ごとの達成表示とポイント加算がある。
- SharedPreferences名は`routine`。タスクはJSON、合計は`total_points`、取得済み報酬は`claimed_rewards`に保存。
- ポイントリセットは確認ダイアログから実行。合計ポイントとレベルを0相当へ戻すが、日課・達成状態・取得済み報酬は残す。
- 最大レベルは60。`requiredRoutineXp(level)`は次の式を使い、`round(raw / 15.0)`を整数化する。levelが1未満または60以上なら0。

```text
1 <= L < 16: 375 + 118 * (L - 1)
16 <= L < 40: 2375 + 290 * (L - 16)
40 <= L < 50: 10550 + 960 * (L - 40)
50 <= L < 55: 26400 + 2400 * (L - 50)
55 <= L < 60: 232350 + 26490 * (L - 55) + 110 * (L - 55)^2
requiredXP(L) = round(raw / 15)
```

- 報酬レベルは16、20、25、30、35、40、50、55、60。現行表示名は順にAnemo Essential、Geo Essential、Electro Essential、Dendro Essential、Hydro Essential、Pyro Essential、Lunar Essential、Cryo Essential。LV60はアイコン変更権。
- 報酬画像は`app/src/main/res/drawable-nodpi/routine_reward_lv16.png`〜`routine_reward_lv60.png`。設定画面のアプリアイコン選択とactivity-aliasへ同期する。
- レベル報酬一覧とアプリアイコン設定は折りたたみ可能。`animateContentSize`、expand/shrink、fade、広いV字矢印を使い、最下部でも全体が一緒に動く設計。

## テーマとアプリアイコン

- `ui/theme/Theme.kt`、`Color.kt`、`AppIconManager.kt`、`AndroidManifest.xml`のactivity-aliasが担当。
- ライト／ダーク切替はMaterial 3カラーを同時補間。紫が途中に出ない夜空系ダーク配色、背景グラデーションは360ms、面・文字色は420msを基準とする。
- 設定画面のテーマスイッチは、ライト側の太陽、ダーク側の月・星を持つMaterial 3 Expressiveピル。押下とサム移動を同じイージングで動かす。
- アイコン変更時はランチャーaliasを一つだけ有効化し、アイコンに合う背景グラデーションとテーマを適用する。DOSUKOIの暗色WebView背景はアイコン背景へ変更しない。
- DebugビルドだけApplication IDへ`.debug`を付け、表示名を`Essential Debug`とする。ComponentNameのpackageはApplication ID、クラス名はソースnamespaceを使用し、正式配布版とデータを消さず共存できる。
- 現行アイコンID（内部IDは互換性のため変更しない）: `default_light`、`default_dark`、`mint`（Anemo）、`sunset`（Geo）、`violet`（Electro）、`forest`（Dendro）、`ocean`（Hydro）、`ruby`（Pyro）、`sky`（Lunar）、`rose`（Cryo）、`reference`（LV60）。

## Classiについて（重要）

- Classi機能は項目・画面・GeckoView・拡張機能を含めて現行アプリから全削除済み。再追加しない限り復活させない。
- `MainActivity.cleanupRetiredClassiData()`が起動時に旧Classi SharedPreferences、Mozilla/geckoキャッシュ、Keystore aliasを回収する。
- 削除前バックアップはローカル`backups/`にあり、`.gitignore`で公開対象外。バックアップを勝手に削除しない。
- 過去の`Work history.txt`にはClassi開発時の記録が残っているが、現行ソースの仕様ではない。

## アプリ更新

- `update/`配下の`AppUpdateUi.kt`、`GitHubUpdateRepository.kt`、`UpdatePolicy.kt`、`UpdateStoragePolicy.kt`、`UpdateAvailableDialog.kt`、`UpdateInstallActivity.kt`。
- `gradle.properties`の`UPDATE_REPOSITORY=Aikidoooo/Essential-for-Android`からGitHub Releasesの最新版を起動時／手動で確認する。常駐WorkManagerや無確認自動更新はしない。
- APKはアプリ専用noBackup領域へ一時保存し、サイズ、SHA-256、applicationId、versionCode、versionName、minSdk、署名証明書を検証してからPackageInstallerへ渡す。
- キャンセル・失敗・再起動後の`.part`、期限切れAPK、管理情報のないファイルを回収する。既存のyt-dlp実行環境を誤削除しない。
- 正式Releaseは同一署名鍵が前提。必要Secretsは`ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`。鍵やTokenをリポジトリへ入れない。
- GitHub Actionsはタグ`v*`で起動し、4 ABI Rust再ビルド、単体テスト、lint、署名、apksigner、16KB zipalign、5 APK＋SHA256SUMSをReleaseへ添付する。

## Rust/JNI

- `core-rust/Cargo.toml`、`core-rust/src/lib.rs`。crate名`essential_core`、cdylib、releaseはLTO・size最適化・strip。
- 生成済み`libessential_core.so`は`app/src/main/jniLibs/<ABI>/`へ置く。
- Rustを変更した場合は`scripts/build-rust.ps1`でAndroid NDK `27.0.12077973`の4ターゲットを再ビルドしてからGradleを実行する。
- 重い処理・コアはRust、Android UI／端末処理はKotlin/Composeという境界を維持する。

## テスト

- `app/src/test`にDownloader、BlockBlast、QR、Routine、Schedule、UpdatePolicy、UpdateStoragePolicyのJUnitテストがある。
- 最低限、変更後に`:app:testDebugUnitTest`と`:app:assembleDebug`を実行する。必要に応じて`lintDebug`も実行する。
- `git diff --check`を最後に実行する。
- UI変更はエミュレーターのスクリーンショットとUIツリーで、表示・押下経路・文言を確認する。
- 物理端末、実カメラ、実Firebaseルーム、サイト仕様変更後の外部URL、120Hz体感は別の未確認事項として報告する。

## 現在の直近タスクの状態

- Block Blastの継続保存はエミュレーターで強制終了後の復元まで確認済み。ゲーム終了面と倍率の実プレイ体感は未確認。
- 縦動画のフレーム切り取りは実フレーム570×1280へ枠が追従することをエミュレーターで確認済み。物理端末のコーデック差とPNG保存完走は未確認。
- Pro Filmはホーム、機能一覧、専用画面への遷移を`Essential Debug`で確認済み。実写真のPNG保存と実機カメラとの色一致は未確認。
- v0.5.6のGitHub Actions run `34473536697`は成功し、5 ABI種別の署名付きAPKと`SHA256SUMS.txt`を公開済み。
