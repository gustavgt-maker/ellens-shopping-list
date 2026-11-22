# Ellen's Shopping List

Native Android-Einkaufslisten-App (Kotlin, Jetpack Compose, Material 3, Room, DataStore).
Vollständig offline: keine Internetberechtigung, keine Cloud, keine Werbung, kein Tracking.

> **Stand der Prüfung – bitte lesen.**
> Dieses Projekt wurde in einer Umgebung ohne Zugriff auf das Android SDK und die
> Maven-Repositories (Google/Maven Central) erstellt. Deshalb wurde es dort **nicht mit Gradle
> kompiliert, keine APK gebaut und die automatisierten Tests nicht ausgeführt**.
> Tatsächlich geprüft wurde:
> - die reine Logik (Namensnormalisierung, Vorschlagsfilter/-sortierung, Sortierpositionen,
>   Zusammenführen der Drag-Reihenfolge, Backup-Dateiablage mit Rotation und Verifikation)
>   – mit dem Kotlin-2.0.21-Compiler übersetzt und mit einem Prüfprogramm ausgeführt,
> - alle Kotlin-Dateien auf Syntaxfehler.
>
> Typprüfung gegen die Android-/Compose-Bibliotheken, Room-Codegenerierung, Unit-Tests und
> APK-Build stehen noch aus. Der schnellste Weg dahin ist unten beschrieben (GitHub Actions
> oder Android Studio). Sollte der erste Build Fehler melden, bitte die Meldung zurückgeben –
> sie sind dann gezielt behebbar.

---

## Funktionen

- Beliebig viele Listen mit dezenter Pastellfarbe; Übersicht nach letzter Verwendung mit Anzahl gesamt/offen
- „Gemeinsam einkaufen“: mehrere Listen zusammen, gleiche Artikel als Block, jede Zeile in der Farbe ihrer Liste
- Wechsel zwischen Listen per Wischen über die Kopfzeile oder über die untere Listenleiste
- Großer Schalter „Bildschirm bleibt an“ (wirkt nur, solange die App sichtbar ist)
- Schnelle Eingabe: Artikel + optionale Menge, Eingabetaste fügt hinzu, Felder leeren sich, Tastatur bleibt offen
- Lernende Artikeldatenbank mit alphabetischen Vorschlägen und eigener Verwaltung
- Dublettenprüfung (Groß-/Kleinschreibung und Leerzeichen egal), zusätzlich per Datenbank-Index abgesichert
- Antippen = erledigt (rot durchgestrichen, wandert nach unten); erneut antippen = zurück an alte Position
- Drag & Drop am Griff ≡; erledigte Artikel per Wischen nach links löschen, offene über den Stift – jeweils mit „Rückgängig“
- Liste leeren / Liste löschen mit Sicherheitsabfrage
- Hell / Dunkel / Automatisch, vier Schriftarten, fünf Schriftgrößen, Haptik abschaltbar
- Tägliche automatische Sicherung (3 Stände), manuelle Sicherung, Export/Import über den Android-Dateidialog
- Scrollposition und zuletzt geöffnete Liste werden gespeichert

Details zu Entscheidungen und Architektur: [`docs/ANALYSE_UND_ARCHITEKTUR.md`](docs/ANALYSE_UND_ARCHITEKTUR.md)

## Technische Eckdaten

| | |
|---|---|
| minSdk | 26 (Android 8.0) – `java.time` ohne Desugaring, variable Schriften; deckt ca. 97 % der aktiven Geräte ab |
| targetSdk / compileSdk | 35 (Android 15) |
| Kotlin | 2.0.21 (Compose-Compiler-Plugin) |
| Android Gradle Plugin | 8.7.3, Gradle 8.11.1 (Wrapper enthalten) |
| Compose BOM | 2024.12.01 (Material 3 1.3.1) |
| Room | 2.6.1 (KSP), Schema-Export nach `app/schemas/` |
| Drag & Drop | `sh.calvin.reorderable:reorderable:2.4.0` (Apache 2.0) |
| Java | JDK 17 zum Bauen |

Die Versionen sind ein bewährt zusammenpassender Satz. Android Studio schlägt eventuell neuere
vor; ein Update ist möglich, aber nicht nötig.

## APK bauen

### Variante A: ohne eigene Installation – GitHub Actions (empfohlen)

1. Neues (privates) Repository auf GitHub anlegen und den Projektordner hochladen
   (alle Dateien inkl. `gradlew`, `gradle/wrapper/` und `.github/`).
2. Unter **Actions** startet „Android Build“ automatisch (oder manuell per „Run workflow“).
3. Der Lauf führt alle Unit-Tests aus und baut die Debug-APK.
4. Nach Abschluss unten im Lauf unter **Artifacts** `ellens-shopping-list-debug-apk` herunterladen.
5. Zusätzlich `room-schemas` herunterladen und den Inhalt nach `app/schemas/` ins Repository legen
   (Grundlage für spätere Datenbank-Migrationen).

### Variante B: Android Studio

1. Android Studio (aktuelle stabile Version) installieren.
2. *File → Open* → Ordner `EllensShoppingList` wählen. Gradle-Sync abwarten
   (lädt SDK-Komponenten und Bibliotheken; Internet nötig).
3. Falls gefragt: Android SDK Platform 35 installieren, Gradle-JDK = 17 (oder das mitgelieferte JBR).
4. *Build → Build App Bundle(s) / APK(s) → Build APK(s)*.
5. Die APK liegt unter `app/build/outputs/apk/debug/app-debug.apk`.

### Variante C: Kommandozeile

```bash
# Voraussetzung: JDK 17, Android SDK (ANDROID_HOME gesetzt oder local.properties mit sdk.dir=...)
./gradlew testDebugUnitTest     # alle automatisierten Tests
./gradlew assembleDebug         # Debug-APK
```

## Installation auf dem Smartphone

1. APK auf das Gerät kopieren (USB, eigene Dateiablage).
2. Datei antippen. Android fragt einmalig nach der Erlaubnis „Unbekannte Apps installieren“
   für die verwendete Datei-App – erlauben.
3. Updates installieren sich direkt über die vorhandene App (fester Signaturschlüssel
   `app/debug.keystore` ab Version 1.1). Daten bleiben erhalten.
4. Die Debug-Variante hat die Paket-ID `de.ellens.shoppinglist.debug` und kann parallel zu einer
   späteren Release-Version installiert sein. Daten wandern zwischen beiden über Export/Import.

Für eine dauerhafte Release-Version: in Android Studio *Build → Generate Signed App Bundle/APK*
mit eigenem Schlüssel. **Den Schlüssel sicher aufbewahren** – Updates sind nur mit demselben
Schlüssel möglich.

## Automatisierte Tests (`app/src/test`)

Alle Tests laufen als JVM-Tests (Robolectric für Room/Compose), also ohne Gerät:
`./gradlew testDebugUnitTest`

| Anforderung | Test |
|---|---|
| 1 Listen erstellen, umbenennen, leeren, löschen | `ShoppingRepositoryTest.lists_createRenameClearDelete`, `overview_sortedByLastUseWithCounts` |
| 2 Artikel hinzufügen und bearbeiten | `items_addAndEdit`, `newItems_appendAtEndOfOpenItems` |
| 3 Dublettenprüfung | `duplicates_areRejectedIgnoringCaseAndSpaces` |
| 4 Automatische Aufnahme in die Vorschlagsdatenbank | `catalog_learnsNewItemsAndSurvivesListDeletion`, `catalog_manageWithoutTouchingListItems` |
| 5 Alphabetische Vorschläge | `CoreLogicTest.suggestions_*` |
| 6 Abhaken und erneutes Aktivieren | `toggleDone_restoresOriginalPosition` |
| 7 Ursprüngliche Sortierposition | `toggleDone_positionStaysConsistentAfterReordering` |
| 8 Manuelles Umordnen | `reorder_movesAndPersistsOrder`, `reorder_manyMovesTriggerRenumberingAndStayConsistent`, `CoreLogicTest.ordering_*` |
| 9 Löschen und Rückgängig | `deleteAndUndo_restoresCompleteItem`, `deleteItem_twiceIsHarmless` |
| 10 Persistenz nach Neustart | `PersistenceTest.dataSurvivesRestart` |
| 11 Einstellungen dauerhaft | `SettingsRepositoryTest` |
| 12 Backup erstellen und wiederherstellen | `BackupTest.exportAndRestore_roundTripIsComplete`, `backupFile_containsEverything` |
| 13 Beschädigte Backup-Dateien | `BackupTest.corruptedFiles_areRejectedWithoutTouchingData`, `oversizedFile_isRejected` |
| 14 Rotation (max. 3 automatische) | `BackupTest.dailyBackup_onceperDayAndKeepsThree`, `failedWrite_neverReplacesOlderBackups`, `manualBackups_areRotatedSeparately` |
| 15 Große Schriftgrößen | `LargeFontTest` (Schriftfaktor 1,0 / 1,5 / 2,0 auf 360 dp Breite) |

## Manuelle Tests auf einem echten Smartphone

Am besten auf einem kleinen (≈ 5,5–6") und einem großen Gerät, je einmal hell und dunkel.

**Grundfunktionen**
1. Erster Start: leere Übersicht mit Hinweis. „Neue Liste“ → Name → Liste öffnet sich direkt.
2. 20 Artikel zügig hintereinander eingeben (nur Eingabetaste): Tastatur bleibt offen, Fokus bleibt im Artikelfeld, keine doppelten Einträge, neuer Artikel wird sichtbar gescrollt.
3. „milch“ eingeben, wenn „Milch“ existiert → Meldung „Artikel bereits auf der Liste“, kein Eintrag.
4. Vorschläge: Buchstaben tippen → alphabetische Liste über der Eingabe; Vorschlag antippen → Name übernommen, Fokus im Mengenfeld.
5. Eingabefeld bleibt bei geöffneter Tastatur sichtbar (auch mit Gestennavigation und 3-Tasten-Navigation).

**Abhaken, Sortieren, Löschen**
6. Artikel antippen → rot durchgestrichen, wandert unter „Erledigt“; erneut antippen → zurück an alte Stelle.
7. Mehrfach umsortieren (Griff ≡), dann abhaken/reaktivieren → Reihenfolge bleibt nachvollziehbar.
8. Drag & Drop am Listenende und -anfang, auch mit automatischem Scrollen bei langer Liste.
9. Zeile nach links und nach rechts wischen → gelöscht, Snackbar „Rückgängig“ → Artikel samt Menge, Status und Position zurück.
10. Stift → Bearbeiten-Dialog; Speichern ändert den Erledigt-Status nicht.
11. Prüfen: Wischen über eine Artikelzeile wechselt nie die Liste; Wischen über die Kopfzeile löscht nie einen Artikel.

**Navigation und Wiederherstellung**
12. Drei Listen anlegen; über Kopfzeile wischen und über die untere Leiste wechseln (auch mit 15+ Listen: Leiste scrollt).
13. In einer Liste nach unten scrollen → Home-Taste → andere App → zurück: gleiche Liste, gleiche Position.
14. Entwickleroptionen → „Aktivitäten nicht speichern“ einschalten, Schritt 13 wiederholen (simuliert Prozessende).
15. App über „Zuletzt verwendet“ wegwischen und neu starten → Übersicht erscheint.
16. Smartphone neu starten → alle Daten vorhanden.

**Bildschirm, Darstellung, Barrierefreiheit**
17. Schalter „Bildschirm bleibt an“ ein: Display bleibt länger als das System-Timeout an; App verlassen → normales Timeout gilt. Schalter-Zustand nach Neustart erhalten.
18. Hell/Dunkel/Automatisch umschalten (Automatisch mit System-Dark-Mode prüfen), Statusleisten-Symbole lesbar.
19. Jede Schriftart und Schriftgröße „Riesig“ zusammen mit Android-Schriftgröße „Maximal“ und „Anzeigegröße groß“: nichts abgeschnitten, Eingabe zweizeilig, Knöpfe bedienbar.
20. TalkBack: Artikelzeile liest Name und Aktion („Als erledigt markieren“), Griff und Stift sind benannt.
21. Entwickleroptionen → Animationsskala „aus“: Listenwechsel ohne Animation.
22. Haptik in den Einstellungen ausschalten → keine Vibration mehr.

**Sicherungen**
23. Einstellungen → „Backup jetzt erstellen“ → erscheint unter „Interne Sicherungen“.
24. „Backup exportieren“ → in eigenem Ordner speichern → Datei mit Dateimanager prüfen.
25. Daten ändern → „Backup importieren“ → Bestätigungsdialog zeigt Anzahl Listen/Artikel → „Daten ersetzen“ → alter Stand zurück, Einstellungen ebenfalls.
26. Eine beliebige andere Datei (Foto, Textdatei) importieren → Ablehnung mit Meldung, Daten unverändert.
27. Datumsumstellung um 1 Tag vor (oder an mehreren Tagen öffnen) → neue automatische Sicherung; nach 4 Tagen nur 3 automatische vorhanden.
28. Export auf altem Gerät → Import auf neuem Gerät (Smartphonewechsel).

**Datenschutz**
29. App-Info → Berechtigungen: keine. Im Flugmodus funktioniert alles.

## Projektstruktur

```
app/src/main/java/de/ellens/shoppinglist/
  EllenApp.kt, MainActivity.kt        App-Start, Abhängigkeiten, Bildschirm-Flag, Tagessicherung
  core/                               reine Logik (Normalisierung, Vorschläge, Sortierung)
  data/db/                            Room: Entities, DAOs, Datenbank, Migrationen
  data/repo/                          Repositories für Listen/Artikel und Artikeldatenbank
  data/settings/                      Einstellungen (DataStore)
  data/backup/                        Sicherungsformat, Prüfung, Dateiablage, Manager
  ui/                                 Navigation, Theme, Screens, Komponenten
app/src/main/res/font/                Schriftdateien (OFL 1.1)
app/src/main/assets/licenses/         Lizenztexte der Schriften
app/src/test/                         automatisierte Tests
docs/                                 Analyse und Architektur
.github/workflows/android-build.yml   Cloud-Build mit Tests und APK
```

## Wartung

- **Datenbank ändern:** Entity anpassen, `AppDatabase.VERSION` erhöhen, Migration in `Migrations.ALL` ergänzen, exportiertes Schema einchecken.
- **Sicherungsformat ändern:** `BackupFormat.VERSION` erhöhen; ältere Versionen beim Import weiter unterstützen.
- **Spätere Synchronisierung:** Repositories sind die einzige Stelle mit Datenzugriff; eine Sync-Schicht kann dort ansetzen. Dafür müsste die Internetberechtigung ergänzt werden (derzeit per Manifest ausdrücklich entfernt).
