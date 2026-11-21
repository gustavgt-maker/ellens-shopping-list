# kotlinx.serialization: generierte Serializer der Backup-Modelle behalten
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class de.ellens.shoppinglist.data.backup.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class de.ellens.shoppinglist.data.backup.**$$serializer { *; }
