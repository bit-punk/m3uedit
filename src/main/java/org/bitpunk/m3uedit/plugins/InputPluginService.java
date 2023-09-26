package org.bitpunk.m3uedit.plugins;

import org.bitpunk.m3uedit.plugins.input.Input;

import java.util.*;

public class InputPluginService {
    private static InputPluginService inputPluginService;
    private final ServiceLoader<Input> loader;


    private InputPluginService() {
        this.loader = ServiceLoader.load(Input.class);
    }

    public static synchronized InputPluginService getInstance() {
        if (inputPluginService == null) {
            inputPluginService = new InputPluginService();
        }
        return inputPluginService;
    }

    public Optional<String> getInputDescription(String name) {
        String inputDescription = null;
        Iterator<Input> inputIterator = loader.iterator();
        while (inputDescription == null && inputIterator.hasNext()) {
            Input currentInput = inputIterator.next();
            inputDescription = currentInput.getDescription();
        }
        return Optional.ofNullable(inputDescription);
    }

    public List<String> getInputNames() {
        List<String> filterNames = new ArrayList<>();
        for (Input currentInput : loader) {
            filterNames.add(currentInput.getName());
        }
        return filterNames;
    }

    public Input getInput(String selectedInputName) {
        Input input = null;
        for (Input currentInput : loader) {
            if (selectedInputName.equals(currentInput.getName())) {
                input = currentInput;
            }
        }
        return input;
    }
}
