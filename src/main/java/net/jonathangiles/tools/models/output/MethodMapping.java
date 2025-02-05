package net.jonathangiles.tools.models.output;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.TreeMap;

public class MethodMapping {
    @JsonProperty("oldLibrary")
    private String oldLibrary;

    // Maps from async client names to the method mappings for that client
    @JsonProperty("clients")
    private Map<String, ClientMethodMapping> clients = new TreeMap<>();

    public String getOldLibrary() {
        return oldLibrary;
    }

    public void setOldLibrary(String oldLibrary) {
        this.oldLibrary = oldLibrary;
    }

    public Map<String, ClientMethodMapping> getClients() {
        return clients;
    }

    public void addClient(String clientName, ClientMethodMapping clientMethodMapping) {
        clients.put(clientName, clientMethodMapping);
    }
}