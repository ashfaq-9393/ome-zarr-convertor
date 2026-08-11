package org.ome.converter.service.runtime;

import org.ome.converter.core.exception.ConversionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class BundledOmeZarrRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(BundledOmeZarrRuntimeService.class);
    private static final BundledOmeZarrRuntimeService INSTANCE = new BundledOmeZarrRuntimeService();

    private Process activeViewProcess;
    private int activePort = -1;
    private Path activeDatasetPath = null;

    public static BundledOmeZarrRuntimeService getInstance() {
        return INSTANCE;
    }

    public BundledOmeZarrRuntimeService() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopValidatorProcess));
    }

    public synchronized File resolveBundledPythonExecutable() throws ConversionException {
        String userDir = System.getProperty("user.dir", ".");
        Path[] searchPaths = new Path[]{
            Paths.get(userDir, "ome-zarr-runtime", "python.exe"),
            Paths.get(userDir, "..", "ome-zarr-runtime", "python.exe"),
            Paths.get(System.getProperty("app.home", userDir), "ome-zarr-runtime", "python.exe")
        };

        for (Path p : searchPaths) {
            File file = p.toFile();
            if (file.exists() && file.isFile()) {
                log.info("Resolved bundled Python runtime executable at: {}", file.getAbsolutePath());
                return file;
            }
        }

        log.error("Bundled Python runtime missing. Searched paths: {}", (Object) searchPaths);
        throw new ConversionException("OME-Zarr runtime is missing.");
    }

    public synchronized int launchOfficialValidator(Path datasetPath) throws ConversionException {
        if (datasetPath == null || !datasetPath.toFile().exists()) {
            throw new ConversionException("Selected OME-Zarr dataset was not found.");
        }

        File pythonExe = resolveBundledPythonExecutable();

        // Normalize dataset path (strip trailing slashes/backslashes so os.path.split works in Python)
        Path cleanPath = datasetPath.toAbsolutePath().normalize();
        File datasetFile = cleanPath.toFile();
        String absPath = datasetFile.getAbsolutePath();
        while (absPath.endsWith("\\") || absPath.endsWith("/")) {
            absPath = absPath.substring(0, absPath.length() - 1);
        }

        String imageName = cleanPath.getFileName().toString();

        // If the same dataset is already running, just re-open the browser
        if (activeViewProcess != null && activeViewProcess.isAlive() && cleanPath.equals(activeDatasetPath) && activePort > 0) {
            log.info("Validator server is already running for {} on port {}. Opening browser...", cleanPath, activePort);
            openBrowserForDataset(activePort, imageName);
            return activePort;
        }

        // Stop any previously running process
        stopValidatorProcess();

        int port = findAvailablePort(8000);
        String pythonScript = String.format(
            "from ome_zarr.cli import main; import sys; sys.argv=['ome_zarr', 'view', '--port', '%d', r'%s']; main()",
            port,
            absPath.replace("'", "\\'")
        );

        ProcessBuilder pb = new ProcessBuilder(pythonExe.getAbsolutePath(), "-c", pythonScript);
        pb.directory(pythonExe.getParentFile().getParentFile()); // Root app directory

        Map<String, String> env = pb.environment();
        env.remove("PYTHONPATH");
        env.remove("PYTHONHOME");

        log.info("Launching official ome_zarr view CLI on port {} for dataset: {}", port, absPath);

        StringBuilder errorOutput = new StringBuilder();
        try {
            activeViewProcess = pb.start();
            activePort = port;
            activeDatasetPath = cleanPath;

            // Stream stderr asynchronously to capture launch failures
            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(activeViewProcess.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.info("[ome_zarr CLI stderr] {}", line);
                        synchronized (errorOutput) {
                            errorOutput.append(line).append("\n");
                        }
                    }
                } catch (Exception ignored) {}
            });
            stderrThread.setDaemon(true);
            stderrThread.start();

            // Stream stdout asynchronously
            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(activeViewProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.info("[ome_zarr CLI stdout] {}", line);
                    }
                } catch (Exception ignored) {}
            });
            stdoutThread.setDaemon(true);
            stdoutThread.start();

            // Give process 1.5s to start or report error
            boolean terminatedEarly = activeViewProcess.waitFor(1500, TimeUnit.MILLISECONDS);
            if (terminatedEarly && activeViewProcess.exitValue() != 0) {
                String errDetails = errorOutput.toString().trim();
                stopValidatorProcess();
                throw new ConversionException("The bundled OME-Zarr tools could not be started: " + (errDetails.isEmpty() ? "Process exited with code " + activeViewProcess.exitValue() : errDetails));
            }

            // Open user's default browser with full dataset image path parameter
            openBrowserForDataset(port, imageName);
            return port;

        } catch (ConversionException ce) {
            throw ce;
        } catch (Exception e) {
            stopValidatorProcess();
            throw new ConversionException("The bundled OME-Zarr tools could not be started.", e);
        }
    }

    public synchronized void stopValidatorProcess() {
        if (activeViewProcess != null) {
            try {
                if (activeViewProcess.isAlive()) {
                    log.info("Terminating active ome_zarr view process...");
                    activeViewProcess.destroy();
                    if (!activeViewProcess.waitFor(2, TimeUnit.SECONDS)) {
                        activeViewProcess.destroyForcibly();
                    }
                }
            } catch (Exception e) {
                log.warn("Exception shutting down ome_zarr view process", e);
            } finally {
                activeViewProcess = null;
                activePort = -1;
                activeDatasetPath = null;
            }
        }
    }

    private void openBrowserForDataset(int port, String imageName) {
        String targetUrl = String.format("https://ome.github.io/ome-ngff-validator/?source=http://localhost:%d/%s", port, imageName);
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(targetUrl));
                log.info("Opened default system browser to URL: {}", targetUrl);
            } else {
                log.warn("Desktop browse action not supported. Running OS fallback to open URL: {}", targetUrl);
                String os = System.getProperty("os.name", "").toLowerCase();
                if (os.contains("win")) {
                    new ProcessBuilder("cmd", "/c", "start", targetUrl).start();
                } else if (os.contains("mac")) {
                    new ProcessBuilder("open", targetUrl).start();
                } else {
                    new ProcessBuilder("xdg-open", targetUrl).start();
                }
            }
        } catch (Exception e) {
            log.error("Failed to open default system web browser for URL: {}", targetUrl, e);
        }
    }

    private int findAvailablePort(int startPort) {
        for (int port = startPort; port < startPort + 100; port++) {
            try (ServerSocket socket = new ServerSocket(port)) {
                return port;
            } catch (Exception ignored) {}
        }
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (Exception e) {
            return startPort;
        }
    }
}
