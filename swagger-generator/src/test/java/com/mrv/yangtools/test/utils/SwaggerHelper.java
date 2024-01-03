/*
 *  Copyright (c) 2024 Amartus. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 *     Contributors:
 *       Bartosz Michalik <bartosz.michalik@amartus.com>
 */

package com.mrv.yangtools.test.utils;

import io.swagger.models.ArrayModel;
import io.swagger.models.ComposedModel;
import io.swagger.models.Model;
import io.swagger.models.Path;
import io.swagger.models.RefModel;
import io.swagger.models.Swagger;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

public class SwaggerHelper {
  private final Swagger swagger;

  public SwaggerHelper(Swagger swagger) {
    this.swagger = swagger;
  }

  public Swagger getSwagger() {
    return swagger;
  }

  public Stream<String> getReferencesFromDefinitions() {
    return fromDefinition(swagger.getDefinitions().values());
  }

  public Stream<String> getReferencesFromPaths() {
    return swagger.getPaths().values().stream()
        .flatMap(this::fromPath);
  }

  public Stream<String> fromPath(Path path) {
    return path.getOperations().stream()
        .flatMap(this::fromOperation);
  }
  public Stream<String> fromOperation(io.swagger.models.Operation operation) {
    var body =  operation.getParameters().stream()
        .filter(it -> it instanceof BodyParameter)
        .map(it -> (BodyParameter) it)
        .flatMap(it -> fromDefinition(it.getSchema()));
    var responses = operation.getResponses().values().stream()
        .flatMap(it -> fromDefinition(it.getResponseSchema()));
    return Stream.concat(body, responses);
  }



  private Optional<String> fromDefinition(Property model) {
    if(model instanceof RefProperty) {
      return Optional.of(((RefProperty) model).getSimpleRef());
    }

    if(model instanceof ArrayProperty) {
      return fromDefinition(((ArrayProperty) model).getItems());
    }

    return Optional.empty();
  }

  private Stream<String> fromDefinition(Collection<Model> models) {
    if(models == null) {
      return Stream.empty();
    }
    return models.stream().flatMap(this::fromDefinition);
  }

  private Stream<String> fromDefinition(Model model) {
    if(model == null) {
      return Stream.empty();
    }

    if(model instanceof RefModel) {
      return Stream.of(((RefModel) model).getSimpleRef());
    }

    if(model instanceof ArrayModel) {
      return fromDefinition(((ArrayModel) model).getItems()).stream();
    }

    var props = Optional.ofNullable(model.getProperties()).stream()
        .flatMap(it -> it.values().stream())
        .flatMap(it -> fromDefinition(it).stream());
    var composed = Stream.<String>empty();
    if(model instanceof ComposedModel) {
      composed = fromDefinition(((ComposedModel) model).getAllOf());
    }

    return Stream.concat(props, composed);

  }

}
