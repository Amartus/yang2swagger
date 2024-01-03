/*
 *  Copyright (c) 2024 Amartus All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *    Contributors:
 *       Bartosz Michalik <bartosz.michalik@amartus.com>
 */

package com.mrv.yangtools.codegen.impl.postprocessor;

import static org.junit.Assert.assertTrue;

import com.mrv.yangtools.test.utils.SwaggerHelper;
import io.swagger.models.Swagger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

public class ReplaceEmptyWithParentTest extends AbstractWithSwagger {

  @Test
  public void withEmptyParent() {
    new ReplaceEmptyWithParent().accept(swagger);

    noDanglingReferences(swagger);
  }

  private void noDanglingReferences(Swagger swagger) {
    var path = new SwaggerHelper(swagger).getReferencesFromPaths();
    var defs = new SwaggerHelper(swagger).getReferencesFromDefinitions();
    var all = Stream.concat(path, defs).collect(Collectors.toSet());

    var definitions = swagger.getDefinitions().keySet();
    var remaining = all.stream().filter(it -> !definitions.contains(it)).collect(Collectors.toSet());
    assertTrue("Dangling references: " + remaining, remaining.isEmpty());
  }

}