# Design QA

## 比較対象

- Source visual truth 1: `.codex-remote-attachments/01a06149-2ffd-7860-a4d9-9fc23c421479/78a385b1-47ca-4824-a4bb-59712c3f1f70/1-Photo-1.jpg`
- Source visual truth 2: `.codex-remote-attachments/01a06149-2ffd-7860-a4d9-9fc23c421479/78a385b1-47ca-4824-a4bb-59712c3f1f70/2-Photo-2.jpg`
- Implementation UI: `essential-schedule-030.png`、`essential-schedule-entry-030.png`
- Implementation PDF render: `output/image/schedule-preview-pdf.png`
- Implementation vertical image: `output/image/schedule-preview.png`
- State: ライトテーマ、行程5件のサンプル出力。参照画像は複製対象ではなく、表構造と縦型情報階層の参考資料として比較した。

## Viewportと正規化

- Android UI viewport: 1080 x 2424 px、Pixel_10aエミュレーター、Android 17、端末画面全体を同一密度で取得。
- Source 1: 844 x 1280 px。
- Source 2: 496 x 1280 px。
- PDF implementation: A4相当1240 x 1754 pxを120 dpiで2067 x 2924 pxへレンダリング。
- Vertical image implementation: 1600 x 2355 px。
- 参照資料と実装は内容量と縦横比が異なるため、ピクセル単位の重ね合わせは行わず、情報階層、表の列構成、余白、読み順を正規化して比較した。

## Full-view comparison evidence

- PDFは参照1の主要構造である題名、共通情報、発着時間、目的地、備考・内容の罫線表、注意事項を一枚の読み順へ再構成できている。
- 縦型画像は参照2の主要構造である大きな題名、短い概要、繰り返し項目、十分な行間、控えめな枠、フッターを維持している。
- Android入力画面は既存EssentialのMaterial 3 Expressive、黄緑から橙の背景、角丸入力、セクション見出しを維持し、参照資料の内容を入力可能なUIへ変換している。

## Focused region comparison evidence

- PDF header/table: `output/image/schedule-preview-pdf.png`で共通情報のラベルと値が分離され、3列の見出し、罫線、5行の時刻・目的地・内容に欠けや重なりがないことを確認した。
- Vertical list: `output/image/schedule-preview.png`で番号マーカー、目的地、時刻、説明が一貫した間隔で並び、文字切れや枠外描画がないことを確認した。
- Input row: `essential-schedule-entry-030.png`で出発・到着を横並び、目的地・内容を縦並びにし、行程追加と3形式出力が同じ読み順に収まることを確認した。

## Required fidelity surfaces

- Fonts and typography: Android標準日本語sans-serifを使用。題名、セクション、本文、補助情報のウェイト差が明確で、PDFとPNGに豆腐文字や欠落はない。
- Spacing and layout rhythm: UIは14 dp基準の間隔、出力は表と縦型で別の余白規則を使用。入力欄、表セル、縦型項目に重なりなし。
- Colors and visual tokens: UIはEssentialの既存黄緑・黄・橙を維持。PDFは印刷性を優先した白黒、縦型画像は参照に近い生成紙色と控えめなアクセント色を使用。
- Image quality and asset fidelity: 参照画像は出力へ埋め込まず、内容構造だけを再実装。PDFレンダーと1600 px幅PNGは輪郭、文字、罫線が鮮明。
- Copy and content: 発着時間、目的地、備考・体験内容、注意事項という依頼内容に沿う日本語ラベルで統一。

## Comparison history

### Iteration 1

- [P2] PDF共通情報のラベルが本文より過度に大きく、値との間隔が不足していた。
- Fix: 題名描画後に共通情報用の太字サイズを25 px基準へ戻し、ラベルと値の階層を分離した。
- Post-fix evidence: `output/image/schedule-preview-pdf.png`で日付、目的、集合、参加者、費用、移動が均等な2列情報として読めることを確認した。

### Iteration 2

- P0/P1/P2の残件なし。PDF、縦型画像、Android入力画面に切れ、重なり、操作不能な主要ボタンはない。

## Findings

- ブロッキングまたは修正必須の視覚差分なし。
- P3: 物理端末ではフォントレンダリングと表示密度が異なる可能性があるため、将来の実機確認で微調整余地がある。

## Implementation checklist

- [x] 行程を動的に追加・削除できる入力UI
- [x] PDFの表形式と7件単位の自動改ページ
- [x] 件数に応じて伸びる縦型PNG
- [x] UTF-8文章の行程出力
- [x] 実ファイル生成テスト
- [x] 参照資料と実出力の視覚比較

final result: passed

# Design QA — DOSUKOI ゲームUIのサイズ・退出・120fps（2026-09-06）

## 比較対象

- Source visual truth: `C:\Users\waki1\AppData\Local\Temp\codex-clipboard-e61df8c9-92bc-44c3-ae64-19c5c80ba9b1.png`
- Implementation gameplay UI: `output/dosukoi-game-size-exit-final.png`
- Exit result: `output/dosukoi-room-after-exit-final.png`
- Side-by-side comparison: `output/dosukoi-game-comparison-final.png`
- State: Android 17 Pixel_10aエミュレーター、debug APK、ダークテーマ、WebViewのmotion-fps=120。

## Viewportと正規化

- Source viewport: 374 x 718 px。
- Implementation viewport: 1080 x 2424 px（WebView CSS viewport 約411 x 795）。
- 比較画像は両方を高さ900 pxへ正規化し、上部ステータス、MATCH SIGNAL、フェーズカード、入力・提出、WORD LOGの順序と左右余白を確認した。

## Full-view comparison evidence

- 実装側はゲーム画面の各まとまりを同じ左右余白で配置し、フェーズカード内の条件カードと入力行を縮小ブレークポイント付きで整理した。
- 小型端末向け`max-width:390px`／`max-height:760px`ルールで、見出し・条件値・入力欄・提出ボタン・履歴カードが縦方向に重ならないよう密度を調整した。
- WORD LOGの下部に「退出する」を追加し、押下後はゲーム状態を破棄してCreate Room／Join Roomのルーム選択へ復帰することを確認した。

## Focused region comparison evidence

- `output/dosukoi-game-size-exit-final.png`で、PHASE 3の頭文字「ぼ」・文字数「2」、回答入力、提出ボタン、対戦履歴、退出ボタンを確認した。
- WebViewの`document.documentElement.dataset.motionFps`が`120`となり、Android 15以降のWebViewへ`requestedFrameRate=120`を設定していることをCDPで確認した。
- 退出ボタン押下後、CDPで`dosukoiMenuScreen`がactive、`dosukoiRoomScreen`がvisible、`dosukoiIntroScreen`が非表示となることを確認した。

## Iterations and findings

- [P1] ゲーム中に明示的な退出操作がなく、ブラウザ戻るだけでは意図が伝わりにくかった。
- Fix: WORD LOG下部にゲーム専用の「退出する」を追加し、既存のルーム離脱処理へ接続した。
- [P2] 大画面向け固定サイズでは小型端末で縦方向の余白が不足する可能性があった。
- Fix: 小型端末向けレスポンシブ密度ルールを追加し、入力行・条件カード・履歴・退出ボタンを段階的に縮小した。
- P0/P1/P2の残件なし。120fps設定、ゲーム画面、退出後のルーム選択復帰を実画面で確認した。

## Implementation checklist

- [x] ゲームUI各要素のサイズと余白を調整
- [x] 小型端末向けレスポンシブサイズ
- [x] ゲーム画面の退出ボタン
- [x] 退出後のルーム選択復帰
- [x] WebViewへのmotion-fps=120反映
- [x] 比較画像とエミュレーター実画面確認
- [x] `test`、`lint`、`assembleDebug`

final result: passed

# Design QA — DOSUKOI中央揃え・マインスイーパー旗モード・QRタイル（2026-09-06）

## 比較対象

- Source visual truth: `C:\Users\waki1\AppData\Local\Temp\codex-clipboard-66550a58-5834-4aee-b9c0-c62be3042789.png`
- Implementation DOSUKOI room: `output/dosukoi-room-after-center-fix.png`
- Side-by-side comparison: `output/dosukoi-room-comparison-after-fix.png`
- Implementation Minesweeper: `output/minesweeper-readable.png`
- Implementation flag mode: `output/minesweeper-flag-mode.png`
- State: Android 17 Pixel_10aエミュレーター、最新版debug APK、ライトテーマ（マインスイーパー）／ダークFluid Gradient（DOSUKOI）。

## Viewportと正規化

- Implementation viewport: 1080 x 2424 px。WebView内部のCSS viewportは約411.43 x 795 px。
- Source viewport: 400 x 859 px。
- 参照と実装は高さ900 pxへ正規化した比較画像で、主要カードの左右位置、幅、入力欄、ボタンの読み順を確認した。

## Full-view comparison evidence

- Create RoomとJoin Roomは親コンテナ中央に配置され、モバイル用90%幅の旧ルールによる左寄せを解消した。
- Join Roomのペーストアイコンは入力欄右端へ残しつつ、ボタン背景・枠・ぼかし・影を除去してフラットな操作アイコンにした。
- DOSUKOIの上部ヘッダーとWebView基準色を`#0f0f1a`へ統一し、Fluid Gradientの連続性を維持した。
- マインスイーパーは明るいセル、罫線、色分けした数字、状態カードを使用し、旗モードの切り替えとタップ配置を画面上で確認した。

## Focused region comparison evidence

- Card alignment: 比較画像のImplementation側でCreate／Joinカードの左右端が揃い、中央軸からのずれがない。
- Paste action: 入力欄内のペーストアイコンは独立したLiquid Glass面を持たず、押下時の縮小だけを維持。
- Minesweeper board: `output/minesweeper-readable.png`で9×9の全セル、状態カウンター、旗モードOFFを確認。`output/minesweeper-flag-mode.png`で旗モードON、旗数1、旗セル、モード中の説明文を確認。
- QR tile asset: `app/src/main/res/drawable/ic_qr_tile.xml`をMaterial Icons QrCode2相当の塗りつぶしセルへ変更し、stroke由来の崩れを除去した。

## Required fidelity surfaces

- Fonts and typography: DOSUKOIの英字ロゴと日本語見出しの階層を維持。マインスイーパーは大きなタイトルと状態情報で即時認識できる。
- Spacing and layout rhythm: ルームカードは左右20 CSS px相当の均等余白。マインスイーパーのセル間隔は3 dp、外周カードと状態カードの間隔は14〜16 dp。
- Colors and visual tokens: DOSUKOIは暗いネイビー／青／紫、マインスイーパーは黄緑〜橙の既存Essential背景を維持。数字色は隣接地雷数に応じて区別。
- Image quality and asset fidelity: QRタイルはストローク依存を使わず、24 dpで再現性のある塗りつぶしパスを使用。
- Copy and content: 「旗モード ON／OFF」「旗モード中：タップで旗を立てる／外す」を表示し、長押し操作も従来どおり利用可能。

## Comparison history

### Iteration 1

- [P1] モバイル用`.glass-card { width: 90% !important; }`がDOSUKOIカードを左寄せにしていた。
- Fix: DOSUKOI専用セレクターで`width: 100% !important`と`margin-inline: auto`を指定し、親へ`justify-items: center`を追加。
- [P2] ペーストボタンが半透明背景と境界線を持ち、入力欄内で小さなLiquid Glass面に見えていた。
- Fix: 背景、枠、影、backdrop-filterを無効化し、フラットなアイコン操作へ変更。

### Iteration 2

- [P1] マインスイーパーは長押しだけで旗を立てるため、片手操作時にモードが分かりにくかった。
- Fix: 状態カードへ明示的な旗モードボタンを追加。ON時のタップを旗操作へ切り替え、OFF時は従来のタップ開示と長押し旗を維持。
- [P2] QRタイルの複数矩形をstrokeで描画しており、クイック設定の小サイズでセルが崩れていた。
- Fix: Material Icons QrCode2相当の単一塗りつぶしパスへ置換。

### Iteration 3

- P0/P1/P2の残件なし。ルーム選択、旗モード、QRタイルリソースの視覚確認で主要な切れ・重なり・崩れはない。

## Findings

- ブロッキングまたは修正必須の視覚差分なし。
- P3: QRタイルの実際のクイック設定パネル上の見え方は、端末メーカーごとのアイコンマスク適用後に追加確認余地がある。

## Implementation checklist

- [x] Create／Joinカードの中央揃え
- [x] ペーストボタンのLiquid Glass背景除去
- [x] DOSUKOI上部・WebViewの基準色統一
- [x] マインスイーパーの視認性改善
- [x] 旗モードON／OFFとタップ切り替え
- [x] QRクイック設定アイコンをMaterialパスへ修正
- [x] エミュレーター実画面で操作確認
- [x] `test`、`lint`、`assembleDebug`

final result: passed

# Design QA — DOSUKOI Room / Waiting / Game（2026-09-06）

## 比較対象

- Source visual truth: `C:\Users\waki1\AppData\Local\Temp\codex-clipboard-0dc7b2c3-56cb-4360-b7c9-d7c3fa547aa2.png`
- Implementation room UI: `output/dosukoi-room-current.png`
- Implementation waiting lobby: `output/dosukoi-waiting-lobby-final2.png`
- Implementation gameplay UI: `output/dosukoi-game-material.png`
- Side-by-side comparison: `output/dosukoi-room-comparison.png`
- State: Android 17 Pixel_10aエミュレーター、ダークテーマ、Firebase接続済み。参照画像はルーム選択画面のレイアウト基準として使用し、待機場とゲーム画面は同じデザイン言語で拡張した。

## Viewportと正規化

- Implementation viewport: 1080 x 2424 px。WebView内部のCSS viewportは約411.43 x 795 px。
- Source viewport: 400 x 859 px。
- 比較画像では両方を高さ900 pxへ正規化し、端末外形ではなく左右余白、カード幅、情報階層、主要操作の位置を比較した。

## Full-view comparison evidence

- ルーム画面は参照のヘッダー、ROOM PLAY、DOSUKOIロゴ、Create Room、Join Room、プロフィールショートカットという読み順を維持した。
- ルーム画面の主要コンテンツは左右約20 CSS pxの同一余白で中央配置され、Create RoomとJoin Roomの幅も一致している。
- Fluid Gradientは暗い青から紫の範囲に限定し、文字と入力欄のコントラストを損なわず、全画面で連続して動作する。
- 待機場はレーダー、招待コード、参加者、退出／開始操作を一画面内に収め、ゲーム画面は状態、フェーズ、主操作、対戦履歴の順に整理した。

## Focused region comparison evidence

- Join Room input: 入力文字と重ならない右端に外部SVGのペーストアイコンを配置。ローカルappassets HTTPSオリジンに限定したネイティブClipboardブリッジが存在することをDevToolsで確認した。
- Waiting lobby: `output/dosukoi-waiting-lobby-final2.png`で招待コード、4枠の参加者表示、1 / 10表示、下部アクションに切れ・重なりがない。
- Gameplay: `output/dosukoi-game-material.png`でLIVE MATCH、MATCH SIGNAL、PHASE 1、開始操作、WORD LOGが一貫したMaterial 3 Expressiveの形状と間隔で表示される。

## Required fidelity surfaces

- Fonts and typography: DOSUKOIロゴの字間、英字キッカー、日本語見出し、補助文の階層を維持。文字切れなし。
- Spacing and layout rhythm: 主要面は同一の左右余白を使用。待機場のカードとゲームフェーズは16〜24 px相当の間隔で分離。
- Colors and visual tokens: 深いネイビー、青、紫、水色を共通トークンとして使用し、Liquid Glassの境界線と内側ハイライトを全画面で統一。
- Image quality and asset fidelity: ペーストアイコンは外部SVGで高密度表示に対応。Fluid GradientはCSS transform中心でGPU合成し、静止画背景を引き延ばしていない。
- Copy and content: Create Room／Join Roomなど既存ゲーム用語を保持し、待機場とゲーム状態は日本語で即座に理解できる表現にした。

## Comparison history

### Iteration 1

- [P1] 待機場の参加者カードが縮み、下部アクションとプロフィールショートカットへ重なっていた。
- Fix: 待機場とゲーム画面の直下要素を縮小不可にし、待機場／ゲーム中はプロフィールショートカットを隠した。下部余白も画面内へ収まる値に調整した。
- Post-fix evidence: `output/dosukoi-waiting-lobby-final2.png`で参加者4枠と下部2ボタンが独立し、ナビゲーションバーまで重なりがない。

### Iteration 2

- P0/P1/P2の残件なし。参照との比較、待機場、ゲーム画面の全景確認で、主要操作の切れ・重なり・左右幅の不一致はない。

## Findings

- ブロッキングまたは修正必須の視覚差分なし。
- P3: 複数の物理端末を使うFirebase対戦同期と、実クリップボード内の文字列を使う貼付操作はエミュレーター単体では未検証。

## Implementation checklist

- [x] Join RoomのペーストアイコンとネイティブClipboardブリッジ
- [x] ルーム画面の左右同幅
- [x] Fluid Gradient motion
- [x] 近未来型の対戦待機場
- [x] Material 3 ExpressiveゲームUI
- [x] ボタンのマイクロインタラクション
- [x] エミュレーターでルーム、待機場、ゲーム画面を視覚確認
- [x] `test`、`lint`、`assembleDebug`

final result: passed

# Design QA — DOSUKOI Phase 3境界線・モーション滑らかさ（2026-09-07）

## 比較対象

- Source visual truth: `C:\Users\waki1\AppData\Local\Temp\codex-clipboard-8351a120-2045-419d-acf3-32907181392e.png`
- Implementation: `output/dosukoi-game-smooth-final.png`
- Side-by-side comparison: `output/dosukoi-game-smooth-comparison.png`
- State: Android 17 Pixel_10aエミュレーター、DOSUKOI Phase 3（条件に合う言葉を入力）、ダークテーマ。

## Focused region comparison evidence

- 参照画像で条件カードの青い境界線がフェーズカード左右からはみ出していたため、旧CSSに残っていた`min-width:400px`を上書きし、`min-width:0`、`max-width:100%`、`box-sizing:border-box`を適用した。
- `#phaseWord`へ`overflow:hidden`を設定し、子要素の境界線が親フェーズカード外へ描画されないことを確認した。
- DevTools計測（CSS viewport約411.43 x 795 px）で、フェーズカード内側は`x=36.76..374.67`、条件カードは`x=52.76..358.67`となり、左右とも内側へ収まっている。`overflow:hidden`、`min-width:0px`、`max-width:100%`、`box-sizing:border-box`も確認済み。

## Motion evidence

- Phase／セクション入場を`translate3d`へ統一し、`backface-visibility:hidden`と`will-change:transform, opacity`でコンポジター合成を促す構成へ変更した。
- イージングを`cubic-bezier(0.22, 1, 0.36, 1)`へ統一し、フェーズ0.70秒、セクション0.74秒の自然な減速へ調整した。
- `output/dosukoi-game-smooth-final.png`で条件カード、入力欄、WORD LOG、退出ボタンの視認性と配置を確認した。120fps設定時のWebView希望フレームレート伝播は既存実装を維持している。

## Implementation checklist

- [x] 条件カードの青い境界線をフェーズカード内へ収束
- [x] 小型画面でのmin-width由来の横方向オーバーフローを解消
- [x] translate3d／backface-visibility／will-changeによる滑らかなモーション
- [x] Phase 3の実画面スクリーンショットと比較画像
- [x] `test`、`lint`、`assembleDebug`

final result: passed
