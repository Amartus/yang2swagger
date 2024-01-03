/*
 *  Copyright (c) 2024 Amartus. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html

 *  Contributors:
 *       Bartosz Michalik <bartosz.michalik@amartus.com>
 */

package com.mrv.yangtools.codegen.main;

import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.kohsuke.args4j.CmdLineParser;

public interface Utils {
  static Swagger runParser(List<String> args) {
    Main main = new Main();
    CmdLineParser parser = new CmdLineParser(main);
    try {
      parser.parseArgument(args);
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      main.out = baos;
      main.init();
      main.generate();

      return  new SwaggerParser().parse(baos.toString(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

  }
}
