# ScannerApp

A simple and efficient Android application for scanning documents using Google ML Kit's Document Scanner API.

## Features

- **Document Scanning**: High-quality document scanning with automatic edge detection and perspective correction.
- **Global Multi-Page Zoom**: Smooth pinch-to-zoom and panning across the entire document session for detailed inspection.
- **Animated Contextual UI**: Action buttons automatically animate and hide during zoom to provide a distraction-free, full-screen viewing experience.
- **AI-Powered Text Extraction**: Extract text from images using Google's Gemini AI, including support for handwritten text.
- **Voice Commands**: Apply AI edits and formatting to extracted text using natural language voice commands.
- **Text-to-Speech (TTS)**: Read extracted text aloud for accessibility and convenience.
- **Image Compression**: Reduce document size with built-in compression to save storage space.
- **File Size Tracking**: View the total size of your scanned documents directly in the history list.
- **Document History**: Save and manage your scanned documents with persistent storage.
- **Multiple Formats**: Support for both JPEG (images) and PDF output.
- **Multi-page Scanning**: Scan multiple pages into a single session.
- **Image Preview**: View scanned pages in a scrollable list.
- **Sharing**: Easily share scanned images or the generated PDF with other applications.
- **Printing**: Direct printing support for scanned documents.
- **Gallery Import**: Option to import existing images from the gallery for processing.
- **Confirmation Dialogs**: Safety features like exit confirmation and delete confirmation with "Never ask again" option.

## Technologies Used

- **Kotlin**: Primary programming language.
- **Jetpack Compose**: Modern UI toolkit for building the native interface.
- **Google Gemini AI**: Integration via Generative AI SDK for advanced text extraction and processing.
- **ML Kit Document Scanner**: Google's powerful document scanning library.
- **ML Kit Text Recognition**: For on-device OCR capabilities.
- **Speech Services**: Android Speech-to-Text (RecognizerIntent) and Text-to-Speech (TTS) integration.
- **Room Persistence Library**: Local database for saving document history and metadata.
- **Coil**: Image loading library for Compose.
- **Android Print Framework**: For document printing capabilities.
- **FileProvider**: Securely sharing files between apps.
- **Coroutines & Flow**: For asynchronous operations and reactive database updates.

## Prerequisites

- Android Studio Ladybug or newer.
- Android device or emulator running API level 24 (Android 7.0) or higher.
- Google Play Services installed on the device (required for ML Kit).

## Getting Started

1.  **Clone the repository**:
    ```bash
    git clone https://github.com/your-username/ScannerApp.git
    ```
2.  **Open in Android Studio**:
    Launch Android Studio and select "Open" to navigate to the project folder.
3.  **Build and Run**:
    Connect an Android device and click the "Run" button in Android Studio.

## Project Structure

- `MainActivity.kt`: Main UI and logic handling for scanning, history, and document details.
- `data/`: Contains Room database configuration, DAOs, and Entity models (`ScannedDocument.kt`, `DocumentDao.kt`, `AppDatabase.kt`).
- `ui/theme/`: Compose theme and styling definitions.
- `res/xml/file_paths.xml`: Configuration for `FileProvider` to enable secure file sharing.
- `build.gradle.kts`: Project dependencies and configuration.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
