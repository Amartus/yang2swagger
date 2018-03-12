/*
 * Copyright (c) 2018 Amartus. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Bartosz Michalik <bartosz.michalik@amartus.com>
 */
package com.mrv.yangtools.codegen.impl.postprocessor;

import io.swagger.models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.mrv.yangtools.codegen.impl.postprocessor.SwaggerRefHelper.*;

/**
 * @author bartosz.michalik@amartus.com
 */
public class RemoveUnusedDefinitions implements Consumer<Swagger> {
    private final Logger log = LoggerFactory.getLogger(RemoveUnusedDefinitions.class);
    @Override
    public void accept(Swagger swagger) {
        int initial = swagger.getDefinitions().size();
        Pruner pruner = new Pruner(buildHierarchy(swagger));
        while(pruner.hasPruneable()) {
            Stream<String> prune = pruner.prune();
            Map<String, Model> defs = swagger.getDefinitions();
            prune.forEach(type -> {
                log.info("Removing unused type {}", type);
                defs.remove(type);
            });
            swagger.setDefinitions(defs);
        }
        int afterPruning = swagger.getDefinitions().size();
        log.debug("Pruned {} of {} definitions.", initial-afterPruning, initial);
    }

    private Map<String, TypeNode> buildHierarchy(Swagger swagger) {
        TypesUsageTreeBuilder typesUsageTreeBuilder = new TypesUsageTreeBuilder();

        swagger.getDefinitions().forEach((type, value) -> {
            Stream<String> using = getUses(type, value);
            Stream<String> references = getReferences(type, value);
            typesUsageTreeBuilder.referencing(type, references);
            typesUsageTreeBuilder.using(type, using);
        });
        swagger.getPaths().forEach((path, value) -> {
            Stream<String> using = getReferencing(path, value);
            typesUsageTreeBuilder.markReferenced(path, using);
        });

        return typesUsageTreeBuilder.build();
    }

    private Stream<String> getReferencing(String id, Path path) {
        log.debug("Getting references for path {}", id);
        return path.getOperations().stream().flatMap(o -> {
            String bodyRef = getFromBody(o);
            if(bodyRef != null) {
                return Stream.concat(Stream.of(bodyRef), getFromResponses(o));
            }
            return getFromResponses(o);
        });
    }


    private class Pruner {
        private final Map<String, TypeNode> types;
        private Set<String> toProne;

        private Pruner(Map<String, TypeNode> types) {
            this.types = types;
        }

        public boolean hasPruneable() {
            toProne = types.values().stream()
                    .filter(t -> !t.isUsed()).map(t -> {
                        log.debug("Unused type: {}", t.type);
                        return t.type;
                    }).collect(Collectors.toSet());

            return !toProne.isEmpty();
        }

        public Stream<String> prune() {
            toProne.forEach(t -> {
                TypeNode typeNode = types.get(t);
                typeNode.removingType();
                types.remove(t);
            });
            return toProne.stream();
        }
    }

}


