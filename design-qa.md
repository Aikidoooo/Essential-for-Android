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

---

## 2026-10-01 まんなか：添付4画面に基づくUI検証

### 比較対象と状態

- Source visual truth（メイン）：`C:/Users/waki1/AppData/Local/Temp/codex-clipboard-9e4e3e93-892d-4d11-9f57-3e8d5a650110.png`
- Source visual truth（今日）：`C:/Users/waki1/AppData/Local/Temp/codex-clipboard-5b53a050-7802-440c-b413-9eb9655d7e90.png`
- Source visual truth（明日）：`C:/Users/waki1/AppData/Local/Temp/codex-clipboard-bb3440c4-a098-46ae-9476-2c3e72ce6baf.png`
- Source visual truth（明後日）：`C:/Users/waki1/AppData/Local/Temp/codex-clipboard-fbbc3e47-506f-4fe3-b4d1-10a55eccb92f.png`
- 実装：`D:/#AI開発/Android/Essential/app/src/main/java/jp/essential/app/feature/mannaka/MannakaScreen.kt`と`MannakaControls.kt`。既存Androidアプリを変更し、Webモックや新しいサイトは作成していない。
- Implementation screenshot：`D:/#AI開発/Android/Essential/app/build/visual-qa/mannaka-reference-main.png`、`mannaka-reference-time-today.png`、`mannaka-reference-time-tomorrow.png`、`mannaka-reference-time-day-after.png`。
- 状態：ライトテーマ、参加者2人・駅未選択、今日19:00。下部パネルは今日／明日／明後日の各選択状態で19:00。MainActivityからopen_feature=mannakaで起動した実アプリの画面を取得した。
- 参考画像・最終実装画像はともに1290×2796px。AVDをdensity 480の430×932dpへ一時変更して比較。Androidのステータス・ジェスチャーバーはOSの表示を保持し、画像内のiOS表示は再作成していない。検証後は元の1080×2424px／density 420へ戻す。
- 比較ボードは双方を同じ比率で645×1398pxへ縮小し、同じ入力画像に並べて確認した。CSSサイズはネイティブ実装のため該当なし。

### 比較証跡と確認結果

- Full-view comparison：`app/build/visual-qa/mannaka-comparison-main.png`、`mannaka-comparison-today.png`、`mannaka-comparison-tomorrow.png`、`mannaka-comparison-day-after.png`。両画像を同時に開いて、情報の順序、丸いカード、色、2人の入力、追加ボタン、検索ボタン、集合時間パネルの構成を確認。
- Focused region comparison：`app/build/visual-qa/mannaka-comparison-time-detail.png`。集合日の選択位置、時刻の大きさ、±30／±5、6プリセット、決定ボタンを文字が読める大きさで確認。
- フォント・文字組み：太字中心だった従来UIを通常ウェイトへ変更。26spの機能名、18spの時間／行動、17spの名前、38spのパネル時刻、12〜16spの補助情報を使用。時刻・駅入力・下部ボタンに文字欠けや重なりがない。iOSの日本語フォントと同一の書体は同梱せずAndroid標準フォントを使用する。
- 余白・配置：集合時間→任意の遊び→参加者→追加→入力案内→検索の順を再現。カード半径28dp、下部パネル半径30dpと外側8dpの余白を採用。Androidの戻る導線と標準タップ領域を保持し、プリセット2段目／決定ボタンの縦位置は参照より少し下になる。初期2人で検索ボタン全体が表示されることを確認。
- 配色：背景FFF8F3、アクセントFF665B、白いカード、FFF0EBの薄い入力面、落ち着いた補助文字で参考のクリーム／コーラル系へ揃えた。上端の緑色を取り除き、システムバーの背面まで同じ背景にした。機能内の文字色は親の緑系から独立させた。
- 画像・アイコン品質：ラスタ画像のある画面ではない。Google公式Material Iconsの12ベクターをそのまま変換して使用し、大きなビットマップやアイコンSDKを追加していない。ピン、人物、時計、飲食、履歴、歯車、ハート、追加、きらめきの役割が一致し、ぼけ・欠けはない。参照のSF風アイコンとは形状が一部異なる。Apache-2.0全文と出典を同梱。
- 文言・内容：「まんなか」「みんなの“まんなか”、どこだろう？」「集合時間」「集まって何する？（任意）」「参加者」「名前（任意）」「最寄り駅を選ぶ」「駅まで何分？（任意）」「参加者を追加」「中心駅を探す」を反映。ホームのウィジェット名は依頼どおり「まんなか！」を保持。

### 修正と再比較の履歴

1. 初回ネイティブ表示で検索ボタンがジェスチャーバーの下へ一部隠れるP2を確認（`mannaka-restyled-dark.png`）。要約カードの行間／内側余白と参加者カードの余白を調整。1290×2796pxのメイン実画面を再取得し、検索ボタンが全体表示されることを確認。
2. 同寸法の比較で上端の緑色、下部パネルの外側余白とドラッグハンドル、時刻ステップが接するP2を確認（`mannaka-main-before-final.png`、`mannaka-time-before-final.png`）。まんなか専用背景を背面へ適用し、パネル外側8dp、ハンドルの余白、ステップの間隔／高さ、時刻の文字サイズを修正。全4状態と時刻部分の比較ボードを再生成して解消を確認。
3. 最初の自動撮影は別Composeルートを取得してパネルが写らなかったため、MainActivityをActivityScenarioで起動し、UiAutomationで実画面を取得する方法へ修正。押下演出が収まる待機を追加して全4状態を再取得。最終比較ボードはこの実画面から生成した。

### 機能・検証と限界

- `testDebugUnitTest`：55件失敗0。追加2件で時刻の加減算／日付内への折り返し／時刻のゼロ埋めを確認。
- `MannakaUiTest`：3件成功。実アプリで3日選択、±5分／±30分、18:30プリセット、決定後の表示、参加者3人へ追加して削除、名前、駅まで12分、4テンプレート、自由入力の美術館を確認。選択駅・名前・駅までの時間の保存形式の往復も確認。
- `lintDebug`：0 errors／83 warnings。今回の機能から出るLint警告は解消。集合日の移動量は配置時に読むoffsetラムダへ変更し、アニメーションの毎フレームの再構成を避けた。
- `assembleDebug`／`assembleDebugAndroidTest`成功。最終Debug APKはAVDへ更新。
- ネイティブアプリのためブラウザーコンソール確認は該当なし。実機、200%文字サイズ、回転操作、検索→駅→施設の全通し操作、今回追加したお気に入り／履歴の実操作・端末内永続化、フレーム時間は未検証。これらを今回の画像一致の証明には含めない。
- 集合時間と駅までの時間は予定のメモ。経路時間・運賃に基づく計算を追加したと説明しない。

### 残る微調整（P3）

- Android標準フォントとGoogle公式アイコンの形状、影、タップ領域による縦位置はiOSの参考と完全一致しない。「こんな感じのUI」という依頼に沿うネイティブ実装上の調整として許容する。画面構成、色、操作順、各パネルの状態を変更するP0／P1／P2は残っていない。

final result: passed

## 2026-10-01 まんなか：カード内駅候補と専用結果画面

### 参照と確認した画像

- 参照：codex-clipboard-db36951e-6e15-4f6a-a858-e4631994e253.png（駅入力中）、codex-clipboard-161ff66c-9f0f-40a5-9284-0778d7e540f4.png（まんなか候補）。
- 実装：app/build/visual-qa/mannaka-reference-inline.png、mannaka-reference-results.png。Pixel_10a AVDを一時的に1290×2796px／density480へ変更して取得。
- 全体の比較：mannaka-comparison-inline.png、mannaka-comparison-results.png。
- 部分の比較：mannaka-comparison-inline-detail.png、mannaka-comparison-map-detail.png、mannaka-comparison-result-card-detail.png。比較はcompare_mannaka_followup.pyで作成し、全体と各領域を確認した。
- 比較画面の駅と住所はUIテスト用データである。実際の地図タイルを読み込み、テスト用の座標から順位・直線距離を算出している。参考画像の駅順位と移動時間を製品の計算結果として固定していない。

### 検出した差分と修正

1. P0：WebViewの地図が空白。地図ライブラリー・3マーカー・タイルは取得されていたが、地図要素のCSS高さが0pxになっていた。innerWidth／innerHeightを使って実寸を設定し、invalidateSizeで表示を更新。最終DOMは幅398・高さ242・3マーカー・実タイル6枚。最終画像で地図と3マーカーを確認した。
2. P1：駅入力が独立したパネルになっており、参照のカード内候補と違っていた。参加者カード内に候補4行、ピン付き入力欄、クリア、入力状態の枠線を配置。名前がある場合は先頭文字をアバターへ表示。650ms待機・旧検索の取り消し・一致候補の先行表示で操作を維持した。
3. P1：結果が入力画面の下へ追加される構成。専用「まんなか候補」画面に移し、戻る・順位案内・地図・本命・他の候補・共有・周辺施設を順に配置。番号マーカーまたは候補行を選んで本命カードを切り替えられる。
4. P2：本命カードの余白と参加者の最長表示が大きく、アバターの高さが揃わなかった。最長バッジをアバター上へ重ね、文字の行高と余白を調整。最大距離を大きく、別候補は小さな順位行にした。共有ボタンを順位行の直後へ配置した。
5. P2：地図右下の出典が角丸で切れた。出典の右／下へ余白を設け、最終画像で「© OpenStreetMap contributors」の全文が読めることを確認。
6. P2：入力が伸びると検索ボタンが下に押し出される。IMEの高さがある場合は案内と検索ボタンをIME上へ固定し、リスト下端へ余白を追加した。今回のAVDはIMEが小型入力バー表示で、通常サイズのキーボードの下端配置を実機で確認した証拠には含めない。

### 検証と範囲

- 単体テスト57件成功。最大距離を平均距離より優先するケース、同じ駅の別要素を候補からまとめるケースを追加。
- UIテスト4件成功（最終20.415秒）。旧検索を取り消し、一致駅を先に表示し、4行の候補から選択、全員の駅を選んで結果へ遷移、DOMの地図寸法と3マーカー、マーカー操作、候補行の切り替え、入力画面への戻りと選択値保持を確認。従来の集合時間・参加者追加削除・歩行時間・4テンプレートと自由入力・保存形式のテストも継続。
- Lintは0 errors／83 warnings。ic_mannaka_settingsの既存VectorPath警告を含み、警告ゼロではない。Debug／AndroidTest APKの最終ビルド成功。
- 実データ取得では町屋の2路線、町屋駅前、町屋二丁目を確認。全国の正規表現・周辺の正規表現はタイムアウトしたため、地域の駅データを取得して端末側で絞る方式へ変更した。周辺取得失敗時でも先に表示した一致駅は利用できる。
- 地図のコンソールはデバッグ版だけログに記録。ライブラリー・描画関数を読み込み、JavaScriptエラーで空白になった状態を成功扱いせず、実画像とDOMの寸法で修正を確認した。
- 実機、通常キーボード時の下端配置、200%文字サイズ、回転、極端な人数でのフレーム時間、駅から施設までの実ネットワーク全通し、共有先への送信、履歴とお気に入りの永続化は未検証。
- 検証データによる端末履歴への影響を防ぐため、UIテスト前後に履歴・お気に入りを退避・復元する処理を追加し、4件成功（19.783秒）を確認。以前の今回のテスト予定だけを完全一致で取り除き、利用者の予定は保持する。画面寸法・density・IME設定・テーマを元の状態へ戻し、実アプリを再起動した。

### 意図した差分と残る微調整

- 電車の経路時間は取得していないため、参考の「13分」などを表示せずkmの直線距離とその計算基準を表示。運賃・乗換を考慮した順位ではない。実経路は外部地図で確認できる。
- Apple Mapsの地図画像を流用せず、OpenStreetMapの地図を使用。地図の配色・文字・王冠の字形、Android標準の戻る矢印・IME・安全領域、参加者の名前表示は参照と異なる。候補一覧はスクロール可能で、端末寸法によって3件目の表示位置が変わる。
- 全体と部分の再比較で、今回の対象の画面構成・入力候補・地図表示・候補選択を阻害する未修正のP0／P1／P2は確認していない。上記の未検証条件は画像一致や実機性能の証明へ含めない。

final result: passed


## 2026-10-01 Essential配色・戻るボタン統一

ユーザー指示により参考画像のコーラル配色からEssential共通テーマへ変更。入力・結果・シート・地図マーカーが共通配色を参照する。入力と結果にGlassBackButtonを使用し、ヘッダーの歯車を削除。説明シートは下部リンクへ移動して保持。

更新成功後のAndroid UIテスト4件成功。入力／結果のライト画面を目視確認し、緑とクリーム、ボタン形状、ヘッダーに歯車がないこと、結果地図と戻る操作を確認。lintエラー0、警告85（未使用になった旧戻る・歯車vectorの警告を含む）。ダーク配色は共通テーマの参照をコード確認し、実機とダーク画面の目視は未実施。画像：app/build/visual-qa/mannaka-essential-main.png、mannaka-essential-results.png。


## 2026-10-01 全国駅オフライン検索と400MB条件

Essential共通配色と戻るボタン、歯車の非表示、テンプレート3種類を維持。実際の全国駅データ（国土交通省2025年度）を使った町屋・両国の入力から中心駅候補表示を確認。施設検索エラー時にも結果・再検索・地図検索を使用可能。機内モードで駅選択と中心駅結果表示を確認した。地図タイルと施設検索は引き続き通信が必要。

最終UI・容量テスト6件成功、単体60件成功、lintエラー0／警告85。容量はAndroidのappBytes＋dataBytesで400MB以下を確認。地図専用キャッシュは12MB以内。CPU別配布APKは約109～139MB、Universalはインストール容量400MB条件を満たさないため対象外。実機の容量や将来の利用者保存データの増加は未保証。

スクリーンショット：app/build/visual-qa/mannaka-offline-final-results.png。数値の詳細：app/build/visual-qa/mannaka-storage-size.json、mannaka-app-size.json。


## 2026-10-01 駅選択後の周辺施設画面

中心駅候補検索では施設通信を行わず、駅選択後に専用画面へ遷移。未指定なら3テンプレートと自由入力を表示し、指定済みなら選択駅で施設検索を自動開始。条件の変更、施設エラー、再検索、地図検索、共有、戻る操作を保持。

UI7件＋容量1件の計8件成功。条件選択と自動検索結果の画面を目視確認し、Essential共通配色・戻るボタン・ラベル・検索条件・CTAの表示を確認。自動検索結果の施設は注入したテスト応答。実サービス施設の網羅性や営業状態は未検証。

最新検証端末のアプリ＋データ容量は約375.5MBで400MB以内。CPU別Debug APKの容量検査も成功。実機／Universalインストール容量／保存データによる将来の増加については従来の条件と制限を維持。

画面：app/build/visual-qa/mannaka-facility-activity.png、mannaka-facility-preset-custom.png。検証：mannaka-facility-flow-ui-test.txt、mannaka-facility-flow-size.json。

## 2026-10-01 まんなか Progressive Motion UI
共通ProgressiveWidgetを入力・駅候補・活動選択・施設結果・シートへ適用。段階別の再生周期、画面内表示時の開始、安定したリストキーを採用。画面切替のWebView二重生成なし。Debug/Lint/AndroidTestビルド成功、既存のUI7件＋容量1件成功。活動選択と施設結果の完了画像に表示欠落なし。実機FPSは未計測。検証エミュレーターでアプリ＋データ373,948,416バイト、400MB以内。ログと画像はapp/build/visual-qa/mannaka-motion-*へ保存。

## 2026-10-01 通知ログ
ホームの新規ベルアイコンのウィジェットから専用画面へ開くことを、実際のタップとUI hierarchyで確認。Essentialの配色、GlassBackButton、半透明ガラスカード、ProgressiveWidgetを採用。通常ダーク画面とライトのテスト通知カードに文字欠落・重なりなし。秒単位の受信時刻、本文、keep、削除、許可／接続待ち表示を確認。通知アクセス設定は利用者が許可する導線。最終4テスト成功、Lintエラー0。72時間境界とkeepの永続保存は時刻を固定したDBテスト、実受信・内容更新・同じ本文の新規通知は実NotificationManagerで確認。実機と72時間の実時間経過は未検証。インストール容量は検証AVDで368,791,552バイト。ログ・画像はapp/build/visual-qa/notification-*に保存。

## 2026-10-01 メイン3画面の横スワイプ
ホーム↔機能一覧↔プロフィールを横スワイプで切替。既存のタブ選択・方向別Progressive Motionとスクロール保存を維持。両端は停止、個別機能では無効。最終Compose UI3件成功：双方向・端・タブタップ、縦／短い移動、長押しの実際の並べ替え、機能内の非切替を検証。並べ替え設定はテスト後に復元。Lintエラー0、Debugビルド成功。実機操作感は未確認。ログはapp/build/visual-qa/tab-swipe-*。

## 2026-10-01 統合スキャナー
参考画像2と実装画像を同じ比較出力で確認。カメラ映像を背景に、倍率→白い撮影ボタン→写真アイコン／モード選択／Autoの順で配置。ユーザー指定のQR・文字への名称変更、QR限定Auto、倍率プリセット40%縮小を反映。QR／文字の両画面で文字欠落・重なりなし。Essentialの戻る操作とガラス調選択を維持。
最新エミュレーターUI7件と容量1件成功。モード復元と旧ショートカット、撮影処理、倍率、タブ操作を検証。実機と複数レンズは未検証。画像：app/build/visual-qa/scanner-qr-ui.png、scanner-text-ui.png。
final result: passed

## 2026-10-01 スキャナー倍率・モード切替
初期表示は1.4倍のプリセットだけでシャッター直上。ドラッグ時のみ既存目盛りへ切替し、停止後に復帰。文字モードのAutoは位置を固定して減光と斜線を表示し、使用不可のセマンティクスを確認。QR・文字のライブカメラ画像に重なり・文字欠落なし。選択ピルはホームのバネ設定で移動し、次の映像までは直前のフレームを引き継ぐ。複数レンズ実機とFPSは未検証。
画像：app/build/visual-qa/scanner-gesture-qr.png、scanner-gesture-text.png。UI5件成功。
final result: passed

## 2026-10-01 切替時の段階表示を解除
QR・文字画面でProgressiveWidget再生と映像フェードを削除し、ガラス選択ピルの移動は維持。QRシャッターの斜線と無効状態、文字側の撮影可能状態を確認。倍率目盛りの横ドラッグ展開、連続更新、プリセット変更時の表示、停止後の復帰をUIテストで確認。更新後にUI5件＋容量1件成功。QR画像に文字欠落・重なりなし。実機のFPSと複数レンズは未確認。
画像：app/build/visual-qa/scanner-no-reload-qr.png、scanner-no-reload-text.png。
final result: passed

## 2026-10-01 各要素の切替モーション
斜線を連続線描画＋透過、上部ラベルを縦移動＋透過と幅補間、緑QR枠を微小拡縮＋透過で出入りさせる。共通のモード進行度で同期し、画面全体の再表示演出はなし。切替96msの中間状態と両方向の完了状態をテストで確認。UI5件＋容量1件成功。文字側の静止画で緑枠と撮影斜線が消え、Auto斜線が残ることを確認。実機FPSは未計測。
画像：app/build/visual-qa/scanner-element-motion-qr.png、scanner-element-motion-text.png。
final result: passed

## 2026-10-01 通知アクセス設定欄とロゴ
許可済みの実画面で通知アクセス設定カードがなく、戻るボタン横に水色ベルロゴがあることを確認。画面を開いたまま通知アクセスを取消・付与してカードの再表示・非表示をUIテストで確認。元のアクセス状態へ反映完了まで待って復元。既存通知受信・keep・削除と容量を含む5件成功。実機は未確認。
画像：app/build/visual-qa/notification-access-header.png。結果：notification-access-verified-tests.txt。
final result: passed
