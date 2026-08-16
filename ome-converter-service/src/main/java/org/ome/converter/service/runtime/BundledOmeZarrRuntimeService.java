package org.ome.converter.service.runtime;

import org.ome.converter.core.exception.ConversionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
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

    public synchronized Path resolveDatasetPath(Path inputPath) {
        if (inputPath == null || !java.nio.file.Files.exists(inputPath)) {
            return inputPath;
        }

        // 1. Direct dataset check
        if (java.nio.file.Files.exists(inputPath.resolve(".zattrs")) || java.nio.file.Files.exists(inputPath.resolve("zarr.json"))) {
            return inputPath;
        }

        // 2. Scan child subdirectories inside container directory
        if (java.nio.file.Files.isDirectory(inputPath)) {
            try (var stream = java.nio.file.Files.list(inputPath)) {
                java.util.List<Path> candidates = stream
                    .filter(java.nio.file.Files::isDirectory)
                    .filter(p -> java.nio.file.Files.exists(p.resolve(".zattrs"))
                              || java.nio.file.Files.exists(p.resolve("zarr.json"))
                              || p.getFileName().toString().toLowerCase().endsWith(".zarr")
                              || p.getFileName().toString().toLowerCase().endsWith(".ome.zarr"))
                    .sorted((p1, p2) -> {
                        try {
                            return java.nio.file.Files.getLastModifiedTime(p2).compareTo(java.nio.file.Files.getLastModifiedTime(p1));
                        } catch (Exception e) {
                            return 0;
                        }
                    })
                    .toList();

                if (!candidates.isEmpty()) {
                    Path resolved = candidates.get(0);
                    log.info("Auto-resolved dataset subfolder '{}' from container directory '{}'", resolved.getFileName(), inputPath.getFileName());
                    return resolved;
                }
            } catch (Exception e) {
                log.warn("Failed to scan directory for Zarr datasets: {}", inputPath, e);
            }
        }

        return inputPath;
    }

    public synchronized String startValidatorServerAndGetUrl(Path datasetPath) throws ConversionException {
        if (datasetPath == null || !datasetPath.toFile().exists()) {
            throw new ConversionException("Selected OME-Zarr dataset directory was not found.");
        }

        Path resolvedPath = resolveDatasetPath(datasetPath);
        File pythonExe = resolveBundledPythonExecutable();

        Path cleanPath = resolvedPath.toAbsolutePath().normalize();
        File datasetFile = cleanPath.toFile();
        String absPath = datasetFile.getAbsolutePath();
        while (absPath.endsWith("\\") || absPath.endsWith("/")) {
            absPath = absPath.substring(0, absPath.length() - 1);
        }

        String imageName = cleanPath.getFileName().toString();
        File validatorDir = resolveValidatorDirectory();

        if (activeViewProcess != null && activeViewProcess.isAlive() && cleanPath.equals(activeDatasetPath) && activePort > 0) {
            log.info("Validator server is already running for {} on port {}.", cleanPath, activePort);
            return String.format("http://127.0.0.1:%d/validator/index.html?source=http://127.0.0.1:%d/%s", activePort, activePort, imageName);
        }

        stopValidatorProcess();

        int port = findAvailablePort(8000);
        String safeDatasetPath = absPath.replace("\\", "/");
        String safeValidatorPath = validatorDir.getAbsolutePath().replace("\\", "/");

        String pythonScript = String.format(
            "import os, sys\n" +
            "from http.server import HTTPServer, SimpleHTTPRequestHandler\n" +
            "dataset_path = r'%s'\n" +
            "validator_dir = r'%s'\n" +
            "dataset_parent, dataset_name = os.path.split(dataset_path)\n" +
            "class SameOriginValidatorHandler(SimpleHTTPRequestHandler):\n" +
            "    def end_headers(self):\n" +
            "        self.send_header('Access-Control-Allow-Origin', '*')\n" +
            "        self.send_header('Access-Control-Allow-Methods', 'GET, OPTIONS, HEAD')\n" +
            "        self.send_header('Access-Control-Allow-Headers', '*')\n" +
            "        super().end_headers()\n" +
            "    def do_OPTIONS(self):\n" +
            "        self.send_response(200, 'ok')\n" +
            "        self.end_headers()\n" +
            "    def translate_path(self, path):\n" +
            "        clean_path = path.split('?')[0]\n" +
            "        if clean_path.startswith('/validator'):\n" +
            "            rel = clean_path[len('/validator'):].lstrip('/')\n" +
            "            target = os.path.join(validator_dir, rel)\n" +
            "            if not os.path.exists(target) and rel.startswith('schemas/'):\n" +
            "                if rel.endswith('.schema') and os.path.exists(target[:-7]):\n" +
            "                    return target[:-7]\n" +
            "                elif os.path.exists(target + '.schema'):\n" +
            "                    return target + '.schema'\n" +
            "            return target\n" +
            "        else:\n" +
            "            target = os.path.join(dataset_parent, clean_path.lstrip('/'))\n" +
            "            if not os.path.exists(target):\n" +
            "                parts = clean_path.lstrip('/').split('/')\n" +
            "                if len(parts) >= 6:\n" +
            "                    fallback_parts = parts[:2] + ['0'] + parts[2:]\n" +
            "                    fallback_target = os.path.join(dataset_parent, *fallback_parts)\n" +
            "                    if os.path.exists(fallback_target):\n" +
            "                        return fallback_target\n" +
            "            return target\n" +
            "server = HTTPServer(('', %d), SameOriginValidatorHandler)\n" +
            "server.serve_forever()\n",
            safeDatasetPath,
            safeValidatorPath,
            port
        );

        ProcessBuilder pb = new ProcessBuilder(pythonExe.getAbsolutePath(), "-c", pythonScript);
        pb.directory(pythonExe.getParentFile().getParentFile());

        Map<String, String> env = pb.environment();
        env.remove("PYTHONPATH");
        env.remove("PYTHONHOME");

        log.info("Launching same-origin HTTP validator server on port {} for dataset: {}", port, absPath);

        StringBuilder errorOutput = new StringBuilder();
        try {
            activeViewProcess = pb.start();
            activePort = port;
            activeDatasetPath = cleanPath;

            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(activeViewProcess.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.info("[ome_zarr server stderr] {}", line);
                        synchronized (errorOutput) {
                            errorOutput.append(line).append("\n");
                        }
                    }
                } catch (Exception ignored) {}
            });
            stderrThread.setDaemon(true);
            stderrThread.start();

            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(activeViewProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.info("[ome_zarr server stdout] {}", line);
                    }
                } catch (Exception ignored) {}
            });
            stdoutThread.setDaemon(true);
            stdoutThread.start();

            boolean terminatedEarly = activeViewProcess.waitFor(1500, TimeUnit.MILLISECONDS);
            if (terminatedEarly && activeViewProcess.exitValue() != 0) {
                String errDetails = errorOutput.toString().trim();
                stopValidatorProcess();
                throw new ConversionException("The bundled OME-Zarr server could not be started: " + (errDetails.isEmpty() ? "Process exited with code " + activeViewProcess.exitValue() : errDetails));
            }

            return String.format("http://127.0.0.1:%d/validator/index.html?source=http://127.0.0.1:%d/%s", port, port, imageName);

        } catch (ConversionException ce) {
            throw ce;
        } catch (Exception e) {
            stopValidatorProcess();
            throw new ConversionException("The bundled OME-Zarr streaming server could not be started.", e);
        }
    }

    private File resolveValidatorDirectory() {
        try {
            File appRootDir = resolveBundledPythonExecutable().getParentFile().getParentFile();
            File distValidator = new File(appRootDir, "validator");
            if (distValidator.exists() && distValidator.isDirectory()) {
                return distValidator;
            }
        } catch (Exception ignored) {}

        File devValidator = new File("ome-converter-service/src/main/resources/validator");
        if (devValidator.exists() && devValidator.isDirectory()) {
            return devValidator;
        }

        File userHomeValidator = new File(System.getProperty("user.home"), ".ome_converter/validator");
        if (!userHomeValidator.exists()) {
            userHomeValidator.mkdirs();
            try {
                copyClasspathResourceToFile("/validator/index.html", new File(userHomeValidator, "index.html"));
                File assetsDir = new File(userHomeValidator, "assets");
                assetsDir.mkdirs();
                copyClasspathResourceToFile("/validator/assets/index.ddfebf21.js", new File(assetsDir, "index.ddfebf21.js"));
                copyClasspathResourceToFile("/validator/assets/index.5cf17a37.css", new File(assetsDir, "index.5cf17a37.css"));
            } catch (Exception e) {
                log.warn("Failed extracting bundled validator resources", e);
            }
        }
        return userHomeValidator;
    }

    private void copyClasspathResourceToFile(String resourcePath, File targetFile) {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in != null) {
                Files.copy(in, targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignored) {}
    }

    public synchronized int launchOfficialValidator(Path datasetPath) throws ConversionException {
        startValidatorServerAndGetUrl(datasetPath);
        return activePort;
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
