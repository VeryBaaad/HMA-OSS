# The Modern Xposed framework instantiates the entry class by name, see
# META-INF/xposed/java_init.list.
-keep class icu.nullptr.hidemyapplist.xposed.XposedEntry { *; }

# `io.github.libxposed:api` is a compileOnly dependency: the framework injects it into
# the target process at runtime, so R8 must neither package nor complain about it.
# These rules mirror the consumer rules shipped inside the api artifact.
-dontwarn io.github.libxposed.api.**
-dontwarn io.github.libxposed.annotation.**
-keep,allowoptimization public class io.github.libxposed.api.** {
    public <fields>;
    protected <fields>;
    public <methods>;
    protected <methods>;
    public <init>(...);
    protected <init>(...);
}

-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn android.content.res.XModuleResources
-dontwarn android.content.res.XResources
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BCProvider
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE
