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