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
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.mrv.yangtools.codegen.impl.postprocessor.SwaggerRefHelper.*;

/**
 * Leave only data pathMatchers to given type hierarchies
 * or prune referencing path definitions.
 * If nothing is specified swagger model remains intact.
 * @author bartosz.michalik@amartus.com
 */
public class PathPrunner implements Consumer<Swagger> {

    private final Logger log = LoggerFactory.getLogger(PathPrunner.class);

    private final List<String> exclude;
    private Set<String> types;
    private Set<String> pathMatchers;

    /**
     * @param excludePrefixes excluded from analysis
     */
    public PathPrunner(String... excludePrefixes) {
        this.exclude = Arrays.asList(excludePrefixes);
        this.types = new HashSet<>();
        this.pathMatchers = new HashSet<>();
    }

    private Predicate<String> startsWith(Collection<String> prefixes) {
        return candidate -> prefixes.stream().anyMatch(candidate::startsWith);
    }

    /**
     * Keep path referencing to type
     * @param type type referenced by path operations
     * @return this
     */
    public PathPrunner withType(String type) {
        types.add(type);
        return this;
    }

    /**
     * Add path to be pruned. All path starting with parameter are pruned
     * @param startingWith prefix for path
     * @return this
     */
    public PathPrunner prunePath(String startingWith) {
        pathMatchers.add(startingWith);
        return this;
    }

    @Override
    public void accept(Swagger swagger) {
        if(swagger.getPaths() == null) return;
        prunePaths(swagger);

        if(swagger.getDefinitions() == null || types.isEmpty()) return;
        new TypePruner(swagger).prune();
    }

    private static Stream<String> inheritenceStructure(String type, Map<String, TypeNode> hierarchy) {
        return Stream.concat(Stream.of(type),
            hierarchy.get(type).getReferencing().stream().flatMap(tn -> inheritenceStructure(tn.type, hierarchy)));
    }

    private class TypePruner {
        private final Swagger swagger;
        private final Predicate<String> excludedPaths;
        private final Map<String, TypeNode> hierarchy;

        private TypePruner(Swagger swagger) {
            this.swagger = swagger;
            excludedPaths = startsWith(exclude);
            hierarchy = buildTypeHierarchy(swagger);
        }

        void prune() {
            final Set<String> toRemove = swagger.getPaths().entrySet().stream()
                    .filter(e -> !excludedPaths.test(e.getKey()))
                    .filter(e -> removeByType(e.getKey(), e.getValue()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            swagger.setPaths(swagger.getPaths().entrySet().stream().filter(p -> {
                boolean remove = toRemove.stream().anyMatch(tR -> p.getKey().startsWith(tR));
                if(remove) log.debug("Removing path based on type {}", p.getKey());
                return !remove;
            }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        }

        /**
         * Remove all path referring types that are not conforming to the configured types {@link PathPrunner#withType(String)}
         * @param path path to examine
         * @return true if path should be pruned
         */
        private boolean removeByType(String pathName, Path path) {

            Operation get = path.getGet();
            if (get != null) {
                return toRemoveByResponse(pathName, get);
            }
            Operation post = path.getPost();
            if (post != null) {
                return toRemoveByBody(pathName, "POST", post);
            }

            Operation put = path.getPut();

            return put != null && toRemoveByBody(pathName, "PUT", put);

        }

        private boolean toRemoveByBody(String pathName, String operationName, Operation o) {
            String modelId = getFromBody(o);
            if(modelId == null) {
                log.warn("not recognized {} path {}. Skipping.", operationName, pathName);
                return false;
            }
            return inheritenceStructure(modelId, hierarchy).noneMatch(m -> types.contains(m));
        }

        private boolean toRemoveByResponse(String pathName, Operation o) {
            String modelId = getFromResponse(o, "200");
            if(modelId == null) {
                log.warn("not recognized GET path {}. Skipping.", pathName);
                return false;
            }

            return inheritenceStructure(modelId, hierarchy).noneMatch(m -> types.contains(m));
        }
    }

    private void prunePaths(Swagger swagger) {
        Predicate<String> match = startsWith(pathMatchers);
        Predicate<String> excluded = startsWith(exclude);

        if(pathMatchers.isEmpty()) return;

        Map<String, Path> paths = swagger.getPaths();
        Set<String> toRemove = paths.entrySet().stream()
                .filter(e -> !excluded.test(e.getKey()) && match.test(e.getKey()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        toRemove.forEach(p -> {
            log.debug("Removing path based on type {}", p);
            paths.remove(p);
        });
        swagger.setPaths(paths);
    }

    private Map<String, TypeNode> buildTypeHierarchy(Swagger swagger) {

        TypesUsageTreeBuilder builder = new TypesUsageTreeBuilder();
        swagger.getDefinitions().forEach((type, value) -> {
            Stream<String> references = getReferences(type, value);
            builder.referencing(type, references);
        });
        return builder.build();
    }
}
