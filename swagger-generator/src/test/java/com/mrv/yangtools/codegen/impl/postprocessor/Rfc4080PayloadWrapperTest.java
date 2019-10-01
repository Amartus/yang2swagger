package com.mrv.yangtools.codegen.impl.postprocessor;

import org.junit.Test;

import static org.junit.Assert.*;


/**
 * @author bartosz.michalik@amartus.com
 */
public class Rfc4080PayloadWrapperTest extends AbstractWithSwagger {
    @Test
    public void wrapperTest() {
        Rfc4080PayloadWrapper wrapper = new Rfc4080PayloadWrapper();

        wrapper.accept(swagger);

        long numberOfWrappers = swagger.getDefinitions().keySet().stream()
                .filter(k -> k.endsWith("Wrapper"))
                .count();

        assertTrue(numberOfWrappers == swagger.getPaths().size());
    }
}