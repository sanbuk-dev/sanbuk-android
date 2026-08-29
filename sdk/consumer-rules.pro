# A publisher's release build strips what it cannot see being used. The public
# surface is called from their code, so it survives on its own — but the names
# below are reached reflectively or matter in a stack trace, and losing them
# turns a support question into a mystery.
-keep public class ir.sanbuk.sdk.Sanbuk { public *; }
-keep public class ir.sanbuk.sdk.SanbukAd { public *; }
-keep public class ir.sanbuk.sdk.SanbukAdView { public *; }
-keep public class ir.sanbuk.sdk.SanbukStyle { public *; }
-keep public class ir.sanbuk.sdk.SanbukFullscreen { public *; }
-keep public interface ir.sanbuk.sdk.SanbukFullscreen$Callbacks { public *; }
# Named in the merged manifest, so it is reached by name, not by a call site.
-keep class ir.sanbuk.sdk.internal.FullscreenActivity { *; }
-keepnames class ir.sanbuk.sdk.core.** { *; }
