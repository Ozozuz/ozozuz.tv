# ozozuz.tv — Android 0.7.0

Catalogo e navigazione Android nativi. WebView limitata al player video incorporato. Android 8.0 o successivo.

## Funzioni

- Nome mostrato nell'app e nel launcher: ozozuz.tv. Il package e la chiave di sviluppo restano quelli delle versioni precedenti per consentire gli aggiornamenti.
- Icona succube adulta bionda, sopracciglia bionde e dettagli punk; PNG nelle densità del launcher e icona adattiva con sfondo grafite.
- Tre collegamenti in basso soltanto nella home: GitHub, X e Repository. Aprono un browser/app esterna su tocco esplicito; sono nascosti durante la ricerca e assenti nelle schede dei titoli.

- URL HTTPS modificabile dal comando «dominio» nella barra superiore, normalizzato alla home e salvato dopo un caricamento riuscito. Il campo rimane nascosto durante la normale navigazione e si apre automaticamente se il caricamento fallisce.
- Titolo in evidenza e sezioni reali della home: titoli del momento, aggiunti di recente, top 10.
- Sfondo della scheda specifico del film/serie, ricavato dalla sua immagine ufficiale di background (con cover/locandina come alternativa). Immagine fissa dietro gli episodi, ritaglio proporzionale, desaturazione e opacità ridotta, velo scuro e sfumatura verso il basso per mantenere leggibile il testo.
- Cache delle immagini di sfondo limitata a 8 MiB; caricamento asincrono e riuso al cambio stagione. I titoli senza immagini mantengono il fondo scuro.
- Stile ispirato ai riferimenti forniti: fondo quasi nero (#0C0E10), pannelli grafite (#111418), bordi sottili (#252A30), accento verde (#49F879), raggi piccoli di 3–6 dp. Titoli e comandi monospaziati, trame in sans-serif, filtri testuali con parentesi e stato selezionato. Nessun font esterno o decorazione animata.
- Copertine, nome, tipo e voto quando presenti.
- Ricerca sul catalogo del sito da almeno 2 caratteri, con debounce di 400 ms e risposte obsolete ignorate. I filtri Film / Serie TV si applicano ai risultati ricevuti. Cancellando il testo si torna alla home.
- Un tocco su un titolo, dalla home o dalla ricerca, apre una scheda completa. Le serie mostrano un pannello delle stagioni a griglia con selezione evidenziata e un elenco di schede episodio: miniatura reale, numero, titolo e trama breve. La puntata selezionata viene evidenziata in verde. I film hanno un pulsante Guarda film.
- Le miniature degli episodi provengono dalle immagini cover dei dati della stagione, con caricamento asincrono, riciclo delle righe e cache dedicata da 8 MiB. Se non disponibili resta una cornice con numero e simbolo Play.
- Area video delle serie proporzionale alla larghezza: 16:9 più 56 dp di spazio verticale, minimo 260 dp, per separare la barra di avanzamento dai pulsanti centrali. Ricalcolo al cambio di larghezza/orientamento.
- Player incorporato con controlli del fornitore, fullscreen, pulsanti episodio precedente/successivo e ricaricamento del link temporaneo.
- Indietro chiude prima il fullscreen, poi il player e infine la scheda. Il catalogo e la ricerca rimangono nella schermata precedente.
- Errori leggibili per dominio scaduto, challenge, HTTP inatteso o struttura modificata.

## Puntate viste e download

La casella **Già vista** sotto il player segna manualmente la puntata. `SharedPreferences` conserva la coppia ID titolo/ID episodio, indipendente dal dominio; il badge ✓ appare sulla miniatura anche nelle sessioni successive. Deselezionando la casella si elimina il segno. Nessuna sincronizzazione esterna.

Premi Play una volta, poi **Scarica ↓** e scegli una cartella locale nel selettore Android. Il download usa una copia in memoria della playlist già accettata dal player. Seleziona la migliore variante fino a 720p (la più piccola disponibile se tutte superiori) e l'audio italiano se presente, altrimenti quello predefinito. Scarica le tracce e le riunisce con MediaExtractor/MediaMuxer senza ricodifica. I sottotitoli non vengono esportati.

Un servizio foreground gestisce un download alla volta, con avanzamento e annullamento sia in pagina sia nella notifica. Il file MP4 finale resta nella posizione scelta; **Apri MP4** e la notifica di completamento aprono il lettore locale nell'app, che espone la stessa spunta. Non servono permessi generici di accesso ai file. Serve spazio per le tracce temporanee e il file finale; i parziali sono eliminati dopo errori o annullamento. Un arresto del processo non consente ripresa automatica.

Supportati HLS VOD finiti, segmenti TS/fMP4 e AES-128 ordinario, video H.264/H.265 e audio AAC. Discontinuità, SAMPLE-AES/DRM e cambi di inizializzazione sono rifiutati. La destinazione deve consentire seek: scegli memoria locale. Un link scaduto richiede di ricaricare il player e riavviare il download. Le risorse provengono soltanto dalle playlist autorizzate; TLS e restrizioni della WebView restano attivi.

## Compilare e installare

Apri questa cartella in Android Studio con JDK 17 e Android SDK 35. Attendi Gradle Sync e usa Run sul dispositivo, oppure Build > Build App Bundle(s) / APK(s) > Build APK(s).

Da terminale con Gradle 8.13, JDK 17 e SDK Android 35 configurati:

```sh
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Il wrapper Gradle 8.13 è incluso. Per compilare direttamente con gli strumenti SDK (alternativa verificata, senza Gradle):

```sh
JAVA_HOME=/path/to/jdk17 ANDROID_HOME=/path/to/android-sdk ./tools/build-sdk.sh
```

Questo percorso richiede JDK 17, SDK platform 35, build-tools 35.0.0, Python 3, curl e sha256sum. L'APK viene creato in `build/sdk/StreamClean.apk`. La chiave locale `debug.keystore`, se presente, è esclusivamente una chiave di sviluppo (password `android`); non viene pubblicata nel repository. Lo script SDK la genera se manca; Gradle usa quella locale se presente, altrimenti quella debug predefinita. Per una release pubblica configura una tua chiave separata.

La workflow `.github/workflows/android.yml` compila e pubblica l'APK come artifact di GitHub Actions dopo un push o un avvio manuale (Run workflow). L'APK debug è adatto ai test, non è una release firmata per lo store.

## Come legge il sito

Il parser cerca `[data-page]` nell'HTML, decodifica l'attributo tramite jsoup e legge il JSON Inertia: `props.title`, `props.sliders[].label`, `props.sliders[].titles[]`. Le copertine vengono costruite da `images[].filename` per il tipo `poster`, preferendo la lingua italiana, sotto `https://cdn.<dominio-selezionato>/images/`.

Il catalogo richiede l'HTML della home, ricerca, schede, stagioni e pagine watch/iframe sul dominio selezionato, oltre alle copertine del relativo CDN. Nel catalogo gli script, i banner HTML, gli analytics e le anteprime non vengono eseguiti. Alla selezione di un video l'app legge la pagina watch e la pagina iframe senza eseguirne gli script, estrae l'iframe ufficiale vixcloud.cc e costruisce un contenitore HTML minimo per quel player. JavaScript è abilitato solo nella WebView del player, necessario ai suoi controlli e allo streaming HLS. Nessun bridge JavaScript/Java viene esposto.

HTTPS obbligatorio, verifica TLS standard, redirect limitati e consentiti solo sull'host esplicitamente previsto. Un redirect a un nuovo dominio viene rifiutato: va inserito manualmente. Nessuna scoperta automatica del dominio e nessun bypass delle challenge. I limiti di risposta sono 4 MiB per l'HTML e 3 MiB per immagine; il decoder ridimensiona le copertine e la cache bitmap è limitata a 16 MiB.

Questo riduce la superficie esposta agli annunci ma non costituisce una garanzia assoluta sulla sicurezza di server e immagini. Le richieste rivelano al sito/CDN l'indirizzo IP del dispositivo. Il catalogo cambia e un futuro cambiamento dello schema potrebbe richiedere un aggiornamento del parser.

La ricerca legge `GET /it/search?q=<testo>` e `props.titles` / `props.totalCount` dalla risposta HTML, usando lo stesso parser inerte della home. Mostra la prima pagina restituita dal sito (attualmente fino a 60 risultati); quando il totale è maggiore indica ad esempio `60 di 62`. La paginazione non è ancora implementata.

## Riproduzione

Serie: apri il titolo → scegli la stagione → tocca un episodio → premi Play nei controlli video. Film: apri il titolo → Guarda film → Play. Gli episodi privi di video sono indicati come non disponibili. Prec./Succ. navigano nella stagione corrente; per cambiare stagione usa il selettore.

Il player conserva audio, sottotitoli, qualità e controlli offerti dal sito. La riproduzione online resta nel player incorporato; il lettore nativo apre soltanto gli MP4 salvati esplicitamente. Non vengono modificati token, aggirati login o abilitati privilegi premium. I link temporanei sono richiesti nuovamente quando selezioni un video o premi Ricarica. Il supporto account non è implementato: se un contenuto richiede login viene mostrato un messaggio.

La WebView blocca nuove finestre, download, navigazioni della pagina principale, accesso file/content, permessi dispositivo e contenuti HTTP. Gli iframe sono ammessi solo sul percorso ufficiale `/embed/<id>` di vixcloud.cc. Le risorse statiche sono limitate al provider e al CDN noto. Le playlist HLS vengono inoltrate preservando gli header CORS del server: ogni playlist autorizza esclusivamente gli URL esatti delle varianti, delle tracce audio, delle chiavi e dei frammenti che dichiara. Questo supporta CDN variabili e segmenti con estensione `.html`, senza autorizzare genericamente i domini esterni. La lista viene azzerata a ogni cambio di video; analytics e script pubblicitari estranei restano esclusi. La verifica TLS resta attiva. Queste restrizioni riducono l'esposizione senza garantire l'assenza assoluta di pubblicità prodotta dal fornitore stesso. Un futuro cambio di host del player/CDN può richiedere aggiornamenti.

## Verifica

Parser verificabile tramite test JVM su fixture sintetiche senza contenuti del catalogo. Il file VALIDATION.md descrive le verifiche effettivamente eseguite nell'ambiente di sviluppo.
