# Olympus VSI & OIR to OME-Zarr Converter (VSI Studio Pro)

Enterprise modular application for converting Olympus VSI and OIR microscopy image files to OME-Zarr (v0.4 / v0.5 with Zarr v3) with raw metadata preservation, gap analysis, and interactive NGFF validation.

---

## Quick Start (No External Dependencies Required)

The project includes a **fully self-contained, pre-packaged distribution**. You do not need to install Java, Maven, or Python to run the desktop application:

- **Launch via script:** Double-click or run [`run_app.bat`](file:///run_app.bat) in the root folder.
- **Direct executable:** Run [`dist/output/VSIStudioPro/VSIStudioPro.exe`](file:///dist/output/VSIStudioPro/VSIStudioPro.exe).
- **With console logs:** Run [`run_app_console.bat`](file:///run_app_console.bat) to view real-time stdout/stderr conversion logs in a terminal window.

---

## Project Architecture & Pre-Bundled Components

The application is structured to run isolated without polluting the host environment:

| Component | Location | Details |
| :--- | :--- | :--- |
| **Desktop Application** | `dist/output/VSIStudioPro/VSIStudioPro.exe` | Native Windows executable (jpackage) |
| **JavaFX Application Jar** | `dist/output/VSIStudioPro/app/` | Shaded executable JAR with Bio-Formats & JavaFX |
| **Isolated Java Runtime** | `dist/output/VSIStudioPro/runtime/` | Bundled OpenJDK 21 runtime |
| **Isolated Python Runtime** | `ome-zarr-runtime/` & `dist/output/VSIStudioPro/ome-zarr-runtime/` | Embedded Python 3.12 with `ome-zarr==0.9.0`, `zarr==2.18.2`, `numpy==1.26.4`, `numcodecs==0.12.1` |
| **NGFF Web Validator** | `dist/output/VSIStudioPro/validator/` | Local HTML5/JS validator embedded in the UI |

---

## Python Runtime Setup (Optional / Maintenance)

The embedded Python environment is already pre-installed. If you ever need to reconstruct or re-bundle it from scratch:

```powershell
.\setup_bundled_python.ps1
```

This script extracts `python-3.12.8-embed-amd64.zip`, configures `python312._pth`, installs pip via `get-pip.py`, and pins the required OME-Zarr dependencies.

---

## Running the Project from Source (Development Mode)

You can run the project directly from its source code using either of the following methods:

### Option 1: Convenient One-Click Runner
Double-click or run [`run_project.bat`](file:///run_project.bat) from the project root:
```cmd
run_project.bat
```
*(If the JAR has not been built yet, this script will automatically compile and package the project first.)*

### Option 2: Run directly via Maven JavaFX Plugin
Install the reactor modules once, then run the UI module:
```cmd
mvn.bat install -DskipTests
mvn.bat javafx:run -pl ome-converter-ui
```
The install step is also required again after changing APIs in a sibling module. With the Maven wrapper, use `.\mvnw.cmd` for both commands.

### Option 3: Run the Compiled Shaded JAR
```cmd
java -jar ome-converter-ui\target\ome-converter-ui-1.0.0-SNAPSHOT.jar
```

---

## Building & Testing from Source

1. **Java JDK 21**: `JAVA_HOME` is configured at `C:\Users\30182339\AppData\Local\Programs\Microsoft\jdk-21.0.12.101-hotspot`.
2. **Maven**: Apache Maven 3.9.9 is installed and configured in user PATH, with local `mvn.bat` and `mvnw.cmd` wrappers in the project root.
3. **Compile all modules**:
   ```cmd
   mvn.bat compile
   ```
4. **Run all automated tests**:
   ```cmd
   mvn.bat test
   ```
5. **Package shaded JAR**:
   ```cmd
   mvn.bat package -DskipTests
   ```

