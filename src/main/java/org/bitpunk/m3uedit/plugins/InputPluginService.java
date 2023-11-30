package org.bitpunk.m3uedit.plugins;

import org.bitpunk.m3uedit.plugins.input.Input;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public class InputPluginService implements PluginService {
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

    @Override
    public List<? extends Plugin> getAllPlugins() {
        List<Input> inputPlugins = new ArrayList<>();
        for (Input currentInput : loader) {
            inputPlugins.add(currentInput);
        }
        return inputPlugins;
    }

    @Override
    public List<String> getAllPluginNames() {
        List<String> inputNames = new ArrayList<>();
        for (Input currentInput : loader) {
            inputNames.add(currentInput.getName());
        }
        return inputNames;
    }

    @Override
    public Input getPlugin(String selectedInputName) {
        Input input = null;
        for (Input currentInput : loader) {
            if (selectedInputName.equals(currentInput.getName())) {
                input = currentInput;
            }
        }
        return input;
    }
}
