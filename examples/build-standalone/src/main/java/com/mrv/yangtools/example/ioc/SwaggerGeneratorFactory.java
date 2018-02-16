package com.mrv.yangtools.example.ioc;

import java.util.Set;

import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

import com.mrv.yangtools.codegen.IoCSwaggerGenerator;

public interface SwaggerGeneratorFactory {

	public IoCSwaggerGenerator createSwaggerGenerator(SchemaContext ctx, Set<Module> modulesToGenerate);
}
