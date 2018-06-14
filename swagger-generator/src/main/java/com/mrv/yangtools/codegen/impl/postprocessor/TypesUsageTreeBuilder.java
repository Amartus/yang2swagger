package com.mrv.yangtools.codegen.impl.postprocessor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Type usage builder. There are two types of relations supported
 * <ul>
 *     <li>uses</li> - poitned by properties of a given definition
 *     <li>references</li> - part of composition of a hgiven definition
 * </ul>
 * @author bartosz.michalik@amartus.com
 */
class TypesUsageTreeBuilder {
    private final Logger log = LoggerFactory.getLogger(TypesUsageTreeBuilder.class);
    HashMap<String, TypeNode> types = new HashMap<>();

    public void referencing(String type, Stream<String> referencing) {
        updateReferencing(type, referencing, false);
    }

    public void using(String type, Stream<String> using) {
        final TypeNode tn = types.computeIfAbsent(type, x ->  {
            log.debug("Adding new type {}", x);
            return new TypeNode(x);
        });
        using.map(t -> types.computeIfAbsent(t, v -> {
            log.debug("Adding new type {}", v);
            return new TypeNode(v);
        })).forEach(c -> c.usedBy(tn));
    }

    public void markReferenced(String path, Stream<String> referencing) {
        updateReferencing(path, referencing, true);
    }

    private void updateReferencing(String type, Stream<String> using, boolean isRoot) {
        final TypeNode tn = types.computeIfAbsent(type, x -> new TypeNode(x, isRoot));
        using.map(t -> types.computeIfAbsent(t, v -> {
            log.debug("Adding new type {}", v);
            return new TypeNode(v);
        })).forEach(c -> c.referencedBy(tn));
    }

    public Map<String, TypeNode> build() {
        return new HashMap<>(types);
    }
}
