package com.mrv.yangtools.codegen.impl.postprocessor;

/**
 * @author bartosz.michalik@amartus.com
 */
public class Rfc4080PayloadWrapper extends PayloadWrapperProcessor {
    @Override
    protected String toProperty(String path) {
        String[] split = path.split("/");
        String lastSegment = split[split.length - 1];
        split = lastSegment.split("=");
        return split[0];
    }
}
