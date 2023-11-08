package org.bitpunk.m3uedit.controller;

import org.bitpunk.m3uedit.model.DataModel;

public interface ModelAware {
    void initModel(DataModel dataModel);
}