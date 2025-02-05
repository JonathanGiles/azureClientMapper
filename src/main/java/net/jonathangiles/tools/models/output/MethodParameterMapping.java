package net.jonathangiles.tools.models.output;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class MethodParameterMapping {
    @JsonProperty("parameters")
    private List<MethodSignature.Parameter> parameters;

    @JsonProperty("matches")
    private List<String> matches;

    public List<MethodSignature.Parameter> getParameters() {
        return parameters;
    }

    public void setParameters(List<MethodSignature.Parameter> parameters) {
        this.parameters = parameters;
    }

    public List<String> getMatches() {
        return matches;
    }

    public void setMatches(List<String> matches) {
        this.matches = matches;
    }
}