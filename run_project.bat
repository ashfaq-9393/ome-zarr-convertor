@echo off
setlocal
cd /d "%~dp0"

if not exist "ome-converter-ui\target\ome-converter-ui-1.0.0-SNAPSHOT.jar" (
    echo Target JAR not found. Building project with Maven first...
    call mvn.bat package -DskipTests
)

echo Starting OME-Zarr Converter (Project Source Build)...
java -jar "ome-converter-ui\target\ome-converter-ui-1.0.0-SNAPSHOT.jar" %*
