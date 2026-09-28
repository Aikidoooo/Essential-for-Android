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

# Design QA — Pro Filmメーカー別現像エンジン再生成（2026-09-11）

## 比較対象

- Implementation loaded state: `D:\#AI開発\Android\Essential\build-profilm-engine-controls2.png`
- Implementation M3 state: `D:\#AI開発\Android\Essential\build-profilm-engine-m3.png`
- State: Android 17 Pixel_10aエミュレーター、Pro Filmへ画像を読み込み済み。参照画像の比較スライダーと既存の角丸構成を維持したまま、フィルター選択後の処理名と現像結果を確認した。

## Focused region comparison evidence

- Leica M9選択時に「Leica M9現像エンジンで再生成」、Leica M3選択時に「Leica M3現像エンジンで再生成」へステータスが切り替わることをUIツリーで確認した。
- 選択中フィルターの説明カードも「現像エンジン • …を再生成」へ更新し、単純なフィルター適用ではなく現像処理を選択していることを明示した。
- 画像処理はシーンリニア化、固有色行列、トーンカーブ、局所コントラスト、ハレーション、粒状感、周辺減光、sRGB再エンコードの順序で構成し、比較スライダーの元画像／加工後画像を維持した。

## Findings

- ブロッキングまたは修正必須の表示差分なし。
- P2: メーカーの実機ISP、非公開LUT、撮影時のレンズ・露出情報は取得できないため、物理端末のJPEGとピクセル単位で完全一致するものではない。公開されている色傾向を現像エンジンのパラメーターとして再構成している。

## Implementation checklist

- [x] 8プリセットを固有色行列・トーン・光学特性へ分離
- [x] sRGB→シーンリニア→現像→sRGB再エンコード
- [x] 現像エンジン名と再生成ステータスの表示
- [x] Leica M9／M3のエミュレーター選択確認
- [x] `testDebugUnitTest`、`lintDebug`、`assembleDebug`

final result: passed

# Design QA — Block Blast ドラッグ専用UI・中央グリップ（2026-09-10）

## 比較対象

- source visual truth: `C:/Users/waki1/AppData/Local/Temp/codex-clipboard-7b700d07-4ff7-4150-8862-5e08968b14ab.png`
- implementation screenshot: `D:/#AI開発/Android/Essential/build-blockblast-drag-only-latest.png`
- drag verification screenshot: `D:/#AI開発/Android/Essential/build-blockblast-drag-only-latest-drag.png`
- comparison input: `D:/#AI開発/Android/Essential/design-qa-comparison.png`
- state: 参考画像と同じ手札領域を表示し、外枠・背景面・状態文言を除去して拡大したブロック形状だけを表示した状態。右上設定、リセット操作、タップ配置と下部案内文は削除し、別キャプチャでは手札のブロックを盤面中央へドラッグ配置した。参考画像は手札領域だけの切り抜きのため、上部のゲーム盤面・スコアは比較対象外とした。

## キャプチャと正規化

- source: 367 × 158 px（手札領域の切り抜き）
- implementation: 1080 × 2424 px、Pixel系エミュレーター、density 420 dpi（`wm size`／`wm density`で確認）
- native Compose画面のためCSS viewportはなし。実装側から手札領域（x=40..1040、y=1680..2050）を切り出し、両方を幅540 pxへ等比縮小して白い区切りを挟み、同一画像へ合成した。
- 参考画像が手札領域だけのため、端末のステータスバー・ナビゲーションバーと盤面本体は比較対象外にした。

## 全体比較

合成画像で、淡いアクア背景、3つの手札領域、拡大したブロックの間隔と色調を確認した。実装側はカードの外枠・背景面・状態ラベル・下部案内文を描画せず、ドラッグ操作に必要なブロック形状だけを表示する依頼に合わせている。参照側にある枠と文字との差分は意図した仕様差分である。

## フォーカス領域比較

- 盤面: 参考画像の立体的なブロック表現を、グラデーション・上辺ハイライト・影付きセルで再現。空きセルの薄い罫線も維持した。
- 手札／操作: 3つの不可視タッチ領域を横並びにし、画面にはブロック形状だけを外接矩形で表示する構成へ整理した。選択状態や配置済み状態の枠・文言は描画せず、従来のタップ選択とドラッグ開始を維持した。前画面へ戻るボタンはEssential組み込み要件のため左上へ追加した。
- ドラッグ中: 浮遊プレビューから枠・背景・案内文を取り除き、ブロック形状だけを表示した。形状の中心を指位置へ合わせ、盤面側の配置原点も形状中心から算出するため、指でブロック中央をつかんで移動できる。
- ドラッグ専用化: 手札のタップ選択と盤面セルのタップ配置を無効化し、手札から盤面へのドラッグだけを操作経路として残した。右上設定ボタン、ゲームリセットボタン、タップ操作の案内文も表示しない。
- アイコン: 参考画像の標準的な王冠・歯車はテキストグリフで実装している。固有ロゴや画像素材は参考画像にないため、追加の画像資産は不要と判断した。

## Findings

- P3（許容）: 手札のブロック形状はゲーム開始時にランダム生成されるため、参考画像と実装キャプチャの形状そのものは異なる。形状を囲う外枠や文言、参照側にあるタップ操作は今回の指定どおり削除した。ゲーム操作用の不可視ドラッグ領域には影響しない。

## Comparison history

- 初回実装: 汎用Materialカードと縦スクロールを使っていたため、参考画像との配色・情報階層が大きく異なった。通常ドラッグ時に親スクロールがジェスチャーを奪う可能性もあった。
- 修正: Block Blast専用のアクア配色、立体セル、手札カード、戻る／設定操作、浮遊プレビューを追加。盤面内に収まるレイアウトへしてスクロールを除去し、ドラッグ開始中にカードのレイアウトサイズを変えないことでジェスチャー終了を安定化した。
- 手札UI調整: カード内の「タップで選ぶ」「選択中」「配置済み」「ドロップ」文言に加え、カードの枠・背景面も取り除き、形状の外接矩形だけを表示する構成へ変更した。
- 中央グリップ調整: `blockShapeMetrics`で形状中心を算出し、浮遊プレビューと盤面配置原点の双方へ同じ中心基準を適用した。
- 再キャプチャ: `build-blockblast-hand-only-latest.png` と `build-blockblast-hand-only-latest-drag.png` で、手札内に枠・状態文言が残らずブロックだけが表示されること、ドラッグ後にカード解放・盤面反映・スコア更新が行われることを確認した。P0〜P2の未解決差分はない。
- ドラッグ専用化: 右上設定、リセット、タップ選択・盤面タップ配置、下部案内文を削除し、手札の待機ブロックを17dpから25dpへ拡大した。
- 再キャプチャ: `build-blockblast-drag-only-latest.png` と `build-blockblast-drag-only-latest-drag.png` で、旧ボタン・旧案内文が表示されず、拡大した手札ブロックとドラッグ後の盤面反映・スコア更新を確認した。P0〜P2の未解決差分はない。

## Implementation Checklist

- [x] 参考画像のアクア背景、スコア、ベスト表示、8×8盤面、3手札を実装
- [x] 参考画像に合わせたセルのグラデーション、ハイライト、影、角丸を実装
- [x] 手札を外枠・背景・状態文言なしのブロック形状だけの表示へ整理
- [x] ドラッグ中の浮遊表示をブロックだけにし、指で形状の中心をつかむ配置へ統一
- [x] 前画面へ戻るボタンを維持
- [x] 右上設定・リセット・タップ選択・盤面タップ配置・下部案内文を削除
- [x] 手札から盤面への通常ドラッグだけを操作経路として維持
- [x] 待機ブロックを拡大
- [x] 無効な場所では配置せず、候補色とメッセージで通知
- [x] 参考画像と実装を同一比較入力で確認

## Follow-up Polish

- ステータスバーとナビゲーションバーをゲーム背景色へ連続させる場合は、Essential全体のWindowInsets方針と合わせて別途調整する。

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

# Design QA — Pro Film 強度泡モーション・外部Picker（2026-09-11）

## 比較対象

- Source visual truth: `C:\Users\waki1\AppData\Local\Temp\codex-clipboard-e2820bb7-a678-45d1-a5bd-957feb27c5b1.png`、`C:\Users\waki1\AppData\Local\Temp\codex-clipboard-b4b1f46b-4b7d-4eda-bf74-1843df71d45e.png`
- Implementation Pro Film: `D:\#AI開発\Android\Essential\build-profilm-updated-empty.png`
- Implementation external picker: `D:\#AI開発\Android\Essential\build-profilm-external-picker.png`
- State: Android 17 Pixel_10aエミュレーター、ライトテーマ、Pro Film空状態。1枚目は強度レールと泡モーション、2枚目はシステムPickerの外部アプリ導線を視覚基準として扱った。手描き画像内の文字は実装指示ではなく、配置と状態の参考とした。

## Focused region comparison evidence

- 強度レールは太い紫色の角丸トラック、白い円形サム、現在値100%を同じ領域へまとめた。泡は複数の白い円を右から左へ循環させ、上下の揺れと透明度変化を加えた常時モーションとして実装した。
- ヘッダーの戻る操作はファイル参照など既存画面と同じ46dp円形Surface、`‹`記号、見出し＋補助説明へ統一した。+ボタンと保存アイコンは除去し、保存はテキストのみとした。
- 写真参照とファイル参照は共通`ACTION_GET_CONTENT`契約へ切り替え、画像・動画・音声のMIME配列を渡す。Android 17エミュレーターではPhoto Pickerが開き、実Xiaomi端末ではインストール済みのPhotos／Driveが「他のアプリでファイルを探す」候補へ参加できるIntent経路へ切り替えた。Drive未インストールのエミュレーターでカードが出ないことは仕様上の未検証範囲として記録した。

## Required fidelity surfaces

- Motion: `InfiniteTransition`の連続位相で泡を循環させ、Sliderの操作感と競合しないようレール内へクリップした。
- Controls: 既存の強度0〜100%、フィルター選択、写真参照、保存状態を維持しながら、参照画像の色・太さ・角丸を反映した。
- External providers: Photosなど`ACTION_GET_CONTENT`へ応答するアプリを候補に含め、File Referenceでは永続権限を返さないプロバイダーでも読み込みを止めない。

## Findings

- ブロッキングまたは修正必須の視覚差分なし。
- P3: 外部Pickerに表示されるPhotos／Driveの名称・カード数はXiaomiのOS、アプリのインストール状態、ユーザーアカウントで変わるため、実機表示は端末側で最終確認が必要。

## Implementation checklist

- [x] 参照画像準拠の泡付き強度レール
- [x] 既存機能と同じ戻るUI
- [x] +ボタンと保存アイコンの除去
- [x] Photos／Drive候補へ対応するGET_CONTENT契約
- [x] エミュレーターで強度UIとPicker起動を確認
- [x] `testDebugUnitTest`、`lintDebug`、`assembleDebug`

final result: passed

# Design QA — Pro Film Image Comparison Slider（2026-09-11）

## 比較対象

- Source visual truth: `C:\Users\waki1\AppData\Local\Temp\codex-clipboard-71ddb488-9b98-4ee1-b858-805a61e29601.png`
- Implementation empty state: `D:\#AI開発\Android\Essential\build-profilm-redesign-empty.png`
- Implementation loaded state: `D:\#AI開発\Android\Essential\build-profilm-redesign-loaded2.png`
- Implementation drag state: `D:\#AI開発\Android\Essential\build-profilm-redesign-dragged.png`
- State: Android 17 Pixel_10aエミュレーター、ライトテーマ、Pro Film画面。参照画像の手描き表現はそのまま複製せず、上部ツールバー、比較枠、フィルター列、設定スライダーという構成と操作をComposeへ再構成した。

## Focused region comparison evidence

- ヘッダーは左の円形戻る、中央のPro Film、右の円形追加、角丸の保存操作を同じ読み順で配置した。追加は写真ピッカー、保存は加工済みPNG出力へ接続している。
- 比較枠は画像の縦横比を維持した角丸コンテナとし、左側を加工後、右側を元画像として常に同時表示する。黄色の縦バーと円形グリップはタップ・ドラッグで位置が変わり、`build-profilm-redesign-dragged.png`で中央から右側への移動を確認した。
- フィルターは横スクロール可能なMaterial 3 FilterChipへ置き換え、Leica M9／M3、Xiaomi、OPPO、Huawei、Vivo、Xperia、FUJIFILMの全8種へ到達できる。選択状態の説明は直下の角丸面へ表示する。
- 設定は紫色の強度Sliderと数値表示を角丸カードへまとめ、既存の0〜100%強度処理と再レンダリング状態を維持した。

## Required fidelity surfaces

- Fonts and typography: Material 3の見出し・本文・ラベルを使用し、参照画像の見出し／セクション／補助情報の階層を維持した。
- Spacing and layout rhythm: 20 dp外側余白、16 dp基準のセクション間隔、28〜32 dpの角丸でヘッダーから設定までを連続させた。
- Colors and visual tokens: Essentialの黄緑Liquid Glass背景を維持し、比較バーは参照画像に合わせた黄色、強度設定は紫で識別した。
- Image quality and interaction: 元画像と加工画像を同一サイズで重ね、バー位置をスプリング補間してProgressive Motionを付与した。追加・戻る操作にはcontentDescriptionを設定した。

## Findings

- ブロッキングまたは修正必須の視覚差分なし。
- P3: 手描きフォントそのものは端末標準フォントへ置き換えているため、将来ブランドフォントを同梱する場合は見出しだけ差し替え余地がある。

## Implementation checklist

- [x] 参照画像に沿ったPro Filmヘッダー
- [x] 黄色Image Comparison Slider（タップ・ドラッグ対応）
- [x] 画像縦横比を維持する角丸比較枠
- [x] 8種フィルターの横スクロール選択
- [x] 紫色の強度設定Slider
- [x] 空状態、読み込み中、保存可能状態の表示
- [x] エミュレーターで空状態・画像表示・ドラッグ状態を視覚確認
- [x] `testDebugUnitTest`、`lintDebug`、`assembleDebug`

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

# Design QA — QRスキャナーのズームUI（2026-09-28）

## 比較対象

- Source visual truth 1: `C:\Users\waki1\AppData\Local\Temp\codex-clipboard-e92d8046-d4de-4732-b6b4-1bcc882de7ec.png`（368 x 772 px、上部プロフィール欄の置換と画面内配置を確認）
- Source visual truth 2: `C:\Users\waki1\AppData\Local\Temp\codex-clipboard-74ae5221-a303-4a29-8621-e597c8f3d30f.png`（701 x 294 px、倍率プリセットと曲線ズーム目盛りの視覚基準）
- Implementation screenshot: `app/build/visual-qa/qr-scanner-updated.png`（1080 x 2424 px）
- State: Pixel_10a AVD／Android 17／420 dpi（論理画面411 x 923 dp）。`jp.essential.app.debug` versionName `0.6.2-debug`でQRスキャナーを表示し、倍率1x、Auto未選択。
- 正規化: 参照2はズームコントロールだけを切り出した横長画像で、実装は縦長のカメラ画面のため、画面全体のピクセル重ね合わせは行わず、参照1で配置、参照2でコントロール形状・並び・曲線を同じ比較入力に並べて確認した。

## Full-view comparison evidence

- 参照1の「Android標準プロファイル」位置は、実装では「クイック設定に追加」ボタンになっている。画面下部にあった同ボタンの重複表示はなくなった。
- 倍率プリセットはカメラ上部から下部へ移動し、ズーム目盛りの直上に並ぶ。Autoチェックと読み取り枠は維持されている。
- 参照2に合わせ、選択中の白い円枠、暗い半透明プリセットカプセル、曲線状の目盛り、赤い現在位置マーカーを維持し、目盛り背面の角丸パネルを取り除いた。

## Focused region comparison evidence

- 参照2と実装画像のズーム領域を同じ比較入力で確認。プリセット列は目盛りより上、目盛りは左右へ広がる浅い弧、現在位置は赤い縦マーカーで示され、1xラベルは1x位置の上にある。
- 横方向ドラッグに`detectHorizontalDragGestures`を使用し、タップ、アクセシビリティの進捗操作、倍率プリセット、触覚フィードバックを保った。

## Required fidelity surfaces

- Fonts and typography: 既存のMaterial 3日本語sans-serifとlabelLargeを使用。ズーム倍率ラベルは曲線位置に追従し、カメラ映像に対して白で表示される。
- Spacing and layout rhythm: プリセット列を目盛り直上へまとめ、上部中央の操作ボタンをプロフィール欄の位置へ配置。横幅は既存の左右18 dp余白に追従する。
- Colors and visual tokens: 非選択プリセットは暗い半透明面、選択中は白い円枠、目盛りは白、現在位置は赤。目盛り領域の塗りつぶし面はない。
- Image quality and asset fidelity: 背景はQRスキャナーのライブカメラ映像を維持し、参照画像の風景を固定画像として合成していない。
- Copy and content: プロフィール名を除去し、同じ上部位置に「クイック設定に追加」を表示。倍率表記はW／1x／3.5x／10x。

## Comparison history

### Iteration 1

- [P1] プロフィール名が残り、クイック設定追加ボタンが目盛りの上にあり、プリセットが画面上部に離れていた。目盛りは暗い角丸面の中にあり、ラベルは固定高さだった。
- Fix: プロフィール位置へクイック設定追加ボタンを移動。プリセットを下部目盛りの上へ移し、重複ボタンと目盛り背景面を削除。ドラッグを横方向に限定し、倍率ラベルを弧に沿わせた。
- Post-fix evidence: `app/build/visual-qa/qr-scanner-updated.png`でボタン位置、プリセット／目盛りの順序、透過背景、赤い1xマーカーを確認した。

## Findings

- P0/P1/P2の残件なし。ソース画像1の配置変更と画像2のズームコントロール構成を実装した。
- P3: AVDのCameraXズーム範囲は1xのみのため、画面ではW／3.5x／10xが無効表示となり、対応する目盛りラベルも出ない。対応倍率がある実機ではカメラ能力に応じて表示される。AVDでは複数倍率間のドラッグ動作を確認できていない。

## Implementation checklist

- [x] プロフィール表示を削除し、同じ位置へQuick Settings追加操作を移動
- [x] プリセットを曲線目盛りの直上へ配置
- [x] 目盛りを横方向ドラッグ／タップで操作可能に維持
- [x] Debug APKをエミュレーターへインストールし、QRスキャナー画面をキャプチャ

final result: passed

# Design QA — ミニゲーム追加（2026-09-28）

## 確認環境

- Pixel_10a AVD／Android 17／420 dpi。Debug版`jp.essential.app.debug`を起動。
- 一覧画面: `app/build/visual-qa/minigame-menu-final.png`
- ヘビ画面: `app/build/visual-qa/snake-screen-adjusted.png`
- 単語ゲーム: `app/build/visual-qa/word-distance-screen.png`、`app/build/visual-qa/word-distance-result-submitted.png`
- 参照ゲームの紹介ページとプレイ画面を調査し、ノーマル／エンドレス、7文字制限、距離更新ルールを確認した。

## 確認内容

- ミニゲーム一覧に「一番遠い単語」「ヘビゲーム」のカードが表示され、各画面へ遷移する。
- ヘビ画面は縦長端末内に盤面・スコア・一時停止・方向操作を収め、盤面のタイル、ヘビ、エサを視認できる。ゲームが時間経過で進み、壁衝突後にゲームオーバー状態へ移る。
- 単語ゲームのモード選択、最大7文字の入力表示、送信後の距離結果画面を確認した。
- P3: 参照ゲームのAIモデルは公開情報から再利用できないため、Essentialではローカル語彙の簡易距離推定としている。未知語への距離は決定的な簡易ベクトル推定となる。参照元と同じスコアにはならず、画面上で説明している。
- P3: エンドレス連鎖、エサ取得／成長、スワイプによる方向操作はコード実装済みだが、エミュレーターでの個別操作確認は行っていない。自動テストと物理端末確認も未実施。

## 実装チェック

- [x] 新規2ゲームの一覧導線
- [x] EssentialネイティブUIの単語ゲームとヘビゲーム
- [x] 参照AIとのスコア差を画面表示
- [x] Debugビルド／エミュレーターインストール／画面確認

final result: passed with noted limits

# Design QA — ミニゲーム画面調整・公式ゲーム内蔵（2026-09-28）

## 確認環境

- Pixel_10a AVD／Android 17／420 dpi。Debug版`jp.essential.app.debug`をインストール。
- ヘビ: `app/build/visual-qa/snake-ready.png`、`app/build/visual-qa/snake-playing.png`
- 一番遠い言葉: `app/build/visual-qa/word-final-menu.png`、`app/build/visual-qa/word-final-play.png`
- 参照元: [unityroom「いちばん遠い言葉」](https://unityroom.com/games/word-distance)。公式ページをWebViewで読み、署名付きプレイヤーURLへ遷移する構成。

## 確認内容

- ヘビ画面は縦スクロールを使わず、Pixel_10aの縦画面にヘッダー、3つのスコア、盤面、開始操作、方向パッド、説明文が収まる。ナビゲーションバーとの重なりも避けている。
- ヘビは最初に「準備完了」を表示し、方向ボタンで開始・移動する。開始直後の盤面と稼働中の盤面をAVDで確認した。
- 一番遠い言葉はEssential独自の画面枠内で公式WebGLゲームを開く。ノーマル／エンドレス選択、ノーマル開始後のテーマと入力欄を表示し、操作に反応することを確認した。
- unityroomのゲーム本体はアプリに同梱せず、ネット接続時に読み込む。入力後の結果画面、通信が切れた状態、物理端末でのキーボードとゲーム操作は未確認。
- ビルド／AVDインストール成功。自動テストとコミット・公開は実施していない。

## 実装チェック

- [x] ヘビ画面のスクロールをなくし、全UIを縦画面内へ配置
- [x] 開始前状態と方向操作による開始を追加
- [x] 公式unityroomゲームをEssential画面内のWebViewで起動
- [x] Pixel_10a AVDで画面と基本操作を確認

final result: passed with noted limits

# Design QA — unityroom WebViewの軽量化・Liquid Glass（2026-09-28）

## 確認環境

- Pixel_10a AVD／Android 17／Debug版 `jp.essential.app.debug` versionCode 23、versionName 0.6.2-debug。
- 更新後の画面: `app/build/visual-qa/word-distance-updated.png`

## 確認内容

- ミニゲーム一覧から「一番遠い言葉」を開き、WebViewでunityroomのゲームモードを表示した。ノーマル開始後、テーマ「猫」と7文字入力欄、送信ボタンが表示されることを確認した。
- Essential外枠のGlass面、反射縁、読み込み状況、戻る／再読み込み操作、ステータス表示を目視した。Unity WebGL本体との重ね合わせでもゲーム操作範囲を保つ。
- アプリを一度ホームへ移動して再表示し、ゲーム画面へ戻れることを確認した。画面離脱時はWebViewのJavaScriptタイマーを止め、復帰時とWebView破棄時にタイマー状態を復元する。
- Pixel AVDはXiaomi端末ではないため、Xiaomi判定、60Hz要求、HyperOS上のメモリ／消費電力は実機検証していない。画面上のモーション／FPS測定と自動テストも未実施。

## 実装チェック

- [x] WebViewを画面ライフサイクルと同期し、画面離脱時に停止・破棄
- [x] 背面時のJavaScriptタイマー停止と画面復帰／破棄時の共有状態復元
- [x] 非表示時に描画器を低優先度へ変更し、描画プロセス終了から再生成
- [x] Liquid Glass外枠と押下ばね／段階表示
- [x] Xiaomi／Redmi／POCO判定とAPI 35以降の60Hz要求
- [x] Debug APKビルド、AVDへの署名互換な上書きインストール、画面確認

final result: passed with device-specific limits

# Design QA — ヘビゲームの移動スティックとWord Distanceの表示枠（2026-09-28）

## 確認環境

- Pixel_10a AVD／Android 17／Debug版 `jp.essential.app.debug`。
- Snake操作画面: `app/build/visual-qa/snake-joystick-playing.png`
- Word Distance表示: `app/build/visual-qa/word-distance-current.png` と `app/build/visual-qa/word-distance-play-final.png`

## 確認内容

- 方向ボタンの代わりに円形スティックを表示し、右へドラッグするとヘビが進むことを確認した。盤面スワイプ、スタート／一時停止は維持した。
- unityroomのゲームをノーマル開始し、猫のお題と7文字以内の入力欄が表示されることを確認した。ゲームキャンバスの左右・上下に均等な余白ができ、画面中央へ収まる。
- Unity WebView上で実測したCSS viewportとキャンバス寸法を確認し、最終キャンバスの縮尺が0.92、左右・上下の位置がそれぞれviewportの4%となることを確認した。
- 物理端末、自動テスト、回答送信後の結果表示は未確認。

## 実装チェック

- [x] 方向ボタンを移動スティックへ置換
- [x] 同じ方向のスティック操作で開始、逆方向を抑止
- [x] WebViewの実測寸法に基づくUnity表示領域の補正
- [x] Debug APKビルド、AVDへのインストール、表示と操作を確認

final result: passed on AVD; physical-device verification not performed

# Design QA — 「一番遠い言葉」WebView消音（2026-09-28）

## 確認環境

- Pixel_10a AVD／Android 17／Debug版 `jp.essential.app.debug` versionCode 23、versionName 0.6.2-debug。
- 実行画面: `app/build/visual-qa/audio-mute-word-play.png`

## 確認内容

- 公式unityroomゲームのプレイ画面を表示したまま、WebViewの対象プレイヤーURLでドキュメント開始時消音フックが有効になることを確認した。
- Chrome DevTools Protocolの確認値は`muted=true`、`hooked=true`、AudioContext出力ゲイン`0`。確認用AudioNodeを出力先へ接続し、ゲートの値が0になることを検証した。
- 消音対象はこのゲームのWebView内だけで、Androidのシステム音量は変更しない。HTMLメディアは個別にミュートする。
- 物理端末での聴感確認と自動テストは未実施。

## 実装チェック

- [x] Web Audio出力をAudioContextごとにゲイン0へ接続
- [x] HTML audio／videoメディアをミュート
- [x] `*.play.unityroom.com`のページ開始時スクリプト注入
- [x] Debug APKビルド、AVDへインストール、実行中WebViewのフックを確認

final result: passed on AVD; physical-device listening verification not performed

# Design QA — ヘビゲーム移動スティックのLiquid Glass化（2026-09-28）

## 確認環境

- Pixel_10a AVD／Android 17／Debug版 `jp.essential.app.debug` versionCode 23、versionName 0.6.2-debug。
- ゲーム準備画面: `app/build/visual-qa/snake-glass-playing.png`
- 右方向の操作確認: `app/build/visual-qa/snake-glass-playing-verified.png`

## 確認内容

- スティックつまみから方向を示す矢印文字がなくなり、つまみは反射と透過を使ったガラス表現で表示される。
- スティックの外周に半透明の青緑ガラス、細い反射縁、内側リングを表示した。
- スティックを右へドラッグしてゲームが開始し、ヘビが進むことを確認した。
- 物理端末と自動テストは未実施。

## 実装チェック

- [x] 画面内の矢印グリフを削除し、読み上げ用ラベルを維持
- [x] スティック台座とつまみにLiquid Glassの透過・反射・縁取りを適用
- [x] Debug APKビルド、AVDへのインストール、方向操作を確認

final result: passed on AVD; physical-device verification not performed

# Design QA — 移動スティックの反射表現を削除（2026-09-28）

## 確認環境

- Pixel_10a AVD／Android 17／Debug版 `jp.essential.app.debug` versionCode 23、versionName 0.6.2-debug。
- ヘビゲーム画面: `app/build/visual-qa/snake-glass-no-reflection.png`

## 確認内容

- スティック台座とつまみの上部光沢ライン、明るい反射グラデーションを除いた。
- 半透明の単色面、控えめな縁取り、柔らかな影、薄い円形ガイドは維持した。
- AVDの画面で更新後の見た目を確認した。自動テストと物理端末確認は未実施。

## 実装チェック

- [x] 反射ラインと反射グラデーションを削除
- [x] Liquid Glassの半透明面と縁取りを維持
- [x] Debug APKビルド、AVDへインストール、画面確認

final result: passed on AVD; physical-device verification not performed
