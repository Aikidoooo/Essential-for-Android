# JNIから参照するクラス名とメソッド名を保持する。
-keep class jp.essential.app.core.EssentialCore { *; }
-keep class ai.onnxruntime.** { *; }

# yt-dlp更新ZIPの展開では、Apache Commons CompressがZipExtraField実装を
# Reflectionで生成する。Release最適化後も公開引数なしコンストラクタを保持する。
-keep,allowoptimization,allowobfuscation class org.apache.commons.compress.archivers.zip.** implements org.apache.commons.compress.archivers.zip.ZipExtraField {
    public <init>();
}

# WebViewから呼び出すJavaScriptインターフェースの公開メソッドを保持する。
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
