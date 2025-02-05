package net.jonathangiles.tools.models.output;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.TreeMap;

public class ClientMethodMapping {
    @JsonProperty("methods")
    private Map<String, MethodParameterMapping> methods = new TreeMap<>();

//    public void setMethods(Map<String, MethodParameterMapping> methods) {
//        this.methods = new TreeMap<>(methods);
//    }

    public void addMethod(String methodName, MethodParameterMapping methodParameterMapping) {
        methods.put(methodName, methodParameterMapping);
    }

    public Map<String, MethodParameterMapping> getMethods() {
        return methods;
    }
}