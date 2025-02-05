package net.jonathangiles.tools.models.input;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class LibraryConfig {
    @JsonProperty("oldLibrary")
    private String oldLibrary;

    @JsonProperty("newLibrary")
    private String newLibrary;

    @JsonProperty("clientMappings")
    private Map<String, String> clientMappings;

    // Getters and setters
    public String getOldLibrary() {
        return oldLibrary;
    }

    public void setOldLibrary(String oldLibrary) {
        this.oldLibrary = oldLibrary;
    }

    public String getNewLibrary() {
        return newLibrary;
    }

    public void setNewLibrary(String newLibrary) {
        this.newLibrary = newLibrary;
    }

    public Map<String, String> getClientMappings() {
        return clientMappings;
    }

    public void setClientMappings(Map<String, String> clientMappings) {
        this.clientMappings = clientMappings;
    }
}