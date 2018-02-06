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

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author bartosz.michalik@amartus.com
 */
public class SingleParentInheritenceModel implements Consumer<Swagger> {
    private static final Logger log = LoggerFactory.getLogger(SingleParentInheritenceModel.class);
    @Override
    public void accept(Swagger swagger) {
        swagger.getDefinitions().entrySet().stream().filter(e -> {
            Model m = e.getValue();
            return m instanceof ComposedModel
                    && ((ComposedModel) m).getAllOf().stream()
                    .filter(c -> c instanceof RefModel).count() > 1;
        }).forEach(e -> {
            ComposedModel model = (ComposedModel) e.getValue();
            ModelImpl impl = (ModelImpl) model.getAllOf().stream()
                    .filter(m ->  m instanceof ModelImpl)
                    .findFirst().orElse(new ModelImpl());

            if(! model.getAllOf().contains(impl)) {
                log.debug("Adding simple model for values to unpack -  {}", e.getKey());
                model.setChild(impl);
            }

            List<RefModel> references = model.getAllOf().stream().filter(c -> c instanceof RefModel && !c.equals(model.getParent()))
                    .map(c -> (RefModel)c)
                    .collect(Collectors.toList());

            List<ModelImpl> toUnpack = references.stream()
                    .map(r -> swagger.getDefinitions().get(r.getSimpleRef()))
                    .filter(m -> m instanceof ModelImpl)
                    .map(m -> (ModelImpl) m)
                    .collect(Collectors.toList());


            if (references.size() != toUnpack.size()) {
                log.warn("Cannot unpack references for {}. Only simple models supported. Skipping", e.getKey());
            }

            log.debug("Unpacking {} models of {}", toUnpack.size(),  e.getKey());

            toUnpack.forEach(m -> copyAttributes(impl, m));
            model.getAllOf().removeAll(references);

        });

    }

    private void copyAttributes(ModelImpl target, ModelImpl source) {
        //TODO may require property copying and moving x- extensions down to properties
        source.getProperties().forEach((k,v) -> target.addProperty(k, v));
    }
}
