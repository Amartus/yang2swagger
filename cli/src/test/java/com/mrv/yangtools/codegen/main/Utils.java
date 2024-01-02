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
