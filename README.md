# Scheda

App Android per seguire la scheda di allenamento in palestra: un esercizio alla volta, a caratteri grandi, un solo tap per serie, schermo sempre acceso durante l'allenamento.

## Installazione

1. Scarica `scheda-x.y.apk` dalla pagina **Releases** del repository, direttamente dal telefono.
2. Aprilo. Android chiederà di consentire a quell'app (browser o file manager) di **installare app sconosciute**: consentilo solo per questa installazione.
3. Richiede Android 8.0 o successivo.

L'APK è firmato con una chiave di debug: va bene per uso personale, non per il Play Store. Per aggiornare l'app basta installare il nuovo APK sopra quello vecchio. La scheda salvata resta.

## Uso

- **Home**: un pulsante per ogni giorno (Lunedì, Martedì…). Toccalo per iniziare.
- **Allenamento**: in alto vedi il gruppo muscolare e l'esercizio, poi `3 × 10 ripetizioni` oppure `3 × a cedimento`, i pallini delle serie e cosa viene dopo.
  - **SERIE FATTA**: il pulsante grande in basso. All'ultima serie passa da solo all'esercizio successivo.
  - **Annulla**: torna indietro di una serie.
  - **Salta esercizio**: passa al successivo.
  - **Esci**: torna alla home. L'allenamento resta "in corso" e lo riprendi toccando lo stesso giorno.
- Lo schermo resta acceso solo nella schermata di allenamento.
- Se l'app viene chiusa, alla riapertura riparte dall'esercizio e dalla serie dove eri.

## Modificare la scheda

Dalla home tocca **Modifica scheda**. Puoi incollare il testo dalle note oppure importare un file `.txt`. L'anteprima mostra cosa è stato riconosciuto prima di salvare.

Formato riconosciuto (puntini `•`, trattini e righe vuote vengono ignorati):

```
Monday Upper Body Focus     ← titolo: inizia un nuovo allenamento
• Lunedì                    ← riga subito dopo il titolo: nome mostrato sul pulsante
• Petto:                    ← termina con ":" = gruppo muscolare
• Panca Piana 3x10          ← esercizio: serie x ripetizioni
• Shoulder Machine 3xF      ← F = a cedimento
• Squat 4x8-10 80kg rec 90  ← intervalli e note dopo le ripetizioni sono ammessi
```

Una riga che non contiene `NxM` e non termina con `:` viene letta come titolo di un nuovo allenamento. Controlla l'anteprima.

## Compilare da sorgente

Servono JDK 17+ e l'Android SDK (platform 37).

```bash
./gradlew testDebugUnitTest assembleRelease
```

L'APK viene generato in `app/build/outputs/apk/release/app-release.apk`.

Stack: Kotlin, Jetpack Compose (`Modifier.keepScreenOn()`, Compose UI ≥ 1.9), nessuna dipendenza oltre ad AndroidX. Nessun permesso richiesto, nessuna connessione a internet.
