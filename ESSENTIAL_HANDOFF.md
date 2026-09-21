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
- 現在の公開バージョン: `v0.5.8`（versionCode 19）。`v0.5.9`（versionCode 20）はGitHub公開準備中。
- v0.5.9はPro Film削除、Downloader拡張、音声分離、アプリ共通レベル／プロフィール、Liquid Glass下部ナビゲーション、日課・QR・ミニゲーム改善を含む。
- 公開コミット: `d1786f7 Essential 0.5.8を公開`。タグは`v0.5.0`〜`v0.5.8`。
- GitHub Release `Essential v0.5.8`は正式公開済み。Release URLは`https://github.com/Aikidoooo/Essential-for-Android/releases/tag/v0.5.8`。ローカル検証用スクリーンショットとUIツリーは公開対象外。
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
- `versionCode = 20`
- `versionName = "0.5.9"`
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
- 現在の最新x86_64 Debug APKは約148MB。音声分離モデルとONNX Runtimeを同梱するため、universalは約367MB。端末にはABI別APKを優先する。
- 物理端末、カメラ、実ネットワーク、Firebase実アカウント、120Hzの体感は必要に応じて別途確認する。

### ファイル参照の音声分離

- `FileReferenceScreen.kt`の音声参照時に、Spleeter 2-stem FP16 ONNXモデルを使った端末内分離を表示する。ボーカルと楽器を個別に0〜200%で調整し、個別WAVと合成WAVを`Download/Essential`へ保存する。
- モデルは`app/src/main/assets/models/audio_separation/spleeter-2stems-fp16/`へ同梱し、初回使用時にアプリ専用領域へ展開する。入力はFFmpegで44.1kHz／16bit／ステレオへ正規化し、8秒ブロック＋1秒コンテキストでメモリを抑えて処理する。
- 実端末での分離速度、RAM、音質、長時間音源、各ABIの実行確認は未実施。今回確認済みなのはソースコンパイル、Unit Test、Lint、Debug APK生成まで。

## アプリの入口・ナビゲーション

- `MainActivity.kt`: Intent処理、共有URL、Quick Settings QR入口、テーマ、アイコン、fps、初回設定、廃止Classiデータ回収。
- `LauncherActivity.kt`: activity-aliasから実画面を起動する非表示ランチャー入口。
- `ui/EssentialApp.kt`: Home、コマンドパレット、プロフィール、FeatureRoute、背景、ボトムナビ、画面遷移。
- 初回起動時、`home_shortcut_configured`が未設定ならプロフィール画面を開く。設定したホームショートカットは`appearance` SharedPreferencesへ保存する。
- 現在のFeatureRouteは`Downloader`、`QrScanner`、`Schedule`、`Files`、`MiniGame`、`Routine`。
- Homeの機能枠はダウンローダー、QRスキャナー、行程表、ファイル参照、ミニゲーム、日課。Pro FilmとClassi枠は現行版から削除済み。
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
- ズーム操作は1×・2×・5×の補助FilterChipと共通の泡付きレールを使い、端末のCameraX対応範囲外の倍率は無効表示にする。
- 物理カメラでの位置精度・端末別の露出や倍率は未確認。

### 行程表・ファイル参照

- 行程表: `feature/schedule/ScheduleGeneratorScreen.kt`、`ScheduleDocumentGenerator.kt`。入力を端末内でPDF、UTF-8文章、PNGへ出力。保存先はStorage Access Framework。
- ファイル参照: `feature/files/FileReferenceScreen.kt`、`MediaFileEngine.kt`、`FfmpegRunner.kt`。元ファイルを変更せず、圧縮・変換・背景透過の生成物を保存。ML Kit Subject Segmentationは初回モデル準備が必要な場合がある。
- 動画のフレーム切り取りは専用画面で動画プレビュー、時間スライダー、前後1フレーム移動、PNG保存を行う。実フレーム寸法を優先して縦横比へ追従し、外枠、プレイヤー、フォールバック画像をすべて角丸にする。
- 標準デコーダーが再生を拒否した場合、システムエラーダイアログを出さず`MediaMetadataRetriever`の実フレームプレビューへ切り替える。

### Pro Film

この機能は2026-09-20のユーザー指示によりアプリから削除済みです。以下は削除前の履歴であり、現行ソース・APKには含まれません。

- `feature/profilm/ProFilmScreen.kt`と`feature/profilm/StyleEngine.kt`。CameraStyleAI v0.1の共通StyleEngineへ置き換え、Exif orientationを反映して端末ヒープ予算内は原寸、超過時は自動sampleする。
- Leica M9（5候補33^3 Adaptive LUT）、Leica M3、Xiaomi Leica Original、Hasselblad / OPPO、Huawei XMAGE、vivo ZEISS、Sony Xperia ST、FUJIFILM PROVIAの8種類。Huawei／vivoはBeta近似表示。
- `LutStyle`、`AdaptiveLutStyle`、`MonoFilmStyle`、`ParametricStyle`、将来用`OnnxStyle`を共通契約で選択し、100%のStyleEngine結果と元画像を強度0〜100%で合成する。中間強度は線形光量補間、メーカー内部ISPの完全再現とは表現しない。
- HasselbladのApache-2.0 cubeは`app/src/main/assets/styles/hasselblad_standard_srgb_65.cube`（33^3 fallback）、ライセンス本文は`app/src/main/assets/licenses/V-Log-Alchemy-Apache-2.0.txt`。sRGB→V-Log adapter経由で適用する。
- Huawei XMAGE Betaは暖色係数`0.16`、彩度`1.02`、赤茶色抑制行列で近似する。フィルター切替時の処理Linear/Bubbly進捗バーは削除し、非同期処理・キャンセル・保存時の進捗表示は維持する。
- 比較プレビューは元画像と現像後画像を同じ枠・同じ位置へ重ね、現像後レイヤーだけをクリップするImage Comparison Slider。参照写真の縦横比へ枠と後続UIがスプリング追従する。
- 強度レールは紫色・白ノブ・右から左へ流れる泡の`ui/BubblyBars.kt`を使用し、同じコンポーネントをアプリ全体のSlider／ProgressIndicatorへ適用する。Sliderは紫ゲージを白ノブの右端まで下に敷くため、0〜100%の全域でノブ左側に隙間が生じない。
- 保存はJPEG quality 96、標準Exif（撮影日時、カメラ／レンズ、露出、ISO、焦点距離、ホワイトバランス、GPS等）を可能な範囲でコピーし、orientation=Normal、ColorSpace=sRGBへ正規化する。ICCバイト列とメーカー独自タグは保証しない。
- 現像処理は完全オフラインで動作するが、Essential全体には既存Downloader等のためINTERNET権限が残る。Pro Film処理自体はネットワークAPIを呼ばない。
- `docs/PROFILM_CAMERASTYLEAI_INTEGRATION.md`に置き換え内容、ライセンス、検証結果、未確認事項を記録している。
- `emulator-5554`で最新Debug APKを起動し、Photo Picker、Pro Film遷移、8スタイルの横スクロール選択、M9/M3/Huawei/vivoの非同期処理、Intensity 50%、Before/After表示、JPEG保存完了と保存画像orientation=0を確認済み。物理端末の色再現・速度・OOMは未確認。
- 最新Essential Universal unsigned Release APKは`app/build/outputs/apk/release/app-universal-release-unsigned.apk`、254,675,446 bytes、SHA-256 `2CC1BB10311EB46A36394F8EA2C9A1514C78544A6DCD8BECC6D3FC7A5A27661C`。最新x86_64 Debugは96,954,232 bytes、SHA-256 `988BD5D5F40AB775BA504B7CC0BC4EC5DCAEDF5FA344C6B9DDB40D7D863F8B66`。sRGB共有4096分割transfer tableで高解像度の毎画素powを削減し、M9適応候補を33^3へ更新、Intensity 0%高速経路を追加した。固定LUT／Parametric／Monoスタイルでは全画素統計走査を省略し、65^3 LUT／M9候補生成はバックグラウンド準備、大画像統計は行単位キャンセル対応へ更新した。デコード時はDisplay P3等をsRGBへ明示変換する。カタログ準備失敗時は画面エラーへ変換し、クラッシュさせない。全スタイル共通の線形暗部トーで黒つぶれを抑え、中間調・白点は維持する。

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

- `MiniGameScreen.kt`内に実装。先に「マス目を選択」画面を表示し、8×8（地雷10）、9×9（地雷10）、12×12（地雷22）、12×24（地雷44）から選択する。
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
- ドラッグ中は`blockPointerAboveFinger`でブロックを指の上へ表示し、表示プレビューと吸着／当たり判定を同じ補正座標で処理する。
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
- 報酬の展開・折りたたみは遅延感を抑えた短いモーション（サイズ220ms、展開240ms、折りたたみ170ms）。日課項目は情報行の長押しまたは「設定」から通知オン／オフと通知時刻を編集できる。
- `RoutineCadence.Event`のイベントミッションは開始日・開始時刻・期間日数を持ち、期間中は1日1回、期間外は達成不可。`RoutineNotificationScheduler`がデイリー／ウィークリー／イベントの通知をAlarmManagerへ登録し、Android 13以降は通知権限を要求する。設定ダイアログは縦スクロール対応。

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
- `storage/StorageMaintenance.kt`が更新後の初回起動と6時間間隔で、アプリ固有の期限切れ一時キャッシュ、旧形式のyt-dlpルート、FFmpegKit対応ABIで不要な展開済みFFmpegを回収する。ユーザーのDownload/Essential保存物、設定、現行Python／yt-dlp環境は対象外。arm64-v8a／x86_64では`YtDlpDownloadEngine`もFFmpegランタイムの重複展開を行わず、音声は取得後にFFmpegKitで変換する。
- 正式Releaseは同一署名鍵が前提。必要Secretsは`ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`。鍵やTokenをリポジトリへ入れない。
- GitHub Actionsはタグ`v*`で起動し、4 ABI Rust再ビルド、単体テスト、lint、署名、apksigner、16KB zipalign、5 APK＋SHA256SUMSをReleaseへ添付する。

## Rust/JNI

- `core-rust/Cargo.toml`、`core-rust/src/lib.rs`。crate名`essential_core`、cdylib、releaseはLTO・size最適化・strip。
- 生成済み`libessential_core.so`は`app/src/main/jniLibs/<ABI>/`へ置く。
- Rustを変更した場合は`scripts/build-rust.ps1`でAndroid NDK `27.0.12077973`の4ターゲットを再ビルドしてからGradleを実行する。
- 重い処理・コアはRust、Android UI／端末処理はKotlin/Composeという境界を維持する。

## テスト

- `app/src/test`にDownloader、BlockBlast、QR、Routine、Schedule、UpdatePolicy、UpdateStoragePolicy、StorageMaintenanceのJUnitテストがある。
- 最低限、変更後に`:app:testDebugUnitTest`と`:app:assembleDebug`を実行する。必要に応じて`lintDebug`も実行する。
- `git diff --check`を最後に実行する。
- UI変更はエミュレーターのスクリーンショットとUIツリーで、表示・押下経路・文言を確認する。
- 物理端末、実カメラ、実Firebaseルーム、サイト仕様変更後の外部URL、120Hz体感は別の未確認事項として報告する。

## 現在の直近タスクの状態

- Block Blastの継続保存はエミュレーターで強制終了後の復元まで確認済み。ゲーム終了面と倍率の実プレイ体感は未確認。
- 縦動画のフレーム切り取りは実フレーム570×1280へ枠が追従することをエミュレーターで確認済み。物理端末のコーデック差とPNG保存完走は未確認。
- Pro Filmは削除前にホーム、機能一覧、専用画面への遷移を`Essential Debug`で確認済み。現行版では専用画面・導線・アセットを削除し、APKへ残っていないことを静的確認した。
- 2026-09-11時点で共通バー、QR倍率補助、Block Blastの指上プレビュー、日課イベント／通知設定を含むDebugビルドをエミュレーターへインストール済み。実機の通知発火・カメラ倍率上限・長押し体感は未確認。
- v0.5.8のGitHub Actions run `34579976274`は成功し、5 ABI種別の署名付きAPKと`SHA256SUMS.txt`を公開済み。

## アプリレベル／プロフィール／ミニゲームXP（2026-09-20）

- `profile/AppProgress.kt`の`AppProgressStore`をアプリ全体のXP・報酬受取状態の共通保存領域とした。初回アクセス時に旧`routine` SharedPreferencesの`total_points`と`claimed_rewards`を移行し、日課の既存進行を保持する。
- レベル計算式は`requiredAppXp`／`calculateAppLevel`へ移し、旧日課テスト用の`requiredRoutineXp`／`calculateRoutineLevel`は互換ラッパーとして残した。日課画面はアプリレベルカードと日課本体だけを表示し、レベル報酬一覧は削除した。
- ホーム下部ナビゲーションの「設定」を「プロフィール」へ変更した。プロフィール画面で表示名（15文字以内）と絵文字アバターを設定でき、既存のテーマ、アプリアイコン、モーションfps、ホームショートカット、yt-dlp更新、アプリ更新設定はプロフィール画面下部から引き続き利用できる。報酬一覧と受取操作はプロフィール画面だけに置いた。
- どすこいは画面表示後5分ごとに2XPを加算する。プロフィール名を`profile` SharedPreferencesへ保存し、WebViewへ`EssentialProfileBridge`を追加して、プロフィール画面とどすこいの表示名を`essential_profile_name`／`dosukoi_nickname`で同期する。
- Block BlastはゲームセッションIDとXP受取済みIDを保存し、ゲーム終了時に最終スコアの10分の1を通常の四捨五入で一度だけXPへ加算する。マインスイーパーは8×8／9×9で1XP、12×12で2XP、12×24で5XPをクリア時に一度だけ加算する。
- `AppProgressRulesTest`でBlock Blastの四捨五入境界とマインスイーパー4盤面のXPを検証した。
- ASCII Junction経由の最終確認はJUnit 29件成功、Lint `errors=0`（issues=67）、Debug universal APK生成成功。APKは`C:\Users\waki1\Desktop\essential-build\app\build\outputs\apk\debug\app-universal-debug.apk`、366,578,915 bytes、SHA-256 `6CD0E90D23A055789DA142CD28ED945A5FD23CE1E14D9DA290B7D0A23748D1E0`。`git diff --check`は改行コード変換警告のみ。
- 署名不一致の既存`emulator-5554`へ再インストールするためのアンインストール／データ消去は行っていない。プロフィール保存、どすこい5分計時、Block Blast／マインスイーパーの実機XP付与、報酬受取、アプリ内表示は静的・ビルド検証までで、端末UIスモークは未確認。

## Liquid Glass下部バナー・プロフィール画像（2026-09-20）

- `ui/EssentialApp.kt`の下部ナビゲーションは、半透明surface、白い光沢グラデーション、上端ハイライト、境界線、影、選択位置のスプリングを重ねたLiquid Glass風のバナーになっている。Home／機能一覧／プロフィールの既存遷移は維持する。
- プロフィールアイコンは`ProfileAvatar`で表示する。画像がある場合はアプリ専用領域の画像をEXIF回転補正・最大512px相当へ縮小して表示し、画像がない／読み込み失敗時は保存済み絵文字を表示する。グラデーションリング、内側余白、影、右下の画像追加ボタンを持つ。
- プロフィール画像の参照は`EssentialMediaPickerContract`へ`image/*`を渡して行う。`profile/ProfileStore`が参照元を`files/profile/profile_icon_<timestamp>.img`へコピーし、元ファイルを変更せずにパスを保存する。画像変更時は旧コピーを回収し、「絵文字に戻す」で画像を外せる。
- 確認用エミュレーターで、Liquid Glassバナー、画像選択ボタン、プロフィールアイコン、絵文字列を表示確認済み。実ユーザー画像の選択・保存・再起動復元は未確認。JUnit 29件成功、Lintエラー0、Debug APK生成成功。

## ホーム下部コマンドパレット（2026-09-20）

- `ui/EssentialApp.kt`の中央「機能一覧」タブを、従来の一覧画面への直接遷移ではなく、フルビューポートのコマンドパレット起点へ変更した。Homeとプロフィールのタブ遷移は維持し、Back／外側タップ／ESC相当の戻る操作でパレットを閉じる。
- パレットは暗い半透明オーバーレイ、白い境界線、角丸の液体ガラス風コンテナで構成した。検索入力、区切り線、カテゴリ別スクロールリスト、アイコン、機能名、説明、キーボードショートカットを表示し、検索結果の先頭を`bg-white/10`相当のアクティブ状態で強調する。
- 既存のTailwind／React環境はないため、添付画像をレイアウトと雰囲気の参考として、既存のAndroid Jetpack Composeへ同等の視覚表現を実装した。新規画像アセットは追加していない。
- コマンド項目はダウンローダー、ファイル参照、ミニゲーム、QRスキャナー、行程表、日課。項目選択後は既存FeatureRouteへ接続し、パレットを閉じて対象機能を開く。
- ASCII Junction `C:\Users\waki1\Desktop\essential-build`経由で`:app:compileDebugKotlin`および`:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`をオフライン実行し、いずれも`BUILD SUCCESSFUL`。今回変更後のユニットテスト件数、Lint issue数はGradle出力に件数表示がなかったため未再集計だが、LintエラーはなくAPK生成まで完了した。universal Debug APKは366,578,915 bytes、出力先は`C:\Users\waki1\Desktop\essential-build\app\build\outputs\apk\debug\app-universal-debug.apk`。
- `git diff --check`は改行コード変換警告のみ。`adb`コマンドがこのシェルのPATHに存在せず、今回のコマンドパレットの実機／エミュレーターUIスモークは未実施。既存データを消去するアンインストールやRelease公開は行っていない。

## ホーム上部バナー True Liquid Glass（2026-09-20）

- `ui/EssentialApp.kt`のホーム上部`HeroCard`へ、Android 13以上で`RuntimeShader`＋`RenderEffect.createRuntimeShaderEffect`を使う屈折レイヤーを追加した。AGSLの`contents.eval`で外周ほど強くなる座標変位を行い、赤・緑・青を別座標でサンプリングしてRGB分散を表現する。単純なぼかしではなく、バナー内の背景光彩を光学的に歪ませる構成。
- 下層にheroLight／heroShade／heroAccentの放射光と移動する反射光、上層に白・マゼンタ・紫・シアンの境界sweep、内側境界、上端スペキュラーハイライトを重ねた。既存`PressableGlassCard`のタップ時スプリング縮小とハプティックフィードバックは維持した。
- API 33未満はRuntimeShaderを生成せず、Canvasの光彩・反射・色収差風境界へフォールバックする。API 33呼び出しは実行時API判定とLint抑制で保護し、Shader生成失敗時もCanvasフォールバックへ切り替える。
- `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug --rerun-tasks --no-daemon --max-workers=1`が成功し、Lintエラー0、Debug APK生成成功。APKは`C:\Users\waki1\Desktop\essential-build\app\build\outputs\apk\debug\app-universal-debug.apk`、355,616,368 bytes、SHA-256 `C256BA001A2826C06F21A7BC5D249672A462A19893C4E38421FCF241921973DF`。
- 既存`jp.essential.app.debug`と署名不一致のため、`emulator-5554`への`adb install -r`は`INSTALL_FAILED_UPDATE_INCOMPATIBLE`となった。既存データ保護のためアンインストール／データ消去は行わず、今回のRuntimeShader実描画は未確認。静的実装、Kotlinコンパイル、JUnit、Lint、APK生成は確認済み。

### Debug APK更新インストール済み（2026-09-20）

- ユーザーの明示依頼により、署名不一致だった確認用`jp.essential.app.debug`をアンインストールし、最新universal Debug APKを`emulator-5554`へ再インストールした。Release版`jp.essential.app`、ソース、Download保存物は変更していない。Debug版のアプリ内プロフィール／設定データは初期化された。
- `versionCode=19`、`versionName=0.5.8-debug`で起動成功。ホームタブへ移動し、Liquid Glassバナーの屈折光彩、色分散風フリンジ、ハイライト、文字可読性をエミュレーターで目視確認済み。
- キャプチャ：`C:\Users\waki1\AppData\Local\Temp\essential-liquid-glass-home.png`

## 下部ナビゲーション Liquid Glassアイコンバー（2026-09-20）

- `EssentialNavigationBar`を、背景が透けて移る横長66dpのLiquid Glassレンズへ変更した。ホーム、機能一覧、プロフィールの表示ラベルは削除し、アイコン中心の操作へ変更した。contentDescriptionはアクセシビリティのため残している。
- アプリアイコンに連動した背景光彩、Android 13以上のRuntimeShader屈折、白・シアン・ピンクの境界分散、上端ハイライト、移動反射光を追加した。API 33未満とShader生成失敗時はCanvasフォールバックを使う。
- 選択中のアイコンだけを40dpのガラスピルで強調し、スプリング移動、押下縮小、機能一覧バッジ、ハプティックを維持した。
- `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug --rerun-tasks --no-daemon --max-workers=1`成功、Lintエラー0。最新APKは`C:\Users\waki1\Desktop\essential-build\app\build\outputs\apk\debug\app-universal-debug.apk`、355,620,784 bytes、SHA-256 `CC24EA0EBB66AD92668C788026CF3D3B451852C67DABCD0F959B4604E58AA611`。
- `emulator-5554`へ上書きインストールし、下部ラベルなしのLiquid Glassアイコンバーを目視確認済み。キャプチャ：`C:\Users\waki1\AppData\Local\Temp\essential-liquid-glass-nav-labels-removed.png`

## 下部Liquid Glassの固定青色除去（2026-09-20）

- 下部ナビゲーションが青く染まっていた原因を、固定シアン／青／ピンクの背景・境界色と切り分けた。下部専用`LIQUID_GLASS_NAV_SHADER_SOURCE`を追加し、既存背景のRGB別サンプリングと白い反射だけを行うよう修正した。
- `backgroundStart`／`backgroundEnd`を低い不透明度で使い、固定青色を廃止。選択ピルと境界も現在のアプリアイコン背景色＋白へ変更した。ホーム背景の実際の淡い黄緑が透けて見えることを確認済み。
- テスト・Lint・assemble成功、Lintエラー0。最新APKは`C:\Users\waki1\Desktop\essential-build\app\build\outputs\apk\debug\app-universal-debug.apk`、355,620,956 bytes、SHA-256 `03BB45979FBBE6DE460CEA08710AE789F29AE18EB0366FA421BF91341AC57FF9`。
- `emulator-5554`へ上書きインストールし、実画面で青色固定着色の除去を確認した。キャプチャ：`C:\Users\waki1\AppData\Local\Temp\essential-liquid-glass-nav-transparent.png`

## 下部Liquid Glass背面の日課表示修正（2026-09-20）

- `ui/EssentialApp.kt`の`Scaffold`コンテンツは`innerPadding`全体ではなく、上部の`calculateTopPadding()`だけを適用する。これにより、日課・機能一覧・プロフィールのスクロールコンテンツが下部バーの背面まで描画される。
- 下部バーと最後のカードの間にあった、背面の`AnimatedBackdrop`だけが見える緑色の空き帯をなくした。機能一覧の最下部で日課カードがバー背面へ連続して重なることをUIツリーとスクリーンショットで確認済み。
- `EssentialNavigationBar`の外側の白い背景を除去し、`LiquidGlassNavigationBackdrop`のアプリアイコン色の矩形と下部グローを低アルファへ変更した。白い反射、境界線、選択ピル、影、RuntimeShader／Canvasフォールバックは維持する。
- 検証済みuniversal Debug APK: `C:\Users\waki1\Desktop\essential-build\app\build\outputs\apk\debug\app-universal-debug.apk`、355,620,676 bytes、SHA-256 `0242D71A6B2BFE377404A32ECEDF98D0726D834E36BF98553890321E5958F574`。
- `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`は成功し、Lintエラー0。`emulator-5554`へ`adb install -r`で更新インストールし、確認キャプチャは`C:\Users\waki1\AppData\Local\Temp\essential-features-bottom-transparent2.png`。

## 下部Liquid Glassの透明度82%・下部影グラデーション（2026-09-20）

- `ui/EssentialApp.kt`の下部バーは透明度82%（不透明度18%）を`NAVIGATION_GLASS_TRANSPARENCY`／`NAVIGATION_GLASS_SURFACE_ALPHA`で定義した。日課カードなどの背面コンテンツは引き続きバー越しに見える。
- バー内部の縦グラデーションは、上部の白い反射から中間の薄い白を経て、下端の黒い影へ連続させた。下へ行くほど暗くなるLiquid Glass表現になっている。
- 追加の白い背景面は低アルファへ調整し、透明度82%の視認性と背面文字の可読性を両立した。選択ピル、境界線、移動光、RuntimeShader／Canvasフォールバックは維持する。
- 検証済みuniversal Debug APK: `C:\Users\waki1\Desktop\essential-build\app\build\outputs\apk\debug\app-universal-debug.apk`、355,620,688 bytes、SHA-256 `E0CB5E5B342E9ADA4708710BB364848BF59A0E3BA71BC1942CAA2771186C98C6`。JUnit 29件成功、Lintエラー0。
- `emulator-5554`へ更新インストール済み。確認キャプチャは`C:\Users\waki1\AppData\Local\Temp\essential-features-navigation-shadow.png`。

## プログラム整理による軽量化（2026-09-20）

- グラフィック、Liquid Glass、画像、モデル、音声資産、同梱ネイティブライブラリは変更していない。ReleaseのみR8コード縮小を有効化し、`isShrinkResources = false`でリソース削除を無効化した。
- `app/proguard-rules.pro`へ`@JavascriptInterface`の公開メソッド保持ルールを追加した。WebViewのどすこい連携をR8後も維持し、`EssentialCore`とONNX Runtimeの既存保持ルールも維持している。
- `MainActivity`の廃止Classiデータ・更新APK・ストレージ保守を、処理順を維持したまま`Dispatchers.IO`へ移した。初回Compose描画をファイル整理でブロックしない。
- `AppUpdateModel`の更新進捗通知を1%単位へ集約した。ネットワーク処理と検証は変更せず、進捗中のCompose再描画・UIコルーチン起動を抑える。
- Release APKサイズはR8前後で次のように変化した。
  - arm64-v8a: `133,478,096` → `125,535,399` bytes
  - armeabi-v7a: `110,662,040` → `102,719,343` bytes
  - x86_64: `139,806,559` → `131,863,862` bytes
  - x86: `120,213,496` → `112,270,803` bytes
- universal: `342,793,894` → `334,851,201` bytes
- 縮小後universal Releaseの内訳はDEX `5,101,244` bytes、`lib/` `427,500,683` bytes、`assets/` `40,455,122` bytes、`res/` `5,493,537` bytes。主な削減はコードであり、同梱モデルとネイティブライブラリは維持している。
- `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease`をオフライン実行し、`BUILD SUCCESSFUL`、JUnit全29件成功、Lintエラー0を確認した。R8のKotlin metadata警告と、zip形式ネイティブライブラリのstrip警告は既知の非致命警告として残っている。
- 最新Debug APKを`emulator-5554`へ更新インストールし、`jp.essential.app.debug.MainActivity`の起動成功と直近Logcatにクラッシュがないことを確認した。今回の変更はUI見た目を対象外としているため、追加の画面キャプチャは作成していない。
- `emulator-5554`へ更新インストール済み。機能一覧の最下部で、下部バーのboundsは`[48,2178][1032,2329]`、最終項目「ミニゲーム」の文字boundsは`[305,2085][579,2146]`となり、文字がバー開始位置より32px上にあることを確認した。

## 下部バーと最終コンテンツの重なり修正（2026-09-21）

- 前回、Liquid Glassの背面までコンテンツを描画するため`Scaffold`の下部余白を外していた。その結果、最下部までスクロールしたときに最後のボタンが下部バーと重なる状態になっていた。
- `ui/EssentialApp.kt`の`AnimatedContent`へ`innerPadding.calculateBottomPadding()`を適用した。背景は画面全体に残し、画面コンテンツだけが下部バーの高さ分を避けるため、背面の色やLiquid Glassの見た目は維持したまま最終項目を上へスクロールできる。
- `:app:lintDebug :app:assembleDebug`成功、Lintエラー0、Debug APK生成成功。ASCII Junction経由の`:app:testDebugUnitTest`も`BUILD SUCCESSFUL`で、JUnit全件成功。
- 日本語パス直実行ではテストランナーの`ClassNotFoundException`、JDK 25ではKotlin DSLの`25.0.4.1`解釈エラーが発生した。JDK 23を一時指定し、既存のASCII Junction経由でテストを再実行して成功させた。プロジェクト設定やユーザー環境は変更していない。
- `emulator-5554`へDebug APKを更新インストールし、機能一覧の最下部を確認した。下部バーのboundsは`[48,2178][1032,2329]`、最終項目「ミニゲーム」の文字boundsは`[305,2085][579,2146]`で、文字と操作対象がバーに重ならないことを確認済み。
- Debug APKを`emulator-5554`へ再インストールし、機能一覧を最下部までスワイプした。追加スワイプ後も最後の日課説明文は`[305,1810][927,1915]`、下部タブは`[48,2178][1032,2329]`で安定した。キャプチャ：`C:\Users\waki1\AppData\Local\Temp\essential-bottom-transparent-final3.png`

## 透明なタブ背面と末尾スクロール余白（2026-09-21）

- `AnimatedContent`全体の下部余白を削除し、Liquid Glassタブの裏側にホーム／機能一覧／プロフィールの背景コンテンツが透けて見える状態へ戻した。
- `HomeScreen`、`FeaturesScreen`、`ProfileScreen`の`LazyColumn`へ`innerPadding.calculateBottomPadding()`を渡し、末尾へ`NAVIGATION_CONTENT_CLEARANCE = 32.dp`を追加した。タブの色、透明度82%、影グラデーション、RuntimeShader／Canvasフォールバックは変更していない。
- `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`成功、JUnit全件成功、Lintエラー0。Debug APK生成成功。
- エミュレーターでは、最下部で最後の日課説明文が`y=1915`、下部タブ上端が`y=2178`となり、追加スワイプ後も位置が変わらないことを確認済み。背景カードはタブ背面へ透明に連続して表示される。

## 下部タブ・QR Auto・プロフィール初期状態の調整（2026-09-21）

- `ui/EssentialApp.kt`の`NAVIGATION_CONTENT_CLEARANCE`を`32.dp`から`29.dp`へ変更し、最下部スクロール時の余白を約10%縮小した。背景が下部タブ背面へ透ける構成、透明度82%、下方向の影グラデーションは維持している。
- 下部ナビゲーションのCanvas反射線、外周の白い境界線、選択中ホームタブの白い輪郭線を除去した。選択背景、アイコン、アニメーション、contentDescriptionは維持している。
- `feature/qr/QrScannerScreen.kt`のURL自動オープンSwitchを倍率セクション内へ移動し、現在倍率`1.0×`の下に`Auto` Checkboxとして表示する。チェック状態の保存とQR認識時の自動URLオープン処理は既存のまま。
- `ProfileScreen`の`rewardsExpanded`初期値を`false`へ変更したため、プロフィールを開いた直後はレベル報酬一覧が閉じている。
- ASCII Junction `C:\Users\waki1\Desktop\essential-build`で`:app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --no-daemon --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' --rerun-tasks`が成功。JUnit成功、Lintエラー0、Debug APK生成成功。APKは`C:\Users\waki1\Desktop\essential-build\app\build\outputs\apk\debug\app-universal-debug.apk`（355,619,964 bytes、SHA-256 `C232226CB6DE4C551FDFC20D9F30E54EDE2AE4DCFECEA9A014EB53B1F5B9F170`）。
- `emulator-5554`の`jp.essential.app.debug`へ`adb install -r`し、プロフィールの報酬閉状態、QR画面のAuto配置、ホームの白線除去をUIツリー／画面キャプチャで確認した。Release版のデータ削除、コミット、push、公開は行っていない。

## 下部末尾余白を追加で20%縮小（2026-09-21）

- `ui/EssentialApp.kt`の`NAVIGATION_CONTENT_CLEARANCE`を`29.dp`から`23.2.dp`へ変更した。現在値から正確に20%減らし、ホーム／機能一覧／プロフィールの末尾スクロール余白へ反映している。
- 透明な下部Liquid Glassタブ、背面コンテンツ、影グラデーション、アニメーションは変更していない。
- `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --no-daemon --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' --rerun-tasks`成功、JUnit成功、Lintエラー0、Debug APK生成成功。
- `emulator-5554`へ`jp.essential.app.debug`を`adb install -r`し、機能一覧の最下部で最終日課説明文`[305,1833][927,1938]`、下部タブ`[48,2178][1032,2329]`を確認した。追加スワイプ後も位置は安定している。
- 最新APKは`C:\Users\waki1\Desktop\essential-build\app\build\outputs\apk\debug\app-universal-debug.apk`、355,620,044 bytes、SHA-256 `8A3436A76A28DE7B04D27EACBE1AD6098F2C2305CBFECA443A5E725C3381945C`。

## 下部末尾余白を16.8dpへ変更（2026-09-21）

- `ui/EssentialApp.kt`の`NAVIGATION_CONTENT_CLEARANCE`を`23.2.dp`から`16.8.dp`へ変更した。ホーム／機能一覧／プロフィールの末尾余白へ反映している。
- 下部Liquid Glassの透明表示、背面コンテンツ、色、影グラデーション、アニメーションは変更していない。
- `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --no-daemon --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' --rerun-tasks`成功、JUnit成功、Lintエラー0、Debug APK生成成功。
- `emulator-5554`の`jp.essential.app.debug`へ`adb install -r`で更新インストール済み。APKは355,620,044 bytes、SHA-256 `5D7699AC0194C8FF2FDEF213AD5AAC2552BC3918DE35956033489B313CA7DD6D`。

## 下部タブ余白12.8dp・白線除去・プロフィール実アイコン連動（2026-09-21）

- `ui/EssentialApp.kt`の`NAVIGATION_CONTENT_CLEARANCE`を`12.8.dp`へ変更した。ホーム／機能一覧／プロフィールの末尾余白へ反映している。
- 下部タブの白い反射線、白い選択輪郭、外周境界線、Backdropの白い反射を除去した。透明度82%を維持するため、アプリアイコン背景色の18%サーフェスと下方向の影を使い、Backdrop／Canvasをクリップ領域いっぱいに描画して外周の透明な4dp帯をなくした。アイコンRowの余白は維持している。
- ルートで`ProfileStore`のアイコン文字列と画像パスを保持し、`ProfileScreen`の変更コールバックから下部ナビへ共有した。プロフィールタブは一般的な輪郭アイコンではなく、プロフィール画面と同じ`ProfileAvatar`を表示する。画像を設定すれば画像、画像未設定時は選択中の絵文字が表示される。
- ASCII Junction `C:\Users\waki1\Desktop\essential-build`で`:app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --no-daemon --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' --rerun-tasks`成功、JUnit成功、Lintエラー0、Debug APK生成成功。既存のzip形式ネイティブライブラリstrip警告は継続した。
- 最終APKを`emulator-5554`の`jp.essential.app.debug`へ`adb install -r`し、ホーム画面の最終キャプチャ`C:\Users\waki1\AppData\Local\Temp\essential-home-nav-12-8dp-final-verified2.png`を取得した。APKは`C:\Users\waki1\Desktop\essential-build\app\build\outputs\apk\debug\app-universal-debug.apk`、355,624,784 bytes、SHA-256 `A6D84C36807413EB4D4ECAC37078A472CEBB40BC3D9E0EA55211722B7D1139CD`。コミット、push、Release公開、データ削除は行っていない。

## 下部タブのHomeアイコン・プロフィールアバター・Liquid Glass改善（2026-09-21）

- `ui/EssentialApp.kt`の`EssentialSymbol.Home`は、屋根・左右の壁・下辺を個別の`drawLine`で描画するようにした。機能一覧やプロフィールへ移動しても、ホームアイコンが消えずに表示される。
- `ProfileAvatar`は`BoxWithConstraints`で表示領域を読み取り、絵文字を領域に合わせて少し大きく表示する。画像は`ContentScale.Crop`と円形クリップで中央に収め、枠は1dpへ細くした。下部タブのプロフィール枠は28dpで、プロフィール画面と同じ画像または絵文字を表示する。
- 下部タブの選択中ガラスは88dp×44dpへ拡大した。RuntimeShaderの白いハイライトは無効化し、アプリアイコン色の淡い光、選択ピル、押下時アニメーション、API 33未満のCanvasフォールバックを維持している。
- 背面カード境界の白い横線を隠すために追加していた局所的な明るい反射帯を削除し、タブ全体へ下方向の連続シャドウグラデーションを適用した。下へ行くほど暗くなるが、透明度82%と背面コンテンツの透過表示は維持する。
- Java 23を明示したASCII Junction `C:\Users\waki1\Desktop\essential-build`で`:app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --no-daemon --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' --rerun-tasks`が成功。JUnit成功、Lintエラー0、Debug APK生成成功。既存のNDK内zip形式ライブラリstrip警告は非致命警告として残っている。
- `emulator-5554`の`jp.essential.app.debug`へ更新インストール済み。確認キャプチャは`C:\Users\waki1\AppData\Local\Temp\essential-features-nav-final2.png`と`C:\Users\waki1\AppData\Local\Temp\essential-profile-nav-final2.png`。最終APKは`C:\Users\waki1\Desktop\essential-build\app\build\outputs\apk\debug\app-universal-debug.apk`、355,625,824 bytes、SHA-256 `F745D7B6B78ACE74D3CD0F27E02A525621B2C98373D87CCDAF7DD5F51C2386AC`。コミット、push、Release公開、データ削除は行っていない。

## ダウンローダー下矢印アイコン修正（2026-09-21）

- `ui/EssentialApp.kt`の`EssentialSymbol.Download`で、塗りつぶしPathを使っていた矢印先端を左右2本の丸線へ変更した。縦線・下向きの矢印先端・下部水平線を同じStrokeで描画するため、小さいダウンローダーカード内でも先端が欠けない。
- 他の機能アイコン、アイコン色、カードレイアウト、Liquid Glass、ダウンロード処理は変更していない。
- Java 23を明示したASCII Junction `C:\Users\waki1\Desktop\essential-build`で`:app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --no-daemon --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' --rerun-tasks`が成功。JUnit成功、Lintエラー0、Debug APK生成成功。既存のNDK内zip形式ライブラリstrip警告は非致命警告として残っている。
- `emulator-5554`の`jp.essential.app.debug`へ更新インストール済み。機能一覧のダウンローダーカード確認キャプチャは`C:\Users\waki1\AppData\Local\Temp\essential-features-downloader-card-final.png`。最終APKは`C:\Users\waki1\Desktop\essential-build\app\build\outputs\apk\debug\app-universal-debug.apk`、355,625,736 bytes、SHA-256 `5B4CD4EE063AF5C72BEA2AF708A329F261436259EF9F4631D90B272DC1917815`。コミット、push、Release公開、データ削除は行っていない。

## Essential v0.5.9公開準備（2026-09-21）

- `app/build.gradle.kts`は`versionCode=20`、`versionName=0.5.9`。`.github/release-notes/v0.5.9.md`へv0.5.8以降の変更内容を整理した。
- 公開対象は現行アプリの追跡済み変更、音声分離モデル、`AudioStemSeparationEngine`、プロフィール／ストレージ処理とテスト、Release notes。CameraStyleAI研究フォルダ、ローカルログ、`.android`、旧Pro Film設計資料は含めない。
- Java 23とASCII Junctionで`:app:testDebugUnitTest :app:lintDebug :app:assembleRelease`が成功。JUnit 29件成功、Lintエラー0、5種類の未署名Release APKはすべて`jp.essential.app`、versionCode 20、versionName 0.5.9。正式署名と16KiB zipalign検査はタグpush後のGitHub Actionsで行う。
- 公開差分の秘密情報形式検査は0件、最大の新規モデルファイルは19,681,024 bytes、`git diff --check`はエラー0。`origin/main...main`は公開準備開始時点で0/0、`v0.5.9`タグは未作成。
