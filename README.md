# 🎓 AsimutApp

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Build](https://img.shields.io/badge/Build-Release-success.svg)](https://github.com/MrMcSnow/AsimutApp/releases)
[![Version](https://img.shields.io/badge/Version-1.3.0-orange.svg)]()

_AsimutApp_ ist eine mobile Anwendung für Studierende der **Hochschule für Musik, Theater und Medien Hannover (HMTMH)**.  
Sie bündelt alle wichtigen Hochschuldienste an einem Ort – von Asimut über LMS bis zum Deutschlandticket.

---

## DE **Hauptfunktionen**

- 🗂️ **Schneller Zugriff auf Hochschul-Dienste**
  - **Asimut** – Raum- und Stundenplanverwaltung  
  - **LMS (Moodle)** – Lernplattform und Kursinhalte  
  - **QIS** – Noten, Prüfungen, Einschreibungen  
  - **StudMail** – offizielle Studierenden-E-Mail  
  - **PaperCut** – Druckverwaltung und Kopierguthaben  
  - **HMTMH.de** – offizielle Website der Hochschule  
  > Alle Dienste werden direkt im integrierten WebView geöffnet.

- 🎟️ **Kartenverwaltung**
  - Studierendenausweis (manuelle Eingabe oder NFC-Scan)
  - **Deutschlandticket (.pkpass)** – Import, Barcode-Anzeige
  - Kartenstapel-Ansicht mit Animationen  
  - Karte als „Standardkarte“ markieren oder durch Wischen löschen  
  - Maximal 5 Karten speicherbar  

- 🔐 **Automatisches Einloggen**
  - Gespeicherte Logins für alle Hochschul-Websites  
  - Sichere Speicherung in SharedPreferences  
  - Schnelle Anmeldung ohne manuelle Eingabe  
  - Option „Zugangsdaten löschen“ im Menü

- 📂 **Dateiverwaltung**
  - Herunterladen und Hochladen von Dokumenten in **LMS / QIS / PaperCut**
  - Unterstützung des Android Storage Access Framework (SAF)

- 🌙 **Modernes Design**
  - Hell-/Dunkelmodus  
  - Material Design, CardView-Layout  
  - Animierte Kartenstapel  
  - Schwebende runde „+“-Taste zum Hinzufügen neuer Karten  

---

## 🔗 **Verknüpfte Hochschul-Portale**

| Dienst     | URL                                   |
|-------------|----------------------------------------|
| Asimut      | https://hmtm-hannover.asimut.net/      |
| LMS         | https://lms.hmtm-hannover.de/          |
| QIS         | https://qis.hmt.hispro.de/             |
| StudMail    | https://stud.hmtm-hannover.de/         |
| PaperCut    | https://papercut.hmtm-hannover.de/     |
| HMTMH.de    | https://www.hmtm-hannover.de/          |

> Alle sind im Navigationsmenü erreichbar.

---

## 🛡️ **Sicherheit**

- Passwörter werden niemals im Klartext gespeichert  
- Keine Datenweitergabe an Dritte  
- Automatisches Einfügen nur auf den autorisierten Hochschulseiten  
- Möglichkeit zum sofortigen Löschen aller gespeicherten Zugangsdaten  
- Siehe [Security Policy](./SECURITY.md)

---

## 🚀 **Installation**

1. Lade die aktuelle Version [**app-release.apk**](https://github.com/MrMcSnow/AsimutApp/releases) herunter  
2. Aktiviere auf deinem Gerät „Installation aus unbekannten Quellen“  
3. Öffne AsimutApp und melde dich mit deinen Hochschuldaten an  

---

## 🧩 **Technologien**

- **Kotlin**, **AndroidX**, **Material Components**  
- **WebView** mit Datei-Upload/Download (SAF)  
- **CardView** + **ConstraintLayout**  
- **NFC-Unterstützung** für Studierendenausweis  
- **.pkpass-Parser** für Deutschlandticket  
- Datenspeicherung über **Room** / **SharedPreferences**

---

## 👨‍🎓 **Autor**

**Serhii Lobazanov**  
Jazz & Media Student @ HMTMH  
📍 Hannover, Deutschland  
📧 mr.mcsnow.skylight@gmail.com  
🌐 [Telegram](https://t.me/Mr_McSnow) • [Instagram](https://instagram.com/mr.mcsnow)

---

# EN **English Version**

**AsimutApp** is a mobile application for students of the **Hochschule für Musik, Theater und Medien Hannover (HMTMH)**, bringing all essential academic tools together — Asimut, LMS, QIS, StudMail, PaperCut, and the university website — in one intuitive app.

---

## 📱 Main Features

- 🗂️ **University Portals Integration**
  - **Asimut** – room booking and schedule management  
  - **LMS (Moodle)** – learning platform and course access  
  - **QIS** – grades, exams, registrations  
  - **StudMail** – official student mail access  
  - **PaperCut** – printing balance and document upload  
  - **HMTMH.de** – official university website  
  > All services open directly within an in-app WebView.

- 🎟️ **Cards Section**
  - Student ID (manual entry or NFC reading)  
  - **Deutschlandticket (.pkpass)** import and barcode display  
  - Animated stacked cards layout  
  - Tap to set primary, swipe left to delete  
  - Up to 5 cards per user  

- 🔐 **Auto Login**
  - Secure credential storage and autofill  
  - Works across all connected university sites  
  - One-tap “Clear Credentials” option  

- 📂 **File Handling**
  - Supports file **downloads and uploads** in LMS / QIS / PaperCut  
  - Based on Android’s Storage Access Framework (SAF)

- 🌙 **UI / UX**
  - Modern Material 3 design  
  - Light/Dark mode support  
  - Floating circular “+” button for new cards  
  - Smooth animations and shadows

---

## 👨‍🎓 **Author**

**Serhii Lobazanov**  
Jazz & Media Student @ HMTMH  
📍 Hannover, Germany  
📧 mr.mcsnow.skylight@gmail.com  
🌐 [Telegram](https://t.me/Mr_McSnow) • [Instagram](https://instagram.com/mr.mcsnow)
