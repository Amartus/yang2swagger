package com.mrv.yangtools.example.ioc;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.mrv.yangtools.codegen.PathHandlerBuilder;

public class GeneratorInjector extends AbstractModule {

	@Override
	protected void configure() {

		install(new FactoryModuleBuilder().build(SwaggerGeneratorFactory.class));
		
		requestStaticInjection(IoCGeneratorHelper.class);

		bind(PathHandlerBuilder.class).to(com.mrv.yangtools.codegen.rfc8040.PathHandlerBuilder.class);

	}
}
