package controllers;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import annotations.Bind;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Controller {
    private final String modelName;
    private final Map<String, Object> variables;

    public Controller(String modelName) {
        this.modelName = modelName;
        this.variables = new LinkedHashMap<>();
    }

    public Controller readDataFrom(String fileName) {
        List<String> lines;
        try {
            lines = Files.readAllLines(Paths.get(fileName));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        int yearsIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("LATA")) {
                yearsIndex = i;
                break;
            }
        }
        if (yearsIndex == -1) {
            throw new IllegalArgumentException("Missing 'LATA' line in the data file.");
        }
        String[] years = lines.get(yearsIndex).trim().split("\\s+");
        int LL = years.length > 1 ? years.length - 1 : 0;
        variables.put("LL", LL);
        for (int i = yearsIndex ; i < lines.size(); i++) {
            String[] parts = lines.get(i).trim().split("\\s+");
            if (parts.length <= 1) {
                throw new IllegalArgumentException("Missing values for a variable.");
            }
            String variableName = parts[0];
            int numValues = parts.length - 1;
            if(i == yearsIndex){
                int[] values1 = new int[LL];
                for (int j = 1; j <= LL; j++) {
                    values1[j - 1] = Integer.parseInt(years[j]);
                }
                variables.put(variableName, values1);
            }
            else {
                double[] values = new double[LL];

                for (int j = 1; j <= LL; j++) {
                    if (j > numValues) {
                        values[j - 1] = Double.parseDouble(parts[numValues]);
                    } else values[j - 1] = Double.parseDouble(parts[j]);
                }
                variables.put(variableName, values);
            }
        }

        return this;
    }

    public Controller runModel() {
        try {
            Class<?> modelClass = Class.forName(modelName);
            Object modelInstance = modelClass.getDeclaredConstructor().newInstance();

            for (java.lang.reflect.Field field : modelClass.getDeclaredFields()) {
                if (field.isAnnotationPresent(Bind.class)) {
                    field.setAccessible(true);
                    String fieldName = field.getName();
                    if (variables.containsKey(fieldName)) {
                        field.set(modelInstance, variables.get(fieldName));
                    }
                }
            }

            java.lang.reflect.Field llField = modelClass.getDeclaredField("LL");
            llField.setAccessible(true);
            llField.set(modelInstance, variables.get("LL"));

            modelClass.getDeclaredMethod("run").invoke(modelInstance);

            for (java.lang.reflect.Field field : modelClass.getDeclaredFields()) {
                if (field.isAnnotationPresent(Bind.class)) {
                    field.setAccessible(true);
                    String fieldName = field.getName();
                    variables.put(fieldName, field.get(modelInstance));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return this;
    }


    public void runScriptFromFile(String fileName) {
        String script;
        try {
           script = new String(Files.readAllBytes(Paths.get(fileName)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        runScript(script);
    }
    public void runScript(String script) {
        Binding binding = new Binding(variables);
        GroovyShell shell = new GroovyShell(binding);
        shell.evaluate(script);
        Map<String, Object> updatedVariables = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String variableName = entry.getKey();
            Object value = binding.getVariable(variableName);
            updatedVariables.put(variableName, value);
        }
        variables.putAll(updatedVariables);
    }

    public String getResultsAsTsv() {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String variableName = entry.getKey();
            Object value = entry.getValue();
            if(!variableName.equals("LL") && !(variableName.length() <= 1)) {
                result.append(variableName).append("\t");

                if (value instanceof double[] values) {
                    for (double v : values) {
                        result.append(v).append("\t");
                    }
                } else if (value instanceof int[] values) {
                    for (int v : values) {
                        result.append(v).append("\t");
                    }
                } else {
                    result.append(value).append("\t");
                }
                result.append("\n");
            }
        }
        return result.toString();
    }
}