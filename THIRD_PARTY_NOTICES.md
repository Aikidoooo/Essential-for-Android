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
- Google ML Kit Barcode Scanning — Google APIs Termsおよび配布物のライセンス条件
  - https://developers.google.com/ml-kit/terms
- Google ML Kit Subject Segmentation 16.0.0-beta1 — Google APIs Termsおよび配布物のライセンス条件
  - https://developers.google.com/ml-kit/vision/subject-segmentation/android

youtubedl-androidを含むAPKを第三者へ配布する場合、Essential側の配布形態もGPL-3.0との整合が必要です。リリース前にライセンス全文の同梱とソース提供方法を確定してください。
