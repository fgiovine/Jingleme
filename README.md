# Ba-dum-tss

App Android che tiene il microfono aperto e fa partire un rullo di batteria, un applauso
o una risata di gruppo quando qualcuno chiude una battuta.

## Come si avvia

1. Apri la cartella `badumtss/` con Android Studio (Ladybug o successivo).
2. Sync di Gradle, poi Run. Non serve nessun file esterno: i jingle vengono sintetizzati
   al primo avvio e scritti in `cacheDir`.
3. Al primo tocco su VIA l'app chiede microfono e notifiche.

`minSdk 26`, `targetSdk 35`, Kotlin 2.0, Compose.

## Come decide quando suonare

Ci sono due rilevatori, combinabili dalla schermata principale.

**Pause** (`PunchlineDetector`) — Lavora solo sull'inviluppo di energia: fondo di rumore
adattivo, poi cerca la forma "qualcuno alza la voce per almeno 260 ms, poi silenzio per
N ms". È la pausa dopo la punchline, quella in cui in TV parte il rullo. Costa quasi
nulla, non richiede modelli, funziona subito. Non capisce le parole: una frase enfatica
qualsiasi la fa scattare.

**Risate** (`LaughDetector`) — Classifica il suono ambientale con YAMNet e si accende
sulle classi *laughter, giggle, chuckle, chortle, snicker, cackle*. È il segnale più
affidabile, perché la risata è acusticamente distintiva mentre la battuta no.

Il modello è **opzionale e non incluso**. Per attivarlo:

```
app/src/main/assets/yamnet.tflite
app/src/main/assets/yamnet_class_map.csv
```

Entrambi si scaricano da TensorFlow Hub / Kaggle Models (`yamnet`, versione TFLite).
Gli indici delle classi vengono letti dal CSV e non sono scritti a mano nel codice,
perché cambiano tra le versioni. Senza il modello l'app parte lo stesso e resta in
modalità pausa: te lo dice in schermata.

## I tre problemi che ti costerebbero tempo, e come sono risolti

**Rientro dell'audio.** Il jingle esce dallo speaker, il microfono lo risente, il
classificatore dice "risata", riparte. Tre difese in serie: `AudioSource.VOICE_COMMUNICATION`
(attiva la catena vocale del dispositivo), `AcousticEchoCanceler` + `NoiseSuppressor`
espliciti, e il gating in `JinglePlayer.mutedUntil` che ignora il microfono per tutta la
durata del jingle più 350 ms di coda. Sopra c'è la pausa configurabile tra due jingle.

**Latenza.** Un rullo che arriva due secondi dopo la battuta non fa ridere. I campioni
stanno decodificati in memoria in un `SoundPool` (caricarli a runtime aggiunge ~200 ms),
i frame sono da 32 ms, l'inferenza YAMNet gira ogni 256 ms su una finestra scorrevole di
0,975 s. Il thread di cattura ha priorità massima.

**Microfono in background.** Da Android 14 serve un foreground service con
`FOREGROUND_SERVICE_MICROPHONE` e la notifica persistente, avviato mentre l'activity è
visibile. È `ListenerService`.

## Sui jingle

Le laugh track delle sitcom americane sono materiale protetto: non si possono estrarre e
distribuire. Per questo i tre suoni sono generati proceduralmente in `JingleFactory`:

- **Ba-dum-tss** — due colpi di rullante (rumore in banda 300–3800 Hz più corpo a 185 Hz
  e crack a 330 Hz) e un piatto a 3–11 kHz con decadimento lungo.
- **Applausi** — circa 900 battimani sparsi con densità che sale in 350 ms e decade.
- **Risate** — dodici voci di rumore filtrato modulate a 4–7 Hz. È un segnaposto onesto,
  non una laugh track da studio.

Per sostituirli con campioni veri, metti i file in `app/src/main/assets/jingles/` con i
nomi `rimshot`, `laugh`, `applause` (`.wav`, `.ogg` o `.mp3`): hanno la precedenza sulla
sintesi. Fonti CC0 utili: Freesound, con licenza verificata file per file.

## Se finisce sul Play Store

Un'app che ascolta in continuazione richiede la dichiarazione di uso del microfono in
primo piano nella Play Console e una privacy policy. Il punto a favore è che qui l'audio
non lascia mai il dispositivo: viene analizzato frame per frame e scartato, niente
buffer persistenti né rete. Vale la pena dirlo esplicitamente nella scheda.

## Da provare, se vuoi spingerti oltre

Il rilevamento della battuta vera richiede il riconoscimento del parlato. La strada è
VAD (Silero, ~1 MB in ONNX) per segmentare la frase, poi `SpeechRecognizer` in modalità
offline con `EXTRA_PREFER_OFFLINE`, o Whisper tiny via whisper.cpp. Il budget è la pausa
naturale dopo la punchline: circa 700 ms. Stretto ma non impossibile.

## Struttura

```
core/AudioEngine.kt        cattura a 16 kHz, frame da 32 ms, AEC e NS
core/PunchlineDetector.kt  inviluppo di energia, fondo adattivo, picco poi pausa
core/LaughDetector.kt      YAMNet opzionale, indici letti dalla class map
core/JingleFactory.kt      sintesi dei tre suoni e scrittura WAV
core/JinglePlayer.kt       SoundPool precaricato e gating del microfono
core/ListenerService.kt    foreground service, orchestrazione, notifica
core/AppState.kt           stato condiviso e impostazioni persistite
ui/                        schermata Compose
```

## Ottenere l'APK

Il modo più rapido senza installare niente: fai push del progetto su un repository
GitHub. Il workflow in `.github/workflows/build.yml` compila a ogni push e pubblica
`app-debug.apk` nella scheda **Actions → il run più recente → Artifacts**. Ci vogliono
circa quattro minuti.

```bash
cd badumtss
git init && git add . && git commit -m "primo commit"
git branch -M main
git remote add origin git@github.com:<utente>/<repo>.git
git push -u origin main
```

Sul telefono l'APK è firmato con la chiave di debug, quindi serve consentire
l'installazione da sorgenti sconosciute per l'app da cui lo apri.

In alternativa, in locale: apri la cartella in Android Studio e usa
**Build → Build App Bundle(s) / APK(s) → Build APK(s)**, oppure `./gradlew assembleDebug`
dopo che Android Studio ha generato il wrapper.
