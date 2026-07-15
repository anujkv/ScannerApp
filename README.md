# ScannerApp

A high-performance Android application for professional document scanning, AI-powered processing, and advanced document management.

## Key Features

- **Smart Document Scanning**: High-quality scanning with automatic edge detection, perspective correction, and multi-page support via Google ML Kit.
- **Advanced Interactive Watermarking**:
    - **Precise Positioning**: Drag and drop watermark text to any position on the document.
    - **Full Control**: Adjust rotation (-180° to 180°), size (50% to 300%), and color (7 main professional colors).
    - **Tiled Pattern**: Option to repeat the watermark across the entire page in a 3x6 grid for maximum security.
    - **Seamless Integration**: Watermarks are permanently merged into exported JPEG images and generated PDFs.
- **Media Player Style Background Speech**:
    - **Foreground Service**: Listen to your documents while the app is in the background or minimized.
    - **Notification Controls**: Pause, Resume, or Stop reading directly from the notification tray.
    - **Smart Redirection**: Tap the notification to jump back exactly to the home session or document details page you were viewing.
    - **Batch Processing**: Optimized page-by-page extraction; the app starts reading the first page instantly while processing subsequent pages in the background.
- **Gemini AI Integration**:
    - **AI Visual Scan**: Deep text extraction including handwritten notes using Gemini 1.5 Flash.
    - **AI Voice Edits**: Apply complex edits to extracted text using natural language voice commands.
- **Enhanced UX**:
    - **Global Multi-Page Zoom**: Smooth pinch-to-zoom across the entire scrollable document list.
    - **Contextual UI**: Action buttons automatically hide during zoom to provide a distraction-free, full-screen view.
    - **Image Compression**: One-tap size reduction (approx. 60%) to save storage space.
- **Document Management**: 
    - Full persistent history with file size tracking.
    - **Advanced Organization**: Rename documents for better categorization.
    - **Quick Preview**: Explicit preview actions alongside tap-to-view.
    - Native Android Print Framework support.
    - Secure file sharing via `FileProvider`.

## Architecture

The application is built using modern Android development patterns:

- **State-Driven Architecture**: The project uses a single entry point (`MainActivity`) to host all screens. Navigation and UI state are managed using Jetpack Compose `remember` and `mutableStateOf` patterns, providing smooth transitions and unified resource management.
- **Clean Separation**:
    - **Logic Layer**: Data persistence and AI logic handled by Room Database and ML Kit/Gemini engines.
    - **UI Layer**: Declarative screens built with Jetpack Compose components.
    - **Reactive Updates**: Uses Coroutines and Flows for efficient background processing and UI updates.

## Technologies Used

- **Kotlin**: Core language.
- **Jetpack Compose**: Declarative UI with advanced animations and gesture handling.
- **Google Gemini AI**: Generative AI SDK for advanced document intelligence.
- **ML Kit**: Document Scanner and Text Recognition (OCR) APIs.
- **Room Database**: Local metadata and history persistence.
- **Coil**: Responsive image loading and previewing.
- **Android Service Framework**: Foreground services for reliable background task execution.

## Prerequisites

- Android Studio Ladybug or newer.
- Android device or emulator running API level 24 (Android 7.0) or higher.
- Google Play Services installed on the device.
- Gemini API Key (configured in `MainActivity.kt`).

## Getting Started

1.  **Clone the repository**:
    ```bash
    git clone https://github.com/anujkv/ScannerApp.git
    ```
2.  **Open in Android Studio**:
    Launch Android Studio and select "Open" to navigate to the project folder.
3.  **Build and Run**:
    Connect an Android device and click the "Run" button in Android Studio.

## Project Structure

- `MainActivity.kt`: Main UI coordinator for scanning, history, and document interaction.
- `service/ScannerTTSService.kt`: Handles background speech synthesis and OCR processing.
- `ui/screens/`: Contains modular screen implementations (`HomeScreen`, `HistoryScreen`, `DocumentDetailsScreen`).
- `data/`: Room database layer (`ScannedDocument.kt`, `DocumentDao.kt`, `AppDatabase.kt`).
- `ui/theme/`: Material 3 design system implementation.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
