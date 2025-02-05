package net.jonathangiles.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import net.jonathangiles.tools.models.input.LibraryConfig;
import net.jonathangiles.tools.models.output.ClientMethodMapping;
import net.jonathangiles.tools.models.output.MethodMapping;
import net.jonathangiles.tools.models.output.MethodParameterMapping;
import net.jonathangiles.tools.models.output.MethodSignature;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {

    private static final String CONFIG_JSON = "config.json";
    private static final String MAPPINGS_FOLDER = "mappings";

    public static void main(String[] args) throws Exception {
        System.out.println("Application starting...");

        // Read configuration from config.json
        ObjectMapper mapper = new ObjectMapper();
        List<LibraryConfig> config = mapper.readValue(Paths.get(CONFIG_JSON).toFile(), new TypeReference<List<LibraryConfig>>() {});
        List<String> missingClients = new ArrayList<String>();

        // Ensure mappings folder exists
        Path mappingsFolderPath = Paths.get(MAPPINGS_FOLDER);
        if (!Files.exists(mappingsFolderPath)) {
            Files.createDirectory(mappingsFolderPath);
        }

        for (LibraryConfig libConfig : config) {
            String oldLibrary = libConfig.getOldLibrary();
            Path mappingFilePath = mappingsFolderPath.resolve(oldLibrary.replace(":", "_") + ".json");

            // Load existing mappings if they exist
            MethodMapping existingMapping = null;
            if (Files.exists(mappingFilePath)) {
                existingMapping = mapper.readValue(mappingFilePath.toFile(), MethodMapping.class);
            }

            FileSystem newLibFS = (libConfig.getNewLibrary() != null && !libConfig.getNewLibrary().isEmpty())
                    ? createInMemoryFileSystem(libConfig.getNewLibrary()) : null;

            MethodMapping newMapping = extractMethodMapping(
                    createInMemoryFileSystem(oldLibrary),
                    newLibFS,
                    libConfig.getClientMappings(),
                    missingClients
            );

            newMapping.setOldLibrary(oldLibrary);

            // Merge new mappings with existing mappings
            if (existingMapping != null) {
                mergeMappings(existingMapping, newMapping);
            } else {
                existingMapping = newMapping;
            }

            // Write the updated mapping to file
            writeJsonToFile(existingMapping, mappingFilePath);
        }

        // Output missing clients
        if (!missingClients.isEmpty()) {
            System.out.println("The following async clients were found, but a matching client in the new library was not found:");
            for (String client : missingClients) {
                System.out.println("    " + client);
            }
        }

        System.out.println("Application finished.");
    }

    private static void mergeMappings(MethodMapping existingMapping, MethodMapping newMapping) {
        for (Map.Entry<String, ClientMethodMapping> entry : newMapping.getClients().entrySet()) {
            String clientName = entry.getKey();
            ClientMethodMapping newClientMapping = entry.getValue();

            ClientMethodMapping existingClientMapping = existingMapping.getClients().get(clientName);
            if (existingClientMapping == null) {
                existingMapping.addClient(clientName, newClientMapping);
            } else {
                for (Map.Entry<String, MethodParameterMapping> methodEntry : newClientMapping.getMethods().entrySet()) {
                    String methodName = methodEntry.getKey();
                    MethodParameterMapping newMethodMapping = methodEntry.getValue();

                    MethodParameterMapping existingMethodMapping = existingClientMapping.getMethods().get(methodName);
                    if (existingMethodMapping == null) {
                        existingClientMapping.addMethod(methodName, newMethodMapping);
                    } else {
                        existingMethodMapping.getMatches().addAll(newMethodMapping.getMatches());
                        existingMethodMapping.setMatches(existingMethodMapping.getMatches().stream().distinct().collect(Collectors.toList()));
                    }
                }
            }
        }
    }

    private static FileSystem createInMemoryFileSystem(String coordinates) throws Exception {
        Path artifactPath = getArtifactPath(coordinates);

        // Ensure the artifact is downloaded
        if (!Files.exists(artifactPath)) {
            throw new IOException("Artifact not found: " + coordinates);
        }

        System.out.println("    Opening artifact from: " + artifactPath);
        return FileSystems.newFileSystem(URI.create("jar:" + artifactPath.toUri()), Collections.<String, Object>emptyMap());
    }

    private static Path getArtifactPath(String coordinates) throws IOException {
        String[] parts = coordinates.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid coordinates: " + coordinates);
        }
        String groupId = parts[0];
        String artifactId = parts[1];
        String version = parts[2];

        String artifactFileName = artifactId + "-" + version + "-sources.jar";
        String localRepoPath = System.getProperty("user.home") + "/.m2/repository";
        Path localArtifactPath = Paths.get(localRepoPath, groupId.replace('.', '/'), artifactId, version, artifactFileName);

        if (Files.exists(localArtifactPath)) {
            System.out.println("    Found artifact in local .m2 cache");
            return localArtifactPath;
        }

        System.out.println("    Failed to open artifact in local .m2 cache");
        return downloadArtifact(groupId, artifactId, version, artifactFileName);
    }

    private static Path downloadArtifact(String groupId, String artifactId, String version, String artifactFileName) throws IOException {
        String mavenCentralUrl = String.format("https://repo1.maven.org/maven2/%s/%s/%s/%s",
                groupId.replace('.', '/'), artifactId, version, artifactFileName);

        System.out.println("    Downloading artifact from Maven Central: " + mavenCentralUrl);
        URL url = new URL(mavenCentralUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IOException("Failed to download artifact from Maven Central: " + mavenCentralUrl);
        }

        Path tempFile = Files.createTempFile(artifactId + "-" + version, "-sources.jar");
        Files.copy(connection.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
        return tempFile;
    }

    private static MethodMapping extractMethodMapping(FileSystem oldFs, FileSystem newFs, Map<String, String> clientMappings, List<String> missingClients) throws IOException {
        MethodMapping methodMapping = new MethodMapping();
        JavaParser parser = new JavaParser();

        // Parse old library
        Map<String, List<MethodSignature>> oldClientMethods = new HashMap<String, List<MethodSignature>>();
        for (Path root : oldFs.getRootDirectories()) {
            Files.walk(root)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            CompilationUnit cu = parser.parse(path).getResult().orElse(null);
                            if (cu != null) {
                                cu.accept(new AsyncClientMethodCollector(), oldClientMethods);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
        }

        // Parse new library (if available)
        Map<String, List<MethodSignature>> newClientMethods = new HashMap<String, List<MethodSignature>>();
        if (newFs != null) {
            for (Path root : newFs.getRootDirectories()) {
                Files.walk(root)
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .forEach(path -> {
                            try {
                                CompilationUnit cu = parser.parse(path).getResult().orElse(null);
                                if (cu != null) {
                                    cu.accept(new SyncClientMethodCollector(), newClientMethods);
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
            }
        }

        // Compare clients and methods
        for (String oldClient : oldClientMethods.keySet()) {
            String newClient = clientMappings.getOrDefault(oldClient, oldClient.replace("AsyncClient", "Client"));
            List<MethodSignature> oldMethods = oldClientMethods.get(oldClient);
            List<MethodSignature> newMethods = newClientMethods.getOrDefault(newClient, Collections.<MethodSignature>emptyList());

            if (newMethods.isEmpty()) {
                missingClients.add(oldClient);
            }

            ClientMethodMapping clientMethodMapping = new ClientMethodMapping();
            for (MethodSignature oldMethod : oldMethods) {
                MethodParameterMapping methodParameterMapping = new MethodParameterMapping();
                methodParameterMapping.setParameters(oldMethod.getParameters());
                List<String> matches = newMethods.stream()
                        .filter(newMethod -> newMethod.getName().equals(oldMethod.getName()) && newMethod.getParameters().equals(oldMethod.getParameters()))
                        .map(MethodSignature::getSignature)
                        .collect(Collectors.toList());
                methodParameterMapping.setMatches(matches);

                clientMethodMapping.addMethod(oldMethod.getName(), methodParameterMapping);
            }
            methodMapping.addClient(oldClient, clientMethodMapping);
        }

        return methodMapping;
    }

    private static class AsyncClientMethodCollector extends VoidVisitorAdapter<Map<String, List<MethodSignature>>> {
        @Override
        public void visit(ClassOrInterfaceDeclaration cid, Map<String, List<MethodSignature>> methodMapping) {
            super.visit(cid, methodMapping);

            if (cid.getNameAsString().endsWith("AsyncClient")) {
                String className = cid.getFullyQualifiedName().orElse(cid.getNameAsString());
                List<MethodSignature> methods = new ArrayList<MethodSignature>();
                cid.getMethods().forEach(method -> methods.add(getMethodSignature(method)));
                methodMapping.put(className, methods);
            }
        }

        private MethodSignature getMethodSignature(MethodDeclaration method) {
            String name = method.getNameAsString();
            List<MethodSignature.Parameter> parameters = new ArrayList<MethodSignature.Parameter>();
            method.getParameters().forEach(param -> {
                parameters.add(new MethodSignature.Parameter(param.getType().asString(), param.getNameAsString()));
            });
            String signature = name + "(" + parameters.stream()
                    .map(p -> p.getType() + " " + p.getName())
                    .collect(Collectors.joining(", ")) + ")";
            return new MethodSignature(name, parameters, signature);
        }
    }

    private static class SyncClientMethodCollector extends VoidVisitorAdapter<Map<String, List<MethodSignature>>> {
        @Override
        public void visit(ClassOrInterfaceDeclaration cid, Map<String, List<MethodSignature>> methodMapping) {
            super.visit(cid, methodMapping);

            if (!cid.getNameAsString().endsWith("AsyncClient")) {
                String className = cid.getFullyQualifiedName().orElse(cid.getNameAsString());
                List<MethodSignature> methods = new ArrayList<MethodSignature>();
                cid.getMethods().forEach(method -> methods.add(getMethodSignature(method)));
                methodMapping.put(className, methods);
            }
        }

        private MethodSignature getMethodSignature(MethodDeclaration method) {
            String name = method.getNameAsString();
            List<MethodSignature.Parameter> parameters = new ArrayList<MethodSignature.Parameter>();
            method.getParameters().forEach(param -> {
                parameters.add(new MethodSignature.Parameter(param.getType().asString(), param.getNameAsString()));
            });
            String signature = name + "(" + parameters.stream()
                    .map(p -> p.getType() + " " + p.getName())
                    .collect(Collectors.joining(", ")) + ")";
            return new MethodSignature(name, parameters, signature);
        }
    }

    private static void writeJsonToFile(MethodMapping result, Path filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT); // Enable pretty-printing
        mapper.writeValue(filePath.toFile(), result);
    }
}