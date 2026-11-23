# Ellen's Shopping List – Anforderungsanalyse und Architektur

## Leitlinie für alle Erweiterungen (festgelegt 23.11.2026)

- **Keine kostenpflichtigen Dienste.**
- **Alles läuft eigenständig bzw. selbst betrieben** (eigenes Gerät / eigener Server zu Hause), auch wenn das den Funktionsumfang minimal einschränkt.
- **Keine Fremddienste für Analyse, Tracking, Werbung oder Cloud-Speicher** (z. B. kein Google Analytics, kein Firebase, keine Google-Cloud-Sicherung).
- Bei mehreren Lösungswegen wird die selbst gehostete, quelloffene Variante gewählt (z. B. ntfy statt Firebase Cloud Messaging für Benachrichtigungen).
- Fernzugriff von unterwegs über eine eigene WireGuard-VPN-Verbindung (z. B. über den Router, falls er das kann, oder selbst gehostetes Headscale) statt fremder Tunnel- oder Relay-Dienste.

## Ausbaustufen „Einträge aus der Ferne“

1. **Übernahme aus Nachrichten** (offline, ohne Server): Text per „Teilen“ oder Einfügen übernehmen, Artikel und Mengen erkennen, Vorschau, in gewählte Liste eintragen.
2. **Gemeinsame Listen über eigenen Heimserver** (geplant, sobald das Gerät für Hausautomation steht): Synchronisierungsdienst + Weboberfläche (iPhone der Ehefrau, als Web-App auf dem Home-Bildschirm) + Benachrichtigung neuer Artikel über selbst gehostetes ntfy.

## Phase 1: Widersprüche, Probleme und getroffene Präzisierungen

| # | Anforderung | Problem | Entscheidung |
|---|---|---|---|
| 1 | Übersicht „nach letzter Verwendung sortiert“ **und** Wischen zwischen Listen | Wenn das Öffnen einer Liste deren Zeitstempel ändert, würde sich die Reihenfolge beim Wischen ständig umsortieren – man käme nie zur „nächsten“ Liste. | Übersicht: nach letzter Verwendung (Öffnen oder Ändern). Wischen und untere Leiste: **stabile Reihenfolge nach Anlagedatum**. |
| 2 | „Ursprüngliche Position“ eines reaktivierten Artikels | Nach Umsortierungen ist „ursprünglich“ nicht eindeutig. | Jeder Artikel hat eine dauerhafte Position in einer gemeinsamen Ordnung aller Artikel der Liste. Abhaken ändert sie nicht; beim Reaktivieren kehrt der Artikel an seinen Platz **relativ zu seinen bisherigen Nachbarn** zurück. Positionen sind eindeutig, das Ergebnis ist deterministisch (durch Tests abgesichert). |
| 3 | Reihenfolge der erledigten Artikel | Nicht spezifiziert. | Zuletzt abgehakt steht direkt unter den offenen Artikeln. Erledigte Artikel sind nicht per Drag & Drop sortierbar (die Anforderung nennt nur offene). |
| 4 | Antippen eines Vorschlags „übernimmt“ den Artikel | Unklar, ob sofort hinzugefügt oder nur ins Feld übernommen. | Übernahme ins Namensfeld, Fokus springt ins Mengenfeld; Eingabetaste fügt hinzu. So kann direkt eine Menge ergänzt werden. (Einfach änderbar in `ListScreen.kt`, `onPick`.) |
| 5 | Vorschlagsfilter | „anhand der Eingabe gefiltert“ – Präfix oder enthält? | **Enthält** (findet „Vollmilch“ bei „milch“), Groß-/Kleinschreibung egal, Ergebnis alphabetisch nach deutscher Sortierung (Umlaute bei Grundbuchstaben). |
| 6 | Bearbeiten eines Artikels auf einen schon vorhandenen Namen | Nicht spezifiziert. | Wird wie beim Hinzufügen abgelehnt („Artikel bereits auf der Liste“). Der neue Name wird in die Artikeldatenbank aufgenommen. |
| 7 | Artikeldatenbank umbenennen/löschen | Darf Listen nicht verändern. | Listeneinträge speichern ihren Namen eigenständig; es gibt bewusst keine Verknüpfung. |
| 8 | „Keine Cloud-Dienste“ vs. Android-Systemsicherung | Android sichert App-Daten standardmäßig in Google Drive. | Cloud-Sicherung per `dataExtractionRules`/`fullBackupContent` ausgeschlossen; direkte Gerät-zu-Gerät-Übertragung (Android 12+) bleibt erlaubt. |
| 9 | Wiederherstellung nach App-Wechsel vs. neuer Start zeigt Übersicht | Android kann den Prozess im Hintergrund beenden. | Navigation und aktuelle Liste liegen im Instanzzustand (übersteht Prozessende). Ein neuer Start ohne gespeicherten Zustand beginnt mit der Übersicht. Scrollpositionen pro Liste zusätzlich in der Datenbank. |
| 10 | WorkManager für Backups | Hintergrundaufgaben ohne Nutzung sind nicht gefordert („bei Nutzung“). | Tägliche Sicherung beim Start/Vordergrund der App in einer Coroutine; kein WorkManager nötig (weniger bewegliche Teile). |
| 11 | Schriftgröße in der App + Android-Schriftgröße | Doppelte Skalierung kann sehr groß werden. | App-Stufe (0,9–1,5) multipliziert die Systemstufe. Bei sehr großer Schrift (≥ 1,6) ordnet sich die Eingabezeile zweizeilig an. |
| 12 | Bildschirm-Schalter „ganz oben“ | Übersicht und Listenansicht. | Schalter in beiden Ansichten ganz oben, zusätzlich in den Einstellungen. Wirkung über `FLAG_KEEP_SCREEN_ON` nur am App-Fenster. |
| 13 | Rot nur als Akzent | – | Rot ausschließlich für erledigte Artikel, Löschaktionen und den Hinweis zum Deinstallationsrisiko. |
| 14 | Schriften | Lizenz | Roboto, Open Sans, Noto Sans, Atkinson Hyperlegible – alle SIL Open Font License 1.1, Lizenztexte unter `app/src/main/assets/licenses/`. |
| 15 | Ausrichtung | Hochformat gefordert, Tablets später | `screenOrientation="portrait"`. Hinweis: Sobald später `targetSdk 36` (Android 16) gesetzt wird, ignoriert Android diese Sperre auf großen Bildschirmen; die Oberfläche ist dafür bereits flexibel aufgebaut. |

## Phase 2: Architektur

```
UI (Jetpack Compose, Material 3)
 ├─ OverviewScreen      ← OverviewViewModel
 ├─ ListScreen          ← ListViewModel (SavedStateHandle: aktuelle Liste)
 ├─ CatalogScreen       ← CatalogViewModel
 └─ SettingsScreen      ← SettingsViewModel
        │  StateFlow / Ereignisse
Repository-Schicht
 ├─ ShoppingRepository  (Listen, Artikel, Sortierung, Rückgängig)
 ├─ CatalogRepository   (Vorschlagsdatenbank)
 ├─ SettingsRepository  (DataStore)
 └─ BackupManager       (Snapshot, Rotation, Export/Import)
        │
Daten
 ├─ Room/SQLite: shopping_lists, shopping_items, catalog_items (WAL, Transaktionen)
 ├─ DataStore: settings.preferences_pb
 └─ files/backups/*.json (privater App-Speicher)
Reine Logik (ohne Android, voll testbar): core/NameNormalizer, SuggestionFilter, ItemOrdering
```

### Datenmodell (Room, Version 2)

- `shopping_lists(id, name, created_at, last_used_at, scroll_index, scroll_offset, color_index)`
- `shopping_items(id, list_id → shopping_lists.id ON DELETE CASCADE, name, normalized_name, quantity, is_done, position, done_at, created_at)`
  - eindeutiger Index `(list_id, normalized_name)` → keine Dubletten, auch nicht bei Doppel-Tipp
- `catalog_items(id, name, normalized_name UNIQUE, created_at)` – unabhängig von Listen

Migrationen: `Migrations.ALL` (1→2: Spalte `color_index`, vorhandene Listen erhalten Farben 1–6). **Kein** `fallbackToDestructiveMigration` – ein Update löscht nie Daten.

### Sortierung

Position = `Long` mit Abstand 1024. Verschieben setzt die Position zwischen die neuen Nachbarn; ist kein Platz frei oder wäre die Position nicht eindeutig, wird die Liste unter Beibehaltung der Reihenfolge neu durchnummeriert (in einer Transaktion).

### Sicherungen

- JSON, Format-ID `ellens-shopping-list-backup`, Formatversion 1.
- Schreiben: temporäre Datei → `fsync` → zurücklesen und vollständig prüfen → atomar umbenennen → erst dann alte Stände rotieren.
- Automatisch: höchstens 1× pro Kalendertag, 3 Stände. Manuell: 3 Stände (getrennt). Vor jedem Import: Sicherheitskopie (2 Stände).
- Import: Größenlimit 20 MB, UTF-8-Prüfung, JSON-Schema, Formatversion, Querverweise, Dubletten, leere Namen. Einspielen in **einer** Datenbanktransaktion.

### Gesten ohne Konflikt

| Bereich | Geste | Wirkung |
|---|---|---|
| Kopfzeile (Listenname) | horizontal wischen | vorherige/nächste Liste |
| Erledigte Artikelzeile | von rechts nach links wischen | Artikel löschen (mit Rückgängig); offene Artikel: Stift → Löschen |
| Griff ≡ links | ziehen | Umsortieren |
| Text der Zeile | antippen | erledigt/offen |
| Stift rechts | antippen | bearbeiten |
