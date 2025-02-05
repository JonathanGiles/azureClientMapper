package net.jonathangiles.tools.models.output;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class MethodSignature {
    @JsonProperty("name")
    private final String name;

    @JsonProperty("parameters")
    private final List<Parameter> parameters;

    @JsonProperty("signature")
    private final String signature;

    public MethodSignature(String name, List<Parameter> parameters, String signature) {
        this.name = name;
        this.parameters = parameters;
        this.signature = signature;
    }

    public String getName() {
        return name;
    }

    public List<Parameter> getParameters() {
        return parameters;
    }

    public String getSignature() {
        return signature;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodSignature that = (MethodSignature) o;
        return name.equals(that.name) && parameters.equals(that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, parameters);
    }

    public static class Parameter {
        @JsonProperty("type")
        private String type;

        @JsonProperty("name")
        private String name;

        // Default constructor needed for Jackson deserialization
        public Parameter() {
        }

        public Parameter(String type, String name) {
            this.type = type;
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Parameter parameter = (Parameter) o;
            return type.equals(parameter.type) && name.equals(parameter.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, name);
        }
    }

    public List<String> getParametersAsJson() {
        return parameters.stream()
                .map(p -> p.getType() + " " + p.getName())
                .collect(Collectors.toList());
    }
}