# JNIから参照するクラス名とメソッド名を保持する。
-keep class jp.essential.app.core.EssentialCore { *; }
-keep class ai.onnxruntime.** { *; }

# WebViewから呼び出すJavaScriptインターフェースの公開メソッドを保持する。
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
