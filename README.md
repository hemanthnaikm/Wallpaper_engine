# Wallpaper Engine

Wallpaper Engine is a lightweight, Java-based desktop application designed to provide seamless control over your Windows wallpaper collection. Built with Java Swing for a responsive, modern dark-themed interface, the project allows users to organize their images into dynamic categories and trigger automated slideshows directly from their local directories. By leveraging the Java Native Access (JNA) library to interface with the Windows user32 API, the application enables instantaneous wallpaper updates without system reboots. This tool is designed for users who want a simple, efficient way to manage their desktop aesthetics through a clean, intuitive, and performant GUI.

<img width="1920" height="1020" alt="image" src="https://github.com/user-attachments/assets/5c82eba1-3620-4aa9-ad02-cf32c1c1724b" />

## How to Setup

Follow these steps to get the project running on your local machine:

1. **Prepare the Environment**: Ensure you have Java JDK 11 or higher installed on your system. You will also need an IDE like Eclipse to manage and run the project files.
2. **Clone the Source**: Download or clone the Wallpaper Engine repository files into your local development workspace.
3. **Add Dependencies**: This project requires the JNA (Java Native Access) library to communicate with the Windows OS. Download jna.jar and jna-platform.jar from a trusted source (like Maven Central) and add them to your project's Build Path/Library dependencies within Eclipse.
4. **Configure Directories**: The application is hardcoded to scan your C:\Users\[Username]\Pictures directory. Ensure this folder exists and contains sub-folders with image files (such as JPG, PNG, GIF, or WEBP) so the scanner can categorize them properly.
5. **Compile and Build**: Refresh your project in your IDE to ensure all imports are resolved and the JNA libraries are recognized. Build the project to confirm there are no syntax or dependency errors.
6. **Run the Application**: Locate Main.java in the com.wallpaperengine package, right-click it, and select Run As > Java Application. The dark-themed user interface will launch, and the scanner will automatically index your images for use.

## How It Works

The application operates through three primary service layers:

* **Directory Scanner (DirectoryScanner.java)**: Recursively crawls the ~/Pictures directory. It uses a LinkedHashMap to store image paths mapped by folder names, automatically ignoring hidden files and filtering for supported formats (JPG, PNG, GIF, WEBP).
* **Wallpaper Service (WallpaperService.java)**: This is the core engine. It utilizes the SystemParametersInfoW function from user32.dll. By sending the SPI_SETDESKWALLPAPER flag, it updates the Windows registry and triggers an immediate UI refresh for the desktop background.
* **User Interface (Main.java)**: Built with Swing and a custom WrapLayout, the UI manages user interactions. It uses an ExecutorService (thread pool) to load high-resolution image thumbnails asynchronously, ensuring the UI remains responsive even when browsing large folders.

## Conclusion

Wallpaper Engine demonstrates a practical application of Java’s file I/O capabilities and native integration features. By separating the scanning logic, native API communication, and the presentation layer, the application maintains high performance and clean code readability. This project serves as an effective utility for personal desktop customization and highlights the potential of bridging Java applications with native Windows OS functionality.
