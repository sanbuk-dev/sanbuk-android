# A publisher's release build strips what it cannot see being used. The public
# surface is called from their code, so it survives on its own — but the names
# below are reached reflectively or matter in a stack trace, and losing them
# turns a support question into a mystery.
-keep public class ir.sanbuk.sdk.Sanbuk { public *; }
-keep public class ir.sanbuk.sdk.SanbukAd { public *; }
-keep public class ir.sanbuk.sdk.SanbukAdView { public *; }
-keep public class ir.sanbuk.sdk.SanbukStyle { public *; }
-keepnames class ir.sanbuk.sdk.core.** { *; }
