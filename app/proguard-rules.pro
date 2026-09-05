# 混淆规则。当前未开启混淆，仅保留默认配置备用。
-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
