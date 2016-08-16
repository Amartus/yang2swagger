package com.mrv.yangtools.codegen.impl;

import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * @author bartosz.michalik@amartus.com
 */
public class DataNodeHelper {
    public static Iterable<SchemaNode> iterable(DataNodeContainer container) {
        return new DataNodeIterable(container);
    }
    public static Stream<SchemaNode> stream(DataNodeContainer container) {
        return StreamSupport.stream(iterable(container).spliterator(), false);
    }
}
