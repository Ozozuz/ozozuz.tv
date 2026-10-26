# ozozuz.tv — Android 0.9.3

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

Il wrapper Gradle 8.13 è incluso. `tools/build-sdk.sh` è ora un ingresso compatibile
che esegue Gradle e copia l'APK in `build/sdk/StreamClean.apk`: l'integrazione
AndroidX/Google Cast richiede l'unione delle risorse e dei manifest degli AAR.
Usa `./tools/build-sdk.sh` oppure `./gradlew assembleDebug`. `debug.keystore` nella radice, se presente,
viene riutilizzato; altrimenti Gradle usa la propria chiave debug. La chiave è
esclusa da Git e dagli archivi dei sorgenti.

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


## Preferiti, stagioni e libreria offline — 0.8.0

- Preferiti in SharedPreferences `favorites_v1`: ID stabile, slug, nome, tipo, trama e filename immagine. Il CDN viene ricostruito dal dominio corrente. Stella nelle card e nella scheda, sezione dedicata in home con ricerca locale e filtri.
- `ProviderPlaylist` legge solo l'oggetto letterale `window.masterPlaylist`, senza eseguire script, e riproduce la costruzione URL del player ufficiale. `DownloadSource` recupera ogni link al momento del download, quindi i token non sono conservati nella coda.
- `SeasonDownloadPlan` salva soltanto identità e metadati degli episodi disponibili. Il servizio foreground usa un worker sequenziale, una sottocartella per stagione, gli stessi MP4/audio della singola puntata, skip dei file già accessibili e coda dei rimanenti persistente. Il download corrente ricomincia dopo un'interruzione; i completati restano. Annulla svuota la coda ma conserva i completati. Gli errori di una puntata non impediscono il tentativo sulle successive.
- `DownloadsActivity` è raggiungibile dalla home anche offline: elenca i file completati per titolo/stagione, dimensioni, stato già vista, riproduzione VideoView e rimozione dal dispositivo su conferma esplicita. URI e permessi SAF restano quelli scelti dall'utente. Non serve aprire il file manager per riprodurre o eliminare gli episodi.
- `DownloadMetadata` recupera le etichette dei download 0.7.0 dal nome del file quando i nuovi metadati non esistono. Le righe non accessibili sono indicate come tali e possono essere rimosse dall'indice.
- Nuovo logo: variante del riferimento allegato, inquadratura fino alla vita, maglia chiusa; icone legacy e adattive più logo in home. Dettagli e prompt in `assets/ICON.md`.

### Firma della build di test

La vecchia chiave di sviluppo non era inclusa nei sorgenti ed è stata rimossa dalla manutenzione dell'ambiente. La build 0.8.0-test usa una nuova chiave: la firma differisce dalla 0.7.0 installata. Per un aggiornamento che mantenga i dati è necessaria la chiave originale; non disinstallare l'app precedente per aggirare il problema se vuoi conservarne i dati locali. La nuova chiave viene conservata separatamente dai sorgenti e non va pubblicata su GitHub.


La consegna installabile è **ozozuz-tv-v0.8.0-preview.apk**: package `it.reozoz.streamclean.preview`, label `ozozuz.tv (test)`, dati separati e installazione affiancata. Lo script SDK abilita questa variante solo con `SC_PREVIEW=1`; i sorgenti normali mantengono il package originale. La preview non importa le preferenze private dell'altra installazione. La migrazione dei download 0.7 si applica all'aggiornamento del package originale con la firma corretta.

La ripresa elimina anche le tracce temporanee rimaste nei soli percorsi privati dell'app dopo un arresto del processo.

La nuova chiave di sviluppo è salvata separatamente come `ozozuz-tv-development-signing-key.jks` (alias `androiddebugkey`, password debug standard `android`). Per conservare la firma delle future preview, copiala nel progetto come `debug.keystore` prima di compilare. Non includerla in Git o nei sorgenti condivisi.

## Orizzontale forzato e Google Cast — 0.9.0

- `PlaybackRotation` richiede `SCREEN_ORIENTATION_SENSOR_LANDSCAPE` senza cambiare
  le impostazioni globali del telefono. Back ripristina l'orientamento originale.
  Le due activity di riproduzione gestiscono direttamente i cambi di orientamento.
- Online, ↻ sposta la stessa WebView in un contenitore nativo a schermo intero:
  niente nuova richiesta del player o JavaScript bridge. Back chiude prima un
  eventuale fullscreen del provider, poi quello dell'app. Offline, ↻ forza
  l'orientamento del player VideoView e funge anche da interruttore.
- SDK Cast 22.3.1, AppCompat 1.7.0 e MediaRouter stabile 1.8.0. Receiver Google
  predefinito; non è richiesta una app receiver registrata o una API key.
- CastUi offre selettore ufficiale, invio esplicito TV ▶, mini controller e
  controller espanso SDK. Le puntate online sono risolte nuovamente se manca
  uno snapshot valido; gli MP4 locali mantengono la posizione corrente.
- CastRelayService mantiene un relay HTTP temporaneo solo sul Wi-Fi IPv4 nella
  stessa sottorete del receiver. Accesso limitato al suo IP e a un token casuale
  da 256 bit. Il server non accetta URL arbitrari né percorsi di file, solo
  risorse del video selezionato. Chiusura su stop/disconnessione/distruzione.
- L'HLS viene riscritto in URL opachi locali, inclusi varianti, audio, chiavi e
  init map. Le richieste HTTPS al provider rimangono sul telefono con gli header
  necessari e la stessa allowlist per URL completo. Nessuna disabilitazione TLS
  o ampliamento dei permessi WebView. MP4 locali: GET/HEAD e byte range per seek.
- Servizio foreground mediaPlayback, notifica Interrompi e wake lock limitato.
  Il telefono deve rimanere sulla stessa rete Wi-Fi. Non c'è transcoding; codec
  e DRM devono essere supportati dal receiver. Supporto nativo Google Cast;
  AirPlay/DLNA non implementati. Pressione lunga dell'icona apre, se disponibile,
  la trasmissione schermo di Android, separata dal Cast video.
- La preview 0.9.0 usa la stessa firma e lo stesso package della preview 0.8.0.
  I relativi dati restano al loro posto durante l'aggiornamento.

## Nome definitivo — 0.9.1

- Nome nel launcher e nell'app: **ozozuz.tv**, senza suffissi test/preview.
- APK: `ozozuz-tv-v0.9.1.apk`, versionCode 14.
- Una sola variante di compilazione. L'identificatore interno resta quello
  dell'installazione 0.8/0.9 (`it.reozoz.streamclean.preview`) esclusivamente
  per conservarne i dati e consentire l'aggiornamento con la firma attuale.
  La parola preview non compare nel nome visibile o nel nome dell'APK.
- La vecchia installazione 0.7 può essere disinstallata separatamente.

## Crash di avvio Cast — 0.9.2

- Il mini controller SDK 22.3.1 configura pulsanti e stile in `onInflate`.
  Crearlo con `new MiniControllerFragment()` e una transazione programmatica
  saltava quel passaggio e portava a un NullPointerException in `onCreateView`.
- Il controller viene ora istanziato dal layout XML `cast_controls`, con un ID
  separato per il contenitore. L'inflater di AppCompat esegue `onInflate` anche
  sul percorso di ripristino del fragment.
- `StartupTest` riproduce il vecchio crash e verifica il percorso corretto con
  vere risorse/viste SDK, salvataggio e ricreazione dell'activity, avvio e resume
  della home senza Play Services. Robolectric API 26 e 35; solo il binding
  UIMediaController al ricevitore viene sostituito, senza fingere una TV reale.
- VersionCode 15, nome ozozuz.tv e firma invariata: aggiornamento diretto.

## Barra comandi e preferiti — 0.9.3

- Le icone stella/rotazione erano testo da 23 sp dentro pulsanti da 44 dp con
  24 dp di padding: il testo veniva tagliato. Ora sono ImageButton con risorse
  vettoriali, area tocco da 44 dp e grafica indipendente dalla scala dei font.
- Stella più discreta (risorsa da 20 dp), senza riquadro, visibile e utilizzabile
  nella scheda già aperta. Contorno vuoto e riempimento verde riflettono lo stato
  locale dei preferiti; stesso stile anche sulle copertine in home/ricerca.
- Indietro allineato a sinistra; rotazione solo a player aperto. Il comando
  TV ▶ compare solo con un video selezionato e un receiver collegato. Il Cast
  mantiene il selettore ufficiale. Nessuna modifica alla riproduzione.
- Selettore e download usano entrambi stagione minuscolo; spazio fra i pulsanti.
- Test UI Robolectric su API 26/35 con schermata da 320 dp: aggiunta/rimozione
  preferito dalla scheda, persistenza locale, icone e barra senza sovrapposizioni.
  Render nativo API 35 ispezionato separatamente; risposte del catalogo sintetiche.
