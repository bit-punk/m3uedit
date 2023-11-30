package org.bitpunk.m3uedit.plugins;

import org.bitpunk.m3uedit.plugins.output.Output;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public class OutputPluginService implements PluginService {
    private static OutputPluginService outputPluginService;
    private final ServiceLoader<Output> loader;

    private OutputPluginService() {
        this.loader = ServiceLoader.load(Output.class);
    }

    public static synchronized OutputPluginService getInstance() {
        if (outputPluginService == null) {
            outputPluginService = new OutputPluginService();
        }
        return outputPluginService;
    }

    @Override
    public List<? extends Plugin> getAllPlugins() {
        List<Output> outputPlugins = new ArrayList<>();
        for (Output currentOutput : loader) {
            outputPlugins.add(currentOutput);
        }
        return outputPlugins;
    }

    @Override
    public List<String> getAllPluginNames() {
        List<String> outputNames = new ArrayList<>();
        for (Output currentOutput : loader) {
            outputNames.add(currentOutput.getName());
        }
        return outputNames;
    }

}