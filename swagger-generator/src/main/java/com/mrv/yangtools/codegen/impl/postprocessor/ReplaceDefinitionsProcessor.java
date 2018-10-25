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
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Processor that allows replacing one definitions with another in any swagger.
 * This implementation is simple and limited only to the type definitions that are aggregators of references.
 * @author bartosz.michalik@amartus.com
 */
public abstract class ReplaceDefinitionsProcessor implements Consumer<Swagger> {
    private final Logger log = LoggerFactory.getLogger(ReplaceDefinitionsProcessor.class);
    @Override
    public void accept(Swagger target) {
        Map<String, String> replacements = prepareForReplacement(target);

        log.debug("{} replacement found for definitions", replacements.size());
        log.trace("replacing paths");
        target.getPaths().values().stream().flatMap(p -> p.getOperations().stream())
                .forEach(o -> fixOperation(o, replacements));

        target.getDefinitions().forEach((key, value) -> fixModel(key, value, replacements));
        replacements.keySet().forEach(r -> {
            log.debug("removing {} model from swagger definitions", r);
            target.getDefinitions().remove(r);
        });
    }

    protected abstract Map<String, String> prepareForReplacement(Swagger swagger);

    private void fixModel(String name, Model m, Map<String, String> replacements) {
        ModelImpl fixProperties = null;
        if(m instanceof ModelImpl) {
            fixProperties = (ModelImpl) m;
        }

        if(m instanceof ComposedModel) {
            ComposedModel cm = (ComposedModel) m;
            fixComposedModel(name, cm, replacements);
            fixProperties =  cm.getAllOf().stream()
                    .filter(c -> c instanceof ModelImpl).map(c -> (ModelImpl)c)
                    .findFirst().orElse(null);
        }

        if(fixProperties == null) return;
        if(fixProperties.getProperties() == null) {
            if(fixProperties.getEnum() == null) {
                log.warn("Empty model in {}", name);
            }
            return;
        }
        fixProperties.getProperties().forEach((key, value) -> {
            if (value instanceof RefProperty) {
                if (fixProperty((RefProperty) value, replacements)) {
                    log.debug("fixing property {} of {}", key, name);
                }
            } else if (value instanceof ArrayProperty) {
                Property items = ((ArrayProperty) value).getItems();
                if (items instanceof RefProperty) {
                    if (fixProperty((RefProperty) items, replacements)) {
                        log.debug("fixing property {} of {}", key, name);
                    }
                }
            }
        });

    }
    private boolean fixProperty(RefProperty p, Map<String, String> replacements) {
        if(replacements.containsKey(p.getSimpleRef())) {
            p.set$ref(replacements.get(p.getSimpleRef()));
            return true;
        }
        return false;
    }

    private void fixComposedModel(String name, ComposedModel m, Map<String, String> replacements) {
        Set<RefModel> toReplace = m.getAllOf().stream().filter(c -> c instanceof RefModel).map(cm -> (RefModel) cm)
                .filter(rm -> replacements.containsKey(rm.getSimpleRef())).collect(Collectors.toSet());
        toReplace.forEach(r -> {
            int idx = m.getAllOf().indexOf(r);
            RefModel newRef = new RefModel(replacements.get(r.getSimpleRef()));
            m.getAllOf().set(idx, newRef);
            if(m.getInterfaces().remove(r)) {
                m.getInterfaces().add(newRef);
            }
        });
    }


    private void fixOperation(Operation operation, Map<String, String> replacements) {
        operation.getResponses().values()
                .forEach(r -> fixResponse(r, replacements));
        operation.getParameters().forEach(p -> fixParameter(p, replacements));
        Optional<Map.Entry<String, String>> rep = replacements.entrySet().stream()
                .filter(r -> operation.getDescription() != null && operation.getDescription().contains(r.getKey()))
                .findFirst();
        if(rep.isPresent()) {
            log.debug("fixing description for '{}'", rep.get().getKey());
            Map.Entry<String, String> entry = rep.get();
            operation.setDescription(operation.getDescription().replace(entry.getKey(), entry.getValue()));
        }

    }

    private void fixParameter(Parameter p, Map<String, String> replacements) {
        if(!(p instanceof BodyParameter)) return;
        BodyParameter bp = (BodyParameter) p;
        if(!(bp.getSchema() instanceof RefModel)) return;
        RefModel ref = (RefModel) bp.getSchema();
        if(replacements.containsKey(ref.getSimpleRef())) {
            String replacement = replacements.get(ref.getSimpleRef());
            bp.setDescription(bp.getDescription().replace(ref.getSimpleRef(), replacement));
            bp.setSchema(new RefModel(replacement));
        }

    }

    private void fixResponse(Response r, Map<String, String> replacements) {
        if(! (r.getSchema() instanceof RefProperty)) return;
        RefProperty schema = (RefProperty) r.getSchema();
        if(replacements.containsKey(schema.getSimpleRef())) {
            String replacement = replacements.get(schema.getSimpleRef());
            if(r.getDescription() != null)
                r.setDescription(r.getDescription().replace(schema.getSimpleRef(), replacement));
            schema.setDescription(replacement);
            r.setSchema(new RefProperty(replacement));
        }

    }
}
