/*
 * Copyright (c) 2018 Amartus. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Bartosz Michalik <bartosz.michalik@amartus.com>
 */
package com.mrv.yangtools.codegen.impl.postprocessor;

import io.swagger.models.Swagger;

import java.util.function.Consumer;

/**
 * Replace empty definitions with definition parent
 * @author bartosz.michalik@amartus.com
 */
public class ReplaceDummy implements Consumer<Swagger> {

	@Override
	public void accept(Swagger t) {
		// Dont do anything
		
	}
}
