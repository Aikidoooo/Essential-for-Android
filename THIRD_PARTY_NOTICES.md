# サードパーティーソフトウェア

Essentialは次のソフトウェアを利用します。配布時には各ライセンス全文、著作権表示、対応するソース提供条件を確認してください。

- youtubedl-android 0.18.1 — GPL-3.0
  - https://github.com/yausername/youtubedl-android
- yt-dlp — Unlicense
  - https://github.com/yt-dlp/yt-dlp
- FFmpeg — 構成に応じてLGPL-2.1以降またはGPL-2.0以降
  - https://ffmpeg.org/legal.html
- ffmpeg-kit-maintained 8.1.7 — LGPL-3.0以降。同梱するFFmpeg構成のライセンスも別途適用
  - https://github.com/ffmpegkit-maintained/ffmpeg
- ONNX Runtime Android 1.30.0 — MIT License
  - https://github.com/microsoft/onnxruntime
- Spleeter 2-stem FP16モデル — SpleeterのMITライセンスおよびモデル提供元の条件を確認
  - https://github.com/k2-fsa/sherpa-onnx/releases/tag/source-separation-models
  - https://github.com/deezer/spleeter
  - 同梱ファイル: `app/src/main/assets/models/audio_separation/spleeter-2stems-fp16/`
  - ボーカルSHA-256: `24CEF84AEDCD1FE87C0B743EF3370AD34DC1FABF6C9014D6128A75A538C7B668`
  - 伴奏SHA-256: `D14CEA55793CC531A5875F5F4DA08207D1C5AB9292E8E0099A104EECB014FCC0`
- smart-exception-common / smart-exception-java 0.2.1 — ffmpeg-kitの実行時依存関係
  - https://github.com/arthenica/smart-exception
- AndroidX / CameraX / Jetpack Compose — Apache License 2.0
  - https://source.android.com/docs/setup/about/licenses
- Google Material Icons — Apache License 2.0（Copyright Google）
  - https://github.com/google/material-design-icons
  - 「まんなか」のアイコン12点を公式SVGからAndroid VectorDrawableへ変換して使用。
  - 同梱するライセンス全文：`app/src/main/assets/licenses/material-icons-Apache-2.0.txt`
- Google ML Kit Barcode Scanning — Google APIs Termsおよび配布物のライセンス条件
  - https://developers.google.com/ml-kit/terms
- Google ML Kit Subject Segmentation 16.0.0-beta1 — Google APIs Termsおよび配布物のライセンス条件
  - https://developers.google.com/ml-kit/vision/subject-segmentation/android

youtubedl-androidを含むAPKを第三者へ配布する場合、Essential側の配布形態もGPL-3.0との整合が必要です。リリース前にライセンス全文の同梱とソース提供方法を確定してください。

## まんなかの候補地図

Leaflet 1.9.4（BSD-2-Clause）を必要なJS/CSSだけ同梱しています。
出典：https://leafletjs.com/ 、https://github.com/Leaflet/Leaflet/tree/v1.9.4 。
ライセンス全文：`app/src/main/assets/licenses/leaflet-BSD-2-Clause.txt`。
地図タイルはOpenStreetMap（© OpenStreetMap contributors）から表示範囲だけ取得し、WebViewのHTTPキャッシュを使用します。
https://www.openstreetmap.org/copyright


## まんなか：全国駅データ

出典：国土交通省「国土数値情報（鉄道データ）2025年度版」
https://nlftp.mlit.go.jp/ksj/gml/datalist/KsjTmplt-N02-2025.html

CC BY 4.0（https://creativecommons.org/licenses/by/4.0/）。2025年12月31日時点、2026年10月1日取得。
Essentialが駅の線形座標の平均を代表点に変換し、駅コードで統合して、駅名・路線・運営会社・座標だけを抽出・加工したものです。国土交通省が作成したアプリ・駅代表点として表示するものではありません。
収録件数・取得元・加工方法・SHA-256は `app/src/main/assets/mannaka/stations-source.json` に記録しています。原本ZIPは同梱しません。再作成は `scripts/build_mannaka_stations.py` を使用します。
