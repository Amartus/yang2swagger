/*
 *   Copyright (c) 2024 Amartus. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *    Contributors:
 *       Bartosz Michalik <bartosz.michalik@amartus.com>
 */

package com.mrv.yangtools.codegen.impl.postprocessor;

import io.swagger.models.Path;
import io.swagger.models.Operation;
import io.swagger.models.Response;
import io.swagger.models.RefModel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static com.mrv.yangtools.codegen.impl.postprocessor.SwaggerRefHelper.*;

/**
 * @author scott@aliroquantum.com
 */
public class SwaggerRefHelperTest extends AbstractWithSwagger {
  @Test
  public void getFromResponseTest() {
    String success = "200";
    String newModel = "NewModel";
    Path path = swagger.getPath("/b/propE");
    Operation operation = path.getGet();
    Response response = operation.getResponses().get(success);
    response.setResponseSchema(new RefModel(newModel));
    String schema = getFromResponse(operation,success);
    assertEquals(schema,newModel);
  }
}
