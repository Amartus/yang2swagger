/*
 * Copyright (c) 2018 Amartus. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Damian Mrozowicz <damian.mrozowicz@amartus.com>
 */
package com.mrv.yangtools.example.ioc;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.mrv.yangtools.codegen.PathHandlerBuilder;

public class GeneratorInjector extends AbstractModule {

	@Override
	protected void configure() {

		install(new FactoryModuleBuilder().build(SwaggerGeneratorFactory.class));
		
		requestStaticInjection(IoCGeneratorHelper.class);

		bind(PathHandlerBuilder.class).to(com.mrv.yangtools.codegen.impl.path.rfc8040.PathHandlerBuilder.class);

	}
}
