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

import java.util.Set;

import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

import com.mrv.yangtools.codegen.IoCSwaggerGenerator;

public interface SwaggerGeneratorFactory {

	public IoCSwaggerGenerator createSwaggerGenerator(SchemaContext ctx, Set<Module> modulesToGenerate);
}
