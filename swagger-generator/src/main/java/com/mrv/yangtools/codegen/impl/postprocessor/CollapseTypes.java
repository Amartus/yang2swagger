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

import io.swagger.models.ComposedModel;
import io.swagger.models.RefModel;
import io.swagger.models.Swagger;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import static java.util.stream.Collectors.*;

/**
 * Build replacements for reference models only
 * @author bartosz.michalik@amartus.com
 */
public class CollapseTypes extends ReplaceDefinitionsProcessor {

    private Predicate<ComposedModel> referenceOnly = m -> m.getAllOf().stream().allMatch(a -> a instanceof RefModel);
    private Function<ComposedModel, String> toSignature = m -> Integer.toHexString(
            m.getAllOf().stream()
                .filter(a -> a instanceof RefModel)
                .map(a -> ((RefModel) a).getSimpleRef().hashCode())
                .reduce((a,b) -> a + b).orElse(0)
    );


    @Override
    protected Map<String, String> prepareForReplacement(Swagger swagger) {

        Map<String, Set<String>> sameGroups = swagger.getDefinitions().entrySet().stream()
                .filter(e -> e.getValue() instanceof ComposedModel)
                .filter(e -> referenceOnly.test((ComposedModel) e.getValue()))
                .map(e -> new AbstractMap.SimpleEntry<>(toSignature.apply((ComposedModel) e.getValue()), e.getKey()))
                .collect(groupingBy(AbstractMap.SimpleEntry::getKey, mapping(AbstractMap.SimpleEntry::getValue, toSet())));

        return sameGroups.values().stream()
                .filter(v -> v.size() > 1)
                .flatMap(v -> {
                    final String type = v.stream().min(Comparator.comparingInt(String::length)).get();
                    return v.stream().filter(s -> !s.equals(type)).map(s -> new AbstractMap.SimpleEntry<>(s, type));

                }).collect(toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
    }
}
